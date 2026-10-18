package com.dailycontrol.app.schedule

import android.content.Context
import com.dailycontrol.app.data.db.DailyControlDatabase
import com.dailycontrol.app.data.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

// 每日提醒的排程器：按用户设置的时刻，把三餐/晚间规划等每日提醒排到「下一个触发点」
// 时刻已过则排明天同一时刻；重复调用是幂等的（覆盖式重排）
object DailyPlanner {

	suspend fun rescheduleMeals(context: Context) {
		val times = SettingsRepository.get(context).reminderTimes.first()
		scheduleNextDaily(context, ReminderReason.MEAL_BREAKFAST, times.breakfastMinute)
		scheduleNextDaily(context, ReminderReason.MEAL_LUNCH, times.lunchMinute)
		scheduleNextDaily(context, ReminderReason.MEAL_DINNER, times.dinnerMinute)
	}

	// 晚间规划提醒：每日规划时刻（默认 22:00）触发一次；23:00 的二次提醒由到点时动态排
	suspend fun reschedulePlan(context: Context) {
		val times = SettingsRepository.get(context).reminderTimes.first()
		scheduleNextDaily(context, ReminderReason.PLAN_PRIMARY, times.planMinute)
	}

	// 待办提醒自愈：把所有「未完成且提醒时刻未过去」的任务重新排上（开机/启动时调用）
	suspend fun rescheduleTodoTasks(context: Context) {
		val dao = DailyControlDatabase.get(context).todoTaskDao()
		val now = System.currentTimeMillis()
		dao.pendingReminders(now).forEach { task ->
			val remindAt = task.startAtMillis!! - task.remindAheadMinutes * 60_000L
			ReminderScheduler.schedule(
				context,
				ReminderReason.TODO_TASK_BASE + task.id.toInt(),
				remindAt
			)
		}
	}

	// 计算某「每日分钟数」的下一个触发时刻并排程
	private fun scheduleNextDaily(context: Context, requestCode: Int, minuteOfDay: Int) {
		val zone = ZoneId.systemDefault()
		var trigger = LocalDate.now(zone).atStartOfDay(zone).plusMinutes(minuteOfDay.toLong())
		if (!trigger.toInstant().isAfter(Instant.now())) {
			trigger = trigger.plusDays(1)
		}
		ReminderScheduler.schedule(context, requestCode, trigger.toInstant().toEpochMilli())
	}
}
