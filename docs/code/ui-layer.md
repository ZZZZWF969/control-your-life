# 界面层导读（`ui/`）

Jetpack Compose 声明式界面：**界面 = 状态的函数**，状态变了界面自动重画。全层的通用模式：

- 数据库 Flow 用 `collectAsStateWithLifecycle()` 订阅成 Compose 状态，**落库即刷新**，没有手动刷新
- 协程作用域用 `rememberCoroutineScope()`，点击回调里的数据库操作都从这launch
- 弹窗/弹层开关是 `mutableStateOf(false)` 状态，状态驱动显隐

## MainActivity.kt（在根包）—— 唯一的 Activity

整个应用只有一个 Activity，页面切换全在 Compose 导航里完成（单 Activity 架构）。它做三件事：

1. 订阅主题设置，把「用户三档选择 + 系统深浅色」解析成一个布尔值传给主题
2. 挂载应用根组件 `DailyControlAppRoot`
3. **深链入口**：通知点击带着 `EXTRA_ROUTE` 进来（冷启动走 onCreate，已运行走 `onNewIntent`——Manifest 里 `launchMode=singleTop` 保证复用实例），存进 `pendingRouteState` 交给导航容器消费。路由常量（today/record/plan/ai）也定义在这（2026-10-19 起无 remind 路由，原提醒类通知深链改落 today；0.4.0 增加 ai 供 AI 报告通知深链）

## AppRoot.kt —— 导航容器

底部四栏（今日/记录/AI/设置，2026-10-19 页面整合后「提醒」取消）+ NavHost 页面容器：

- `navDestinations` 列表驱动底栏生成，加一个页 = 加一行配置 + 一个 composable 路由
- **深链消费**：`LaunchedEffect(pendingRoute)` 监听路由请求，导航一次后 `onRouteConsumed()` 清空——「执行一次即销毁」的模式
- `record?meal={meal}` 路由带可选参数：三餐通知深链进来直接弹对应餐别的预填表单
- 规划页（plan）不占底栏，从**今日页入口**或 22:00 通知深链进入，保存后 `popBackStack()` 返回

## screens/ —— 六个页面 + 表单 + 卡片

### TodayScreen.kt（今日页，任务中枢——2026-10-19 页面整合后承担原提醒页全部职责）

外层 Scaffold（悬浮「记一笔」FAB + SnackbarHost），内容自上而下：

- 问候语（按小时分段）+ 日期 → 小结卡（三餐完成度徽标 + 运动总时长，算法不变：当天记录的餐别收成 Set 与三餐枚举求交集）
- 今日饮食/运动精简列表（各取前 6 条，**行点击直接弹编辑表单**；超 6 条显示「查看全部（N 条）」跳记录页）
- **TodayVocabCard**（子组件文件）：极简背单词卡——一句话状态（今天背没背）+「今日已背」记录按钮（已背后隐藏）+「去背单词」跳转按钮（配了包名才显示）
- **TodayTodoSection**（子组件文件）：今日待办清单——未完成在前（按开始时刻，无时刻的排最后）、**已完成打勾调浅沉底**；勾选 = 落库 + 撤闹钟，**反勾 = 提醒时刻在未来则重排闹钟**（否则 Snackbar 提示不再补排）；删除带二次确认；空状态给文案 + 去规划按钮
- 底部入口区：「去时钟开闹钟」（明天有唤醒计划且未自记确认时显示，点「闹钟已开 ✓」收起）+「去规划」常驻
- FAB → `EntryTypePickerSheet`（公共类型选择弹层）→ 饮食/运动表单，**保存走公共 `RecordSaver`，全程不跳页**，落库后 Flow 自动刷新列表

### RecordScreen.kt（记录页，按日管理）

- 顶部日期条前后翻日；按所选日期订阅当天的饮食/运动 Flow
- 右下 FAB「记一笔」弹 `EntryTypePickerSheet`（与今日页共用）→ 饮食或运动表单；保存走公共 `RecordSaver`
- **卡片点击 = 编辑**（表单载入该记录），卡片删除按钮 = 二次确认弹窗后删库
- LazyColumn 的 key 加了 `"diet_"` / `"ex_"` 前缀——两类记录在同一个列表里，id 撞号会让 Compose 崩溃（踩过）
- `initialMeal` 参数承接三餐深链：进页自动弹出该餐表单，`mealLinkConsumed` 标记保证每次深链只触发一次

