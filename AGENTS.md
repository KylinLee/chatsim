# AGENTS.md

面向在本仓库（`src`）工作的 agent 的操作说明。**项目介绍与设计说明见 [README.md](README.md)**，本文件只写「怎么做」。

## 交流语言

所有交流请使用**中文**。

## 构建与验证

在 `src` 目录执行（路径含 `&`，`gradlew.bat` 中的 `set "VAR=..."` 引号写法不要还原）：

```
.\gradlew.bat assembleCoreDebug          # 构建 debug APK
.\gradlew.bat testCoreDebugUnitTest      # 运行单元测试
```

产物与安装：

```
app\build\outputs\apk\core\debug\chatsim-core-debug.apk
adb install -r app\build\outputs\apk\core\debug\chatsim-core-debug.apk
```

- 开发与验证统一用 `core` 变体
- 改动涉及角色前缀、会话/时间线合并、通话分组、联系人过滤、SIM 导入等纯逻辑时，必须跑单测
- 不要提交 `build/`、`.gradle/`、`.kotlin/` 等产物
- `adb` 不在 PATH 时用 `$env:ANDROID_HOME\platform-tools\adb.exe`
- 开始使用 `adb` 前，先把手机息屏设为不自动息屏（`adb shell settings put system screen_off_timeout 2147483647`），避免调试过程中锁屏中断；**一轮对话完成后、不再需要 `adb` 时改回原值**
- 调试时在设备上产生的临时文件（`uiautomator dump` 的 `.xml`、`burst*` 目录、截屏产生的 `.png` 等）**必须在一轮对话完成后删除**（如 `adb shell rm -rf /sdcard/burst* /sdcard/*.xml /sdcard/*.png`），与恢复息屏时长一起处理，不要留在用户设备上
- 真机验证界面/动画：可以用 `adb shell uiautomator dump` 读布局，但**不要截图或录屏**（用户明确要求）

### PowerShell 中文输出

控制台默认按 GBK 输出，直接用 `Get-Content` / `Select-String` 读取文件时中文会乱码。仓库内的 opencode 插件 `.opencode/plugins/powershell-utf8.js` 会自动给 bash 工具命令追加 UTF-8 输出编码设置，**新增/修改该插件后需要重启 opencode 才生效**。

插件未生效时（例如在别的 agent/终端里），命令开头手动加：

```powershell
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8; Get-Content README.md -TotalCount 5
```

文件本身是 UTF-8，读取时无需再传 `-Encoding`；乱码只发生在输出环节，不影响文件内容。

## 工作流规则

当从远程下载库文件出现错误并且诊断为网络问题时，不要自己解决，给出原因和建议，并立即结束。

每个功能/修复任务（构建验证除外）必须拆分为以下子步骤，写入 todowrite：

1. 实现代码变更（status: in_progress → completed）
2. git add + git commit（status: pending → in_progress → completed）

强制约束：

- 每个 todo 项的代码修改完成后，必须紧跟一个对应的 git commit 子项
- 只有 git commit 成功执行后，才能将该 todo 标记为 completed
- commit message 格式：`type(scope): subject`
  - `type`：`feat`（新功能）/ `fix`（修复）/ `refactor`（重构）/ `docs`（文档）/ `chore`（构建与杂项）/ `release`（发布）
  - `scope`：可选，英文小写（模块或目录名，如 `rules`、`scheduler`、`notification`）；省略 scope 时写成 `type: subject`
  - `subject`：**英文**简述，动词开头（祈使句），小写开头，不加句号
  - `release` 固定为 `release(<major.minor.patch>): <description>`，是应用版本号与 `release.ps1` 发布构建的唯一来源（Gradle 构建时动态读取最新的一条），且必须是当前分支的末端提交（HEAD）
- 示例：`feat(rules): add relative days time condition`、`fix(recycle): fix role switch not applied`、`docs: rewrite README`、`release(1.2.0): rule engine`
- 禁止把多个 todo 的代码变更合并为一次 commit，禁止跳过 commit
- 不要在未要求时提交改动

## 代码约定

