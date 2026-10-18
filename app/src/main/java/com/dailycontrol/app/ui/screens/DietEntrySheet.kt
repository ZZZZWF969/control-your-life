package com.dailycontrol.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dailycontrol.app.R
import com.dailycontrol.app.data.entity.DietRecord
import com.dailycontrol.app.data.entity.MealType
import com.dailycontrol.app.data.entity.NutrientItem
import com.dailycontrol.app.ui.components.TimePickerDialog
import com.dailycontrol.app.util.TimeUtil
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

// 表单里一行营养素的输入状态：成分名 + 含量
private data class NutrientRowInput(val name: String = "", val amount: String = "")

// 按当前时间智能预选餐别：早上早餐、午后午餐、傍晚晚餐、深夜零食
private fun defaultMealForNow(): String {
	val hour = LocalTime.now().hour
	return when {
		hour < 10 -> MealType.BREAKFAST.storageName
		hour < 15 -> MealType.LUNCH.storageName
		hour < 21 -> MealType.DINNER.storageName
		else -> MealType.SNACK.storageName
	}
}

// 饮食录入/编辑表单（底部弹层）：initialRecord 非空即为编辑模式，date 为所属日期
// 字段按餐别联动：三餐填饭量三档；零食/营养品填重量；营养品再追加结构化营养成分行
// initialMealType：三餐询问通知深链进入时预选的餐别（仅新建时生效）
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DietEntrySheet(
	initialRecord: DietRecord?,
	initialItems: List<NutrientItem>,
	date: LocalDate,
	initialMealType: MealType? = null,
	onDismiss: () -> Unit,
	onSave: (DietRecord, List<NutrientItem>) -> Unit
) {
	val isEditing = initialRecord != null
	var mealType by remember {
		mutableStateOf(
			MealType.fromStorage(initialRecord?.mealType ?: initialMealType?.storageName ?: defaultMealForNow())
		)
	}
	var foodText by remember { mutableStateOf(initialRecord?.foodText ?: "") }
	var foodError by remember { mutableStateOf(false) }
	var portionText by remember { mutableStateOf(initialRecord?.portionText ?: "") }
	var weightText by remember { mutableStateOf(initialRecord?.weightText ?: "") }
	// 营养成分行：编辑时载入已有明细；新建时给一行空白，降低首次输入门槛
	var nutrientRows by remember {
		mutableStateOf(
			initialItems.map { NutrientRowInput(it.name, it.amountText) }.ifEmpty { listOf(NutrientRowInput()) }
		)
	}
	var entryTime by remember {
		mutableStateOf(
			initialRecord?.eatenAt
				?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalTime() }
				?: LocalTime.now().truncatedTo(ChronoUnit.MINUTES)
		)
	}
	var showTimePicker by remember { mutableStateOf(false) }
	// 饭量为空时的默认档位：提前取好字符串，回调里不能调用 stringResource
	val moderatePortion = stringResource(R.string.portion_moderate)

	// 零食与营养品记录重量；营养品还需结构化的营养成分行
	val needsWeight = mealType == MealType.SNACK || mealType == MealType.SUPPLEMENT
	val needsNutrients = mealType == MealType.SUPPLEMENT

	ModalBottomSheet(onDismissRequest = onDismiss) {
		Column(
			modifier = Modifier.fillMaxWidth()
				.imePadding()
				.verticalScroll(rememberScrollState())
				.padding(horizontal = 24.dp)
				.padding(bottom = 32.dp),
			verticalArrangement = Arrangement.spacedBy(12.dp)
		) {
			Text(
				text = stringResource(if (isEditing) R.string.record_edit_diet else R.string.record_new_diet),
				style = MaterialTheme.typography.titleLarge
			)

			Text(text = stringResource(R.string.diet_field_meal), style = MaterialTheme.typography.titleSmall)
			FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
				MealType.entries.forEach { type ->
					FilterChip(
						selected = mealType == type,
						onClick = { mealType = type },
						label = { Text(stringResource(type.labelRes)) }
					)
				}
			}

			OutlinedTextField(
				value = foodText,
				onValueChange = {
					foodText = it
					if (it.isNotBlank()) {
						foodError = false
					}
				},
				label = {
					Text(
						stringResource(
							if (mealType == MealType.SUPPLEMENT) R.string.diet_field_food_supplement
							else R.string.diet_field_food
						)
					)
				},
				supportingText = { if (foodError) Text(stringResource(R.string.diet_food_required)) },
				isError = foodError,
				minLines = 2,
				modifier = Modifier.fillMaxWidth()
			)

			// 三餐：饭量三档快选
			if (!needsWeight) {
				Text(text = stringResource(R.string.diet_field_portion), style = MaterialTheme.typography.titleSmall)
				FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
					listOf(R.string.portion_little, R.string.portion_moderate, R.string.portion_much).forEach { res ->
						val label = stringResource(res)
						FilterChip(
							selected = portionText == label,
							onClick = { portionText = label },
							label = { Text(label) }
						)
					}
				}
			} else {
				// 零食/营养品：重量或容量（包装净含量或实际摄入量）
				OutlinedTextField(
					value = weightText,
					onValueChange = { weightText = it },
					label = { Text(stringResource(R.string.diet_field_weight)) },
					supportingText = { Text(stringResource(R.string.diet_field_weight_hint)) },
					singleLine = true,
					modifier = Modifier.fillMaxWidth()
				)
			}

			// 营养品：结构化营养成分，一行一个成分，可动态增删（复合维生素可分别添加 VA/VB/VC/VD）
			if (needsNutrients) {
				Text(text = stringResource(R.string.diet_field_nutrient_rows), style = MaterialTheme.typography.titleSmall)
				nutrientRows.forEachIndexed { index, row ->
					Row(
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(8.dp)
					) {
						OutlinedTextField(
							value = row.name,
							onValueChange = { value ->
								nutrientRows = nutrientRows.toMutableList().also { it[index] = row.copy(name = value) }
							},
							label = { Text(stringResource(R.string.diet_nutrient_name)) },
							singleLine = true,
							modifier = Modifier.weight(1f)
						)
						OutlinedTextField(
							value = row.amount,
							onValueChange = { value ->
								nutrientRows = nutrientRows.toMutableList().also { it[index] = row.copy(amount = value) }
							},
							label = { Text(stringResource(R.string.diet_nutrient_amount)) },
							singleLine = true,
							modifier = Modifier.weight(1f)
						)
						// 单行删除：至少保留零行也允许（可用下方按钮再加回来）
						IconButton(onClick = {
							nutrientRows = nutrientRows.toMutableList().also { it.removeAt(index) }
						}) {
							Icon(
								imageVector = Icons.Outlined.Close,
								contentDescription = stringResource(R.string.diet_nutrient_remove),
								tint = MaterialTheme.colorScheme.onSurfaceVariant
							)
						}
					}
				}
				OutlinedButton(
					onClick = { nutrientRows = nutrientRows + NutrientRowInput() },
					modifier = Modifier.fillMaxWidth()
				) {
					Icon(imageVector = Icons.Filled.AddCircle, contentDescription = null)
					Spacer(modifier = Modifier.width(8.dp))
					Text(stringResource(R.string.diet_nutrient_add))
				}
			}

			OutlinedButton(
				onClick = { showTimePicker = true },
				modifier = Modifier.fillMaxWidth()
			) {
				Text(
					text = stringResource(R.string.diet_field_time) + "  " +
						entryTime.format(DateTimeFormatter.ofPattern("HH:mm"))
				)
			}

			Button(
				onClick = {
					if (foodText.isBlank()) {
						foodError = true
					} else {
						// 成分名为空的行不保存；有效行转成待入库的实体
						val nutrientEntities = if (needsNutrients) {
							nutrientRows
								.filter { it.name.isNotBlank() }
								.map { NutrientItem(name = it.name.trim(), amountText = it.amount.trim(), dietRecordId = 0) }
						} else {
							emptyList()
						}
						onSave(
							DietRecord(
								id = initialRecord?.id ?: 0L,
								mealType = mealType.storageName,
								foodText = foodText.trim(),
								portionText = if (needsWeight) "" else portionText.ifBlank { moderatePortion },
								eatenAt = TimeUtil.combineToMillis(date, entryTime),
								recordedAt = initialRecord?.recordedAt ?: System.currentTimeMillis(),
								weightText = if (needsWeight) weightText.trim().ifBlank { null } else null,
								nutrientText = null
							),
							nutrientEntities
						)
					}
				},
				modifier = Modifier.fillMaxWidth()
			) {
				Text(stringResource(R.string.action_save))
			}
		}
	}

	if (showTimePicker) {
		TimePickerDialog(
			initial = entryTime,
			onConfirm = {
				entryTime = it
				showTimePicker = false
			},
			onDismiss = { showTimePicker = false }
		)
	}
}