### DietEntrySheet.kt（饮食表单，底部弹层）

录入/编辑共用一个组件（`initialRecord` 为空即新建）。字段**按餐别联动**：三餐显示饭量三档 FilterChip；零食/营养品显示重量输入；营养品再追加「成分 + 含量」动态行（加行/删行，至少留一行空行降门槛）。

两个细节：

- `defaultMealForNow()` 按当前时间智能预选餐别（早上早餐、深夜零食）
- `stringResource` 不能在点击回调里调用（Compose 规则），要用的字符串（如默认饭量档）提前在组合作用域取好

### DietRecordCard.kt / ExerciseRecordCard.kt（列表卡片）

卡片渲染一条记录：餐别徽章 + 内容 + 时间 + **尾部向右箭头**（可点进编辑的视觉暗示，2026-10-19 批次二）；运动卡片显示时长。删除入口在编辑表单内（红色按钮 + 二次确认）。纯展示组件，编辑回调 onClick 上抛给所在页面。

### DietEntrySheet.kt / ExerciseEntrySheet.kt（录入/编辑表单）

除录入编辑外，编辑模式在保存按钮上方提供「删除本条记录」（二次确认在表单内完成，回调 onDelete 上抛，2026-10-19 批次二）。

### ExerciseEntrySheet.kt（运动表单）

类型快选 chips + 「其他」展开自定义输入；起止时间默认「过去半小时到现在」（多数场景零修改）；**校验：结束必须晚于开始**；时长实时计算显示。

### PlanScreen.kt（规划页，一页式）

今天/明天切换 → 待办草稿列表（标题 + 可选开始时间 + 提前量 chips）→ 起床三选一（无课/早八/早十）→ 保存。核心语义：

- **草稿回填**：进页载入该日未完成任务与已存的起床选择（`loadStoredWakePick` 按存档末声时刻判定早八/早十）——所见即所存，追加待办不会丢旧的
- **保存 = 整体替换未完成任务**（已完成的保留），时刻已过的当日待办只记录不排闹钟
- 起床只在「明天」分支管理；选早八/早十排 WakePlanner，无课撤销；保存后弹「去时钟开闹钟」引导（双保险）

### SettingsScreen.kt（设置页，2026-10-26 分组化重构）

主页为**分组入口列表**：六行设置组（外观 / 提醒时间 / 跳转应用 / AI 接口 / 备份与恢复 / 调试工具），其中外观、AI 接口、备份三行带实时状态摘要（主题档位 / 已配置✓ / 上次备份时间——不进子页就能看状态；提醒时间与跳转应用不展示摘要，保持行简洁），点击进入各板块子页；底部保留「重新查看权限引导」整行条目。子页为**页内状态切换 + BackHandler**（与 AI 页详情同模式，不新增导航路由），统一外壳（返回箭头 + 板块标题）。各板块内容拆至子组件文件：

- `SettingsBasicSections.kt`：外观三档 / 四个提醒时刻（TimeSettingRow 整行可点弹滚轮）/ 跳转应用包名
- `AiConfigSection.kt`：AI 接口子页直接复用（三要素 + 测试连接 + 排程联动）
- `SettingsBackupSection.kt`：导出/恢复/应用内备份清单/恢复确认弹窗
- `SettingsDebugSection.kt`：全部调试按钮（真实闹钟走完整链路，每批功能的验收工具；从旧折叠卡升格为子页）

### AiConfigSection.kt（设置页·AI 接口区，0.4.0）

