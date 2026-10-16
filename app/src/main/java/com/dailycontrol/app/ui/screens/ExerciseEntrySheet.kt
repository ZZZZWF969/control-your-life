package com.dailycontrol.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dailycontrol.app.R
import com.dailycontrol.app.data.entity.ExerciseRecord
import com.dailycontrol.app.ui.components.TimePickerDialog
import com.dailycontrol.app.util.TimeUtil
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

// 常见运动类型快选项；「其他」时展开自定义输入
private val quickTypes = listOf(
	R.string.ex_type_run,
	R.string.ex_type_ride,
	R.string.ex_type_strength,
	R.string.ex_type_ball,
	R.string.ex_type_other
)

// 运动录入/编辑表单（底部弹层）：initialRecord 非空即为编辑模式，date 为所属日期
// 校验规则：结束时间必须晚于开始时间（本阶段运动按开始时间归日，不支持跨天时段）
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExerciseEntrySheet(
	initialRecord: ExerciseRecord?,
	date: LocalDate,
	onDismiss: () -> Unit,
	onSave: (ExerciseRecord) -> Unit
) {
	val isEditing = initialRecord != null
	val zone = ZoneId.systemDefault()
	// 默认时段：过去半小时到现在，多数场景下无需改动
	val defaultEnd = LocalTime.now().truncatedTo(ChronoUnit.MINUTES)
	val defaultStart = defaultEnd.minusMinutes(30)

	var typeText by remember { mutableStateOf(initialRecord?.typeText ?: "") }
	var customTypeText by remember { mutableStateOf("") }
	var startTime by remember {
		mutableStateOf(
			initialRecord?.startAt?.let { java.time.Instant.ofEpochMilli(it).atZone(zone).toLocalTime() } ?: defaultStart
		)
	}
	var endTime by remember {
		mutableStateOf(
			initialRecord?.endAt?.let { java.time.Instant.ofEpochMilli(it).atZone(zone).toLocalTime() } ?: defaultEnd
		)
	}
	var editingStart by remember { mutableStateOf(false) }
	var editingEnd by remember { mutableStateOf(false) }
	var timeError by remember { mutableStateOf(false) }
	var intensityText by remember { mutableStateOf(initialRecord?.intensityText ?: "") }
	var totalText by remember { mutableStateOf(initialRecord?.totalText ?: "") }

	// 「其他」的标签文本提前取好：点击回调里不能调用 stringResource
	val otherLabel = stringResource(R.string.ex_type_other)

	// 选了「其他」且未直接填过类型时，显示自定义输入框
	val isOther = typeText == otherLabel

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
				text = stringResource(if (isEditing) R.string.ex_edit else R.string.ex_new),
				style = MaterialTheme.typography.titleLarge
			)

			Text(text = stringResource(R.string.ex_field_type), style = MaterialTheme.typography.titleSmall)
			FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
				quickTypes.forEach { res ->
					val label = stringResource(res)
					FilterChip(
						selected = typeText == label,
						onClick = {
							typeText = label
							if (label != otherLabel) {
								customTypeText = ""
							}
						},
						label = { Text(label) }
					)
				}
			}
			if (isOther) {
				OutlinedTextField(
					value = customTypeText,
					onValueChange = { customTypeText = it },
					label = { Text(stringResource(R.string.ex_field_custom_type)) },
					singleLine = true,
					modifier = Modifier.fillMaxWidth()
				)
			}

			OutlinedButton(
				onClick = { editingStart = true },
				modifier = Modifier.fillMaxWidth()
			) {
				Text(
					text = stringResource(R.string.ex_field_start) + "  " +
						startTime.format(DateTimeFormatter.ofPattern("HH:mm"))
				)
			}
			OutlinedButton(
				onClick = { editingEnd = true },
				modifier = Modifier.fillMaxWidth()
			) {
				Text(
					text = stringResource(R.string.ex_field_end) + "  " +
						endTime.format(DateTimeFormatter.ofPattern("HH:mm"))
				)
			}
			if (timeError) {
				Text(
					text = stringResource(R.string.ex_time_error),
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.error
				)
			}
			// 时长自动计算展示
			Text(
				text = stringResource(R.string.ex_duration) + "  " +
					Duration.between(startTime, endTime).toMinutes() + stringResource(R.string.ex_duration_unit),
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)

			Text(text = stringResource(R.string.ex_field_intensity), style = MaterialTheme.typography.titleSmall)
			FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
				listOf(R.string.intensity_low, R.string.intensity_mid, R.string.intensity_high).forEach { res ->
					val label = stringResource(res)
					FilterChip(
						selected = intensityText == label,
						onClick = { intensityText = label },
						label = { Text(label) }
					)
				}
			}

			OutlinedTextField(
				value = totalText,
				onValueChange = { totalText = it },
				label = { Text(stringResource(R.string.ex_field_total)) },
				supportingText = { Text(stringResource(R.string.ex_field_total_hint)) },
				singleLine = true,
				modifier = Modifier.fillMaxWidth()
			)

			Button(
				onClick = {
					if (!endTime.isAfter(startTime)) {
						timeError = true
					} else {
						onSave(
							ExerciseRecord(
								id = initialRecord?.id ?: 0L,
								typeText = (if (isOther) customTypeText.trim() else typeText).ifBlank { typeText },
								intensityText = intensityText,
								totalText = totalText.trim(),
								startAt = TimeUtil.combineToMillis(date, startTime),
								endAt = TimeUtil.combineToMillis(date, endTime),
								recordedAt = initialRecord?.recordedAt ?: System.currentTimeMillis()
							)
						)
					}
				},
				modifier = Modifier.fillMaxWidth()
			) {
				Text(stringResource(R.string.action_save))
			}
		}
	}

	if (editingStart) {
		TimePickerDialog(
			initial = startTime,
			onConfirm = {
				startTime = it
				editingStart = false
				timeError = false
			},
			onDismiss = { editingStart = false }
		)
	}
	if (editingEnd) {
		TimePickerDialog(
			initial = endTime,
			onConfirm = {
				endTime = it
				editingEnd = false
				timeError = false
			},
			onDismiss = { editingEnd = false }
		)
	}
}
