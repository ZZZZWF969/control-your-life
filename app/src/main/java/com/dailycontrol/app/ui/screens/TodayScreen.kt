package com.dailycontrol.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailycontrol.app.R
import com.dailycontrol.app.data.db.DailyControlDatabase
import com.dailycontrol.app.data.entity.DietRecord
import com.dailycontrol.app.data.entity.DietRecordWithItems
import com.dailycontrol.app.data.entity.ExerciseRecord
import com.dailycontrol.app.data.entity.MealType
import com.dailycontrol.app.ui.components.TypeBadge
import com.dailycontrol.app.util.TimeUtil
import java.time.LocalDate
import java.time.LocalTime

// 按当前时段返回问候语资源
private fun greetingResOf(hour: Int): Int {
	return when (hour) {
		in 5..10 -> R.string.greeting_morning
		in 11..12 -> R.string.greeting_noon
		in 13..17 -> R.string.greeting_afternoon
		in 18..22 -> R.string.greeting_evening
		else -> R.string.greeting_late
	}
}

// 今日页：当天饮食/运动的只读汇总 + 三餐完成度与运动时长小结；编辑操作跳转记录页
@Composable
fun TodayScreen(onGoToRecord: () -> Unit) {
	val context = LocalContext.current
	val db = remember { DailyControlDatabase.get(context) }
	val today = remember { LocalDate.now() }

	val dietRecords by remember {
		db.dietRecordDao().observeBetween(
			TimeUtil.startOfDayMillis(today),
			TimeUtil.endOfDayMillis(today)
		)
	}.collectAsStateWithLifecycle(initialValue = emptyList())
	val exerciseRecords by remember {
		db.exerciseRecordDao().observeBetween(
			TimeUtil.startOfDayMillis(today),
			TimeUtil.endOfDayMillis(today)
		)
	}.collectAsStateWithLifecycle(initialValue = emptyList())

	// 三餐完成情况：早/午/晚任一存在记录即算完成
	val completedMeals = remember(dietRecords) {
		dietRecords.map { MealType.fromStorage(it.record.mealType) }
			.toSet()
			.intersect(setOf(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER))
	}
	// 今日运动总时长（分钟）
	val totalExerciseMinutes = remember(exerciseRecords) {
		exerciseRecords.sumOf { (it.endAt - it.startAt) / 60000L }.toInt()
	}

	Column(
		modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
		verticalArrangement = Arrangement.spacedBy(14.dp)
	) {
		// 问候 + 日期
		Column {
			Text(
				text = stringResource(greetingResOf(LocalTime.now().hour)),
				style = MaterialTheme.typography.headlineMedium
			)
			Text(
				text = TimeUtil.formatDate(today),
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)
		}

		// 小结卡片：三餐完成度 + 运动总时长
		SummaryCard(completedMeals, totalExerciseMinutes)

		// 今日饮食
		Text(
			text = stringResource(R.string.today_section_diet) + "（${dietRecords.size}）",
			style = MaterialTheme.typography.titleMedium
		)
		if (dietRecords.isEmpty()) {
			Text(
				text = stringResource(R.string.today_empty_hint),
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)
		} else {
			dietRecords.take(6).forEach { item ->
				TodayDietRow(item)
			}
		}

		// 今日运动
		Text(
			text = stringResource(R.string.today_section_exercise) + "（${exerciseRecords.size}）",
			style = MaterialTheme.typography.titleMedium
		)
		if (exerciseRecords.isEmpty()) {
			Text(
				text = stringResource(R.string.today_empty_hint),
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)
		} else {
			exerciseRecords.take(6).forEach { record ->
				TodayExerciseRow(record)
			}
		}

		// 任一侧为空时提供快捷入口
		if (dietRecords.isEmpty() || exerciseRecords.isEmpty()) {
			Button(onClick = onGoToRecord, modifier = Modifier.fillMaxWidth()) {
				Text(stringResource(R.string.today_go_record))
			}
		}
	}
}

