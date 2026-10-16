package com.dailycontrol.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.withTransaction
import com.dailycontrol.app.R
import com.dailycontrol.app.data.db.DailyControlDatabase
import com.dailycontrol.app.data.entity.DietRecord
import com.dailycontrol.app.data.entity.ExerciseRecord
import com.dailycontrol.app.data.entity.MealType
import com.dailycontrol.app.data.entity.NutrientItem
import com.dailycontrol.app.ui.components.ConfirmDialog
import com.dailycontrol.app.ui.components.TypeBadge
import com.dailycontrol.app.util.TimeUtil
import kotlinx.coroutines.launch
import java.time.LocalDate

// 记录页：按日查看与管理饮食、运动记录，右下角「记一笔」新增
// initialMeal：三餐询问通知深链带来的预选餐别，进入后自动弹出该餐表单
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordScreen(initialMeal: String? = null) {
	val context = LocalContext.current
	val scope = rememberCoroutineScope()
	val db = remember { DailyControlDatabase.get(context) }
	var selectedDate by remember { mutableStateOf(LocalDate.now()) }

	// 按所选日期订阅当天的饮食记录，数据变化时界面自动刷新
	val dietFlow = remember(selectedDate) {
		db.dietRecordDao().observeBetween(
			TimeUtil.startOfDayMillis(selectedDate),
			TimeUtil.endOfDayMillis(selectedDate)
		)
	}
	val dietRecords by dietFlow.collectAsStateWithLifecycle(initialValue = emptyList())

	// 同一天的饮食记录（按开始时间归日）
	val exerciseRecords by remember(selectedDate) {
		db.exerciseRecordDao().observeBetween(
			TimeUtil.startOfDayMillis(selectedDate),
			TimeUtil.endOfDayMillis(selectedDate)
		)
	}.collectAsStateWithLifecycle(initialValue = emptyList())

	// 弹层状态：类型选择器 / 饮食表单（空=新建，非空=编辑，同时携带营养素明细）/ 运动表单 / 删除确认目标
	var showTypePicker by remember { mutableStateOf(false) }
	var showDietSheet by remember { mutableStateOf(false) }
	var dietSheetRecord by remember { mutableStateOf<DietRecord?>(null) }
	var dietSheetItems by remember { mutableStateOf<List<NutrientItem>>(emptyList()) }
	var showExerciseSheet by remember { mutableStateOf(false) }
	var exerciseSheetRecord by remember { mutableStateOf<ExerciseRecord?>(null) }
	var deleteTarget by remember { mutableStateOf<DietRecord?>(null) }
	var exDeleteTarget by remember { mutableStateOf<ExerciseRecord?>(null) }

	// 三餐询问通知深链：进入记录页自动弹出该餐的预填表单（每次深链仅触发一次）
	var mealLinkConsumed by remember { mutableStateOf(false) }
	LaunchedEffect(initialMeal) {
		if (initialMeal != null && !mealLinkConsumed) {
			dietSheetRecord = null
			dietSheetItems = emptyList()
			showDietSheet = true
			mealLinkConsumed = true
		}
	}

	Scaffold(
		floatingActionButton = {
			ExtendedFloatingActionButton(
				onClick = { showTypePicker = true },
				icon = { Icon(Icons.Filled.Add, contentDescription = null) },
				text = { Text(stringResource(R.string.record_add_entry)) }
			)
		}
	) { innerPadding ->
		LazyColumn(
			modifier = Modifier.fillMaxSize().padding(innerPadding),
			// 底部留出悬浮按钮的高度，保证最后一张卡片能滚到按钮上方，删除按钮不被遮挡
			contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 120.dp),
			verticalArrangement = Arrangement.spacedBy(10.dp)
		) {
			item { DateBar(date = selectedDate, onDateChange = { selectedDate = it }) }

			item {
				Text(
					text = stringResource(R.string.record_section_diet) + "（${dietRecords.size}）",
					style = MaterialTheme.typography.titleMedium
				)
			}
			if (dietRecords.isEmpty()) {
				item {
					Text(
						text = stringResource(R.string.record_empty_diet),
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
				}
			} else {
				// key 加前缀避免与运动记录的 id 在同一个 LazyColumn 里撞号
				items(dietRecords, key = { "diet_${it.record.id}" }) { item ->
					DietRecordCard(
						record = item.record,
						nutrientItems = item.nutrientItems,
						onClick = {
							dietSheetRecord = item.record
							dietSheetItems = item.nutrientItems
							showDietSheet = true
						},
						onDelete = { deleteTarget = item.record }
					)
				}
			}

			item { Spacer(modifier = Modifier.height(8.dp)) }
			item {
				Text(
					text = stringResource(R.string.record_section_exercise) + "（${exerciseRecords.size}）",
					style = MaterialTheme.typography.titleMedium
				)
			}
			if (exerciseRecords.isEmpty()) {
				item {
					Text(
						text = stringResource(R.string.record_empty_exercise),
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
				}
			} else {
				items(exerciseRecords, key = { "ex_${it.id}" }) { record ->
					ExerciseRecordCard(
						record = record,
						onClick = {
							exerciseSheetRecord = record
							showExerciseSheet = true
						},
						onDelete = { exDeleteTarget = record }
					)
				}
			}
		}
	}

	// 「记一笔」的类型选择弹层：饮食可用，运动留待下一批
	if (showTypePicker) {
		ModalBottomSheet(onDismissRequest = { showTypePicker = false }) {
			Column(
				modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
				verticalArrangement = Arrangement.spacedBy(8.dp)
			) {
				Text(
					text = stringResource(R.string.record_choose_type),
					style = MaterialTheme.typography.titleLarge
				)
				Card(
					onClick = {
						showTypePicker = false
						dietSheetRecord = null
						showDietSheet = true
					},
					modifier = Modifier.fillMaxWidth()
				) {
					EntryTypeRow(badgeChar = "餐", badgeColor = Color(0xFFC2F0E9), charColor = Color(0xFF042F2C), titleRes = R.string.record_type_diet, descRes = R.string.record_type_diet_desc, enabled = true)
				}
				// 运动入口
				Card(
					onClick = {
						showTypePicker = false
						exerciseSheetRecord = null
						showExerciseSheet = true
					},
					modifier = Modifier.fillMaxWidth()
				) {
					EntryTypeRow(badgeChar = "动", badgeColor = Color(0xFFC7CBF8), charColor = Color(0xFF1B1F65), titleRes = R.string.record_type_exercise, descRes = R.string.record_type_exercise_desc, enabled = true)
				}
			}
		}
	}

	if (showDietSheet) {
		DietEntrySheet(
			initialRecord = dietSheetRecord,
			initialItems = dietSheetItems,
			date = selectedDate,
			initialMealType = initialMeal?.let { MealType.fromStorage(it) },
			onDismiss = { showDietSheet = false },
			onSave = { record, nutrientItems ->
				showDietSheet = false
				// 记录与营养素明细在同一事务里落库：要么全部成功，要么全部不动
				scope.launch {
					db.withTransaction {
						if (record.id == 0L) {
							val newId = db.dietRecordDao().insert(record)
							if (nutrientItems.isNotEmpty()) {
								db.nutrientItemDao().insertAll(nutrientItems.map { it.copy(dietRecordId = newId) })
							}
						} else {
							db.dietRecordDao().update(record)
							db.nutrientItemDao().deleteByRecordId(record.id)
							if (nutrientItems.isNotEmpty()) {
								db.nutrientItemDao().insertAll(
									nutrientItems.map { it.copy(id = 0L, dietRecordId = record.id) }
								)
							}
						}
					}
				}
			}
		)
	}

	if (showExerciseSheet) {
		ExerciseEntrySheet(
			initialRecord = exerciseSheetRecord,
			date = selectedDate,
			onDismiss = { showExerciseSheet = false },
			onSave = { record ->
				showExerciseSheet = false
				scope.launch {
					if (record.id == 0L) {
						db.exerciseRecordDao().insert(record)
					} else {
						db.exerciseRecordDao().update(record)
					}
				}
			}
		)
	}

	deleteTarget?.let { target ->
		ConfirmDialog(
			title = stringResource(R.string.confirm_delete_diet_title),
			text = stringResource(R.string.confirm_delete_diet_text),
			onConfirm = {
				deleteTarget = null
				scope.launch { db.dietRecordDao().delete(target) }
			},
			onDismiss = { deleteTarget = null }
		)
	}

	exDeleteTarget?.let { target ->
		ConfirmDialog(
			title = stringResource(R.string.confirm_delete_ex_title),
			text = stringResource(R.string.confirm_delete_ex_text),
			onConfirm = {
				exDeleteTarget = null
				scope.launch { db.exerciseRecordDao().delete(target) }
			},
			onDismiss = { exDeleteTarget = null }
		)
	}
}

// 顶部日期条：前后翻日，非今日时提供「回今天」快捷键
@Composable
private fun DateBar(date: LocalDate, onDateChange: (LocalDate) -> Unit) {
	Row(
		modifier = Modifier.fillMaxWidth(),
		verticalAlignment = Alignment.CenterVertically
	) {
		IconButton(onClick = { onDateChange(date.minusDays(1)) }) {
			Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.record_prev_day))
		}
		Column(
			modifier = Modifier.weight(1f),
			horizontalAlignment = Alignment.CenterHorizontally
		) {
			Text(text = TimeUtil.formatDate(date), style = MaterialTheme.typography.titleMedium)
			if (!date.isEqual(LocalDate.now())) {
				TextButton(onClick = { onDateChange(LocalDate.now()) }) {
					Text(
						text = stringResource(R.string.record_back_to_today),
						style = MaterialTheme.typography.labelMedium
					)
				}
			}
		}
		IconButton(onClick = { onDateChange(date.plusDays(1)) }) {
			Icon(Icons.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.record_next_day))
		}
	}
}

// 类型选择弹层里的一行：圆形字标 + 标题 + 描述；enabled=false 时整体降透明度
@Composable
private fun EntryTypeRow(
	badgeChar: String,
	badgeColor: Color,
	charColor: Color,
	titleRes: Int,
	descRes: Int,
	enabled: Boolean
) {
	Row(
		modifier = Modifier.fillMaxWidth().padding(16.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		TypeBadge(badgeChar = badgeChar, background = badgeColor, content = charColor)
		Spacer(modifier = Modifier.size(14.dp))
		Column(modifier = Modifier.weight(1f)) {
			Text(
				text = stringResource(titleRes),
				style = MaterialTheme.typography.titleSmall,
				color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.5f)
			)
			Text(
				text = stringResource(descRes),
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.5f)
			)
		}
	}
}