- 用户可见文案必须**同时**添加到 `values/strings.xml` 与 `values-zh-rCN/strings.xml`
- 分层：`ui → viewmodel → repository/domain → data`；界面只做展示与回调，逻辑放 ViewModel/domain
- 纯逻辑放 `domain/`（无 Android 依赖，便于单测）；Android 相关逻辑放 `data/`
- ViewModel 用 `StateFlow` 暴露状态，界面用 `collectAsStateWithLifecycle()`
- 跨页面刷新用 `AppEventBus` + `AppEvent`
- 保持现有代码风格；除非必要，不写注释
- 不引入新的第三方库，除非确有必要并在 `gradle/libs.versions.toml` 中声明
- 图标统一使用 **Material Symbols**：`res/drawable/ic_symbol_*.xml`（取自 google/material-design-icons 的 `symbols/android/<name>/materialsymbolsoutlined/`，`*_fill1_24px` 对应原 filled、`*_24px` 对应原 outlined，方向性图标保留 `android:autoMirrored`）；界面用 `Icon(painter = painterResource(R.drawable.ic_symbol_*))`；**不要**再使用 `androidx.compose.material.icons`（依赖已移除）

## 新增 / 移除设置项

新增设置项时，按以下链路同步改动：

1. `common/Constants.kt`：SharedPreferences key 常量
2. `data/local/Config.kt`：读写属性
3. `repository/SettingsRepository.kt` + `data/repository/SettingsRepositoryImpl.kt`
4. `viewmodel/SettingsViewModel.kt`：`SettingsState` 字段、setter、`readState()`
5. `ui/settings/SettingsScreen.kt`：界面入口（SwitchRow / EntryRow / chips 等）
6. `values/strings.xml` 与 `values-zh-rCN/strings.xml`

移除设置项时，必须一并清理上述链路以及仅服务于该项的代码。

## 必须知道的坑

