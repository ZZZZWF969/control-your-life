package com.dailycontrol.app.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailycontrol.app.R
import com.dailycontrol.app.backup.BackupManager
import com.dailycontrol.app.backup.BackupFormatException
import com.dailycontrol.app.backup.BackupSummary
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
import com.dailycontrol.app.util.TimeUtil
import kotlinx.coroutines.flow.first
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
	var simMealHint by remember { mutableStateOf(false) }
	var simVocabHint by remember { mutableStateOf(false) }
	var simPlanHint by remember { mutableStateOf(false) }
	var simWakeHint by remember { mutableStateOf(false) }
	var simWakeLastHint by remember { mutableStateOf(false) }
	var aiAlarmHint by remember { mutableStateOf(false) }

	// 备份区状态：结果提示文本 + 恢复流程的预览（选文件解析出、确认后才执行）
	var backupResult by remember { mutableStateOf<String?>(null) }
	var restorePreview by remember { mutableStateOf<BackupSummary?>(null) }
	// 应用内备份清单：null=未打开；打开后逐项尝试解析，summary 为 null 表示该文件已损坏
	var showBackupList by remember { mutableStateOf(false) }
	var backupEntries by remember { mutableStateOf<List<Pair<BackupManager.BackupFileInfo, BackupSummary?>>>(emptyList()) }

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

	// 手动导出：系统文件选择器定位置，默认文件名带日期
	val exportLauncher = rememberLauncherForActivityResult(
		ActivityResultContracts.CreateDocument("application/json")
	) { uri: Uri? ->
		if (uri != null) {
			scope.launch {
				backupResult = if (BackupManager.exportToUri(context, uri)) {
					context.getString(R.string.backup_done)
				} else {
					context.getString(R.string.backup_failed)
				}
			}
		}
	}

	// 恢复第一步：选文件读文本并解析校验，坏文件给可读提示、不动任何数据
	val restoreLauncher = rememberLauncherForActivityResult(
		ActivityResultContracts.OpenDocument()
	) { uri: Uri? ->
		if (uri != null) {
			scope.launch {
				backupResult = try {
					val text = context.contentResolver.openInputStream(uri)?.use {
						it.readBytes().toString(Charsets.UTF_8)
					} ?: throw BackupFormatException("文件无法读取")
					restorePreview = BackupManager.parseAndValidate(text)
					null
				} catch (e: BackupFormatException) {
					context.getString(R.string.backup_bad_file) + "（" + e.message + "）"
				}
			}
		}
	}

	// 精确闹钟权限状态（只读检查，去系统页授权后回来重进页面刷新）
	val exactAlarmGranted = remember { ReminderScheduler.canScheduleExact(context) }
	val lastBackupAt by settings.lastBackupAt.collectAsStateWithLifecycle(initialValue = 0L)

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
		Text(text = stringResource(R.string.settings_section_ai), style = MaterialTheme.typography.titleMedium)
		AiConfigSection()

		HorizontalDivider()
		Text(text = stringResource(R.string.settings_section_backup), style = MaterialTheme.typography.titleMedium)
		Text(
			text = if (lastBackupAt > 0) {
				stringResource(R.string.settings_last_backup_at, TimeUtil.formatDateTime(lastBackupAt))
			} else {
				stringResource(R.string.settings_last_backup_never)
			},
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.onSurfaceVariant
		)
		Text(
			text = stringResource(R.string.settings_backup_hint, 4),
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.onSurfaceVariant
		)
		Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
			Button(
				onClick = {
					backupResult = null
					val stamp = java.time.LocalDateTime.now()
						.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
					exportLauncher.launch("daily-control-backup-$stamp.json")
				},
				modifier = Modifier.weight(1f)
			) {
				Text(stringResource(R.string.settings_backup_now))
			}
			OutlinedButton(
				onClick = {
					backupResult = null
					// 先检索下载目录并逐份解析摘要，再弹应用内清单（用户不用翻系统目录）
					scope.launch {
						backupEntries = BackupManager.listDownloadBackups(context).map { info ->
							info to runCatching { BackupManager.readSummary(context, info.uri) }.getOrNull()
						}
						showBackupList = true
					}
				},
				modifier = Modifier.weight(1f)
			) {
				Text(stringResource(R.string.settings_backup_restore))
			}
		}
		backupResult?.let { result ->
			Text(
				text = result,
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.error
			)
		}

		// 重新查看首启权限引导（2026-10-19 批次4）：置回未完成标志即可让向导重现
		OutlinedButton(
			onClick = {
				scope.launch { settings.setOnboardingDone(false) }
			},
			modifier = Modifier.fillMaxWidth()
		) {
			Text(stringResource(R.string.settings_replay_onboarding))
		}

		// 调试工具折叠卡（2026-10-19 批次3）：默认收起，验收与排查时手动展开
		var debugExpanded by remember { mutableStateOf(false) }
		Column(modifier = Modifier.fillMaxWidth().animateContentSize()) {
			Row(
				modifier = Modifier.fillMaxWidth()
					.clickable { debugExpanded = !debugExpanded }
					.padding(vertical = 14.dp),
				verticalAlignment = Alignment.CenterVertically
			) {
				Text(
					text = stringResource(R.string.settings_section_debug),
					style = MaterialTheme.typography.titleMedium,
					modifier = Modifier.weight(1f)
				)
				Icon(
					imageVector = if (debugExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
					contentDescription = null,
					tint = MaterialTheme.colorScheme.onSurfaceVariant
				)
			}
			if (debugExpanded) {
				Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
					// —— 以下为原调试区内容（保持原缩进） ——

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

		// 三分钟后的真实测试待办：创建真实任务并走完整链路（落库→排程→到点通知→提醒页列表）
		Button(onClick = {
			testTodoHint = true
			scope.launch {
				val now = System.currentTimeMillis()
				val db = DailyControlDatabase.get(context)
				val task = com.dailycontrol.app.data.entity.TodoTask(
					taskDateEpochDay = java.time.LocalDate.now().toEpochDay(),
					title = context.getString(R.string.test_todo_title),
					startAtMillis = now + 3 * 60_000L,
					remindAheadMinutes = 1,
					done = false,
					createdAt = now
				)
				val id = db.todoTaskDao().insert(task)
				ReminderScheduler.schedule(
					context,
					ReminderReason.TODO_TASK_BASE + id.toInt(),
					task.startAtMillis!! - 60_000L
				)
			}
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

		// 30 秒后模拟晚间规划提醒：走真实 PLAN_PRIMARY 闹钟路径，验证 22:00/23:00 双分支
		Button(onClick = {
			simPlanHint = true
			ReminderScheduler.schedule(
				context,
				ReminderReason.PLAN_PRIMARY,
				System.currentTimeMillis() + 30_000L
			)
		}) {
			Text(stringResource(R.string.settings_simulate_plan))
		}
		if (simPlanHint) {
			Text(
				text = stringResource(R.string.sim_plan_scheduled),
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)
		}

		// 起床唤醒实测：1 分钟后起 3 声相继闹铃（走真实接收器与起床震动渠道）
		// 调试模拟用独立码段，不占用真实计划的 WAKE_CYCLE 码块（同码会互相覆盖排程）
		Button(onClick = {
			simWakeHint = true
			val now = System.currentTimeMillis()
			listOf(60_000L, 100_000L, 140_000L).forEachIndexed { index, delay ->
				ReminderScheduler.schedule(
					context,
					ReminderReason.WAKE_DEBUG_RING_BASE + index,
					now + delay
				)
			}
		}) {
			Text(stringResource(R.string.settings_simulate_wake))
		}
		if (simWakeHint) {
			Text(
				text = stringResource(R.string.sim_wake_scheduled),
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)
		}

		// 模拟末声（双保险方案）：30 秒后触发末声，验证持续震动直到处理 + 「检查时钟闹钟」入口
		Button(onClick = {
			simWakeLastHint = true
			ReminderScheduler.schedule(
				context,
				ReminderReason.WAKE_DEBUG_LAST,
				System.currentTimeMillis() + 30_000L
			)
		}) {
			Text(stringResource(R.string.settings_simulate_wake_last))
		}
		if (simWakeLastHint) {
			Text(
				text = stringResource(R.string.sim_wake_last_scheduled),
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)
		}

		// 立即执行一次自动备份（走与每周闹钟完全相同的下载目录链路），验收与演示用
		Button(onClick = {
			scope.launch {
				val ok = BackupManager.autoBackupToDownloads(context)
				BackupManager.rollWeeklySchedule(context)
				backupResult = if (ok) {
					context.getString(R.string.sim_backup_done)
				} else {
					context.getString(R.string.backup_failed)
				}
			}
		}) {
			Text(stringResource(R.string.settings_simulate_auto_backup))
		}

		// 1 分钟后模拟早餐询问：走与真实三餐闹钟完全相同的接收器分支，
		// 用于验证「当天已记录则静默 / 未记录则询问」的两个分支
		Button(onClick = {
			simMealHint = true
			ReminderScheduler.schedule(
				context,
				ReminderReason.MEAL_BREAKFAST,
				System.currentTimeMillis() + 60_000L
			)
		}) {
			Text(stringResource(R.string.settings_simulate_meal))
		}
		if (simMealHint) {
			Text(
				text = stringResource(R.string.sim_meal_scheduled),
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)
		}

		// 30 秒后模拟背单词提醒：走真实循环逻辑（通知+动作+下一锚点续排），验证分支用
		Button(onClick = {
			simVocabHint = true
			ReminderScheduler.schedule(
				context,
				ReminderReason.WORD_CYCLE,
				System.currentTimeMillis() + 30_000L
			)
		}) {
			Text(stringResource(R.string.settings_simulate_vocab))
		}
		if (simVocabHint) {
			Text(
				text = stringResource(R.string.sim_vocab_scheduled),
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

		// AI 调试：把报告水位线回拨 3 天，下次生成为「补交回顾」模式（验收中断补交用）
		Button(onClick = {
			scope.launch {
				val repo = SettingsRepository.get(context)
				val current = repo.aiLastReportEpochDay.first()
				repo.setAiLastReportEpochDay(maxOf(0L, current - 3))
				dbCheckResult = context.getString(R.string.ai_debug_rewind_watermark_done)
			}
		}) {
			Text(stringResource(R.string.ai_debug_rewind_watermark))
		}

		// AI 调试：插一条 20 天前的旧报告，下次生成时应被 14 天滚动清理删除（验收保留窗用）
		Button(onClick = {
			scope.launch {
				val db = DailyControlDatabase.get(context)
				val oldDay = java.time.LocalDate.now().toEpochDay() - 20
				db.aiReportDao().insert(
					com.dailycontrol.app.data.entity.AiReport(
						reportDateEpochDay = oldDay,
						kind = "daily",
						generatedAt = System.currentTimeMillis(),
						summary = "测试用旧报告（应被清理）",
						dietAnalysis = "",
						exerciseAnalysis = "",
						suggestionsJson = "[]",
						questionsJson = "[]",
						parseOk = true,
						rawResponse = "",
						sentPayload = ""
					)
				)
				dbCheckResult = context.getString(R.string.ai_debug_seed_old_report_done)
			}
		}) {
			Text(stringResource(R.string.ai_debug_seed_old_report))
		}

		// AI 调试：把报告闹钟调到 1 分钟后，走与凌晨 4:00 完全相同的接收器分支验证全链路
		Button(onClick = {
			aiAlarmHint = true
			com.dailycontrol.app.schedule.AiReportPlanner.scheduleDebugInOneMinute(context)
		}) {
			Text(stringResource(R.string.ai_debug_alarm_1min))
		}
		if (aiAlarmHint) {
			Text(
				text = stringResource(R.string.ai_debug_alarm_1min_done),
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)
		}
				}
			}
		}
	}

	// 应用内备份清单弹窗：列出下载目录里本应用的备份与摘要；
	// 底部保留系统文件选择器兜底（备份可能存在网盘/其他目录）
	if (showBackupList) {
		AlertDialog(
			onDismissRequest = { showBackupList = false },
			title = { Text(stringResource(R.string.backup_list_title)) },
			text = {
				Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
					if (backupEntries.isEmpty()) {
						Text(
							text = stringResource(R.string.backup_list_empty),
							style = MaterialTheme.typography.bodySmall,
							color = MaterialTheme.colorScheme.onSurfaceVariant
						)
					} else {
						backupEntries.forEach { (info, summary) ->
							Column(
								modifier = Modifier
									.fillMaxWidth()
									.clickable(enabled = summary != null) {
										showBackupList = false
										restorePreview = summary
									}
									.padding(vertical = 10.dp)
							) {
								val kindRes = if (info.displayName.startsWith("daily-control-restore-snapshot-")) {
									R.string.backup_kind_snapshot
								} else {
									R.string.backup_kind_backup
								}
								Text(
									text = stringResource(kindRes) + " · " + TimeUtil.formatDateTime(info.addedAtMillis),
									style = MaterialTheme.typography.titleSmall,
									color = if (summary != null) MaterialTheme.colorScheme.onSurface
									else MaterialTheme.colorScheme.onSurfaceVariant
								)
								Text(
									text = if (summary != null) {
										stringResource(
											R.string.backup_entry_summary,
											summary.dietRecords.size,
											summary.exerciseRecords.size,
											summary.todoTasks.size
										)
									} else {
										stringResource(R.string.backup_unreadable) + "（" + info.displayName + "）"
									},
									style = MaterialTheme.typography.bodySmall,
									color = MaterialTheme.colorScheme.onSurfaceVariant
								)
							}
						}
					}
					TextButton(onClick = {
						showBackupList = false
						restoreLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
					}) {
						Text(stringResource(R.string.backup_pick_other))
					}
				}
			},
			confirmButton = {},
			dismissButton = {
				TextButton(onClick = { showBackupList = false }) {
					Text(stringResource(R.string.action_cancel))
				}
			}
		)
	}

	// 恢复确认弹窗：展示备份内容预览，确认后执行（快照在 restore 内部第一步自动生成）
	restorePreview?.let { summary ->
		AlertDialog(
			onDismissRequest = { restorePreview = null },
			title = { Text(stringResource(R.string.backup_restore_confirm_title)) },
			text = {
				Text(
					stringResource(
						R.string.backup_restore_confirm_text,
						TimeUtil.formatDateTime(summary.exportedAt),
						summary.dietRecords.size,
						summary.nutrientItems.size,
						summary.exerciseRecords.size,
						summary.todoTasks.size
					)
				)
			},
			confirmButton = {
				TextButton(onClick = {
					val toRestore = summary
					restorePreview = null
					scope.launch {
						BackupManager.restore(context, toRestore)
						backupResult = context.getString(R.string.restore_done)
					}
				}) { Text(stringResource(R.string.action_confirm)) }
			},
			dismissButton = {
				TextButton(onClick = { restorePreview = null }) {
					Text(stringResource(R.string.action_cancel))
				}
			}
		)
	}
}

// 单行时间设置：整行可点弹时间选择器（2026-10-19 批次3，时间值不再是弱化的文字按钮）
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
		verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
	) {
		Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
		Text(
			text = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
				.format(DateTimeFormatter.ofPattern("HH:mm")),
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
