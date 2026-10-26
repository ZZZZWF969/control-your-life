package com.dailycontrol.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailycontrol.app.R
import com.dailycontrol.app.data.settings.ReminderTimes
import com.dailycontrol.app.data.settings.SettingsRepository
import com.dailycontrol.app.data.settings.ThemeMode
import com.dailycontrol.app.schedule.DailyPlanner
import com.dailycontrol.app.ui.components.TimePickerDialog
import com.dailycontrol.app.util.TimeUtil
import kotlinx.coroutines.launch
import java.time.LocalTime

// 设置子页·外观 / 提醒时间 / 跳转应用（2026-10-26 分组化重构时从旧设置页原样搬移）
// 内容与逻辑零变化，仅归属各自的子页面

@Composable
internal fun AppearanceSection() {
	val context = LocalContext.current
	val scope = rememberCoroutineScope()
	val settings = SettingsRepository.get(context)
	val themeMode by settings.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)

	SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
		ThemeMode.entries.forEachIndexed { index, mode ->
			SegmentedButton(
				selected = themeMode == mode,
				onClick = { scope.launch { settings.setThemeMode(mode) } },
				shape = SegmentedButtonDefaults.itemShape(index = index, count = ThemeMode.entries.size),
				label = { Text(stringResource(mode.labelRes)) }
			)
		}
	}
}

@Composable
internal fun ReminderSection() {
	val context = LocalContext.current
	val scope = rememberCoroutineScope()
	val settings = SettingsRepository.get(context)
	val reminderTimes by settings.reminderTimes.collectAsStateWithLifecycle(
		initialValue = ReminderTimes(570, 810, 1170, 1320)
	)

	TimeSettingRow(
		label = stringResource(R.string.reminder_breakfast),
		minuteOfDay = reminderTimes.breakfastMinute,
		onPick = { minute ->
			scope.launch {
				settings.setBreakfastMinute(minute)
				DailyPlanner.rescheduleMeals(context)
			}
		}
	)
	TimeSettingRow(
		label = stringResource(R.string.reminder_lunch),
		minuteOfDay = reminderTimes.lunchMinute,
		onPick = { minute ->
			scope.launch {
				settings.setLunchMinute(minute)
				DailyPlanner.rescheduleMeals(context)
			}
		}
	)
	TimeSettingRow(
		label = stringResource(R.string.reminder_dinner),
		minuteOfDay = reminderTimes.dinnerMinute,
		onPick = { minute ->
			scope.launch {
				settings.setDinnerMinute(minute)
				DailyPlanner.rescheduleMeals(context)
			}
		}
	)
	// 晚间规划时刻：保存后由 DailyPlanner.reschedulePlan 消费
	TimeSettingRow(
		label = stringResource(R.string.reminder_plan),
		minuteOfDay = reminderTimes.planMinute,
		onPick = { minute ->
			scope.launch { settings.setPlanMinute(minute) }
		}
	)
}

@Composable
internal fun LaunchSection() {
	val context = LocalContext.current
	val scope = rememberCoroutineScope()
	val settings = SettingsRepository.get(context)
	// 包名编辑用本地状态优先，避免 DataStore 回流打断输入
	val savedVocabPackage by settings.vocabPackage.collectAsStateWithLifecycle(initialValue = "")
	var editingVocabPackage by remember { mutableStateOf<String?>(null) }

	OutlinedTextField(
		value = editingVocabPackage ?: savedVocabPackage,
		onValueChange = { value ->
			editingVocabPackage = value
			scope.launch { settings.setVocabPackage(value) }
		},
		label = { Text(stringResource(R.string.vocab_package_label)) },
		supportingText = { Text(stringResource(R.string.vocab_package_hint)) },
		singleLine = true,
		modifier = Modifier.fillMaxWidth()
	)
}

// 单行时间设置：整行可点弹时间选择器（时间值主色强调，2026-10-19 批次3 原样式）
@Composable
private fun TimeSettingRow(
	label: String,
	minuteOfDay: Int,
	onPick: (Int) -> Unit
) {
	var showPicker by remember { mutableStateOf(false) }
	Row(
		modifier = Modifier.fillMaxWidth()
			.clickable { showPicker = true }
			.padding(vertical = 6.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
		Text(
			text = TimeUtil.formatMinuteOfDay(minuteOfDay),
			style = MaterialTheme.typography.titleMedium,
			color = MaterialTheme.colorScheme.primary
		)
	}
	if (showPicker) {
		TimePickerDialog(
			initial = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60),
			onConfirm = { time ->
				onPick(time.hour * 60 + time.minute)
				showPicker = false
			},
			onDismiss = { showPicker = false }
		)
	}
}
