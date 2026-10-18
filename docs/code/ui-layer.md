# 界面层导读（`ui/`）

Jetpack Compose 声明式界面：**界面 = 状态的函数**，状态变了界面自动重画。全层的通用模式：

- 数据库 Flow 用 `collectAsStateWithLifecycle()` 订阅成 Compose 状态，**落库即刷新**，没有手动刷新
- 协程作用域用 `rememberCoroutineScope()`，点击回调里的数据库操作都从这launch
- 弹窗/弹层开关是 `mutableStateOf(false)` 状态，状态驱动显隐

## MainActivity.kt（在根包）—— 唯一的 Activity

整个应用只有一个 Activity，页面切换全在 Compose 导航里完成（单 Activity 架构）。它做三件事：

1. 订阅主题设置，把「用户三档选择 + 系统深浅色」解析成一个布尔值传给主题
2. 挂载应用根组件 `DailyControlAppRoot`
3. **深链入口**：通知点击带着 `EXTRA_ROUTE` 进来（冷启动走 onCreate，已运行走 `onNewIntent`——Manifest 里 `launchMode=singleTop` 保证复用实例），存进 `pendingRouteState` 交给导航容器消费。路由常量（today/record/remind/plan）也定义在这

## AppRoot.kt —— 导航容器

底部五栏（今日/记录/提醒/AI/设置）+ NavHost 页面容器：

- `navDestinations` 列表驱动底栏生成，加一个页 = 加一行配置 + 一个 composable 路由
- **深链消费**：`LaunchedEffect(pendingRoute)` 监听路由请求，导航一次后 `onRouteConsumed()` 清空——「执行一次即销毁」的模式
- `record?meal={meal}` 路由带可选参数：三餐通知深链进来直接弹对应餐别的预填表单
- 规划页（plan）不占底栏，从提醒页入口或 22:00 通知深链进入，保存后 `popBackStack()` 返回

## screens/ —— 六个页面 + 表单 + 卡片

### TodayScreen.kt（今日页，只读汇总）

问候语（按小时分段）+ 日期 → 小结卡（三餐完成度徽标 + 运动总时长）→ 今日饮食/运动精简列表（各取前 6 条）。三餐完成度的算法：把当天记录的餐别收成 Set，与三餐枚举求交集——**任一存在即算记录**，与三餐询问的判断口径一致。

### RecordScreen.kt（记录页，按日管理）

- 顶部日期条前后翻日；按所选日期订阅当天的饮食/运动 Flow
- 右下 FAB「记一笔」弹类型选择（ModalBottomSheet）→ 饮食或运动表单
- **卡片点击 = 编辑**（表单载入该记录），卡片删除按钮 = 二次确认弹窗后删库
- LazyColumn 的 key 加了 `"diet_"` / `"ex_"` 前缀——两类记录在同一个列表里，id 撞号会让 Compose 崩溃（踩过）
- **保存的事务性**：饮食记录与营养素明细在 `db.withTransaction { }` 里落库，要么全成功要么全不动；编辑明细走「先清空再整批写入」的整体替换
- `initialMeal` 参数承接三餐深链：进页自动弹出该餐表单，`mealLinkConsumed` 标记保证每次深链只触发一次

### DietEntrySheet.kt（饮食表单，底部弹层）

录入/编辑共用一个组件（`initialRecord` 为空即新建）。字段**按餐别联动**：三餐显示饭量三档 FilterChip；零食/营养品显示重量输入；营养品再追加「成分 + 含量」动态行（加行/删行，至少留一行空行降门槛）。

两个细节：

- `defaultMealForNow()` 按当前时间智能预选餐别（早上早餐、深夜零食）
- `stringResource` 不能在点击回调里调用（Compose 规则），要用的字符串（如默认饭量档）提前在组合作用域取好

### DietRecordCard.kt / ExerciseRecordCard.kt（列表卡片）

卡片渲染一条记录：餐别徽章（圆形底色 + 早/午/晚/零/补单字，五套配色定义在文件顶部）+ 内容 + 时间；运动卡片显示时长。纯展示组件，回调（onClick/onDelete）上抛给记录页。

### ExerciseEntrySheet.kt（运动表单）

类型快选 chips + 「其他」展开自定义输入；起止时间默认「过去半小时到现在」（多数场景零修改）；**校验：结束必须晚于开始**；时长实时计算显示。

### RemindScreen.kt（提醒页）

背单词状态卡（今日是否已背 + 确认按钮 + 跳转按钮）→ 今日/明日待办清单（勾选完成会撤掉该任务闹钟；删除带确认）→「去时钟开闹钟」入口（明天有唤醒计划且未确认时显示，点「闹钟已开 ✓」自记确认后收起——应用读不到系统时钟，靠用户自记）→「去规划」入口。

### PlanScreen.kt（规划页，一页式）

今天/明天切换 → 待办草稿列表（标题 + 可选开始时间 + 提前量 chips）→ 起床三选一（无课/早八/早十）→ 保存。核心语义：

- **草稿回填**：进页载入该日未完成任务与已存的起床选择（`loadStoredWakePick` 按存档末声时刻判定早八/早十）——所见即所存，追加待办不会丢旧的
- **保存 = 整体替换未完成任务**（已完成的保留），时刻已过的当日待办只记录不排闹钟
- 起床只在「明天」分支管理；选早八/早十排 WakePlanner，无课撤销；保存后弹「去时钟开闹钟」引导（双保险）

### SettingsScreen.kt（设置页）

外观三档 / 三餐与规划时刻（改完即时重排闹钟）/ 背单词包名 / **链路自检调试区**——发测试通知、创建 3 分钟真实待办、30 秒模拟各提醒、模拟起床末声。这些按钮排的是**真实闹钟走完整链路**（不是直接弹通知），是每批功能的验收工具。

### AiScreen.kt / PlaceholderPage.kt

AI 页暂为占位（第四阶段），PlaceholderPage 是通用占位组件（图标 + 标题 + 描述）。

## components/ —— 跨页面公共组件

### TimePickerDialog.kt —— 双列滚轮时间选择器（2026-10-18 重写）

对外接口与旧时钟盘版完全一致（initial + onConfirm + onDismiss），全应用 6 处时间输入统一走它。内部：

- 每列 = `LazyColumn` + 官方 `rememberSnapFlingBehavior`（惯性滑动后自动吸附对齐整行）
- **循环滚动**：条目重复 10 圈、起始停在中段（`initialTopIndex`），滚不到头；真实值 = 原始索引对 24/60 取模
- **中心高亮**：视口 5 行，上下各垫 2 行 contentPadding，中心槽画高亮带；「哪行是中心」由 `centeredRawIndex` 从布局信息算——取**可见条目自身跨度的中点**再找最近条目（不能直接用视口中点：带 contentPadding 时 viewport 坐标起点是负值，直接算会稳定错 2 行，实测踩过）
- 跨行时 `LocalHapticFeedback` 轻触一下，模拟实体滚轮手感

### TypeBadge.kt / ConfirmDialog.kt

圆形字标徽章（44dp 圆底 + 单字，浅底深字两主题可读）与通用二次确认弹窗——「低耦合复用」的两个小例子。

## theme/ —— 设计系统

- `Color.kt`：浅/深两套完整 Material 3 色板（手调的清新健康风）
- `Theme.kt`：`DailyControlTheme` 按布尔选色板，全局圆角形状规范（卡片大圆角是设计语言）
- `Type.kt`：字体层级
