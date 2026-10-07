# ChatS IM 开发者指南

面向开发者的构建、架构与设计说明。项目简介、功能范围、用户政策与贡献方式见仓库根目录 [README.md](../README.md)；专项设计见 [规则引擎](rule-engine-design.md) 与 [定时任务调度](scheduler.md)。

## 技术栈与构建变体

- 包名：`io.github.kylinlee.chatsim`（debug 后缀 `.debug`）
- 技术栈：Kotlin + Jetpack Compose（Material3）+ Hilt + Room + Navigation3 + kotlinx.serialization + Coil
- 语言：仅提供 `en` 与 `zh-rCN`
- 构建变体：`core` / `fdroid`；开发与验证统一用 `core`

## 构建与运行

环境要求：

- JDK 17
- Android SDK（`compileSdk 36`，含对应 build-tools）
- 可选环境变量：`JAVA_HOME`、`ANDROID_HOME`、`GRADLE_USER_HOME`

构建 debug APK：

```
.\gradlew.bat assembleCoreDebug
```

产物：

```
app\build\outputs\apk\core\debug\chatsim-core-debug.apk
```

运行单元测试：

```
.\gradlew.bat testCoreDebugUnitTest
```

安装到设备：

```
adb install -r app\build\outputs\apk\core\debug\chatsim-core-debug.apk
```

> 注意：本仓库路径包含 `&` 字符，`gradlew.bat` 中使用的是带引号的 `set "VAR=..."` 写法，请勿改回 `set VAR=...`，否则命令行会在 `&` 处被截断。

`adb` 不在 PATH 时使用 `$env:ANDROID_HOME\platform-tools\adb.exe`。

## 目录结构

```
app/src/main/kotlin/io/github/kylinlee/chatsim/
├── ui/            Compose 界面（Screen 与组件）
├── viewmodel/     Hilt ViewModel（StateFlow 状态 + 业务编排）
├── repository/    仓库接口
├── data/
│   ├── repository/ 仓库实现
│   ├── local/      Room 数据库、DAO、SharedPreferences 配置、角色存储
│   ├── model/      数据模型（Room 实体等）
│   ├── messaging/  短信发送（SmsManager 封装）
│   ├── rules/      用户规则执行、存储与命中（无 Hilt 的 Receiver 直接使用）
│   ├── scheduler/  持久化定时任务调度（见 docs/scheduler.md）
│   ├── legacy/     从原项目迁移的兼容代码（系统短信库读写、通话管理等）
│   └── events/     全局事件总线
├── domain/        纯逻辑（无 Android 依赖，可单测）
│   ├── rule/      内置规则引擎与用户规则模型（见 docs/rule-engine-design.md）
│   └── scheduler/ 定时任务模型、对账策略与调度计算
├── navigation/    Navigation3 路由
├── di/            Hilt 模块
└── background/    Service / Receiver（短信、来电、通知、定时消息）
```

## 架构

依赖方向：`ui → viewmodel → repository / domain → data`

- **界面层**：Compose，只做展示与事件回调
- **ViewModel**：用 `StateFlow` 暴露状态，界面用 `collectAsStateWithLifecycle()` 订阅
- **仓库层**：接口在 `repository/`，实现在 `data/repository/`，通过 Hilt `@Singleton` 注入
- **领域层**：`domain/` 放纯逻辑，例如 `RolePrefixer`、`RoleResolver`、`domain/rule`（内置规则引擎与用户规则）、`domain/scheduler`（定时任务模型与对账策略）、`ConversationMerger`、`TimelineMerger`、`CallLogGrouper`、`ContactFilter`、`ContactIndex`、`ContactSections`、`SimContactImporter`、`ContactNames`
- **无 Hilt 的调用方**（Receiver、兼容层）直接使用进程级单例：`domain/rule/RuleEngines`、`data/rules/UserRuleStore`、`data/scheduler/TaskScheduler`
- **跨页面刷新**：`data/events/AppEventBus`（全局 SharedFlow）+ `domain/model/AppEvent`（sealed interface）

## 核心设计

### 角色（小号）

角色模型（`domain/model/Role.kt`）：

```kotlin
Role(id, label, kind = SIM|VIRTUAL, smsPrefix, callPrefix, subscriptionId, enabled, isDefault)
```

