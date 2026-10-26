package com.dailycontrol.app.ui.screens

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import com.dailycontrol.app.R
import com.dailycontrol.app.backup.BackupManager
import com.dailycontrol.app.data.db.DailyControlDatabase
import com.dailycontrol.app.data.entity.DietRecord
import com.dailycontrol.app.data.entity.ExerciseRecord
import com.dailycontrol.app.data.settings.SettingsRepository
import com.dailycontrol.app.notify.Notifier
import com.dailycontrol.app.schedule.AiReportPlanner
import com.dailycontrol.app.schedule.ReminderReason
import com.dailycontrol.app.schedule.ReminderScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// 设置子页·调试工具（2026-10-26 分组化重构时从旧设置页的折叠卡整体搬移升格）
// 这些按钮排的都是真实闹钟走完整链路（不是直接弹通知），是每批功能的验收工具
@Composable
internal fun DebugSection() {
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

	Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
				dbCheckResult = if (ok) {
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
		}, modifier = Modifier.fillMaxWidth()) {
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
		}, modifier = Modifier.fillMaxWidth()) {
			Text(stringResource(R.string.ai_debug_seed_old_report))
		}

		// AI 调试：把报告闹钟调到 1 分钟后，走与凌晨 4:00 完全相同的接收器分支验证全链路
		Button(onClick = {
			aiAlarmHint = true
			AiReportPlanner.scheduleDebugInOneMinute(context)
		}, modifier = Modifier.fillMaxWidth()) {
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
