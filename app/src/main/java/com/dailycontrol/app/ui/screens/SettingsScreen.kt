package com.dailycontrol.app.ui.screens

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailycontrol.app.R
import com.dailycontrol.app.data.db.DailyControlDatabase
import com.dailycontrol.app.data.entity.DietRecord
import com.dailycontrol.app.data.entity.ExerciseRecord
import com.dailycontrol.app.data.settings.SettingsRepository
import com.dailycontrol.app.data.settings.ThemeMode
import com.dailycontrol.app.notify.Notifier
import com.dailycontrol.app.schedule.DailyPlanner
import com.dailycontrol.app.schedule.ReminderReason
import com.dailycontrol.app.schedule.ReminderScheduler
import com.dailycontrol.app.ui.components.TimePickerDialog
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

// 设置页：外观、提醒时间、跳转应用、链路自检；后续 AI 接口等配置在此扩展
@Composable
fun SettingsScreen() {
	val context = LocalContext.current
	val scope = rememberCoroutineScope()
	var dbCheckResult by remember { mutableStateOf<String?>(null) }
	var notifyGranted by remember { mutableStateOf(Notifier.hasPostPermission(context)) }
	var testTodoHint by remember { mutableStateOf(false) }

	// 订阅持久化设置；用户操作后写入 DataStore，界面即时生效
	val settings = SettingsRepository.get(context)
	val themeMode by settings.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
	val reminderTimes by settings.reminderTimes.collectAsStateWithLifecycle(
		initialValue = com.dailycontrol.app.data.settings.ReminderTimes(570, 810, 1170, 1320)
	)
	// 包名编辑用本地状态优先，避免 DataStore 回流打断输入
	val savedVocabPackage by settings.vocabPackage.collectAsStateWithLifecycle(initialValue = "")
	var editingVocabPackage by remember { mutableStateOf<String?>(null) }

	// 系统权限弹窗的结果回调：授权成功后立即补发测试通知
	val permissionLauncher = rememberLauncherForActivityResult(
		ActivityResultContracts.RequestPermission()
	) { granted ->
		notifyGranted = granted
		if (granted) {
			Notifier.sendTestNotification(context)
		}
	}

	// 精确闹钟权限状态（只读检查，去系统页授权后回来重进页面刷新）
	val exactAlarmGranted = remember { ReminderScheduler.canScheduleExact(context) }

	Column(
		modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
		verticalArrangement = Arrangement.spacedBy(16.dp)
	) {
		Text(text = stringResource(R.string.tab_settings), style = MaterialTheme.typography.headlineMedium)
		Text(
			text = stringResource(R.string.settings_desc),
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant
		)

		HorizontalDivider()
		Text(text = stringResource(R.string.settings_section_appearance), style = MaterialTheme.typography.titleMedium)
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

		HorizontalDivider()
		Text(text = stringResource(R.string.settings_section_reminder), style = MaterialTheme.typography.titleMedium)
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
		// 晚间规划时刻：本批仅存储，第 3 批接入实际排程
		TimeSettingRow(
			label = stringResource(R.string.reminder_plan),
			minuteOfDay = reminderTimes.planMinute,
			onPick = { minute ->
				scope.launch { settings.setPlanMinute(minute) }
			}
		)

		HorizontalDivider()
		Text(text = stringResource(R.string.settings_section_launch), style = MaterialTheme.typography.titleMedium)
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

		HorizontalDivider()
		Text(text = stringResource(R.string.settings_section_diag), style = MaterialTheme.typography.titleMedium)

		if (!exactAlarmGranted) {
			Text(
				text = stringResource(R.string.exact_alarm_denied),
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.error
			)
			TextButton(onClick = {
				// 跳系统「精确闹钟」授权页；个别 ROM 无此页时静默失败即可
				runCatching {
					context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
				}
			}) {
				Text(stringResource(R.string.exact_alarm_grant))
			}
		}

		Button(onClick = {
			if (notifyGranted) {
				Notifier.sendTestNotification(context)
			} else {
				permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
			}
		}) {
			Text(stringResource(R.string.settings_send_test_notify))
		}
		if (!notifyGranted) {
			Text(
				text = stringResource(R.string.settings_notify_no_permission),
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)
		}

		// 三分钟后的真实待办提醒：实测「排程→系统闹钟→通知→深链」全链路
		Button(onClick = {
			testTodoHint = true
			ReminderScheduler.schedule(
				context,
				ReminderReason.TODO_TEST,
				System.currentTimeMillis() + 3 * 60_000L
			)
		}) {
			Text(stringResource(R.string.settings_create_test_todo))
		}
		if (testTodoHint) {
			Text(
				text = stringResource(R.string.test_todo_scheduled),
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)
		}

		Button(onClick = {
			// Room 的挂起方法自带线程调度，这里串起「写入样例 → 统计条数」的自检流程
			scope.launch {
				val now = System.currentTimeMillis()
				val db = DailyControlDatabase.get(context)
				db.dietRecordDao().insert(
					DietRecord(
						mealType = "lunch",
						foodText = "样例：米饭、青菜",
						portionText = "七分饱",
						eatenAt = now,
						recordedAt = now
					)
				)
				db.exerciseRecordDao().insert(
					ExerciseRecord(
						typeText = "样例：慢跑",
						intensityText = "中等",
						totalText = "1km",
						startAt = now,
						endAt = now,
						recordedAt = now
					)
				)
				val dietCount = db.dietRecordDao().count()
				val exerciseCount = db.exerciseRecordDao().count()
				dbCheckResult = context.getString(R.string.settings_db_check_result, dietCount, exerciseCount)
			}
		}) {
			Text(stringResource(R.string.settings_db_check))
		}
		dbCheckResult?.let { result ->
			Text(text = result, style = MaterialTheme.typography.bodyMedium)
		}
	}
}

// 单行时间设置：左侧标签、右侧当前时刻按钮，点击弹时间选择器
@Composable
private fun TimeSettingRow(
	label: String,
	minuteOfDay: Int,
	onPick: (Int) -> Unit
) {
	var showPicker by remember { mutableStateOf(false) }
	Row(
		modifier = Modifier.fillMaxWidth(),
		verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
	) {
		Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
		TextButton(onClick = { showPicker = true }) {
			Text(
				text = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
					.format(DateTimeFormatter.ofPattern("HH:mm")),
				style = MaterialTheme.typography.titleMedium
			)
		}
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
