package com.dailycontrol.app.notify

// 通知渠道 ID 的集中定义：发送方与注册方共用一处，避免手写字符串不一致
object NotifyChannels {
	const val GENERAL_ID = "channel_general"
	const val MEAL_ID = "channel_meal"
	const val WORD_ID = "channel_word"
	const val PLAN_ID = "channel_plan"
	const val TODO_ID = "channel_todo"
}
