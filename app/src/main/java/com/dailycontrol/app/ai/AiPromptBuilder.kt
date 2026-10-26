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
			2. 格式：{"date":"YYYY-MM-DD","summary":"关键要点1；要点2；要点3","dietAnalysis":"饮食板块分析，120字内","exerciseAnalysis":"运动板块分析，120字内","suggestions":[{"topic":"主题词","headline":"行动摘要","detail":"展开说明","level":"普通","basis":"建议依据"}],"questions":[]}
			3. suggestions 给 3 到 5 条，按重要性从高到低排列；level 只能填"普通"或"提醒"。summary 是 2 到 4 个关键要点：每个要点为一句 15 字内短语，用中文分号"；"分隔，共 60 字内——概括当日最重要的事实与结论（如"饮品含糖偏多；晚餐缺主食；无运动记录"）。
			4. 每条建议遵循「先做什么、再为什么」：headline 约 15 字，用动宾结构直说要做什么（如"餐后吃橙子猕猴桃补维C"），不写原因；detail 80 字内，展开具体执行——份量、时机、替代选择，涉及营养品时写明剂型与参考剂量区间并注明"以产品说明或医嘱为准"；basis 60 字内，先写数据中的具体事实，再写权威参照与推荐量（如《中国居民膳食指南》建议每天蔬菜300-500克、水果200-350克）。
			5. 检测到营养素摄入不足或过量时，明确指出营养素名称，补充途径分两层：优先给食物来源；若该营养素日常饮食难以足量获取、适合营养品补充（如维生素D、钙、B族），同时给出营养品建议——写明常见剂型与参考剂量区间，并注明"以产品说明或医嘱为准"。不得指定或编造品牌。
			6. 内容宁可具体详细，不要为压缩篇幅牺牲信息量；但不要输出约定之外的字段。
			7. questions 是你要向用户提出的问题，每问格式 {"topic":"主题","text":"问题","type":"profile或oneoff"}。提问务必审慎：用户档案里已有的信息不要再问；身高体重目标等长期不变的信息只在档案缺失时问一次（type 填 profile）；昨晚睡眠等短期波动信息可在相关分析确有需要时问（type 填 oneoff）；没有真正需要的信息就给空数组，不要为了提问而提问。
			8. 全部内容使用简体中文。
		""".trimIndent()
		return AiClient.ChatMessage("system", systemText)
	}

	// 提示词上行用的轻量结构：档案（主题→答案）与待提交追问（问题→答案），
	// 由生成器从存储解析后传入，本层不依赖存储细节
	data class ProfileBrief(val topic: String, val answer: String)
	data class PendingAnswerBrief(val question: String, val answer: String)

	/**
	 * @param targetDate 报告归属日（= 被汇总时段的最末一天）
	 * @param detailFrom / detailTo 明细数据覆盖的日期区间（随模式不同）
	 * @param recentSuggestionTexts 近几天已给过的建议摘要（防重复；首份报告时为空）
	 * @param profileItems 用户档案（0.4.1：档案型追问的答案，长期携带）
	 * @param pendingAnswers 已答未提交的追问（0.4.1：随本次报告一并提交）
	 */
	fun buildUserMessage(
		mode: Mode,
		targetDate: LocalDate,
		detailFrom: LocalDate,
		detailTo: LocalDate,
		detailDiet: List<DietRecordWithItems>,
		detailExercise: List<ExerciseRecord>,
		summaryJson: JSONObject,
		recentSuggestionTexts: List<String>,
		profileItems: List<ProfileBrief>,
		pendingAnswers: List<PendingAnswerBrief>
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
		// 用户档案：档案型追问的长期答案，每次请求都携带——AI 应视为已知信息，不再就此提问
		if (profileItems.isNotEmpty()) {
			root.put("用户档案（已知信息，勿再提问）", JSONObject(profileItems.associate { it.topic to it.answer }))
		}
		// 待提交追问：用户对之前报告问题的回答，随本次报告一并生效
		if (pendingAnswers.isNotEmpty()) {
			root.put("用户对此前追问的补充", JSONArray(pendingAnswers.map { answer ->
				JSONObject().put("问题", answer.question).put("回答", answer.answer)
			}))
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
