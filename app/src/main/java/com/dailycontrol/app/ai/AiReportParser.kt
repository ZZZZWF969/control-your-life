package com.dailycontrol.app.ai

import org.json.JSONArray
import org.json.JSONObject

// AI 返回正文的宽容拆解：正常拿到约定 JSON；模型偶发不守格式时尽力保住字段，
// 完全拆不开则返回 null——由生成器降级为「原文保存、parseOk=false」
object AiReportParser {

	data class ParsedReport(
		val summary: String,
		val dietAnalysis: String,
		val exerciseAnalysis: String,
		val suggestionsJson: String, // [{topic,content,level}] 的 JSON 文本（空数组合法）
		val questionsJson: String    // 0.4.1 追问机制启用，本版恒为空数组
	)

	fun parse(content: String): ParsedReport? {
		val jsonText = extractJsonBlock(content) ?: return null
		val root = try {
			JSONObject(jsonText)
		} catch (e: Exception) {
			return null
		}
		val summary = root.optString("summary", "")
		val diet = root.optString("dietAnalysis", "")
		val exercise = root.optString("exerciseAnalysis", "")
		// 三段正文全空说明返回的根本不是报告结构
		if (summary.isBlank() && diet.isBlank() && exercise.isBlank()) {
			return null
		}
		val suggestions = normalizeSuggestions(root.optJSONArray("suggestions"))
		// 追问归一化（0.4.1）：缺 topic 取问题前 10 字、缺 type 按 oneoff——
		// 缺省按一次性最安全（大不了下次再问），实体结构与 AiAnswers.parseQuestions 对齐
		val questions = normalizeQuestions(root.optJSONArray("questions"))
		return ParsedReport(summary, diet, exercise, suggestions.toString(), questions.toString())
	}

	// 从回复正文里抠出 JSON：剥 ```json 围栏、截首尾大括号（模型偶尔在 JSON 前后加说明文字）
	private fun extractJsonBlock(content: String): String? {
		var text = content.trim()
		if (text.startsWith("```")) {
			text = text.removePrefix("```json").removePrefix("```").trim()
			val fenceEnd = text.lastIndexOf("```")
			if (fenceEnd >= 0) {
				text = text.substring(0, fenceEnd).trim()
			}
		}
		val start = text.indexOf('{')
		val end = text.lastIndexOf('}')
		if (start < 0 || end <= start) {
			return null
		}
		return text.substring(start, end + 1)
	}

	// 追问列表归一化：兼容把 text 写成 question 的返回；空正文条目跳过
	private fun normalizeQuestions(array: JSONArray?): JSONArray {
		val result = JSONArray()
		if (array == null) {
			return result
		}
		for (i in 0 until array.length()) {
			val item = array.optJSONObject(i) ?: continue
			val text = item.optString("text", "").ifBlank { item.optString("question", "") }
			if (text.isBlank()) {
				continue
			}
			result.put(
				JSONObject()
					.put("topic", item.optString("topic", "").ifBlank { text.take(10) })
					.put("text", text)
					.put("type", if (item.optString("type", "") == "profile") "profile" else "oneoff")
			)
		}
		return result
	}

	// 逐条宽容读取建议列表：缺 headline/detail 的条目回退旧 content 字段（兼容旧报告），
	// 没有任何正文的条目跳过，单条脏数据不拖垮整份报告
	private fun normalizeSuggestions(array: JSONArray?): JSONArray {
		val result = JSONArray()
		if (array == null) {
			return result
		}
		for (i in 0 until array.length()) {
			val item = array.optJSONObject(i) ?: continue
			val legacyContent = item.optString("content", "")
			val headline = item.optString("headline", "").ifBlank { legacyContent }
			if (headline.isBlank()) {
				continue
			}
			result.put(
				JSONObject()
					.put("topic", item.optString("topic", ""))
					.put("headline", headline)
					.put("detail", item.optString("detail", ""))
					.put("level", item.optString("level", "普通"))
					.put("basis", item.optString("basis", ""))
			)
		}
		return result
	}
}
