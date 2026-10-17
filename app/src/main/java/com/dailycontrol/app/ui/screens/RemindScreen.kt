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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.dailycontrol.app.data.db.DailyControlDatabase
import com.dailycontrol.app.data.entity.TodoTask
import com.dailycontrol.app.data.settings.SettingsRepository
import com.dailycontrol.app.notify.Notifier
import com.dailycontrol.app.schedule.ReminderReason
import com.dailycontrol.app.schedule.ReminderScheduler
import com.dailycontrol.app.schedule.VocabPlanner
import com.dailycontrol.app.util.TimeUtil
import kotlinx.coroutines.launch
import java.time.LocalDate

// 提醒页：背单词状态、今日/明日待办清单、规划入口
@Composable
fun RemindScreen(onGoToPlan: () -> Unit) {
	val context = LocalContext.current
	val scope = rememberCoroutineScope()
	val settings = SettingsRepository.get(context)
	val db = remember { DailyControlDatabase.get(context) }
	val vocabDoneDate by settings.vocabDoneDate.collectAsStateWithLifecycle(initialValue = "")
	val vocabPackage by settings.vocabPackage.collectAsStateWithLifecycle(initialValue = "")
	val doneToday = vocabDoneDate == LocalDate.now().toString()

	val today = LocalDate.now()
	val tomorrow = today.plusDays(1)
	val todayTasks by remember { db.todoTaskDao().observeByDate(today.toEpochDay()) }
		.collectAsStateWithLifecycle(initialValue = emptyList())
	val tomorrowTasks by remember { db.todoTaskDao().observeByDate(tomorrow.toEpochDay()) }
		.collectAsStateWithLifecycle(initialValue = emptyList())

	Column(
		modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
		verticalArrangement = Arrangement.spacedBy(16.dp)
	) {
		Text(text = stringResource(R.string.tab_remind), style = MaterialTheme.typography.headlineMedium)

		// 背单词状态卡
		Card(modifier = Modifier.fillMaxWidth()) {
			Column(
				modifier = Modifier.fillMaxWidth().padding(16.dp),
				verticalArrangement = Arrangement.spacedBy(12.dp)
			) {
				Text(
					text = stringResource(R.string.vocab_status_title),
					style = MaterialTheme.typography.titleMedium
				)
				Text(
					text = stringResource(
						if (doneToday) R.string.vocab_status_done else R.string.vocab_status_undone
					),
					style = MaterialTheme.typography.bodyMedium,
					color = if (doneToday) MaterialTheme.colorScheme.primary
					else MaterialTheme.colorScheme.onSurfaceVariant
				)
				Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
					Button(
						onClick = {
							scope.launch {
								VocabPlanner.markDoneToday(context)
								Notifier.cancelVocabNotification(context)
							}
						},
						enabled = !doneToday
					) {
						Text(stringResource(R.string.vocab_action_done))
					}
					if (vocabPackage.isNotBlank()) {
						OutlinedButton(onClick = {
							context.packageManager.getLaunchIntentForPackage(vocabPackage)?.let {
								context.startActivity(it)
							}
						}) {
							Text(stringResource(R.string.vocab_action_go))
						}
					}
				}
			}
		}

		// 今日待办
		Text(
			text = stringResource(R.string.remind_section_today_todo) + "（${todayTasks.size}）",
			style = MaterialTheme.typography.titleMedium
		)
		if (todayTasks.isEmpty()) {
			Text(
				text = stringResource(R.string.remind_empty_todo),
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)
		} else {
			todayTasks.forEach { task -> TodoTaskRow(task) }
		}

		// 明日待办
		Text(
			text = stringResource(R.string.remind_section_tomorrow_todo) + "（${tomorrowTasks.size}）",
			style = MaterialTheme.typography.titleMedium
		)
		if (tomorrowTasks.isEmpty()) {
			Text(
				text = stringResource(R.string.remind_empty_todo),
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)
		} else {
			tomorrowTasks.forEach { task -> TodoTaskRow(task) }
		}

		// 规划入口：22:00 通知深链直达规划页；这里提供常驻入口
		OutlinedButton(onClick = onGoToPlan, modifier = Modifier.fillMaxWidth()) {
			Icon(imageVector = Icons.Filled.AddCircle, contentDescription = null)
			Spacer(modifier = Modifier.size(8.dp))
			Text(stringResource(R.string.remind_plan_entry))
		}
	}
}

// 单条待办：勾选完成（撤闹钟）/ 删除
@Composable
private fun TodoTaskRow(task: TodoTask) {
	val context = LocalContext.current
	val scope = rememberCoroutineScope()
	val db = remember { DailyControlDatabase.get(context) }
	var deleting by remember { mutableStateOf(false) }

	Card(modifier = Modifier.fillMaxWidth()) {
		Row(
			modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
			verticalAlignment = Alignment.CenterVertically
		) {
			Checkbox(
				checked = task.done,
				onCheckedChange = { checked ->
					scope.launch {
						db.todoTaskDao().update(task.copy(done = checked))
						if (checked) {
							ReminderScheduler.cancel(context, ReminderReason.TODO_TASK_BASE + task.id.toInt())
						}
					}
				}
			)
			Column(modifier = Modifier.weight(1f)) {
				Text(
					text = task.title,
					style = MaterialTheme.typography.titleSmall
				)
				task.startAtMillis?.let { start ->
					Text(
						text = TimeUtil.formatTime(start) + " · " +
							stringResource(R.string.plan_ahead_option, task.remindAheadMinutes),
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
				}
			}
			IconButton(onClick = { deleting = true }) {
				Icon(
					imageVector = Icons.Outlined.Delete,
					contentDescription = stringResource(R.string.action_delete),
					tint = MaterialTheme.colorScheme.onSurfaceVariant
				)
			}
		}
	}
	if (deleting) {
		com.dailycontrol.app.ui.components.ConfirmDialog(
			title = stringResource(R.string.confirm_delete_todo_title),
			text = stringResource(R.string.confirm_delete_todo_text),
			onConfirm = {
				deleting = false
				scope.launch {
					db.todoTaskDao().delete(task)
					ReminderScheduler.cancel(context, ReminderReason.TODO_TASK_BASE + task.id.toInt())
				}
			},
			onDismiss = { deleting = false }
		)
	}
}
