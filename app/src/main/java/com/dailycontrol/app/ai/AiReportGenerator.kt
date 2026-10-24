package com.dailycontrol.app.ai

import android.content.Context
import com.dailycontrol.app.data.db.DailyControlDatabase
import com.dailycontrol.app.data.entity.AiReport
import com.dailycontrol.app.data.settings.SettingsRepository
import com.dailycontrol.app.util.TimeUtil
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import java.time.LocalDate

// 报告生成编排器：UI 立即生成与定时/自愈触发共用同一入口。
// 核心约定「成功才记账」：接口返回了内容才推进报告水位线；任何失败（欠费/断网/超时）不推进，
// 缺口次日自动显形，由合并补交一次追平——不需要为任何失败类别写专门逻辑
object AiReportGenerator {

	// 报告滚动保留天数
	private const val KEEP_DAYS = 14

	// 补交时发明细的缺口上限：更早的天不发明细（只体现在 30 天概要里），防止单次请求过大
	private const val BACKFILL_DETAIL_LIMIT = 14

	// 概要统计窗口（与首份报告的明细窗口一致）
	private const val SUMMARY_WINDOW_DAYS = 30

	// 单飞锁：闹钟、启动自愈、页面懒检查可能同时到点，只允许一次真实生成
	private val generateMutex = Mutex()

	// 触发结果：调用方（UI / 闹钟 / 自愈）据此展示或发通知
	sealed class Outcome {
		object NotConfigured : Outcome()
		object AlreadyUpToDate : Outcome()
		data class Success(val targetDateEpochDay: Long, val parseOk: Boolean, val summary: String) : Outcome()
		data class Failed(val reasonText: String) : Outcome()
	}

	// 条件触发：配置齐全且昨日报告缺失才生成（闹钟 / 启动自愈 / AI 页懒检查用）
	suspend fun generateIfDue(context: Context): Outcome {
		val repo = SettingsRepository.get(context)
		val config = readConfig(repo)
		if (config == null) {
			return Outcome.NotConfigured
		}
		val yesterday = LocalDate.now().minusDays(1)
		if (repo.aiLastReportEpochDay.first() >= yesterday.toEpochDay()) {
			return Outcome.AlreadyUpToDate
		}
		return runGeneration(context, config)
	}

	// 手动触发：无视水位线强制执行——已生成过昨日报告时重新生成并整行覆盖
	suspend fun generateNow(context: Context): Outcome {
		val repo = SettingsRepository.get(context)
		val config = readConfig(repo) ?: return Outcome.NotConfigured
		return runGeneration(context, config)
	}

	// 配置三要素的非空读取；任一为空返回 null
	private data class AiConfig(val baseUrl: String, val apiKey: String, val model: String)

	private suspend fun readConfig(repo: SettingsRepository): AiConfig? {
		val url = repo.aiBaseUrl.first().trim()
		val key = repo.aiApiKey.first().trim()
		val model = repo.aiModel.first().trim()
		if (url.isBlank() || key.isBlank() || model.isBlank()) {
			return null
		}
		return AiConfig(url, key, model)
	}