- **SIM 角色**：每张活动 SIM 卡一个，无前缀，绑定 `subscriptionId`
- **虚拟角色（小号）**：基于 SIM 主号的虚拟号码，持有两个前缀——`smsPrefix`（信息前缀）与 `callPrefix`（拨号前缀），可绑定 `subscriptionId` 指定用哪张卡发送/拨打
- 前缀**按通道区分、不回退**：`Role.prefixFor(RoleChannel.SMS|CALL)`，某通道前缀为空则该通道不识别、不加前缀

前缀规则（`domain/RolePrefixer.kt`，全部按通道）：

- 仅对 **≥7 位数字**的号码加前缀
- 紧急/服务号码（`110`、`119`、`112`、`911`、`000` 等，见 `EMERGENCY_NUMBERS`）与含 `*`/`#` 的号码不加前缀
- 匹配时先归一化号码（去空格、保留 `+` 号）；同通道多个前缀同时匹配时取**最长前缀**
- 已带该通道前缀的号码不会重复添加；出站时若号码已匹配任一已知角色前缀则整体跳过加前缀（`isAlreadyPrefixed`）
- 仅「虚拟角色 + 已启用 + 该通道前缀非空」生效

角色处理管线（`domain/RoleResolver.kt`，线性、分支最少）：

- 入站：虚拟前缀（最长）→ 订阅 SIM 角色 → 兜底角色（仅 SIM 角色；通话记录无兜底）
- 出站：指定角色 → 按通道加前缀 → 角色订阅
- 该管线是 `RoleResolver` 的唯一实现，同时被规则引擎的内置规则调用（详见 [rule-engine-design.md](rule-engine-design.md)）

存储与仓库：

- `data/local/RoleStore`（`SharedPrefsRoleStore`）：以 JSON 保存角色列表与当前角色 id
- `repository/RoleRepository` / `RoleRepositoryImpl`：暴露 `roles`、`activeRole` 状态流
- `ensureSimRoles()`：为每个活动订阅补齐 SIM 角色（App 启动时调用）
- `isAvailable(role)`：角色是否可用（取决于订阅是否仍活动）
- 当前角色回退顺序：指定 id → `isDefault` → 第一个 SIM 角色 → 第一个启用角色

角色在业务中的应用：

| 场景 | 位置 |
| --- | --- |
| 会话唯一键 `(role_id, peer_number)` | `data/model/Conversation.kt`（Room 唯一索引，peerNumber 已剥离前缀） |
| 入站角色识别 | `IncomingRuleExecutor.evaluateBuiltin`（`SmsReceiver` / `MmsReceiver` / `CallNotificationManager` / `ConversationRepositoryImpl` / `CallLogRepositoryImpl`） |
| 出站角色路由 | `RuleEngines.engine.routeOutgoing`（`MessagingRepositoryImpl` / `ThreadViewModel` / `DialpadViewModel` / `SendMessageTaskHandler` / `DirectReplyReceiver` / `getSystemThreadId` / `CallLauncher`） |
| 角色管理界面 | `ui/settings/RolesScreen.kt` + `RolesViewModel`（信息前缀 / 拨号前缀两个输入框） |
| 会话列表角色过滤 | `ConversationsViewModel.applyFilters`（顶部下拉切换当前角色） |

### 会话与消息

- **存储**：Room 数据库 `conversations.db`（当前 `version = 1`，最新 schema 即初始版本，schema 在 `app/schemas/`）：
  - `conversations`：会话唯一键 `(role_id, peer_number)`，`peer_number` 已剥离小号前缀
  - `messages`：同时存放消息与通话记录（`is_call = 1`，id 取通话记录 id 的负值），会话的数据操作（删除/已读/检索）对两者统一生效
  - `attachments` / `message_attachments` / `recycle_bin_messages`
  - 用户规则、标签、命中与定时任务另有数据表，见对应设计文档
  - 改表结构必须升版本并写迁移（`data/local/MessagesDatabase.kt`）
