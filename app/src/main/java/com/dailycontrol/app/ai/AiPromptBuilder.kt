package com.dailycontrol.app.ai

import com.dailycontrol.app.data.entity.DietRecordWithItems
import com.dailycontrol.app.data.entity.ExerciseRecord
import com.dailycontrol.app.util.TimeUtil
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// 提示词组装：system 固定（营养顾问角色 + 严格 JSON 输出约定），user 按生成模式拼数据段。
// user 内容同时作为「本次发送的数据」的展示来源——中文键让用户能亲眼看懂发了什么
object AiPromptBuilder {

	// 三种生成模式：首份（附近30天明细）/ 日常（昨日）/ 补交（中断缺口的合并回顾）
	enum class Mode { FIRST, DAILY, BACKFILL }

	private val MEAL_LABELS = mapOf(
		"breakfast" to "早餐", "lunch" to "午餐", "dinner" to "晚餐",
		"snack" to "零食", "supplement" to "营养品"
	)

	fun buildSystemMessage(): AiClient.ChatMessage {
		val systemText = """
			你是一位严谨的营养学顾问，服务对象是一名用生活管理应用记录饮食与运动的年轻人。只基于提供的数据给建议，不要编造数据中不存在的信息；用户无明显过量饮食时不要建议节食。参照《中国居民膳食指南（2022）》等权威建议。

			输出要求（必须严格遵守）：
			1. 只输出一个 JSON 对象，不要输出任何其他文字、解释或代码围栏。
			2. 格式：{"date":"YYYY-MM-DD","summary":"一句话总评，40字内","dietAnalysis":"饮食板块分析，120字内","exerciseAnalysis":"运动板块分析，120字内","suggestions":[{"topic":"主题词","content":"具体建议，60字内","level":"普通"}],"questions":[]}
			3. suggestions 给 3 到 5 条，按重要性从高到低排列；level 只能填"普通"或"提醒"。
			4. questions 恒为空数组。
			5. 全部内容使用简体中文。
		""".trimIndent()
		return AiClient.ChatMessage("system", systemText)
	}

	/**
	 * @param targetDate 报告归属日（= 被汇总时段的最末一天）
	 * @param detailFrom / detailTo 明细数据覆盖的日期区间（随模式不同）
	 * @param recentSuggestionTexts 近几天已给过的建议摘要（防重复；首份报告时为空）
	 */
	fun buildUserMessage(
		mode: Mode,
		targetDate: LocalDate,
		detailFrom: LocalDate,
		detailTo: LocalDate,
		detailDiet: List<DietRecordWithItems>,
		detailExercise: List<ExerciseRecord>,
		summaryJson: JSONObject,
		recentSuggestionTexts: List<String>
	): AiClient.ChatMessage {
		val root = JSONObject()
		root.put(
			"生成模式说明",
			when (mode) {
				Mode.FIRST -> "首次报告：请先基于近30天明细与概要总结该用户饮食与运动的整体模式，建议侧重可长期执行的习惯调整"
				Mode.DAILY -> "每日报告：聚焦明细日数据相对概要（常态）的变化，避免与「近期已给建议」重复"
				Mode.BACKFILL -> "补交回顾报告：此前一段时间未生成报告，请先对整个明细时段做整体回顾分析，再给建议"
			}
		)
		root.put("报告归属日", targetDate.toString())
		root.put("近30天概要", summaryJson)
		root.put("明细数据日期范围", "$detailFrom 至 $detailTo")
		root.put("饮食明细", buildDietDetail(detailDiet))
		root.put("运动明细", buildExerciseDetail(detailExercise))
		if (recentSuggestionTexts.isNotEmpty()) {
			root.put("近期已给建议（勿重复）", JSONArray(recentSuggestionTexts))
		}
		return AiClient.ChatMessage("user", root.toString())
	}

	// 「本次发送的数据」展示文本：同一 user 内容的美化排版。
	// 注意不含 API Key——Key 只存在于 HTTP 请求头，永不出现在数据体里
	fun buildSentPayloadText(userMessage: AiClient.ChatMessage): String {
		return JSONObject(userMessage.content).toString(2)
	}

	// 饮食明细逐条 JSON 化：只保留分析必需字段（餐别/时间/食物/饭量/重量/成分），id 等内部字段不上行
	private fun buildDietDetail(records: List<DietRecordWithItems>): JSONArray {
		val array = JSONArray()
		records.forEach { entry ->
			val record = entry.record
			val item = JSONObject()
				.put("日期", dateTextOf(record.eatenAt))
				.put("餐别", MEAL_LABELS[record.mealType] ?: record.mealType)
				.put("时间", TimeUtil.formatTime(record.eatenAt))
				.put("食物", record.foodText)
			if (record.portionText.isNotBlank()) {
				item.put("饭量", record.portionText)
			}
			if (!record.weightText.isNullOrBlank()) {
				item.put("重量容量", record.weightText)
			}
			if (entry.nutrientItems.isNotEmpty()) {
				item.put("成分", JSONArray(entry.nutrientItems.map { nutrient ->
					JSONObject().put("名", nutrient.name).put("量", nutrient.amountText)
				}))
			}
			array.put(item)
		}
		return array
	}

	private fun buildExerciseDetail(records: List<ExerciseRecord>): JSONArray {
		val array = JSONArray()
		records.forEach { record ->
			array.put(
				JSONObject()
					.put("日期", dateTextOf(record.startAt))
					.put("类型", record.typeText)
					.put("强度", record.intensityText)
					.put("总量", record.totalText)
					.put("开始", TimeUtil.formatTime(record.startAt))
					.put("结束", TimeUtil.formatTime(record.endAt))
			)
		}
		return array
	}

	private fun dateTextOf(epochMillis: Long): String {
		return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate().toString()
	}
}