	private suspend fun runGeneration(context: Context, config: AiConfig): Outcome {
		return generateMutex.withLock {
			val repo = SettingsRepository.get(context)
			val db = DailyControlDatabase.get(context)
			val today = LocalDate.now()
			val yesterday = today.minusDays(1)
			val watermark = repo.aiLastReportEpochDay.first()

			// 模式判定：无水位线=首份；缺 1 天（或已生成=重生成）=日常；缺多天=合并补交
			val mode = when {
				watermark == 0L -> AiPromptBuilder.Mode.FIRST
				yesterday.toEpochDay() - watermark <= 1 -> AiPromptBuilder.Mode.DAILY
				else -> AiPromptBuilder.Mode.BACKFILL
			}

			// 明细区间：首份=近30天；日常=昨日；补交=缺口（最早回溯不超过 14 天）
			val detailFrom = when (mode) {
				AiPromptBuilder.Mode.FIRST -> yesterday.minusDays(SUMMARY_WINDOW_DAYS - 1L)
				AiPromptBuilder.Mode.DAILY -> yesterday
				AiPromptBuilder.Mode.BACKFILL -> LocalDate.ofEpochDay(
					maxOf(watermark + 1, yesterday.toEpochDay() - BACKFILL_DETAIL_LIMIT + 1)
				)
			}
			val detailDiet = db.dietRecordDao().getBetweenOnce(
				TimeUtil.startOfDayMillis(detailFrom), TimeUtil.endOfDayMillis(yesterday)
			)
			val detailExercise = db.exerciseRecordDao().getBetweenOnce(
				TimeUtil.startOfDayMillis(detailFrom), TimeUtil.endOfDayMillis(yesterday)
			)

			// 近 30 天概要与近几天已给建议（防重复）独立于明细窗口计算
			val summaryFrom = yesterday.minusDays(SUMMARY_WINDOW_DAYS - 1L)
			val summaryDiet = db.dietRecordDao().getBetweenOnce(
				TimeUtil.startOfDayMillis(summaryFrom), TimeUtil.endOfDayMillis(yesterday)
			)
			val summaryExercise = db.exerciseRecordDao().getBetweenOnce(
				TimeUtil.startOfDayMillis(summaryFrom), TimeUtil.endOfDayMillis(yesterday)
			)
			val summaryJson = AiDataSummarizer.build(summaryDiet, summaryExercise, SUMMARY_WINDOW_DAYS)
			val recentSuggestions = db.aiReportDao().recentParsed(3)
				.flatMap { report -> suggestionsBrief(report.suggestionsJson) }

			val userMessage = AiPromptBuilder.buildUserMessage(
				mode, yesterday, detailFrom, yesterday, detailDiet, detailExercise, summaryJson, recentSuggestions
			)
			val messages = listOf(AiPromptBuilder.buildSystemMessage(), userMessage)
			val sentPayload = AiPromptBuilder.buildSentPayloadText(userMessage)

			// 无论成败都记录尝试时刻（AI 页状态卡展示）
			repo.setAiLastAttemptAt(System.currentTimeMillis())
			when (val callResult = AiClient.chat(config.baseUrl, config.apiKey, config.model, messages)) {
				is AiClient.CallResult.Success -> {
					val parsed = AiReportParser.parse(callResult.content)
					val report = AiReport(
						reportDateEpochDay = yesterday.toEpochDay(),
						kind = mode.name.lowercase(),
						generatedAt = System.currentTimeMillis(),
						summary = parsed?.summary ?: "",
						dietAnalysis = parsed?.dietAnalysis ?: "",
						exerciseAnalysis = parsed?.exerciseAnalysis ?: "",
						suggestionsJson = parsed?.suggestionsJson ?: "[]",
						questionsJson = parsed?.questionsJson ?: "[]",
						parseOk = parsed != null,
						rawResponse = callResult.content,
						sentPayload = sentPayload
					)
					db.aiReportDao().insert(report)
					db.aiReportDao().deleteOlderThan(today.toEpochDay() - (KEEP_DAYS - 1))
					// 接口成功即推进水位线；格式异常不阻塞次日流程（原文已存档，状态卡会提示可重试）
					repo.setAiLastReportEpochDay(yesterday.toEpochDay())
					repo.setAiLastErrorText(if (parsed == null) "上次生成：返回格式异常，已按原文保存，可重新生成" else "")
					Outcome.Success(yesterday.toEpochDay(), parsed != null, report.summary)
				}
				is AiClient.CallResult.HttpError -> {
					val reason = "HTTP ${callResult.httpCode}，${callResult.reasonText}"
					repo.setAiLastErrorText(reason)
					Outcome.Failed(reason)
				}
				is AiClient.CallResult.NetworkError -> {
					repo.setAiLastErrorText(callResult.reasonText)
					Outcome.Failed(callResult.reasonText)
				}
			}
		}
	}

	// 建议列表 → 简短摘要（主题 + 截断内容），用于下一次提示词的「勿重复」段落
	private fun suggestionsBrief(suggestionsJson: String): List<String> {
		return try {
			val array = JSONArray(suggestionsJson)
			List(array.length()) { index ->
				val item = array.optJSONObject(index)
				val topic = item?.optString("topic", "") ?: ""
				val brief = (item?.optString("content", "") ?: "").take(40)
				if (topic.isBlank()) brief else "[$topic] $brief"
			}.filter { it.isNotBlank() }
		} catch (e: Exception) {
			emptyList()
		}
	}
}