- **会话聚合**：`ConversationMerger.merge(smsThreads, calls)` 把短信线程与通话记录按 `(roleId, peerNumber)` 合并；仅通话的会话由通话同步时落库（`getOrCreateConversation`），不是内存合成
- **聊天时间线**：`TimelineRepositoryImpl.getTimeline` → 消息与通话都从本地库读取 → `TimelineMerger.merge`；时间线中的通话逐条显示不合并，状态文案见 `ui/common/Formatters.kt` 的 `callStatusLabel`，时长带「通话时长」标注
- **消息同步**：`ConversationRepositoryImpl.syncWithSystem()` 读取系统线程并写入本地库
- **送达报告**：设置启用后发送短信时请求送达报告（`SmsSender`）；`SmsStatusDeliveredReceiver` 写入本地 `messages.delivery_status`（系统同步覆盖本地行时保留该列）并弹出「送达报告」通知；聊天气泡时间右侧两个状态图标——第一个表示本机是否发出（等待=时钟、失败=✗、成功=✓），第二个仅在启用送达报告时显示（未报告=时钟、送达=✓、失败=✗）
- **定时消息与通知直接回复**：影子消息 + 任务调度与触发路径见 [scheduler.md](scheduler.md)
- **回收站**：`moveMessageToRecycleBin` / `purgeRecycleBinMessages` 与 `recycle_bin_messages` 表；自动清空调度见 [scheduler.md](scheduler.md)
- **用户规则**：入库前拦截、标签与垃圾箱等行为与数据表见 [rule-engine-design.md](rule-engine-design.md)
- **消息横幅通知（heads-up）**：自定义横幅布局（`layout/message_notification_heads_up.xml`）默认展示展开内容——发件人 + 完整正文；锁屏通知可见性为「仅发送人」时正文替换为「新消息」，为「不显示」时不使用自定义横幅。展开/折叠交互本身由系统 SystemUI 控制，应用无法改写

### 联系人

- 数据来源：系统联系人（本地 `data/contacts/ContactsHelper`）+ 缓存 + 号码索引；不支持应用私有来源
- 列表（`ContactsViewModel` / `ContactsScreen`）：
  - 顶部下拉 = 账号来源筛选（全部账号 + 各来源）
  - 分组 chips：仅显示当前所选账号下的分组，单选
  - 置顶分组（`Config.pinnedContacts`），不参与字母分组
  - 按首字母分组（`domain/ContactIndex.kt` + `domain/ContactSections.kt`）：统一排序 key（英文取字母、中文取拼音首字母），非中英文归入 `#` 组并排在最后；组头吸顶
  - 右侧竖向首字母索引条：点击平滑滚动、拖动连续跳转，并随滚动高亮当前分组
  - 每行右侧显示最近联系时间（短信会话 ∪ 通话记录）
  - 右下角 FAB 新建联系人：复用联系人详情页（`ContactDetailsRoute(contactId = 0)`，可由拨号盘预填号码）
- 名称索引（`ContactNameIndex`）：为每个联系人构建名字前缀的倒排索引（字母 + 拨号盘数字键），拨号盘按索引做**首位前缀**匹配；`Contact.startWithSurname`（姓在前显示）会影响索引 key
- 详情（`ContactDetailsViewModel` / `ContactDetailsScreen`）：默认即为编辑态
  - 姓名各部分 + 号码增删改
  - 账号归属多选：已选且存在 → 更新；已选且不存在 → 新建副本；取消勾选 → 删除该账号下的 raw contact
  - 每个账号下有分组子项（`loadContactAccounts` + `loadAccountGroups`）
- 名称显示：`domain/ContactNames.kt` 的 `Contact.displayName()` 按「姓名分隔符」设置拼接姓名各部分，可选：无（默认）/ 空格 / `", "` / `"-"` / `". "` / `"·"`
- SIM 卡导入：`SimContactReader` + `SimContactImporter`
  - 姓名字符串相同视为同一人，号码合并
  - 号码已存在于设备联系人 → 忽略该条记录
  - 一个号码只能归属一个人（先到先得）

### 通话

- `CallLogRepositoryImpl` 把系统通话记录同步进本地库（`syncCalls`：解析角色/会话后写入 `messages`），读取（列表、时间线、最近联系时间）一律走本地库；`CallLogGrouper` 合并连续同号通话
- 刷新链路：`ContentObserver` 监听通话记录 → 300ms 防抖 → 同步进本地库 + `AppEvent.RefreshCallLog`；各页面按内容去重，数据未变化不刷新界面（系统会分多批通知）
- 通话界面与呼叫管理：`ui/call/`、`background/CallService`、`data/legacy/CallManager`
- 通话悬浮窗（`ui/call/OngoingCallOverlay.kt`）：受设置「后台通话悬浮窗」（默认开启）控制；需要「显示在其他应用上方」权限，入口在设置 → Calls

### 拨号盘

