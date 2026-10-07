# 定时任务调度设计

- 状态：已实现（`SEND_MESSAGE`、`EMPTY_RECYCLE_BIN`）
- 日期：2026-09-28
- 适用仓库：`dial&sms`（`src`）
- 依赖：`AlarmManager` + Room（未使用 WorkManager / JobScheduler）

---

## 目录

1. [背景与目标](#1-背景与目标)
2. [总体架构](#2-总体架构)
3. [领域模型](#3-领域模型)
4. [调度](#4-调度)
5. [认领与执行](#5-认领与执行)
6. [取消与删除](#6-取消与删除)
7. [启动/开机对账](#7-启动开机对账)
8. [回收站自动清空](#8-回收站自动清空)
9. [界面接入](#9-界面接入)
10. [数据表](#10-数据表)
11. [文件清单](#11-文件清单)
12. [测试](#12-测试)
13. [已知限制与约定](#13-已知限制与约定)

---

## 1. 背景与目标

定时消息（短信）与回收站自动清空都需要「到点执行」。定时消息对用户承诺的是分钟级精确触发，而 WorkManager 的最小延迟/周期不可控，因此选择 `AlarmManager` 精确闹钟 + 自持久化任务表：

1. **持久化**：任务先落 Room 再注册闹钟，进程被杀、设备重启后可从库中对账恢复；
2. **幂等**：任务 id 唯一，重复调度直接忽略；
3. **互斥**：取消与执行通过状态机 + 单条原子 SQL 更新互斥，同一任务不会既执行又被取消；
4. **可观测**：`scheduled_tasks` 保留状态、尝试次数与失败原因，失败可在聊天中显示失败气泡。

## 2. 总体架构

```text
ThreadViewModel / SettingsViewModel
   │  ScheduledTaskRepository
   ▼
TaskScheduler（object，无 Hilt）
   │  ① 事务：影子消息（+ 会话摘要）+ scheduled_tasks 落库
   │  ② AlarmManager 注册 PendingIntent → ScheduledTaskReceiver
   ▼
ScheduledTaskReceiver（goAsync + WakeLock）
   │  TaskScheduler.handleAlarm(taskId)
   ▼
scheduled_tasks：PENDING --claim--> RUNNING --markDone--> DONE
   │                                   └--异常--> FAILED
   ▼
ScheduledTaskHandlers.handlerFor(type).execute()
   ├─ SendMessageTaskHandler
   └─ EmptyRecycleBinTaskHandler

App.onCreate / BootReceiver(BOOT_COMPLETED, MY_PACKAGE_REPLACED)
   └─ TaskScheduler.reconcile + RecycleBinCleanScheduler.ensure
```

## 3. 领域模型

```kotlin
enum class ScheduledTaskType { SEND_MESSAGE, EMPTY_RECYCLE_BIN }

enum class ScheduledTaskState { PENDING, RUNNING, DONE, FAILED, CANCELLED }

data class ScheduledTask(
    val id: Long,                    // SEND_MESSAGE 复用影子消息 id
    val type: ScheduledTaskType,
    val state: ScheduledTaskState,
    val conversationId: Long,        // EMPTY_RECYCLE_BIN 为 0
    val triggerAt: Long,             // 触发时间（毫秒）
    val payload: String,             // 类型相关 JSON
    val createdAt: Long,
    val updatedAt: Long,
    val attempts: Int = 0,
    val lastError: String? = null,
)

@Serializable data class SendMessagePayload(val conversationId: Long)
```

- `PENDING` 可取消；`claim` 原子转为 `RUNNING`；`markDone` / `markFailed` 进入终态，终态任务不参与对账、不可取消；
- Room 实体 `ScheduledTaskEntity` 将类型/状态存为字符串，映射时遇到未知值返回 null 并跳过（`data/scheduler/ScheduledTaskMappers.kt`）；
- payload 用 kotlinx.serialization 编解码（`SendMessagePayloadCodec`，`ignoreUnknownKeys`），当前发送任务只带 `conversationId`。

## 4. 调度

`TaskScheduler.schedule(context, task, message)`：

1. `db.withTransaction`：若携带影子消息，则 `MessagesDao.insertOrIgnore(message)` 并在会话存在时更新会话摘要（`snippet = body`、`date = max(会话日期, 消息日期)`）；随后 `ScheduledTasksDao.insert(task)`；
2. `insert` 使用 `OnConflictStrategy.IGNORE`，返回 0 表示任务已存在 → 不注册闹钟、返回 false（幂等）；
3. 成功后注册闹钟并向 `AppEventBus` 发 `ConversationsChanged(conversationId)`，会话列表立即出现定时气泡。

定时消息的创建（`ScheduledTaskRepositoryImpl.scheduleMessage`）：

- `messageId = generateRandomId()`（仅内部使用，避开真实短信 id）；
- 影子消息：`type = MESSAGE_TYPE_QUEUED`、`is_scheduled = 1`、`date = triggerAt / 1000`、`conversationId`；
- `task.id = messageId`、`task.triggerAt`、payload = `{"conversationId":N}`。

闹钟注册（`registerAlarm`）：

```kotlin
if (isSPlus() && !alarmManager.canScheduleExactAlarms())
    AlarmManagerCompat.setAndAllowWhileIdle(alarmManager, RTC_WAKEUP, triggerAt, pendingIntent)
else
    AlarmManagerCompat.setExactAndAllowWhileIdle(alarmManager, RTC_WAKEUP, triggerAt, pendingIntent)
```

- `PendingIntent.getBroadcast(context, taskId.toInt(), Intent(ScheduledTaskReceiver).putExtra(TASK_ID, taskId), FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE)`；
- requestCode 取 `taskId.toInt()`，因此任务 id 需要全局唯一（定时消息 id 为 9 位随机数，回收站任务为 `Long.MAX_VALUE`）；
- 无精确闹钟权限时降级为不精确触发，仍为 `RTC_WAKEUP`。

## 5. 认领与执行

`ScheduledTaskReceiver.onReceive`：

- `ScheduledTaskReceiver` 不导出（manifest `exported="false"`）；
- `TASK_ID <= 0` 直接返回；
- 申请 30s `PARTIAL_WAKE_LOCK`，`goAsync()` 持有广播，在 `Dispatchers.IO` 执行 `handleAlarm`，`finally` 释放锁并 `finish()`——`onReceive` 返回后进程随时可能被回收。

`TaskScheduler.handleAlarm`：

1. 读任务并映射为领域模型；
2. `claim(id, now)`：`UPDATE scheduled_tasks SET state='RUNNING', attempts=attempts+1, updated_at=:now WHERE id=:id AND state='PENDING'`；影响行数为 0 → 已被取消或已执行，直接返回；
3. `ScheduledTaskHandlers.handlerFor(type)`；没有对应 handler → `fail("no handler for ...")`；
4. `handler.execute(context, task)` 抛异常 → `fail(异常信息)`；
5. 成功 → `markDone`（`state='DONE'`、清空 `last_error`）→ `refreshMessages()`。

失败处理 `fail`：

- `SEND_MESSAGE`：影子消息 `status = STATUS_FAILED`，聊天中保留失败气泡（显示错误图标）；
- `scheduled_tasks` 标记 `FAILED` 并记录 `last_error`；
- 进入终态后不再自动重试。

`SendMessageTaskHandler.execute` 的发送细节：

- 从本地库读影子消息、会话与角色，缺任一则抛异常标记失败；
- 用 `RuleEngines.engine.routeOutgoing` 计算加前缀后的号码与订阅（见 [rule-engine-design.md](rule-engine-design.md)）；
- 在 `Dispatchers.Main` 调 `sendMessageCompatOrThrow` 发送；成功后删除影子消息，任务行由 `handleAlarm` 标记 DONE。

## 6. 取消与删除

| 方法 | 行为 |
| --- | --- |
| `cancel(taskId)` | 仅对 `PENDING` 原子取消（`cancelPending`）；成功则删除影子消息、重算会话摘要、取消闹钟；返回是否成功。`RUNNING` / 终态不可取消 |
| `cancelTask(taskId)` | 不关联消息的任务（回收站自动清空）：取消 PENDING 后直接删除任务行 |
| `deleteScheduled(taskId)` | 删除定时消息（含失败气泡）：取消 PENDING、删除任务行与影子消息、重算会话、取消闹钟 |
| `cancelForConversation(conversationId)` | 取消某会话全部 PENDING 任务（调用方负责消息删除） |

取消与执行的互斥由 SQL 的 `WHERE state='PENDING'` 保证：闹钟触发后 `claim` 与用户取消竞争，先到者赢。

## 7. 启动/开机对账

`TaskScheduler.reconcile` 在 `App.onCreate` 与 `BootReceiver`（`BOOT_COMPLETED` / `MY_PACKAGE_REPLACED`）触发。

`TaskReconcilePolicy.plan(tasks, now, staleRunningTimeout = 10min)`：

| 状态 | 条件 | 动作 |
| --- | --- | --- |
| PENDING | `triggerAt > now` | `RegisterAlarm` 重排闹钟（覆盖丢闹钟：清理数据、重启等） |
| PENDING | `triggerAt <= now` | `Fail(EXPIRED)`：关机/更新期间错过触发，不补发 |
| RUNNING | `now - updatedAt >= 10min` | `Fail(STALE_RUNNING)`：执行中进程被杀，认领后未收尾 |
| RUNNING | 未超时 | 不处理（可能正在执行） |
| DONE / FAILED / CANCELLED | — | 不处理 |

`Fail` 时除标记任务失败外，`SEND_MESSAGE` 的影子消息也会置为失败状态，最后统一 `refreshMessages()`。

## 8. 回收站自动清空

- 单例任务 id：`RecycleBinCleanScheduler.RECYCLE_BIN_CLEAN_TASK_ID = Long.MAX_VALUE`；
- `ensure(context)`：
  - `useRecycleBin == false` 或 `period <= 0` → 清除现有任务（取消 PENDING + 删除行）；
  - 计算 `nextRecycleBinCleanTrigger(baseTime, period, now)`；若现有 PENDING 任务时间一致则跳过，否则重建任务并交给 `TaskScheduler.schedule`（`message = null`）；
- `nextRecycleBinCleanTrigger`：`now < baseTime` 返回 `baseTime`；否则 `baseTime + ((now - baseTime) / period + 1) * period`，即严格晚于 now 的第一个「基准时间 + N×周期」。错过触发（关机、未启动）顺延到下一周期点，不补执行；
- 周期档位 `RECYCLE_BIN_CLEAN_PERIODS`：1 天 / 3 天 / 1 周 / 2 周 / 1 个月（30 天）/ 3 个月（90 天），默认 1 个月（索引 4）；基准时间精确到分，首次使用时初始化为当前分钟（`Config.init`）；
- 到点执行 `EmptyRecycleBinTaskHandler`：永久删除回收站内全部记录（通话同时删除系统通话记录）、重算受影响会话、有通话时发 `RefreshCallLog`，最后 `RecycleBinCleanScheduler.ensure` 排下一次；
- 触发 `ensure` 的时机：App 启动、开机/应用更新、设置中「回收站开关 / 基准时间 / 周期」变更（`SettingsViewModel.rescheduleRecycleBinClean()`），以及每次自动清空执行完成后。

## 9. 界面接入

- **定时消息胶囊**（`ui/thread/ScheduledMessagesBar.kt`）：输入栏上方显示该会话最早的待发消息（时间 + 内容截断）；多条时点击打开详情弹窗逐条取消，单条时按钮直接删除（取消）；
- **时间线**：`is_scheduled = 1` 的影子消息显示为带时钟图标的气泡，失败后保留气泡并显示错误状态；
- **删除**：沿用普通消息删除入口，`MessageRepositoryImpl.deleteMessage` 检测到 `isScheduled` 转 `deleteScheduledMessage`；
- **数据流**：`ScheduledTaskRepository.pendingTasks(conversationId)` 观察 PENDING 任务（Room Flow），线程页据此刷新。

## 10. 数据表

`scheduled_tasks`（schema 见 `data/model/ScheduledTaskEntity.kt`）：

| 字段 | 说明 |
| --- | --- |
| `id` (PK) | 任务 id；SEND_MESSAGE 复用影子消息 id |
| `type` | `SEND_MESSAGE` / `EMPTY_RECYCLE_BIN` |
| `state` | `PENDING` / `RUNNING` / `DONE` / `FAILED` / `CANCELLED` |
| `conversation_id` | 关联会话；回收站任务为 0 |
| `trigger_at` | 触发时间（毫秒） |
| `payload` | 类型相关 JSON（发送任务为 `{"conversationId":N}`） |
| `created_at` / `updated_at` | 时间戳；RUNNING 超时判断用 `updated_at` |
| `attempts` | 认领次数 |
| `last_error` | 失败原因 |

索引：`state`、`trigger_at`、`conversation_id`。

## 11. 文件清单

| 文件 | 说明 |
| --- | --- |
| `domain/scheduler/ScheduledTask.kt` | 任务类型、状态与模型 |
| `domain/scheduler/SendMessagePayload.kt` | payload 模型与编解码 |
| `domain/scheduler/RecycleBinSchedule.kt` | 周期档位与下次触发计算 |
| `domain/scheduler/TaskReconcilePolicy.kt` | 对账策略（EXPIRED / STALE_RUNNING） |
| `data/model/ScheduledTaskEntity.kt` + `data/scheduler/ScheduledTaskMappers.kt` | Room 实体与映射 |
| `data/local/dao/ScheduledTasksDao.kt` | 原子 claim / cancel / done / fail 等 SQL |
| `data/scheduler/TaskScheduler.kt` | 调度、认领执行、取消、对账、闹钟注册 |
| `data/scheduler/ScheduledTaskHandler.kt` | handler 接口与注册表 |
| `data/scheduler/SendMessageTaskHandler.kt` | 发送定时短信（走规则引擎出站路由） |
| `data/scheduler/EmptyRecycleBinTaskHandler.kt` | 清空回收站并排下一次 |
| `data/scheduler/RecycleBinCleanScheduler.kt` | 单例回收站任务的 ensure / clear |
| `background/ScheduledTaskReceiver.kt` | 闹钟触发入口（goAsync + WakeLock） |
| `background/BootReceiver.kt` | 开机/应用更新后对账 |
| `repository/ScheduledTaskRepository.kt` + `data/repository/ScheduledTaskRepositoryImpl.kt` | 仓库 |
| `viewmodel/ThreadViewModel.kt` | 定时消息创建/取消 |
| `viewmodel/SettingsViewModel.kt` | 回收站设置变更后重排 |
| `ui/thread/ScheduledMessagesBar.kt` | 定时消息胶囊与详情弹窗 |

## 12. 测试

- `domain/scheduler/RecycleBinScheduleTest`：基准时间 + N×周期、错过顺延、边界；
- `domain/scheduler/SendMessagePayloadCodecTest`：payload 编解码；
- `domain/scheduler/TaskReconcilePolicyTest`：未到期重排、过期失败、RUNNING 超时失败、终态忽略。

```
.\gradlew.bat testCoreDebugUnitTest
```

## 13. 已知限制与约定

- 精确触发受系统限制：S+ 未授予「闹钟和提醒」权限时降级为不精确闹钟；省电策略可能进一步延迟；
- 过期任务**不补发**（标记失败），回收站自动清空顺延到下一周期；
- 无自动重试：任务失败即终态，失败原因记录在 `last_error`，失败气泡可由用户手动删除；
- `PendingIntent` requestCode 取 `taskId.toInt()`，新增任务类型时必须保证 id 不冲突；
- 对账只在 App 启动与开机/应用更新时进行；闹钟本身由 AlarmManager 触发，不依赖进程常驻；
- 改 `scheduled_tasks` 表结构必须升 Room 版本并写迁移（见 [developer-guide.md](developer-guide.md)）。
