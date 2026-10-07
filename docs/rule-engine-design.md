# 规则引擎设计文档

- 状态：已实现（内置规则引擎 + 用户规则：角色解析、黑名单、标签、拦截、垃圾箱、命中记录）
- 日期：2026-09-28
- 适用仓库：`dial&sms`（`src`）
- 依赖：无第三方；内置规则条件使用 JDK `Regex`，用户规则条件为声明式 JSON

---

## 目录

1. [背景与目标](#1-背景与目标)
2. [总体架构](#2-总体架构)
3. [角色语义（共识）](#3-角色语义共识)
4. [角色处理管线](#4-角色处理管线)
5. [内置规则引擎](#5-内置规则引擎)
6. [用户规则](#6-用户规则)
7. [用户规则求值](#7-用户规则求值)
8. [执行与落库](#8-执行与落库)
9. [接入点](#9-接入点)
10. [数据表](#10-数据表)
11. [界面](#11-界面)
12. [文件清单](#12-文件清单)
13. [测试](#13-测试)
14. [已知限制与后续方向](#14-已知限制与后续方向)
15. [附录](#15-附录)

---

## 1. 背景与目标

规则能力（分类、拦截、筛选、角色路由）原先以硬编码形式散落在 Receiver、Service、Repository 与 ViewModel 中：

- 角色识别：`RolePrefixer` + `ConversationResolver`，在 4 处以上重复调用；
- 出站路由：`applyPrefix` + `subscriptionId` 在 5 条出站路径上各写一遍；
- 拦截判定：号码/关键词/未知号码分散在不同入口。

现状分为两层：

1. **内置规则引擎**（`domain/rule`）：`CompiledRule` 由「触发集合 + 优先级 + 可选正则条件 + 动作」构成，负责角色解析与系统黑名单，优先级首胜、行为确定；
2. **用户规则引擎**（`domain/rule/user`）：结构化条件（对象-操作符-值 + AND/OR）与动作（标签 / 拦截 / 垃圾箱），面向管理界面编排，多条规则的标签叠加；
3. 两层都**只做决策**，落库/通知/拨号/丢弃等副作用留在 `data/rules` 与调用方；
4. 角色操作全部包装 `RolePrefixer` / `RoleResolver`，不复制逻辑。

用户规则不用正则，而是「对象 + 操作符 + 值」的声明式条件：既能直接用界面控件表达，又避免用户输入正则带来的回溯与转义问题。

## 2. 总体架构

```text
入站事件（SmsReceiver / MmsReceiver / CallScreening / CallLog 同步 / 系统消息同步）
   │  IncomingRuleExecutor.evaluateBuiltin
   ▼
内置规则引擎（RuleEngine + BuiltinRuleSource）
   │  Block → 丢弃；AssignRole → ResolvedNumber（剥离前缀）
   ▼
UserRuleExecutor.evaluate（UserRuleStore 内存缓存 + Room）
   │  blocked → 不入库不通知，只写 rule_hits 快照
   │  tags / trash → 入库后 applyToStored（message_tags / 回收站 / rule_hits）
   ▼
本地数据库（conversations.db）

出站事件（发送短信 / 定时消息 / 通知直接回复 / 线程 id / 拨号）
   │  RuleEngines.engine.routeOutgoing
   ▼
内置规则引擎（ApplyPrefix + AssignSubscription）
   ▼
OutgoingOutcome（加前缀后的号码 + 订阅 + 角色）
```

核心原则：

1. `domain/rule` 为纯逻辑（无 Android 依赖），可单测；
2. 内置引擎无状态，进程级单例 `RuleEngines.engine`（Hilt 侧 `di/RuleModule` 提供同一实例）；
3. 用户规则的存取与执行在 `data/rules`，无 Hilt 的 Receiver 直接使用 `UserRuleStore`，内存缓存 + 写时失效。

## 3. 角色语义（共识）

- **小号是基于 SIM 主号的虚拟号码**；一个角色（`Role`）代表一个小号。
- 每个虚拟角色持有两个前缀：
  - `smsPrefix`：信息前缀（短信 / 彩信 / 通知直接回复 / 定时消息）；
  - `callPrefix`：拨号前缀（来电 / 拨出电话 / 通话记录）。
- 前缀**按通道区分、不回退**：某通道前缀为空，则该通道不识别、不加前缀。
- 判断小号来电/短信：对端号码带有该通道前缀（最长匹配）。
- 使用小号发送或拨号：向对端号码添加该通道前缀。

## 4. 角色处理管线

管线保持线性、分支最少：

```kotlin
enum class RoleChannel { SMS, CALL }

// Role
fun prefixFor(channel: RoleChannel): String   // 通道前缀；空表示该通道不生效

// 入站：前缀（最长）→ 订阅 SIM → 兜底 SIM 角色
fun resolveIncoming(channel, address, subscriptionId, roles, fallbackRoleId): ResolvedNumber? {
    val matched = RolePrefixer.matchRole(address, roles, channel)          // 1
    val role = matched
        ?: roles.firstOrNull { !it.isVirtual && it.subscriptionId == subscriptionId } // 2
        ?: fallbackSimRole(roles, fallbackRoleId)                          // 3
        ?: return null
    return ResolvedNumber(
        peerNumber = matched?.let { RolePrefixer.stripPrefix(address, it, channel) } ?: address,
        role = role,
    )
}

// 出站：角色 → 按通道加前缀 → 角色订阅
fun resolveOutgoing(channel, address, role): OutgoingRoute =
    OutgoingRoute(RolePrefixer.applyPrefix(address, role, channel), role?.subscriptionId, role?.id)
```

- `RoleResolver`（`domain/RoleResolver.kt`）是上述管线的唯一实现，同时被内置规则的 `ValueSource.IncomingRoleId` 调用；
- `fallbackRoleId` 由上下文提供：短信/彩信/来电为当前角色，**通话记录为 null**（保持原有差异）；兜底角色只能是 SIM 角色——当前角色为虚拟角色时不作为兜底，改用指定 id 的 SIM 角色、默认 SIM 角色或第一个启用的 SIM 角色（虚拟角色只能由前缀识别）；
- 前缀匹配会归一化（去空格、保留 `+`），同通道多前缀取最长；出站已带任一已知前缀的号码不会重复加前缀（`RolePrefixer.isAlreadyPrefixed`）；
- 仅「虚拟角色 + 已启用 + 该通道前缀非空」参与前缀识别与添加；加前缀还要求号码可加前缀（≥7 位数字、非紧急/服务号码、不含 `*`/`#`）。

## 5. 内置规则引擎

### 5.1 事件与触发

```kotlin
sealed interface RuleEvent {
    val channel: RoleChannel
    val address: String
    val subscriptionId: Int?
    val timestamp: Long
}

enum class IncomingKind { SMS, MMS, CALL, CALL_LOG }

data class IncomingEvent(kind, address, subscriptionId, timestamp) : RuleEvent
data class OutgoingEvent(address, channel, requestedRoleId, subscriptionId, timestamp) : RuleEvent

enum class RuleTrigger {
    SMS_IN, MMS_IN, CALL_IN, CALL_LOG, MSG_OUT, CALL_OUT;
    fun accepts(event: RuleEvent): Boolean
}
```

### 5.2 动作与动态参数

```kotlin
@Serializable sealed interface ValueSource {
    @Serializable data class Literal(val value: String) : ValueSource
    @Serializable data object IncomingRoleId : ValueSource
    @Serializable data object RequestedRoleId : ValueSource
    @Serializable data object RequestedRoleSubscriptionId : ValueSource
}

@Serializable sealed interface RuleAction {
    @Serializable data class AssignRole(val role: ValueSource, val stripPrefix: Boolean = false) : RuleAction
    @Serializable data class ApplyPrefix(val role: ValueSource) : RuleAction
    @Serializable data class AssignSubscription(val subscriptionId: ValueSource) : RuleAction
    @Serializable data object Block : RuleAction
}
```

动态动作参数全部声明式（`ValueSource`），不存在任意表达式。

### 5.3 上下文

```kotlin
interface RuleContext {
    val roles: List<Role>
    val activeRoleId: Long?
    val fallbackRoleId: Long?
    val now: Long
    val isBlocked: (String) -> Boolean

    fun roleById(id: Long?): Role?
    fun simRoleForSubscription(subscriptionId: Int?): Role?
}

data class RoleRuleContext(
    roles, activeRoleId = null, fallbackRoleId = null,
    requestedRole: Role? = null, now = System.currentTimeMillis(),
    isBlocked = { false },
) : RuleContext
```

`requestedRole` 用于查找不在 `roles` 列表中的显式指定角色（如拨号盘 override）；`isBlocked` 提供系统黑名单号码查询（无 Hilt 的调用方由 `IncomingRuleExecutor` 注入）。

### 5.4 规则与结果

```kotlin
class CompiledRule(
    id: String, name: String, priority: Int, triggers: Set<RuleTrigger>,
    pattern: Regex?, actions: List<RuleAction>,
    matcher: ((RuleEvent, RuleContext) -> Boolean)? = null,
)

interface RuleSource { fun rules(): List<CompiledRule> }

enum class RuleDecision { ALLOW, BLOCK, SILENT }   // SILENT 保留未用

data class IncomingOutcome(decision = ALLOW, resolution: ResolvedNumber?, tags: Set<String>, hits: List<RuleHit>)
data class OutgoingOutcome(number, subscriptionId, roleId, tags: Set<String>, hits: List<RuleHit>)
data class RuleHit(ruleId, ruleName, error: String?)
fun OutgoingOutcome.toRoute(): OutgoingRoute
```

### 5.5 内置规则（`builtin/`）

| id | 触发 | 优先级 | 条件 | 动作 |
| --- | --- | --- | --- | --- |
| `builtin.blacklist` | SMS_IN / MMS_IN / CALL_IN / CALL_LOG | 200 | `matcher`：`ctx.isBlocked(address)` | `Block` |
| `builtin.role.resolution` | SMS_IN / MMS_IN / CALL_IN / CALL_LOG | 150 | 始终（`pattern = null`） | `AssignRole(IncomingRoleId, stripPrefix = true)` |
| `builtin.role.outgoing` | MSG_OUT / CALL_OUT | 150 | 始终（`pattern = null`） | `ApplyPrefix(RequestedRoleId)`、`AssignSubscription(RequestedRoleSubscriptionId)` |

黑名单数据来自系统拦截名单（`getBlockedNumbers`），命中即丢弃（短信/彩信不入库不通知，来电由 `CallScreeningService` 拒绝并跳过通话记录与通知）。

### 5.6 执行语义

- 规则按 `priority` 降序、同优先级按 id 排序；
- **首胜**：
  - 入站 `resolution` 取第一条产生 `AssignRole` 的规则；`Block` 一旦命中即拦截（只记一次命中）；两者互不覆盖；
  - 出站 `ApplyPrefix` 与 `AssignSubscription` 各自首胜，全部有结果后提前结束求值；
- 条件为 `pattern?.containsMatchIn(address) ?: true` 与可选 `matcher` 的与：`pattern = null` 表示始终匹配，不匹配则跳过该规则；
- 内置规则的正则由定义在构造期编译，匹配过程无异常；
- 求值顺序固定，引擎无状态、可重入。

### 5.7 声明式取值（`ValueSource`）

| 取值 | 返回 | 说明 |
| --- | --- | --- |
| `Literal(value)` | 字符串 | 字面量 |
| `IncomingRoleId` | roleId 或 null | 调用 `RoleResolver.resolveIncoming`（前缀 → 订阅 SIM → 兜底角色） |
| `RequestedRoleId` | roleId 或 null | 出站事件的 `requestedRoleId` |
| `RequestedRoleSubscriptionId` | subscriptionId 或 null | `requestedRoleId` 对应角色在 `roles` 中的订阅 |

| 取值 | 入站 | 出站 |
| --- | --- | --- |
| `Literal` | ✓ | ✓ |
| `IncomingRoleId` | ✓ | — |
| `RequestedRoleId` | — | ✓ |
| `RequestedRoleSubscriptionId` | — | ✓ |

## 6. 用户规则

### 6.1 模型

```kotlin
enum class UserRuleAction { ADD_TAG, BLOCK, TRASH }

data class UserRule(
    val id: Long,
    val name: String,
    val action: UserRuleAction,
    val tags: List<String>,
    val conditions: List<RuleCondition>,
    val enabled: Boolean = true,
    val sortOrder: Int = 0,
)

data class RuleCondition(
    val field: RuleObject,
    val operator: RuleOperator,
    val value: String,
    val connector: RuleConnector? = null,   // 连接下一条条件，最后一条忽略，null 按 AND
)
```

- 一条规则只有一个动作；`tags` 对任意动作都可选（ADD_TAG 至少一个），多条规则的标签会叠加；
- 条件列表为空 → 该规则永不命中；
- 条件以 JSON 存 `user_rules.expression`（`UserRuleExpression.encode/decode`，`ignoreUnknownKeys`）；解析失败按空条件处理，不影响其他规则。

### 6.2 条件对象与操作符

| 对象 | 操作符 | 值 |
| --- | --- | --- |
| `PHONE` | CONTAINS / STARTS_WITH / ENDS_WITH / EQUALS | 文本，匹配忽略大小写 |
| `CONTENT` | CONTAINS / STARTS_WITH / ENDS_WITH / EQUALS | 文本，匹配忽略大小写 |
| `ROLE` | EQUALS | 角色 id |
| `SMS_RECORD` | EQUALS | `INBOX` / `SENT` / `ANY` |
| `SMS_RECORD` | BEFORE / AFTER | 绝对毫秒时间戳 |
| `SMS_RECORD` | WITHIN_DAYS / BEFORE_DAYS | 负毫秒偏移（N 天） |
| `CALL_RECORD` | EQUALS | `INCOMING` / `OUTGOING` / `MISSED` / `REJECTED` / `BLOCKED` / `VOICEMAIL` / `ANSWERED_EXTERNALLY` / `NOT_CONNECTED` / `ANY` |
| `CALL_RECORD` | BEFORE / AFTER / WITHIN_DAYS / BEFORE_DAYS | 同短信 |

- `RULE_RECORD_ANY = "ANY"`：记录类对象在「等于」下匹配全部状态（全部短信 / 全部通话）；
- MMS 归入短信（`RecordKind.SMS`）；
- 求值按 `RuleObject` 分派：`PHONE` 匹配 `address`、`CONTENT` 匹配 `body`、`ROLE` 匹配 `roleId`，记录类先校验 `kind` 再匹配方向/结果或时间。

### 6.3 时间语义

- `value` 为毫秒字符串：`< 0` 表示相对求值时刻的偏移（`now + value`，如 `-604800000` = 7 天前），`>= 0` 为绝对时间戳；
- `BEFORE` / `BEFORE_DAYS`：`timestamp < target`；`AFTER` / `WITHIN_DAYS`：`timestamp > target`；
- 旧规则 `BEFORE/AFTER` + 负值仍按相对时间求值（兼容存量的负毫秒偏移）；
- 界面输入「N 天以内 / N 天以前」，存储为负毫秒偏移。

### 6.4 求值

`UserRuleEngine.evaluate` 对**每条启用规则**求值（不是首胜），结果合成：

- `blocked`：任一命中规则为 `BLOCK`；
- `trash`：未被拦截且任一命中规则为 `TRASH`；
- `tags`：命中规则的标签去重并集；
- `hits`：全部命中的规则（用于写命中记录与后续清理）。

`UserRuleExpression.evaluate` 从左到右折叠并短路：连接符取前一条条件的 `connector`（null 按 AND）；AND 且当前为 false 时跳过后续直到 OR，OR 且当前为 true 时同理。

### 6.5 记录映射

`Message.toRuleRecord(roleId)`（`data/rules/RuleRecordMapper.kt`）：

- 短信/彩信 → `RecordKind.SMS` + `SmsDirection`（收件箱/已发送）；
- 通话（`is_call = 1`）→ `RecordKind.CALL` + `callKindOf(callType, duration)`：呼出且无时长 → `NOT_CONNECTED`，其他无时长 → `NOT_CONNECTED`，有通话时长的默认 → `INCOMING`；
- `address` 使用会话对端号码（已剥离前缀），`roleId` 取自所属会话。

### 6.6 手动运行

- 规则列表每行提供「运行」（`UserRuleExecutor.runRule`）；仓库层另有 `runAll` 批量入口（过滤启用且非 BLOCK 的规则）；
- 拦截规则不参与手动运行；
- 运行即重建：先清空这些规则的历史命中，再按当前条件重记，避免陈旧与重复；
- 整批固定同一个 `now`，消息按 200 条分块遍历。

### 6.7 命中清理

- 编辑规则的条件或动作（`expression` / `action` 变化）→ 清空该规则命中；改名、改标签、启停不清空；
- 删除规则 → 一并删除其命中与规则-标签关联；
- 手动运行 → 先清空后重记（见 6.6）。

## 7. 执行与落库

### 7.1 拦截（入库前）

`UserRuleExecutor.evaluate` 返回 `blocked` 时调用 `recordBlockedHits`：记录**不入库、不通知**，只写 `rule_hits` 快照（`message_id = 0`，保存号码、标题、正文、记录类型、通话方向/结果与时长、记录时间）。彩信在下载前还有一次 `IncomingRuleExecutor.isBlocked` 判定（`MmsReceiver.isAddressBlocked`）。

### 7.2 已入库记录

`UserRuleExecutor.applyToStored`：

- 拦截或无任何动作 → 直接返回；
- `trash` 且启用回收站 → `moveMessageToRecycleBin`（回收站关闭时 TRASH 不生效）；
- 逐条命中规则写 `message_tags`（携带 rule id）与 `rule_hits`（`message_id != 0` 且尚不存在时才写，避免系统多批同步造成重复命中；拦截快照 id 为 0 不判重）。

### 7.3 标签

- 标签存 `user_tags`，内置「验证码」（`BuiltinTags`）`is_builtin = 1`，不可删除；
- 保存规则时 `ensureTags` 自动补建标签；删除自定义标签会停用引用它的规则；
- 标签当前写入 `message_tags` 供后续扩展，不在会话/聊天界面展示。

### 7.4 回收站联动

- 存在 TRASH 规则或回收站非空时，设置中回收站开关不可关闭（`SettingsScreen` 计算 `recycleBinLocked`）；
- 回收站自动清空是独立定时任务，见 [scheduler.md](scheduler.md)。

### 7.5 命中记录管理

命中页可多选删除（二次确认）：先删除选中的命中，再删除仍存在的关联消息/通话（已删除的跳过）；「删除」在启用回收站时移入回收站（否则等同彻底删除），「彻底删除」直接删系统通话记录或本地消息。删除后发 `RefreshMessages` / `RefreshCallLog` 事件。

## 8. 接入点

| 场景 | 内置引擎 | 用户规则 | 调用方 |
| --- | --- | --- | --- |
| 短信接收 | Block / AssignRole | 入库前 block（快照）；入库后 tags / trash | `SmsReceiver` |
| 彩信接收 | Block / AssignRole | 同上 | `MmsReceiver` |
| 来电 | Block / AssignRole | block（`CallScreeningService` 拒绝、跳过记录与通知） | `SimpleCallScreeningService` |
| 系统短信线程同步 | —（角色由调用方给定） | 仅新消息：入库前 block；入库后 tags / trash | `ConversationRepositoryImpl.persistSystemMessages` |
| 通话记录同步 | AssignRole（CALL_LOG，无兜底） | 仅新记录：入库后 tags / trash（拦截不改变历史记录入库） | `CallLogRepositoryImpl.syncCalls` |
| 发送短信 | ApplyPrefix / AssignSubscription | — | `MessagingRepositoryImpl.sendMessage` |
| 定时消息 | 同上 | — | `SendMessageTaskHandler`（见 scheduler.md） |
| 通知直接回复 | 同上 | — | `DirectReplyReceiver` |
| 线程 id 查询 | 同上 | — | `ConversationRepositoryImpl.getSystemThreadId` |
| 拨号（会话内） | 同上 | — | `ThreadViewModel.callNumber` |
| 拨号（拨号盘） | 同上 | — | `DialpadViewModel.placeCall` |
| 回拨（来电通知等） | 同上 | — | `CallLauncher` |

补充：短信与来电另有「屏蔽未知号码」「屏蔽隐藏号码」设置，分别在 `SmsReceiver` 与 `SimpleCallScreeningService` 中独立判定，与规则引擎的号码黑名单并行。

## 9. 数据表

`conversations.db`（当前 `version = 1`，schema 见 `data/local/MessagesDatabase.kt`）中与规则相关的表：

| 表 | 关键字段 | 说明 |
| --- | --- | --- |
| `user_rules` | id, name, action, expression(JSON), enabled, sort_order, created_at | 规则主表 |
| `user_rule_tags` | rule_id, tag | 规则-标签关联（主键联合） |
| `user_tags` | name, is_builtin, created_at | 标签表，内置「验证码」 |
| `message_tags` | message_id, tag, rule_id, created_at | 消息标签（主键联合） |
| `rule_hits` | id, rule_id, action, record_kind, call_type, call_duration, message_id, address, title, body, call_kind, record_date, hit_at | 命中记录 / 拦截快照 |

## 10. 界面

- **首页 TAB「规则」**（`UserRulesRoute` → `UserRulesScreen` / `UserRulesViewModel`）：按动作分组的规则列表（开关、单条运行、编辑、删除）；顶部入口管理标签列表与系统黑名单号码；
- **规则编辑**（`UserRuleEditRoute` → `UserRuleEditScreen` / `UserRuleEditViewModel`）：名称、动作选择（「移入垃圾箱」需先启用回收站）、标签多选、条件行（第一行 `[筛选主体][条件][条件值]`，第二行 `[与|或]` 追加条件与删除）；
- **首页 TAB「管理」规则命中页**（`UserRuleRecordsRoute` → `UserRuleRecordsScreen` / `UserRuleRecordsViewModel`）：按动作 Tab 分组、按规则折叠命中记录；「多选」进入多选模式（顶部「全选」，悬浮工具栏「删除」「彻底删除」均需二次确认）；右上角回收站入口（未启用时置灰并提示去设置开启）；通话记录显示「呼出电话 · 3:06」「呼入电话 · 已拒接」式的方向 + 时长/状态。

## 11. 文件清单

内置引擎（`domain/rule` + `domain`）：

| 文件 | 说明 |
| --- | --- |
| `domain/model/Role.kt` | `RoleChannel`、双前缀 `Role.prefixFor(channel)` |
| `domain/RolePrefixer.kt` | 通道化的前缀匹配/剥离/添加 |
| `domain/RoleResolver.kt` | `ResolvedNumber` / `OutgoingRoute` / 入站出站管线 |
| `domain/rule/RuleEvent.kt` / `RuleTrigger.kt` | 事件与触发类型 |
| `domain/rule/RuleAction.kt` | 动作与 `ValueSource` |
| `domain/rule/RuleContext.kt` | 上下文与 `RoleRuleContext` |
| `domain/rule/RuleOutcome.kt` | 结果、`RuleHit`、`toRoute()` |
| `domain/rule/CompiledRule.kt` / `RuleSource.kt` | 规则模型与来源 |
| `domain/rule/RuleEngine.kt` | 求值主循环 |
| `domain/rule/builtin/RoleRules.kt` / `BlacklistRules.kt` | 内置规则 |
| `domain/rule/RuleEngines.kt` | 进程级单例 |
| `di/RuleModule.kt` | Hilt 绑定 |

用户规则（`domain/rule/user` + `data/rules` + `data` + `ui`）：

| 文件 | 说明 |
| --- | --- |
| `domain/rule/user/UserRule.kt` | 规则模型与动作枚举 |
| `domain/rule/user/RuleCondition.kt` | 条件、对象、操作符、连接符、值类型 |
| `domain/rule/user/RuleRecord.kt` | 统一记录视图与枚举 |
| `domain/rule/user/UserRuleEngine.kt` | 结果合成（拦截 / 垃圾箱 / 标签） |
| `domain/rule/user/UserRuleExpression.kt` | 条件 JSON 编解码与求值 |
| `domain/rule/user/BuiltinTags.kt` | 内置标签 |
| `data/rules/UserRuleStore.kt` | 存取、内存缓存、标签与命中写入 |
| `data/rules/UserRuleExecutor.kt` | 拦截快照、入库后处理、手动运行 |
| `data/rules/IncomingRuleExecutor.kt` | 内置判定统一入口（Receiver 无 Hilt） |
| `data/rules/RuleRecordMapper.kt` | `Message → RuleRecord` |
| `data/model/UserRuleEntity.kt` / `UserRuleTagEntity.kt` / `UserTagEntity.kt` / `MessageTagEntity.kt` / `RuleHitEntity.kt` | Room 实体 |
| `data/local/dao/UserRulesDao.kt` / `UserTagsDao.kt` / `MessageTagsDao.kt` / `RuleHitsDao.kt` | DAO |
| `repository/UserRuleRepository.kt` / `data/repository/UserRuleRepositoryImpl.kt` | 仓库与界面用例 |
| `ui/settings/UserRulesScreen.kt` / `UserRuleEditScreen.kt` / `UserRuleRecordsScreen.kt` / `RuleUi.kt` | 界面 |
| `viewmodel/UserRulesViewModel.kt` / `UserRuleEditViewModel.kt` / `UserRuleRecordsViewModel.kt` | ViewModel |

## 12. 测试

- `domain/RolePrefixerTest`：通道前缀、最长匹配、通道隔离、空前缀不生效、剥离；
- `domain/RoleResolverTest`：入站三段管线、出站路由、通话记录无兜底；
- `domain/rule/RuleEngineTest`：优先级首胜、正则命中/不命中、内置规则集成、黑名单；
- `domain/rule/user/UserRuleEngineTest`：拦截优先、标签叠加、垃圾箱与拦截互斥；
- `domain/rule/user/UserRuleExpressionTest`：连接符折叠短路、文本匹配、记录类 `ANY`、绝对/相对时间条件。

验证命令：

```
.\gradlew.bat testCoreDebugUnitTest
.\gradlew.bat assembleCoreDebug
```

## 13. 已知限制与后续方向

- `RuleDecision.SILENT`（落库不通知）已保留但未实现；
- 尚无 `Allow`（显式放行/白名单）与 `Delete` 动作；用户规则的拦截无条件优先，白名单需要引入优先级或顺序模型；
- 标签不在会话/聊天界面展示，仅用于规则管理与后续扩展；
- 关键词类拦截已由用户规则的 `CONTENT` 条件覆盖，旧的「屏蔽关键词」设置项已移除；
- 用户规则常驻内存、逐条全量求值，暂未做编译/索引缓存，适合中小规模规则数量。

## 14. 附录

### 14.1 用户规则存储示例

以「短信内容包含验证码 → 打上验证码标签」为例：

`user_rules` 行：

| 字段 | 值 |
| --- | --- |
| name | 验证码分类 |
| action | `ADD_TAG` |
| expression | 见下方 JSON 字符串 |

`expression`（条件列表的 JSON 字符串）：

```json
[
  {
    "field": "CONTENT",
    "operator": "CONTAINS",
    "value": "验证码",
    "connector": null
  }
]
```

标签关联存 `user_rule_tags`（`rule_id` + `tag`）。

### 14.2 术语表

| 术语 | 含义 |
| --- | --- |
| 角色 / 小号 | 基于 SIM 主号的虚拟号码，持有信息前缀与拨号前缀 |
| 通道 | `SMS`（信息）/ `CALL`（拨号），决定使用哪个前缀 |
| 事件 | 一次待判定的输入 |
| 内置规则 | 触发集合 + 优先级 + 正则/匹配器 + 动作列表，首胜 |
| 用户规则 | 条件列表 + 单一动作（标签/拦截/垃圾箱），全部命中共同生效 |
| 首胜 | 内置引擎中优先级最高的匹配规则生效，后续不覆盖 |
| 命中 | 条件匹配成功且动作产生效果 |
| 快照 | 拦截记录未入库，`rule_hits` 中保存的展示副本 |