三要素输入（地址 / Key 掩码 + 显示切换 / 模型名，本地编辑优先防 DataStore 回流打断输入）+「测试连接」（发最小请求验证链路，三要素填齐才可点）+ 安全说明文案。`snapshotFlow` 监听三要素「齐整↔残缺」切换时调用 `AiReportPlanner.reschedule`——避免每个按键都重排闹钟，又保证配齐/删空的瞬间闹钟状态正确。尾部「**提示词定制（高级）**」折叠区（0.4.1）：警示文案 + 多行编辑框预填完整 system 提示词（自定义 or 内置默认）+ 保存/恢复默认；保存时检测 JSON 输出契约段是否被改动（`AiPromptBuilder.keepsContract`），被改动则弹红色警告 AlertDialog 二次确认后才放行；定制随备份导出。

### OnboardingScreen.kt（首启权限向导，2026-10-19 批次四）

未完成时由 AppRoot 整屏挂载（无底栏）。5 步自管理分页：欢迎 / 通知权限（`RequestPermission` 授权回流 + 授权后自动发测试通知）/ 精确闹钟（可检测 + 重新检测）/ HyperOS 渠道三件套（引导 + 跳系统通知设置 + 发测试通知自查）/ 自启动与电池（引导 + 跳应用详情）。完成与跳过等效，都经 `onFinish` 回调让 MainActivity 置 `onboardingDone` 标志。

### AiScreen.kt（AI 页，0.4.0 从占位重写 · 0.4.1 加追问三入口）

未配置：引导卡 +「去设置」按钮（onGoToSettings 回调走导航去设置页）。已配置：

- **追问横幅（0.4.1）**：有未答问题时置顶显示「AI 想问你 N 个问题」（secondaryContainer 配色），点击打开最新含未答问题的报告详情——「早上看报告顺手答完」的动线入口
- **状态卡**：上次报告日期 / 自动排程说明 / 最近失败原因（红色，来自 `ai_last_error_text`）
- **「立即生成昨日报告」**：调 `generateNow`（无视水位线的覆盖式重生成）；生成中按钮内置小转圈 + 提示文案（大模型需要几十秒）；结果提示区分成功/格式异常/失败三色
- **懒检查**：`LaunchedEffect` 进页时 `generateIfDue` 静默补昨日缺口（凌晨闹钟被吞/恢复备份后的兜底，与闹钟共用生成器单飞锁）
- **报告列表**（14 天窗口 Flow 订阅）：日期 + 首份/回顾徽标 + 总评；标题行右侧「档案」「历史」两个入口（0.4.1）；点击进详情
- **详情**（页内选中态切换）：返回按钮 + 日期；parseOk 时为**总评分点卡 + 四折叠板块**结构——总评卡（primaryContainer 底，2~4 个「· 要点」分行；模型按分号分隔输出要点，旧报告一句话自动单行）；「**建议**」「**追问**」「**分析**」「**对话记录**」四个板块统一折叠头（主色标题+箭头，默认收起、展开各显其卡片：建议逐条卡「提醒」级警示底色+headline 行动摘要+detail/basis 折叠 / 追问问题卡每题类型徽标+作答/状态/修改/删除，保存即 `updateAnswers` 落库 / 饮食与运动两小节 / 发送数据与 AI 原文两小节；空板块不渲染）；parseOk=false 时单卡展示原文（对话记录板块仍可查）
- **个人档案页（0.4.1）**：档案型答案列表（主题/当初的问题/答案/更新时间），答案可编辑、条目可删，读写 DataStore `ai_profile_json`
- **问答历史页（0.4.1）**：全部报告的已答条目按作答时间倒序，含类型与提交状态标注
- **BackHandler**：详情/档案/历史是页内状态而非导航路由，系统返回键被拦截为先关闭子视图（否则会直接退出 AI 页）

## components/ —— 跨页面公共组件

### RecordSaver.kt（在 ui/ 根下）—— 记录落库公共事务（2026-10-19 新增）

`saveDietRecordWithItems`（饮食与营养素明细同事务：新建挂新 id / 编辑整体替换）与 `saveExerciseRecord`（新建/更新），今日页与记录页两处共用——事务细节只此一份。

### EntryTypePickerSheet.kt —— 「记一笔」类型选择弹层（2026-10-19 从记录页抽出）

饮食/运动两卡片的 ModalBottomSheet，今日页与记录页共用；一行类型选项 = 圆形字标 + 标题 + 描述。

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
