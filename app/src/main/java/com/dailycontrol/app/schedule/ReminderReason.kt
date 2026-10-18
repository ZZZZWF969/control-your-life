package com.dailycontrol.app.schedule

// 提醒类型的 requestCode 约定：每个提醒类别占用一段编号空间，取消/重排时按码定位
// 编号同时作为 PendingIntent 的 requestCode 与 Receiver 里的分发依据
object ReminderReason {

	// 意图附加键：Receiver 从中取回分发编号
	const val EXTRA_REQUEST_CODE = "request_code"

	// 三餐询问：1001~1003
	const val MEAL_BREAKFAST = 1001
	const val MEAL_LUNCH = 1002
	const val MEAL_DINNER = 1003

	// 背单词循环提醒
	const val WORD_CYCLE = 2001

	// 晚间规划：首次 + 23:00 二次
	const val PLAN_PRIMARY = 3001
	const val PLAN_SECOND = 3002

	// 待办提醒：测试用固定码；真实待办用基址 + 任务 id（id 由 Room 自增，理论上无上限）
	const val TODO_TEST = 4001
	const val TODO_TASK_BASE = 4100

	// 起床渐进唤醒：固定码块（最多 10 声），每次规划整体撤旧排新，零积累
	// 码段必须整体高于待办区间（4100+任务id 可无限增长），否则会被接收器的
	// 待办范围分支吞掉变成死代码（2026-10-17 排查：旧 5000 段全部落入待办区间）
	const val WAKE_CYCLE_BASE = 200000
	const val WAKE_CYCLE_MAX = 10

	// 起床调试模拟：独立码段，绝不占用 WAKE_CYCLE 码块——
	// 同码即同一 PendingIntent，排程会覆盖真实计划的那一声（曾致末声被搬走）
	const val WAKE_DEBUG_RING_BASE = 200101
	const val WAKE_DEBUG_LAST = 200104

	// 每周自动备份：到点把全部数据经 MediaStore 写入系统下载目录
	const val BACKUP_WEEKLY = 6001
}
