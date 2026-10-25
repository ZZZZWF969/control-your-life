package com.dailycontrol.app.schedule

import android.content.Context
import com.dailycontrol.app.data.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// AI 报告排程器：接口三要素配置齐全时，把每日 4:00 的汇总闹钟排到下一个触发点；
// 配置不全则撤销闹钟（没配接口不该在凌晨空转）。时刻已过则排明天，重复调用幂等
object AiReportPlanner {

	// 报告生成时刻：凌晨 4:00（goal.md 既定规格，本版固定不可配置）
	private const val REPORT_MINUTE_OF_DAY = 4 * 60

	suspend fun reschedule(context: Context) {
		val repo = SettingsRepository.get(context)
		val url = repo.aiBaseUrl.first().trim()
		val key = repo.aiApiKey.first().trim()
		val model = repo.aiModel.first().trim()
		if (url.isBlank() || key.isBlank() || model.isBlank()) {
			ReminderScheduler.cancel(context, ReminderReason.AI_REPORT)
			return
		}
		val zone = ZoneId.systemDefault()
		var trigger = LocalDate.now(zone).atStartOfDay(zone).plusMinutes(REPORT_MINUTE_OF_DAY.toLong())
		if (!trigger.toInstant().isAfter(Instant.now())) {
			trigger = trigger.plusDays(1)
		}
		ReminderScheduler.schedule(context, ReminderReason.AI_REPORT, trigger.toInstant().toEpochMilli())
	}

	// 调试：把报告闹钟改到 1 分钟后，走与凌晨 4:00 完全相同的接收器分支做全链路验证
	fun scheduleDebugInOneMinute(context: Context) {
		ReminderScheduler.schedule(context, ReminderReason.AI_REPORT, System.currentTimeMillis() + 60_000L)
	}
}