- 搜索结果每行提供短信 / 通话两个操作；无结果时显示「新建联系人」，号码自动带入
- 搜索结果按**号码**的最近会话时间（会话 = 短信 ∪ 通话记录）倒序，忽略角色；无会话的号码保持原有顺序（`domain/RecentContactTimes.kt`，用 `ContactFilter.phoneKeys` 兼容国家码/前导 0 变体）
- 左上角角色下拉（`DialpadViewModel.roleOverride`）切换本次拨号与聊天使用的角色
- 拨号按钮右侧为聊天入口，打开所输入号码的会话

### 界面与交互约定

- 响应式布局（断点 600dp）：
  - 宽屏：左侧 `NavigationRail`（顶部应用图标、底部设置入口）
  - 窄屏：底部 `NavigationBar`（消息 / 联系人 / 管理 / 规则），设置入口在右上角
- 导航：单一 `NavDisplay` + `ListDetailSceneStrategy`（宽屏列表/详情并排，窄屏单栏）
- TAB 切换动画方向跟随 TAB 顺序（新 TAB 在左侧则反向滑动）
- 会话/联系人列表左滑露出操作菜单（`ui/common/SwipeRevealRow.kt`，同时只展开一行，滚动收起）；消息气泡与角色管理仍用按压位置弹出的长按菜单（`ui/common/PressOffset.kt`）
- 列表刷新使用顶部 loading 指示器（非全屏），会话列表最短显示 700ms；搜索框展开/收起带动画
- 聊天输入栏延伸到底部系统导航栏区域，内容按 `ime ∪ navigationBars` 让位

### 设置项

设置分为：General / Roles / Contacts / Calls / Messages / Recycle bin / Import & export / About。读写通过 `data/local/Config.kt`，ViewModel 侧经 `SettingsRepository` 暴露。

新增/移除设置项必须同步以下链路（与 [AGENTS.md](../AGENTS.md) 一致）：

1. `common/Constants.kt`：SharedPreferences key 常量
2. `data/local/Config.kt`：读写属性
3. `repository/SettingsRepository.kt` + `data/repository/SettingsRepositoryImpl.kt`
4. `viewmodel/SettingsViewModel.kt`：`SettingsState` 字段、setter、`readState()`
5. `ui/settings/SettingsScreen.kt`：界面入口
6. `values/strings.xml` 与 `values-zh-rCN/strings.xml`

## 测试

```
.\gradlew.bat testCoreDebugUnitTest
```

现有单测：

- `domain/`：`RolePrefixerTest`、`RoleResolverTest`、`ConversationMergerTest`、`TimelineMergerTest`、`CallLogGrouperTest`、`ContactFilterTest`、`ContactIndexTest`、`ContactNamesTest`、`RecentContactTimesTest`、`SimContactImporterTest`
- `domain/rule/`：`RuleEngineTest`、`UserRuleEngineTest`、`UserRuleExpressionTest`
- `domain/scheduler/`：`RecycleBinScheduleTest`、`SendMessagePayloadCodecTest`、`TaskReconcilePolicyTest`
- `data/repository/`：`RoleRepositoryImplTest`
- `viewmodel/`：`RolesViewModelTest`、`DialpadViewModelTest`

## 已知约定与限制

- 应用同时作为默认短信与默认拨号应用；短信与通话数据以系统 telephony 库为准，本地 Room 只保存聚合会话、草稿、定时消息、回收站与规则数据
- 通话记录会分多批通知，刷新必须做防抖 + 内容去重
- 已移除对 Simple-Commons 的依赖：工具扩展在 `common/`、联系人读写与查询在 `data/contacts/`、联系人模型在 `domain/model/`；不再引入 commons 及其传递依赖（Glide / joda-time / Gson 等）
- 不再支持 Simple-Dialer/SMS 的应用私有联系人（`SMT_PRIVATE`），该常量仅用于兼容旧偏好标识
- `Contact.sorting` / `Contact.startWithSurname`（`domain/model/contacts/Contact.kt`）为全局静态字段，加载联系人时从设置同步
- 自适应图标必须有背景层：透明背景会被启动器填充为黑色，因此使用白色背景
- 短信接收必须用 `goAsync()` 持有广播直到入库与通知完成；联系人全局静态字段、拼音索引、navigation3 contentKey、窄屏过渡动画等实现细节与坑，见 [AGENTS.md](../AGENTS.md)

## 相关文档

- [README.md](../README.md)：项目介绍、功能范围、用户政策与贡献方式
- [rule-engine-design.md](rule-engine-design.md)：内置规则引擎与用户规则
- [scheduler.md](scheduler.md)：持久化定时任务调度
- [AGENTS.md](../AGENTS.md)：仓库协作与实现约定
