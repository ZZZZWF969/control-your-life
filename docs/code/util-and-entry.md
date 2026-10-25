# 工具层、应用入口与备份协调层导读

## backup/ —— 备份协调层（`BackupManager.kt`，2026-10-18 新增）

数据安全的唯一出口，详见策略文档 [../data-storage.md](../data-storage.md)。要点：

- **独立成层的原因**：它要同时调度 data 层（全表读写、设置快照）与 schedule 层（恢复后重排五类提醒）——按「上层调下层」的箭头规则，塞进任何一层都会造成反向依赖
- **导出**：四表全量 + 设置 → 单个缩进 JSON（org.json 平台内置，零第三方依赖）；实体↔JSON 的字段映射是文件底部一组独立顶层函数，实体类保持干净
- **恢复**：`listDownloadBackups` 经 MediaStore 按命名前缀检索下载目录里自家文件（自动备份/快照/手动导出到 Download 全命中，凭归属可见性**无需存储权限**）→ `readSummary` 逐份解析摘要 → 应用内清单点选 → 自动先存「恢复前快照」→ 事务内整体替换（先清子表再清主表，写入**保留原 id**——营养素归属、待办闹钟映射都靠 id）→ 重排全部提醒；系统文件选择器仅作「从其他位置」兜底
- **自动备份**：`MediaStore.Downloads` 写入共享下载目录（卸载不删、无需存储权限），滚动保留 4 份（按文件名前缀筛选，恢复快照不占配额）
- **周排程的防推后设计**：下一棒时刻存档 DataStore；启动自愈按存档幂等重排（错过则 1 小时内补做），而不是「每次启动 now+7天」——后者会让天天开应用的用户永远等不到备份


## util/ —— 无状态底层工具

「无状态、无副作用、谁需要谁调用」的最底层，不含业务规则。

### TimeUtil.kt —— 时间换算与格式化

记录页、今日页、调度层共用的时间小工具：

- `startOfDayMillis / endOfDayMillis`：某天的起止 epoch 毫秒——**按日查询的统一边界**（饮食按 eatenAt、运动按 startAt 落在这个区间内算当天）
- `combineToMillis(date, time)`：日期 + 时刻合成毫秒，规划页排待办用
- `formatTime / formatDate`：展示格式化。应用纯中文，日期格式固定 `Locale.CHINA`（「10月18日 星期日」）

**为什么存 epoch 毫秒**：存储无时区歧义（时区规则变了老数据也不受影响），显示时随设备时区/语言走——存储与展示解耦。

### ClockLinker.kt —— 系统时钟跳转封装

双保险方案的「引导去时钟」半边：

- `alarmListIntent()`：构造 `ACTION_SHOW_ALARMS` 标准意图——**只读入口**，打开时钟应用的闹钟列表页，不新建不删除任何东西（与 SET_ALARM 不同，零写入零积累）
- `openAlarmList(context)`：带 `runCatching` 的跳转（个别 ROM 无时钟应用/不响应该入口时返回 false），失败方据此提示

被三处共用：规划页保存后的引导弹窗、提醒页常驻入口、起床末声通知的「检查时钟闹钟」按钮——所以放工具层而非任一调用方。

## DailyControlApp.kt —— 应用入口（根包）

进程启动时最先执行的类，做两件事：

### 1. 注册全部通知渠道

`createNotificationChannels()` 在 onCreate 里同步执行（必须在任何通知发出前）。要点：

- 重复创建同 ID 渠道是安全的，系统忽略——所以每次启动都跑一遍无妨
- 起床渠道 `channel_wake_v2` 特殊配置：`setSound(null, null)` 纯无声 + `enableVibration(true)` + 强震动模式 `[0, 800, 400, 800, ...]`（震 0.8s 歇 0.4s ×4）。**渠道行为创建后不可修改**是平台规则，改行为只能换 ID——这是 v2 的由来（见 notify-layer.md）
- AI 渠道 `channel_ai`（0.4.0）注册为 IMPORTANCE_DEFAULT——凌晨 4:00 出报告不能响铃（见 notify-layer.md）

### 2. 启动自愈（提醒可靠性的第二道保险）

`appScope.launch` 里把全部排程器重跑：三餐、规划、待办、背单词、起床唤醒、每周备份、**AI 报告（0.4.0）**。配合调度层的 BootReceiver（开机广播触发，第一道保险），形成双保险：

- 手机重启 → BootReceiver 重排
- 闹钟丢失 / 时刻修改 / 应用被杀后重启 → 启动时重排

两类入口调用完全相同的幂等排程方法，所以怎么重跑都安全。AI 报告还多一步：自愈重排后顺手 `generateIfDue` 补生成昨日缺失的报告（凌晨关机/闹钟被吞的场景），成功或失败都会发通知。

`appScope` 定义在 **companion object**（0.4.0 从实例字段移入）：接收器的 AI 分支要把长达 2 分钟的网络生成挂上来，广播本身必须立即结束（详见 schedule-layer.md 的 ReminderReceiver 一节）。

## MainActivity.kt

见 ui-layer.md 开头（单 Activity 架构、主题解析、通知深链入口）。

## 根目录其余工程文件速览

| 文件 | 作用 |
|---|---|
| `AndroidManifest.xml` | 五项权限（通知/精确闹钟/开机广播/SET_ALARM/INTERNET——最后一项 0.4.0 新增，全应用唯一网络出口）+ `networkSecurityConfig`（默认 HTTPS，仅模拟器调试地址放行明文）+ `<queries>` 包可见性声明（安卓 11+ 不声明就查不到时钟应用和背单词应用的入口，踩过）+ singleTop 主 Activity + 两个接收器 |
| `app/build.gradle.kts` | 版本号在此（versionName/versionCode，规则见 docs/versioning.md） |
| `app/schemas/` | Room 每个版本的表结构快照（`exportSchema = true` 自动生成），升级迁移时可 diff |
| `gradle/libs.versions.toml` | 全部依赖的版本目录（Version Catalog），加依赖只改这一处 |
| `res/xml/network_security_config.xml` | 网络安全策略（0.4.0）：base 禁明文，仅 10.0.2.2 / 127.0.0.1 域外放开明文供模拟器连本地 mock |
| `script/auto-oper/mock-ai-server.py` | 本地 mock AI 服务（0.4.0）：模拟 OpenAI 兼容端点的成功/401/402/超时/坏格式场景，按模型名关键字切换，开发与自动化验收用 |
