package com.dailycontrol.app.ai

import com.dailycontrol.app.data.entity.DietRecordWithItems
import com.dailycontrol.app.data.entity.ExerciseRecord
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId

// 近 30 天统计概要：由应用自己从数据库计算，AI 不必从流水里自己数——
// 模式给得又快又稳，也把上行数据压到几百字（token 成本与隐私暴露双优）
object AiDataSummarizer {

	// 餐别 storageName → 中文标签：概要是给 AI 看的数据，直接用中文键值自解释
	private val MEAL_LABELS = mapOf(
		"breakfast" to "早餐", "lunch" to "午餐", "dinner" to "晚餐",
		"snack" to "零食", "supplement" to "营养品"
	)

	fun build(
		dietRecords: List<DietRecordWithItems>,
		exerciseRecords: List<ExerciseRecord>,
		windowDays: Int
	): JSONObject {
		val root = JSONObject()
		root.put("统计窗口天数", windowDays)
		root.put("饮食", buildDietSummary(dietRecords, windowDays))
		root.put("运动", buildExerciseSummary(exerciseRecords, windowDays))
		return root
	}

	private fun buildDietSummary(records: List<DietRecordWithItems>, windowDays: Int): JSONObject {
		val root = JSONObject()
		root.put("记录条数", records.size)

		// 三餐规律度：某天出现过该餐别的记录即计 1 天，统计各餐覆盖天数占比
		val daysWithMeal = mutableMapOf<String, MutableSet<Long>>()
		records.forEach { entry ->
			val day = epochDayOf(entry.record.eatenAt)
			daysWithMeal.getOrPut(entry.record.mealType) { mutableSetOf() }.add(day)
		}
		val regularity = JSONObject()
		listOf("breakfast", "lunch", "dinner").forEach { meal ->
			val days = daysWithMeal[meal]?.size ?: 0
			regularity.put(MEAL_LABELS[meal] ?: meal, "$days/$windowDays 天有记录")
		}
		root.put("三餐规律度", regularity)

		// 高频食物：食材文本按常见分隔符拆词统计，取前 10（保留「青椒炒肉」这类不可再拆的菜名）
		val foodCounts = mutableMapOf<String, Int>()
		records.forEach { entry ->
			splitFood(entry.record.foodText).forEach { word ->
				foodCounts[word] = (foodCounts[word] ?: 0) + 1
			}
		}
		root.put("高频食物", topEntriesJson(foodCounts, 10, "食物", "次数"))

		// 营养品：品名频次 + 成分剂量频次（两次聚合分开统计，各有用途）
		val supplements = records.filter { it.record.mealType == "supplement" }
		val supplementCounts = mutableMapOf<String, Int>()
		val nutrientCounts = mutableMapOf<String, Int>()
		supplements.forEach { entry ->
			supplementCounts[entry.record.foodText] = (supplementCounts[entry.record.foodText] ?: 0) + 1
			entry.nutrientItems.forEach { item ->
				val key = "${item.name} ${item.amountText}"
				nutrientCounts[key] = (nutrientCounts[key] ?: 0) + 1
			}
		}
		root.put("常吃营养品", topEntriesJson(supplementCounts, 8, "品名", "次数"))
		root.put("营养素成分频次", topEntriesJson(nutrientCounts, 10, "成分与剂量", "次数"))
		return root
	}

	private fun buildExerciseSummary(records: List<ExerciseRecord>, windowDays: Int): JSONObject {
		val root = JSONObject()
		root.put("记录条数", records.size)
		val coveredDays = records.map { epochDayOf(it.startAt) }.toSet().size
		root.put("覆盖天数", "$coveredDays/$windowDays 天")
		// 类型与强度分布：频次直接 JSON 化（key=类型/强度，value=次数）
		val typeCounts = mutableMapOf<String, Int>()
		val intensityCounts = mutableMapOf<String, Int>()
		records.forEach { record ->
			typeCounts[record.typeText] = (typeCounts[record.typeText] ?: 0) + 1
			intensityCounts[record.intensityText] = (intensityCounts[record.intensityText] ?: 0) + 1
		}
		root.put("类型分布", JSONObject(typeCounts.mapValues { it.value.toString() }))
		root.put("强度分布", JSONObject(intensityCounts.mapValues { it.value.toString() }))
		return root
	}

	// 频次表 → JSON 数组（降序取前 N 条，字段名自定义）
	private fun topEntriesJson(counts: Map<String, Int>, limit: Int, keyName: String, valueName: String): JSONArray {
		return JSONArray(counts.entries
			.sortedByDescending { it.value }
			.take(limit)
			.map { entry -> JSONObject().put(keyName, entry.key).put(valueName, entry.value) })
	}

	// 食材文本拆词：中文顿号/逗号/分号/空格都当分隔符
	private fun splitFood(text: String): List<String> {
		return text.split('、', '，', ',', '；', ';', ' ')
			.map { it.trim() }
			.filter { it.isNotEmpty() }
	}

	private fun epochDayOf(epochMillis: Long): Long {
		return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
	}
}
