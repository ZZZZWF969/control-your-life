package com.dailycontrol.app.schedule

import android.content.Context
import com.dailycontrol.app.data.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

// 起床渐进唤醒排程器（2026-10-17 真机决策：全应用内，不写系统时钟）
// 方式：N 声相继（默认 4），前 N-1 声在「上课前 60~30 分钟」窗口内随机间隔，
// 最后一声固定在上课前 30 分钟（早八 08:00 / 早十 09:45）；固定码块整体撤旧排新，零积累
object WakePlanner {

	private val zone: ZoneId = ZoneId.systemDefault()
	const val DEFAULT_COUNT = 4

	// 早八随机窗口 07:30–07:59（最后一声固定 08:00）；早十 09:15–09:44（固定 09:45）
	private val EARLY8_RANDOM = 450..479
	private val EARLY10_RANDOM = 555..584
	private val EARLY8_LAST = 480
	private val EARLY10_LAST = 585

	// 为目标日期生成 N 声的触发时刻（epoch 毫秒，严格递增）
	fun generateWakeTimes(date: LocalDate, morningClass: Boolean, count: Int = DEFAULT_COUNT): List<Long> {
		val randomRange = if (morningClass) EARLY8_RANDOM else EARLY10_RANDOM
		val lastMinute = if (morningClass) EARLY8_LAST else EARLY10_LAST
		val randomMinutes = mutableSetOf<Int>()
		while (randomMinutes.size < count - 1) {
			randomMinutes.add(Random.nextInt(randomRange.first, randomRange.last + 1))
		}
		val minutes = randomMinutes.sorted() + lastMinute
		return minutes.map { minute ->
			date.atStartOfDay(zone).plusMinutes(minute.toLong()).toInstant().toEpochMilli()
		}
	}

	// 排明天的唤醒计划：撤旧 → 排 N 声 → 存档（重启自愈用）
	suspend fun plan(context: Context, targetDate: LocalDate, morningClass: Boolean) {
		cancelAll(context)
		val times = generateWakeTimes(targetDate, morningClass)
		times.forEachIndexed { index, millis ->
			ReminderScheduler.schedule(context, ReminderReason.WAKE_CYCLE_BASE + index, millis)
		}
		SettingsRepository.get(context).setWakePlan(targetDate.toString(), times)
	}

	// 无课或当天规划：撤掉全部唤醒并清档
	suspend fun cancel(context: Context) {
		cancelAll(context)
		SettingsRepository.get(context).setWakePlan("", emptyList())
	}

	fun cancelAll(context: Context) {
		for (i in 0 until ReminderReason.WAKE_CYCLE_MAX) {
			ReminderScheduler.cancel(context, ReminderReason.WAKE_CYCLE_BASE + i)
		}
	}

	// 重启/应用启动自愈：按存档时刻重排（已过去的跳过）
	suspend fun rescheduleFromStore(context: Context) {
		val repo = SettingsRepository.get(context)
		if (repo.wakePlanDate.first().isBlank()) {
			return
		}
		val now = System.currentTimeMillis()
		repo.wakePlanTimes.first()
			.filter { it > now }
			.forEachIndexed { index, millis ->
				ReminderScheduler.schedule(context, ReminderReason.WAKE_CYCLE_BASE + index, millis)
			}
	}
}