- **navigation3 的 contentKey**：1.1.7 形如 `Pair(key.toString(), key::class.toString())`，新版可能是 `"$key:$key::class"` 字符串；解析路由要按字符串比较并兼容两种格式（见 `ui/ChatSimApp.kt` 的 `homeTabIndex`）
- **窄屏前进/返回动画**：`ListDetailSceneStrategy` 默认 `shouldHandleSinglePaneLayout = false`，单栏（`paneCount <= 1`）时返回 null，由兜底 `SinglePaneScene` 接管，所以前进/返回都走 `NavDisplay` 的 `transitionSpec / popTransitionSpec / predictivePopTransitionSpec`；库内部按自己的 z-index 记账挑 spec 与层级，瞬发返回会出现层级/方向随机，必须在 app 侧固定：三个过渡都用显式 `ContentTransform` 并设 `targetContentZIndex`（前进 `+1f`、返回 `-1f`），且 `transitionSpec` 用「上一帧回退栈」自行判定返回方向（见 `backTransition` / `forwardTransition` / `isBackStackPop`）
- **ListDetail 子页面一律用 `detailPane()`，不要用 `extraPane()`**：库计算 pane 宽度时把剩余宽度给「展开面板中优先级最高者」（Detail/Primary=10 > List/Secondary=5 > Extra/Tertiary=1）。占位符状态是 [List + Detail(占位)]，剩余宽度给 Detail；若子页面用 extra，状态变为 [List + Extra]，Detail 隐藏、List 成为最高优先级 → 剩余宽度分给左栏，左栏宽度从 360dp 跳到剩余宽度，打开右栏页面时左栏文字重排。改成 detail 后右栏始终是 Primary，左右宽度恒定
- **通话记录刷新**：系统会分多批通知，仓库层 300ms 防抖 → `CallLogRepositoryImpl.syncCalls()` 同步进本地库 + 发事件；消费端按内容去重，避免连续刷新
- **通话记录存储**：通话记录与消息同表（`messages.is_call = 1`，id 取通话记录 id 的负值避免与短信 id 冲突），读取一律走本地库，不再每次实时查询系统通话记录
- **联系人来源**：私有联系人功能（`MyContactsContentProvider` / `getMyContactsCursor`）已随 Simple-Commons 依赖一并移除；`SMT_PRIVATE` 仅保留在 `ContactSource.getFullIdentifier()` 中兼容旧偏好标识
- **窄屏 TAB 栏（遮挡式）**：TAB 栏画在页面下层、不参与页面测量；首页场景用 `windowInsetsPadding(bottom)` + `padding(bottom = HomeTabBarHeight)` 让位，非首页的全屏页自然盖住它；被遮挡时 `enabled = false` + `clearAndSetSemantics`，避免点击穿透（见 `ui/ChatSimApp.kt`）
- **联系人全局静态字段**：`Contact.sorting`、`Contact.startWithSurname`（`domain/model/contacts/Contact.kt`）在 `ContactRepositoryImpl.loadContacts` 中从设置同步；联系人显示名走 `domain/ContactNames.kt` 的 `Contact.displayName()`（依赖 `ContactNames.separator`），名字索引与字母分组也依赖它
- **联系人名字索引**：`domain/ContactIndex.kt` 的拼音首字母用 **GBK 区位边界**比较（Unicode 码点顺序与拼音顺序无关，直接比码点会把「王」判成 R）；倒排索引按名字前缀（字母 + 拨号盘数字）建表，拨号盘搜索是首位前缀匹配（不是子串），号码匹配保持包含/后缀
- **联系人搜索匹配**：`ContactFilter` 同时匹配 `displayName()`（按姓名分隔符拼接）与 `Contact.getNameToDisplay()`，并做忽略分隔符的比较（`compactName`），避免「张三」搜不到显示为「张 三」的联系人
- **已固定的行为**：通话列表/最近通话合并连续同号通话（`CallLogGrouper.group()`），但**聊天时间线不合并**（`CallLogRepositoryImpl.getConversationCalls` 逐条返回）；消息输入栏字符计数常显，指示线为常驻 Enabled（`outlineVariant` 1dp）+ 聚焦叠加 Focused（`primary` 2dp）
- **navigation3 版本**：当前 `nav3 = 1.1.7`；升级到 1.2.0-rc01 需要 AGP 9.1 + compileSdk 37 + minSdk 24，属大迁移，暂不升级
- **列表 loading**：用顶部（非全屏）`AnimatedVisibility` 指示器；会话列表最短 700ms
- **临时层菜单**：全局菜单统一走 `ui/common/AppMenus.kt`（`AppMenu` / `AppMenuItem` / `AppSelectableMenuItem`，Expressive 形状、容器色与分段项形状；锚定式下拉复用 `appMenuContainerShape()` / `appMenuContainerColor()` / `appMenuContentPadding()`），不要直接使用 material3 的 `DropdownMenu*`；长按菜单用 `PressAnchorMenu` 在按压位置弹出，不要直接锚在整行上；会话列表与联系人列表已改为**左滑露出操作菜单**（`ui/common/SwipeRevealRow.kt`，同一时刻只允许一行展开，滚动时自动收起），不要再给这两处加长按菜单
- **自适应图标**：必须有背景层，透明会被启动器填充为黑色（当前用白色）
- **短信接收**：`SmsReceiver` 必须用 `goAsync()` 持有广播，直到系统库/本地库写入与通知全部完成——`onReceive` 返回后进程随时会被回收（ColorOS 等会主动压缩/回收后台进程），否则应用被划掉后会丢短信且重进 App 也查不到（消息从未入库）
- **Room 数据库版本**：新包 `io.github.kylinlee.chatsim` 是全新应用、全新数据库，`data/local/MessagesDatabase.kt` 当前 `version = 1`（最新 schema 作为初始版本，无历史迁移；旧包 `com.simplemobiletools.smsmessenger` 的本地数据不会继承）；今后改表结构必须升版本并写迁移，schema 文件在 `app/schemas/`
- **用户规则**：表达式存 JSON（`domain/rule/user/UserRuleExpression.kt`，字段大写、操作符全称如 `STARTS_WITH`，连接符 `AND/OR` 可空、未选按 AND，左折叠+短路；时间条件：`BEFORE/AFTER` 为绝对毫秒、`WITHIN_DAYS/BEFORE_DAYS` 存负毫秒偏移（旧规则 `BEFORE/AFTER`+负值仍按相对时间求值），手动运行按批次固定 `now`）；拦截在入库前判定（不入库不通知，只写 `rule_hits` 快照），标签写 `message_tags`（`Message.tags` 是 `@Ignore` 展示字段，由 `TimelineRepositoryImpl` 填充），移入垃圾箱受回收站开关约束（存在 trash 规则或回收站非空时设置不可关闭）；Receiver 等无 Hilt 的调用方用 `data/rules/UserRuleStore`，旧的「屏蔽关键词」已移除

## 设计参考

角色（小号）、会话与消息、联系人、通话、界面与交互约定、设置项等详细设计，见 [docs/developer-guide.md](docs/developer-guide.md)；规则引擎与定时任务见 [docs/rule-engine-design.md](docs/rule-engine-design.md) 与 [docs/scheduler.md](docs/scheduler.md)。