// 三餐完成度小结卡：早/午/晚徽标（未记录置灰）+ 运动总时长
@Composable
private fun SummaryCard(completedMeals: Set<MealType>, totalExerciseMinutes: Int) {
	Card(modifier = Modifier.fillMaxWidth()) {
		Row(
			modifier = Modifier.fillMaxWidth().padding(16.dp),
			verticalAlignment = Alignment.CenterVertically
		) {
			listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER).forEach { meal ->
				Column(
					modifier = Modifier.weight(1f),
					horizontalAlignment = Alignment.CenterHorizontally
				) {
					val done = meal in completedMeals
					// 已记录用品牌色系徽标，未记录整体置灰
					TypeBadge(
						badgeChar = when (meal) {
							MealType.BREAKFAST -> "早"
							MealType.LUNCH -> "午"
							else -> "晚"
						},
						background = if (done) Color(0xFFC2F0E9) else Color(0xFFE3E6E5),
						content = if (done) Color(0xFF042F2C) else Color(0xFF8A9490)
					)
					Spacer(modifier = Modifier.height(6.dp))
					Text(
						text = if (done) stringResource(meal.labelRes) else stringResource(R.string.today_meal_missing),
						style = MaterialTheme.typography.labelSmall,
						color = if (done) MaterialTheme.colorScheme.onSurface
						else MaterialTheme.colorScheme.onSurfaceVariant
					)
				}
			}
			// 运动时长小结
			Column(
				modifier = Modifier.weight(1.4f),
				horizontalAlignment = Alignment.CenterHorizontally
			) {
				Text(
					text = stringResource(R.string.today_summary_exercise),
					style = MaterialTheme.typography.labelSmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)
				Text(
					text = if (totalExerciseMinutes > 0) {
						stringResource(R.string.today_exercise_minutes, totalExerciseMinutes)
					} else {
						stringResource(R.string.today_exercise_none)
					},
					style = MaterialTheme.typography.titleMedium
				)
			}
		}
	}
}

// 今日页的精简饮食行：徽标 + 内容摘要（只读，编辑去记录页）
@Composable
private fun TodayDietRow(item: DietRecordWithItems) {
	val record: DietRecord = item.record
	val mealType = MealType.fromStorage(record.mealType)
	Row(verticalAlignment = Alignment.CenterVertically) {
		TypeBadge(
			badgeChar = when (mealType) {
				MealType.BREAKFAST -> "早"
				MealType.LUNCH -> "午"
				MealType.DINNER -> "晚"
				MealType.SNACK -> "零"
				MealType.SUPPLEMENT -> "补"
			},
			background = when (mealType) {
				MealType.BREAKFAST -> Color(0xFFF6E3B4)
				MealType.LUNCH -> Color(0xFFC2F0E9)
				MealType.DINNER -> Color(0xFFC7CBF8)
				MealType.SNACK -> Color(0xFFF8D4DE)
				MealType.SUPPLEMENT -> Color(0xFFCFEBC4)
			},
			content = when (mealType) {
				MealType.BREAKFAST -> Color(0xFF6B4E00)
				MealType.LUNCH -> Color(0xFF042F2C)
				MealType.DINNER -> Color(0xFF1B1F65)
				MealType.SNACK -> Color(0xFF652E3E)
				MealType.SUPPLEMENT -> Color(0xFF1F3D17)
			}
		)
		Spacer(modifier = Modifier.size(12.dp))
		Column {
			Text(
				text = record.foodText,
				style = MaterialTheme.typography.titleSmall,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis
			)
			Text(
				text = stringResource(mealType.labelRes) + " · " + TimeUtil.formatTime(record.eatenAt),
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)
		}
	}
}

// 今日页的精简运动行
@Composable
private fun TodayExerciseRow(record: ExerciseRecord) {
	val durationMinutes = ((record.endAt - record.startAt) / 60000L).toInt()
	Row(verticalAlignment = Alignment.CenterVertically) {
		TypeBadge(badgeChar = "动", background = Color(0xFFC7CBF8), content = Color(0xFF1B1F65))
		Spacer(modifier = Modifier.size(12.dp))
		Column {
			Text(
				text = record.typeText,
				style = MaterialTheme.typography.titleSmall,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis
			)
			Text(
				text = stringResource(R.string.today_exercise_minutes, durationMinutes) +
					" · " + TimeUtil.formatTime(record.startAt),
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)
		}
	}
}
