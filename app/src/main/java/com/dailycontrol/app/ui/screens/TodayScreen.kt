package com.dailycontrol.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.dailycontrol.app.data.entity.NutrientItem
import com.dailycontrol.app.data.settings.SettingsRepository
import com.dailycontrol.app.notify.Notifier
import com.dailycontrol.app.schedule.VocabPlanner
import com.dailycontrol.app.ui.RecordSaver
import com.dailycontrol.app.ui.components.EntryTypePickerSheet
import com.dailycontrol.app.ui.components.TypeBadge
import com.dailycontrol.app.util.ClockLinker
import com.dailycontrol.app.util.TimeUtil
import kotlinx.coroutines.launch
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

// 今日页：今天干了什么（饮食/运动回顾）+ 没干什么（三餐完成度、背单词）+ 等着干什么（待办清单）
// 「提醒」页职责已并入本页（2026-10-19 页面整合批次）：
// 记一笔悬浮按钮全程不跳页、记录行点击直接编辑、底部有去规划与去时钟入口
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
	onGoToRecord: () -> Unit,
	onGoToPlan: () -> Unit
) {
	val context = LocalContext.current
	val scope = rememberCoroutineScope()
	val db = remember { DailyControlDatabase.get(context) }
	val settings = SettingsRepository.get(context)
	val snackbarHostState = remember { SnackbarHostState() }
	val today = remember { LocalDate.now() }
	val tomorrow = today.plusDays(1)

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
	val todayTasks by remember { db.todoTaskDao().observeByDate(today.toEpochDay()) }
		.collectAsStateWithLifecycle(initialValue = emptyList())

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

	// 背单词状态：以「已背日期是否为今天」判断（原提醒页逻辑）
	val vocabDoneDate by settings.vocabDoneDate.collectAsStateWithLifecycle(initialValue = "")
	val vocabPackage by settings.vocabPackage.collectAsStateWithLifecycle(initialValue = "")
	val vocabDoneToday = vocabDoneDate == today.toString()

	// 唤醒存档（原提醒页逻辑）：明天有唤醒计划且未自记确认时显示去时钟入口
	val wakePlanDate by settings.wakePlanDate.collectAsStateWithLifecycle(initialValue = "")
	val wakePlanTimes by settings.wakePlanTimes.collectAsStateWithLifecycle(initialValue = emptyList())
	val wakeTomorrow = wakePlanDate == tomorrow.toString() && wakePlanTimes.isNotEmpty()
	val wakeClockConfirmed by settings.wakeClockConfirmedDate.collectAsStateWithLifecycle(initialValue = "")
	val showClockEntry = wakeTomorrow && wakeClockConfirmed != wakePlanDate

	// 弹层状态：类型选择 / 饮食表单（空=新建，非空=编辑）/ 运动表单
	var showTypePicker by remember { mutableStateOf(false) }
	var showDietSheet by remember { mutableStateOf(false) }
	var dietSheetRecord by remember { mutableStateOf<DietRecord?>(null) }
	var dietSheetItems by remember { mutableStateOf<List<NutrientItem>>(emptyList()) }
	var showExerciseSheet by remember { mutableStateOf(false) }
	var exerciseSheetRecord by remember { mutableStateOf<ExerciseRecord?>(null) }

	Scaffold(
		snackbarHost = { SnackbarHost(snackbarHostState) },
		floatingActionButton = {
			ExtendedFloatingActionButton(
				onClick = { showTypePicker = true },
				icon = { Icon(Icons.Filled.Add, contentDescription = null) },
				text = { Text(stringResource(R.string.record_add_entry)) }
			)
		}
	) { innerPadding ->
		Column(
			modifier = Modifier.fillMaxSize()
				.padding(innerPadding)
				.verticalScroll(rememberScrollState())
				.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 120.dp),
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

			// 今日饮食：行可点直接编辑，超 6 条给查看全部入口
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
					TodayDietRow(item) {
						dietSheetRecord = item.record
						dietSheetItems = item.nutrientItems
						showDietSheet = true
					}
				}
				if (dietRecords.size > 6) {
					TextButton(onClick = onGoToRecord, modifier = Modifier.fillMaxWidth()) {
						Text(stringResource(R.string.today_view_all, dietRecords.size))
					}
				}
			}

			// 今日运动：同上
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
					TodayExerciseRow(record) {
						exerciseSheetRecord = record
						showExerciseSheet = true
					}
				}
				if (exerciseRecords.size > 6) {
					TextButton(onClick = onGoToRecord, modifier = Modifier.fillMaxWidth()) {
						Text(stringResource(R.string.today_view_all, exerciseRecords.size))
					}
				}
			}

			// 背单词状态卡（极简版）
			TodayVocabCard(
				doneToday = vocabDoneToday,
				showGoEntry = vocabPackage.isNotBlank(),
				onMarkDone = {
					scope.launch {
						// 记录日期并把循环提醒滚到明天 10:00，撤下当前通知
						VocabPlanner.markDoneToday(context)
						Notifier.cancelVocabNotification(context)
					}
				},
				onGoVocab = {
					context.packageManager.getLaunchIntentForPackage(vocabPackage)?.let {
						context.startActivity(it)
					}
				}
			)

			// 今日待办清单
			TodayTodoSection(
				tasks = todayTasks,
				snackbarHostState = snackbarHostState,
				onGoToPlan = onGoToPlan
			)

			// 去时钟入口（双保险方案，原提醒页逻辑）：明天有唤醒计划且未确认「已开」时显示
			if (showClockEntry) {
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.spacedBy(8.dp),
					verticalAlignment = Alignment.CenterVertically
				) {
					OutlinedButton(
						onClick = {
							// 个别 ROM 无时钟应用/不支持 SHOW_ALARMS 时给出可见反馈，便于真机排查
							if (!ClockLinker.openAlarmList(context)) {
								Toast.makeText(
									context,
									context.getString(R.string.clock_open_failed),
									Toast.LENGTH_SHORT
								).show()
							}
						},
						modifier = Modifier.weight(1f)
					) {
						Text(stringResource(R.string.remind_open_clock_entry, TimeUtil.formatTime(wakePlanTimes.last())))
					}
					TextButton(onClick = {
						scope.launch { settings.setWakeClockConfirmedDate(wakePlanDate) }
					}) {
						Text(stringResource(R.string.remind_clock_confirmed))
					}
				}
			}

			// 去规划入口（常驻）：22:00 通知深链直达规划页，这里提供常驻入口
			OutlinedButton(onClick = onGoToPlan, modifier = Modifier.fillMaxWidth()) {
				Icon(imageVector = Icons.Filled.AddCircle, contentDescription = null)
				Spacer(modifier = Modifier.size(8.dp))
				Text(stringResource(R.string.remind_plan_entry))
			}
		}
	}

	// 「记一笔」的类型选择弹层
	if (showTypePicker) {
		EntryTypePickerSheet(
			onDismiss = { showTypePicker = false },
			onPickDiet = {
				showTypePicker = false
				dietSheetRecord = null
				dietSheetItems = emptyList()
				showDietSheet = true
			},
			onPickExercise = {
				showTypePicker = false
				exerciseSheetRecord = null
				showExerciseSheet = true
			}
		)
	}

	if (showDietSheet) {
		DietEntrySheet(
			initialRecord = dietSheetRecord,
			initialItems = dietSheetItems,
			date = today,
			onDismiss = { showDietSheet = false },
			onSave = { record, nutrientItems ->
				showDietSheet = false
				scope.launch {
					RecordSaver.saveDietRecordWithItems(db, record, nutrientItems)
					snackbarHostState.showSnackbar(context.getString(R.string.saved_done))
				}
			},
			onDelete = dietSheetRecord?.let { target ->
				{
					showDietSheet = false
					dietSheetRecord = null
					scope.launch {
						db.dietRecordDao().delete(target)
						snackbarHostState.showSnackbar(context.getString(R.string.deleted_done))
					}
				}
			}
		)
	}

	if (showExerciseSheet) {
		ExerciseEntrySheet(
			initialRecord = exerciseSheetRecord,
			date = today,
			onDismiss = { showExerciseSheet = false },
			onSave = { record ->
				showExerciseSheet = false
				scope.launch {
					RecordSaver.saveExerciseRecord(db, record)
					snackbarHostState.showSnackbar(context.getString(R.string.saved_done))
				}
			},
			onDelete = exerciseSheetRecord?.let { target ->
				{
					showExerciseSheet = false
					exerciseSheetRecord = null
					scope.launch {
						db.exerciseRecordDao().delete(target)
						snackbarHostState.showSnackbar(context.getString(R.string.deleted_done))
					}
				}
			}
		)
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

// 今日页的精简饮食行：徽标 + 内容摘要，点击直接弹出编辑表单
@Composable
private fun TodayDietRow(item: DietRecordWithItems, onClick: () -> Unit) {
	val record: DietRecord = item.record
	val mealType = MealType.fromStorage(record.mealType)
	Row(
		verticalAlignment = Alignment.CenterVertically,
		modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
	) {
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
		Column(modifier = Modifier.weight(1f)) {
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
		// 「可进入编辑」的视觉暗示（与记录页卡片一致）
		Icon(
			imageVector = Icons.Filled.KeyboardArrowRight,
			contentDescription = null,
			tint = MaterialTheme.colorScheme.onSurfaceVariant
		)
	}
}

// 今日页的精简运动行：点击直接弹出编辑表单
@Composable
private fun TodayExerciseRow(record: ExerciseRecord, onClick: () -> Unit) {
	val durationMinutes = ((record.endAt - record.startAt) / 60000L).toInt()
	Row(
		verticalAlignment = Alignment.CenterVertically,
		modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
	) {
		TypeBadge(badgeChar = "动", background = Color(0xFFC7CBF8), content = Color(0xFF1B1F65))
		Spacer(modifier = Modifier.size(12.dp))
		Column(modifier = Modifier.weight(1f)) {
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
		Icon(
			imageVector = Icons.Filled.KeyboardArrowRight,
			contentDescription = null,
			tint = MaterialTheme.colorScheme.onSurfaceVariant
		)
	}
}
