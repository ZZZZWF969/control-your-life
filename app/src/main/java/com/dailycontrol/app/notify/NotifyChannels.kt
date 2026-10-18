package com.dailycontrol.app.notify

// 通知渠道 ID 的集中定义：发送方与注册方共用一处，避免手写字符串不一致
object NotifyChannels {
	const val GENERAL_ID = "channel_general"
	const val MEAL_ID = "channel_meal"
	const val WORD_ID = "channel_word"
	const val PLAN_ID = "channel_plan"
	const val TODO_ID = "channel_todo"

	// 起床渠道 v2：纯强震动、无声音（用户偏好，2026-10-17）。
	// 换 ID 的原因：渠道的声音/震动行为创建后不可修改，旧渠道（带闹钟音量铃声）只能弃用
	const val WAKE_ID = "channel_wake_v2"
}
