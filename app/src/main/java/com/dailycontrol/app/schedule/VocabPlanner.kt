package com.dailycontrol.app.schedule

import android.content.Context
import com.dailycontrol.app.data.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

// 背单词循环排程器：锚点制——每天 10:00 起每 90 分钟一轮，最后锚点 22:00，跨过 23:00 窗口即止
// 「今日已背」后当天不再提醒，次日 10:00 自动重启循环（按日期判断，无需定时重置任务）
object VocabPlanner {

	private val WINDOW_START = LocalTime.of(10, 0)
	private val WINDOW_END = LocalTime.of(23, 0)
	private val INTERVAL_MINUTES = 90L

	// 计算某个时刻之后的下一个循环锚点；当天窗口耗尽则返回次日 10:00
	fun nextAnchorAfter(from: ZonedDateTime): ZonedDateTime {
		val zone = from.zone
		var anchor = from.toLocalDate().atTime(WINDOW_START).atZone(zone)
		while (!anchor.isAfter(from)) {
			anchor = anchor.plusMinutes(INTERVAL_MINUTES)
			if (anchor.toLocalTime().isAfter(WINDOW_END)) {
				return from.toLocalDate().plusDays(1).atTime(WINDOW_START).atZone(zone)
			}
		}
		return anchor
	}

	// 统一入口：今天已确认背过 → 直接排明天 10:00；否则排下一个未来锚点
	suspend fun reschedule(context: Context) {
		val zone = ZoneId.systemDefault()
		val doneToday = isDoneToday(context)
		val next = if (doneToday) {
			LocalDate.now(zone).plusDays(1).atTime(WINDOW_START).atZone(zone)
		} else {
			nextAnchorAfter(ZonedDateTime.now(zone))
		}
		ReminderScheduler.schedule(context, ReminderReason.WORD_CYCLE, next.toInstant().toEpochMilli())
	}

	// 「今日已背」：记录日期、撤下通知、把循环滚到明天 10:00
	suspend fun markDoneToday(context: Context) {
		SettingsRepository.get(context).setVocabDoneDate(LocalDate.now().toString())
		reschedule(context)
	}

	private suspend fun isDoneToday(context: Context): Boolean {
		return SettingsRepository.get(context).vocabDoneDate.first() == LocalDate.now().toString()
	}
}
