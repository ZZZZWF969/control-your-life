package com.dailycontrol.app.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.dailycontrol.app.R
import java.time.LocalTime

// 通用时间选择弹窗：TimePicker 包在 AlertDialog 里，饮食/运动表单共用
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
	initial: LocalTime,
	onConfirm: (LocalTime) -> Unit,
	onDismiss: () -> Unit
) {
	val pickerState = rememberTimePickerState(
		initialHour = initial.hour,
		initialMinute = initial.minute,
		is24Hour = true
	)
	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text(stringResource(R.string.time_picker_title)) },
		text = { TimePicker(state = pickerState) },
		confirmButton = {
			TextButton(onClick = { onConfirm(LocalTime.of(pickerState.hour, pickerState.minute)) }) {
				Text(stringResource(R.string.action_confirm))
			}
		},
		dismissButton = {
			TextButton(onClick = onDismiss) {
				Text(stringResource(R.string.action_cancel))
			}
		}
	)
}
