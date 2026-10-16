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

	// 待办提醒：测试用固定码；真实待办用基址 + 任务 id（第 3 批启用）
	const val TODO_TEST = 4001
	const val TODO_TASK_BASE = 4100
}
