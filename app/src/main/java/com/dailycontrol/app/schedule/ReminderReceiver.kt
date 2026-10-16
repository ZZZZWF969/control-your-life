package com.dailycontrol.app.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dailycontrol.app.notify.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// 提醒到点接收器：系统闹钟触发后按 requestCode 分发处理
// goAsync + 协程：onReceive 主线程不允许做数据库查询，异步完成后才允许结束广播
class ReminderReceiver : BroadcastReceiver() {

	override fun onReceive(context: Context, intent: Intent) {
		val requestCode = intent.getIntExtra(ReminderReason.EXTRA_REQUEST_CODE, 0)
		val pendingResult = goAsync()
		CoroutineScope(Dispatchers.IO).launch {
			try {
				handle(context, requestCode)
			} finally {
				pendingResult.finish()
			}
		}
	}

	private suspend fun handle(context: Context, requestCode: Int) {
		when (requestCode) {
			// 测试待办：全链路实测（排程→触发→通知→深链）
			ReminderReason.TODO_TEST -> {
				Notifier.sendTodoNotification(context)
			}
			// 三餐询问：第 1 批加入「查当天是否已记录、未记录才发通知」的逻辑；
			// 当前先做幂等续排，保证闹钟链路每天滚动
			ReminderReason.MEAL_BREAKFAST,
			ReminderReason.MEAL_LUNCH,
			ReminderReason.MEAL_DINNER -> {
				DailyPlanner.rescheduleMeals(context)
			}
			// 其余类型（背单词/规划/真实待办）在后续批次接入
			else -> Unit
		}
	}
}
