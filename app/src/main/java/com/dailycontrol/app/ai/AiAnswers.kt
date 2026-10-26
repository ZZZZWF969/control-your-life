package com.dailycontrol.app.ai

import org.json.JSONArray
import org.json.JSONObject

// 追问机制的数据契约（0.4.1）：questionsJson / answersJson / 档案 JSON 的结构与宽容解析。
// 生成器（写入端）与 AI 页（展示作答端）共用这一份，字段语义只此一处说了算
object AiAnswers {

	// 一条 AI 追问（questionsJson 的元素）
	data class QuestionItem(
		val topic: String, // 主题（模型缺省时为问题前 10 字）
		val text: String,  // 问题正文
		val type: String   // profile=档案型（答一次长期携带）/ oneoff=一次性（只随下一次报告提交）
	)

	// 一条用户作答（answersJson 的元素）
	data class AnswerItem(
		val question: String,        // 问题正文（冗余存储，作答与提交自包含）
		val type: String,            // 沿用问题的类型标记
		val answer: String,          // 空串 = 未答（删除答案即回空串）
		val answeredAt: Long,        // 作答时刻，epoch 毫秒
		val submittedEpochDay: Long  // 随哪份报告提交过（0 = 尚未提交）
	)

	// 一条档案项（ai_profile_json 的元素）
	data class ProfileItem(
		val topic: String,     // 主题（覆盖更新的 key）
		val question: String,  // 当初的问题正文（档案页展示用）
		val answer: String,
		val updatedAt: Long
	)

	// ===== questionsJson =====

	fun parseQuestions(json: String): List<QuestionItem> {
		return try {
			val array = JSONArray(json)
			List(array.length()) { index ->
				val item = array.optJSONObject(index)
				QuestionItem(
					topic = (item?.optString("topic", "") ?: "").ifBlank { (item?.optString("text", "") ?: "").take(10) },
					text = item?.optString("text", "") ?: "",
					type = if (item?.optString("type", "") == "profile") "profile" else "oneoff"
				)
			}.filter { it.text.isNotBlank() }
		} catch (e: Exception) {
			emptyList()
		}
	}

	// ===== answersJson =====

	fun parseAnswers(json: String): MutableList<AnswerItem> {
		val result = mutableListOf<AnswerItem>()
		return try {
			val array = JSONArray(json)
			for (i in 0 until array.length()) {
				val item = array.optJSONObject(i) ?: continue
				val question = item.optString("question", "")
				if (question.isBlank()) {
					continue
				}
				result.add(
					AnswerItem(
						question = question,
						type = if (item.optString("type", "") == "profile") "profile" else "oneoff",
						answer = item.optString("answer", ""),
						answeredAt = item.optLong("answeredAt", 0L),
						submittedEpochDay = item.optLong("submittedEpochDay", 0L)
					)
				)
			}
			result
		} catch (e: Exception) {
			result
		}
	}

	fun answersToJson(items: List<AnswerItem>): String {
		return JSONArray(items.map { item ->
			JSONObject()
				.put("question", item.question)
				.put("type", item.type)
				.put("answer", item.answer)
				.put("answeredAt", item.answeredAt)
				.put("submittedEpochDay", item.submittedEpochDay)
		}).toString()
	}

	// ===== ai_profile_json =====

	fun parseProfile(json: String): MutableList<ProfileItem> {
		val result = mutableListOf<ProfileItem>()
		return try {
			val array = JSONArray(json)
			for (i in 0 until array.length()) {
				val item = array.optJSONObject(i) ?: continue
				val topic = item.optString("topic", "")
				if (topic.isBlank()) {
					continue
				}
				result.add(
					ProfileItem(
						topic = topic,
						question = item.optString("question", ""),
						answer = item.optString("answer", ""),
						updatedAt = item.optLong("updatedAt", 0L)
					)
				)
			}
			result
		} catch (e: Exception) {
			result
		}
	}

	fun profileToJson(items: List<ProfileItem>): String {
		return JSONArray(items.map { item ->
			JSONObject()
				.put("topic", item.topic)
				.put("question", item.question)
				.put("answer", item.answer)
				.put("updatedAt", item.updatedAt)
		}).toString()
	}

	// 档案型答案并入档案：按 topic 覆盖（问题文本与更新时间随新答案刷新）
	fun mergeProfileItem(current: List<ProfileItem>, topic: String, question: String, answer: String): List<ProfileItem> {
		val updated = current.filterNot { it.topic == topic } + ProfileItem(
			topic = topic,
			question = question,
			answer = answer,
			updatedAt = System.currentTimeMillis()
		)
		return updated.sortedBy { it.topic }
	}
}
