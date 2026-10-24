# 通知层导读（`notify/`）

界面层与调度层都只调用这一层发通知，不自己拼装 Notification 对象——**通知长什么样只有一处说了算**。

## NotifyChannels.kt —— 渠道 ID 集中定义

七个渠道的 ID 字符串集中在一个 object 里，注册方（DailyControlApp）与发送方（Notifier）共用，避免手写字符串不一致。

`WAKE_ID = "channel_wake_v2"` 带版本号的原因：**安卓的渠道行为（声音/震动）在创建后不可修改**，想改只能换 ID 建新渠道。起床渠道初版带闹钟铃声，用户偏好纯震动后只能弃旧建新（v2），旧渠道残留在用户设备上无法删除——这是平台规则，新渠道设计前要想清楚。

`AI_ID = "channel_ai"`（0.4.0）注册为 **IMPORTANCE_DEFAULT**（其余业务渠道都是 HIGH）：报告在凌晨 4:00 生成，HIGH 级的声音/横幅会把用户吵醒；DEFAULT 安静落通知栏，早晨查看。

## Notifier.kt —— 通知发送统一入口

常量区定义了各通知的 **notify id**：同一 id 再发会覆盖旧通知（背单词循环提醒覆盖同一条就是故意的），不同业务的 id 错开（三餐按餐别偏移、待办按任务 id 偏移）。

每个 `sendXxxNotification` 的共同骨架：检查 `POST_NOTIFICATIONS` 运行时权限（安卓 13+，未授权静默跳过）→ NotificationCompat.Builder 链式拼装 → `NotificationManagerCompat.notify` 发出。

几个值得注意的实现：

### buildTapIntent() —— 深链导航

通知点击不是简单打开应用，而是带路由参数（`EXTRA_ROUTE`，如 `record?meal=breakfast`）指向 MainActivity，由 AppRoot 消费后跳到对应页面——点三餐询问直达**该餐的预填表单**。PendingIntent 用 `FLAG_IMMUTABLE`（安全要求）+ `FLAG_UPDATE_CURRENT`（重发覆盖）。

### sendVocabNotification() —— 可选动作按钮

背单词通知带「去背单词」动作按钮，但要**包名已配置且设备上装了该应用**才加（`getLaunchIntentForPackage` 拿启动意图，拿不到就不加）。注意：**确认「今日已背」的按钮故意不做在通知上**——产品决策，确认必须进应用完成，防止误触（用户 2026-10-17 确认）。

### sendWakeNotification() —— 末声的持续震动

起床通知按「第几声」显示，末声（`isLast`）有三种强化：

1. 文案换成「系统闹钟此刻应已在响，若没响说明时钟闹钟未开」——双保险的兜底提示
2. 加「检查时钟闹钟」动作按钮，PendingIntent 直发 SHOW_ALARMS 意图跳时钟闹钟列表
3. `FLAG_INSISTENT`：通知未被处理前系统**持续间歇重复提醒**（震动），直到用户点掉或划掉——构建器没有对应 API，只能在 build 出的 Notification 对象上改 flags 位

### sendAiReportNotification() —— AI 报告结果通知（0.4.0）

入参是生成器的 `Outcome`（而非散装字段）：成功带总评（parseOk=false 时换成「已保存原文」的说明）、失败带原因与「恢复后自动补交」的安抚文案；`NotConfigured / AlreadyUpToDate` 两态直接不发——没配置或已生成就不该打扰。正文可能较长，套 `BigTextStyle` 保证展开可读全文；深链 ROUTE_AI 落到 AI 页。

### 渠道与震动的分工

起床渠道本身注册为纯震动 + 强震动模式（[0.8s 震 0.4s 歇] ×4，见 DailyControlApp），通知本身不设声音——「叫醒靠时钟闹钟的铃声（走闹钟音量，静音也响），应用只做震动保底」是双保险方案的分工，详见 schedule 层的 WakePlanner 一节。
