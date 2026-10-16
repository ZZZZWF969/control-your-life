package com.dailycontrol.app.schedule

import android.content.Context
import com.dailycontrol.app.data.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// 每日提醒的排程器：按用户设置的时刻，把三餐等每日提醒排到「下一个触发点」
// 时刻已过则排明天同一时刻；重复调用是幂等的（覆盖式重排）
object DailyPlanner {

	suspend fun rescheduleMeals(context: Context) {
		val times = SettingsRepository.get(context).reminderTimes.first()
		scheduleNextDaily(context, ReminderReason.MEAL_BREAKFAST, times.breakfastMinute)
		scheduleNextDaily(context, ReminderReason.MEAL_LUNCH, times.lunchMinute)
		scheduleNextDaily(context, ReminderReason.MEAL_DINNER, times.dinnerMinute)
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
