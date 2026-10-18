package com.dailycontrol.app.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.dailycontrol.app.data.db.DailyControlDatabase
import com.dailycontrol.app.data.entity.MealType
import com.dailycontrol.app.data.settings.SettingsRepository
import com.dailycontrol.app.notify.Notifier
import com.dailycontrol.app.util.TimeUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

// 提醒到点接收器：系统闹钟触发后按 requestCode 分发处理
// goAsync + 协程：onReceive 主线程不允许做数据库查询，异步完成后才允许结束广播
class ReminderReceiver : BroadcastReceiver() {

	override fun onReceive(context: Context, intent: Intent) {
		val requestCode = intent.getIntExtra(ReminderReason.EXTRA_REQUEST_CODE, 0)
		// 到点必留一条日志：真机排查「闹钟触发没」时靠它（其余路径保持静默）
		Log.d("ReminderReceiver", "fired requestCode=$requestCode")
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
			// 三餐询问：当天该餐已记录则静默，未记录才发询问通知；随后滚动续排明日
			ReminderReason.MEAL_BREAKFAST,
			ReminderReason.MEAL_LUNCH,
			ReminderReason.MEAL_DINNER -> {
				val mealType = when (requestCode) {
					ReminderReason.MEAL_BREAKFAST -> MealType.BREAKFAST
					ReminderReason.MEAL_LUNCH -> MealType.LUNCH
					else -> MealType.DINNER
				}
				val today = LocalDate.now()
				val recorded = DailyControlDatabase.get(context).dietRecordDao().countBetween(
					mealType.storageName,
					TimeUtil.startOfDayMillis(today),
					TimeUtil.endOfDayMillis(today)
				)
				if (recorded == 0) {
					Notifier.sendMealNotification(context, mealType)
				}
				DailyPlanner.rescheduleMeals(context)
			}
			// 背单词循环到点：未完成则发提醒通知（仅「去背单词」入口，确认在应用内），随后滚到下一锚点
			ReminderReason.WORD_CYCLE -> {
				val doneToday = SettingsRepository.get(context).vocabDoneDate.first() == LocalDate.now().toString()
				if (!doneToday) {
					Notifier.sendVocabNotification(context)
				}
				VocabPlanner.reschedule(context)
			}
			// 晚间规划首次提醒：未规划则发通知；同时动态排 23:00 的二次提醒（若尚未到）
			ReminderReason.PLAN_PRIMARY -> {
				schedulePlanSecondIfBefore23(context)
				val planned = SettingsRepository.get(context).lastPlannedDate.first() == LocalDate.now().toString()
				if (!planned) {
					Notifier.sendPlanNotification(context, primary = true)
				}
				DailyPlanner.reschedulePlan(context)
			}
			// 23:00 二次提醒：仍未规划才再发一次
			ReminderReason.PLAN_SECOND -> {
				val planned = SettingsRepository.get(context).lastPlannedDate.first() == LocalDate.now().toString()
				if (!planned) {
					Notifier.sendPlanNotification(context, primary = false)
				}
			}
			// 起床唤醒：第 index+1 声；总数从存档读取；末声按存档总数比对
			// 注意：when 从上到下匹配，窄区间（起床/调试）必须排在宽区间（待办）之前，
			// 否则待办分支会吞掉起床码段（旧版 5000 段即因此整个失效）
			in ReminderReason.WAKE_CYCLE_BASE..ReminderReason.WAKE_CYCLE_BASE + ReminderReason.WAKE_CYCLE_MAX - 1 -> {
				val index = requestCode - ReminderReason.WAKE_CYCLE_BASE + 1
				val total = SettingsRepository.get(context).wakePlanTimes.first().size
				val isLast = if (total > 0) index == total else index >= WakePlanner.DEFAULT_COUNT
				Notifier.sendWakeNotification(context, index, total, isLast)
			}
			// 调试：模拟渐进中间声（独立码段，不影响真实计划排程）
			in ReminderReason.WAKE_DEBUG_RING_BASE..ReminderReason.WAKE_DEBUG_RING_BASE + 2 -> {
				Notifier.sendWakeNotification(
					context, requestCode - ReminderReason.WAKE_DEBUG_RING_BASE + 1, 0, isLast = false
				)
			}
			// 调试：模拟末声（持续震动 + 检查时钟入口），total=0 → 文案不显示总数
			ReminderReason.WAKE_DEBUG_LAST -> {
				Notifier.sendWakeNotification(
					context, WakePlanner.DEFAULT_COUNT, 0, isLast = true
				)
			}
			// 待办到点提醒：任务未完成才提醒（宽区间，放在最后兜住）
			in ReminderReason.TODO_TASK_BASE..ReminderReason.TODO_TASK_BASE + 99999 -> {
				val taskId = (requestCode - ReminderReason.TODO_TASK_BASE).toLong()
				val task = DailyControlDatabase.get(context).todoTaskDao()
					.getByReminderRequest(taskId)
				if (task != null && !task.done) {
					Notifier.sendTodoTaskNotification(context, task)
				}
			}
			// 其余类型在后续批次接入
			else -> Unit
		}
	}

	// 规划时刻触发时，把二次提醒排到当晚 23:00（若当前已过 23:00 则跳过）
	private fun schedulePlanSecondIfBefore23(context: Context) {
		val zone = ZoneId.systemDefault()
		val now = ZonedDateTime.now(zone)
		val secondAt = now.toLocalDate().atTime(LocalTime.of(23, 0)).atZone(zone)
		if (secondAt.isAfter(now)) {
			ReminderScheduler.schedule(context, ReminderReason.PLAN_SECOND, secondAt.toInstant().toEpochMilli())
		}
	}
}
