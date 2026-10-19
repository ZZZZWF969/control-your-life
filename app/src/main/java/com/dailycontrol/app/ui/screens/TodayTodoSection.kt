package com.dailycontrol.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dailycontrol.app.R
import com.dailycontrol.app.data.db.DailyControlDatabase
import com.dailycontrol.app.data.entity.TodoTask
import com.dailycontrol.app.schedule.ReminderReason
import com.dailycontrol.app.schedule.ReminderScheduler
import com.dailycontrol.app.ui.components.ConfirmDialog
import com.dailycontrol.app.util.TimeUtil
import kotlinx.coroutines.launch

// 今日待办区（2026-10-19 从提醒页迁入）：未完成在前、已完成打勾调浅沉底；
// 勾选撤闹钟、反勾恢复闹钟（提醒时刻仍在未来时），删除带二次确认
@Composable
fun TodayTodoSection(
	tasks: List<TodoTask>,
	snackbarHostState: SnackbarHostState,
	onGoToPlan: () -> Unit
) {
	val sorted = remember(tasks) { sortForToday(tasks) }

	Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
		Text(
			text = stringResource(R.string.today_section_todo) + "（${tasks.size}）",
			style = MaterialTheme.typography.titleMedium
		)
		if (sorted.isEmpty()) {
			// 空状态：一句引导文案 + 去规划入口
			Card(modifier = Modifier.fillMaxWidth()) {
				Column(
					modifier = Modifier.fillMaxWidth().padding(16.dp),
					verticalArrangement = Arrangement.spacedBy(8.dp)
				) {
					Text(
						text = stringResource(R.string.today_todo_empty),
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
					TextButton(onClick = onGoToPlan) {
						Text(stringResource(R.string.remind_plan_entry))
					}
				}
			}
		} else {
			sorted.forEach { task -> TodoTaskRow(task, snackbarHostState) }
		}
	}
}

// 单条待办：勾选完成（撤闹钟）/ 反勾恢复（时刻在未来则重排）/ 删除（二次确认）
@Composable
private fun TodoTaskRow(task: TodoTask, snackbarHostState: SnackbarHostState) {
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
						val requestCode = ReminderReason.TODO_TASK_BASE + task.id.toInt()
						if (checked) {
							// 勾选 = 完成：撤掉该任务的提醒闹钟
							ReminderScheduler.cancel(context, requestCode)
						} else {
							// 反勾 = 恢复未完成：提醒时刻还在未来则重排闹钟，已过则提示不再补排
							val remindAt = task.startAtMillis?.let { it - task.remindAheadMinutes * 60_000L }
							if (remindAt != null && remindAt > System.currentTimeMillis()) {
								ReminderScheduler.schedule(context, requestCode, remindAt)
							} else if (remindAt != null) {
								snackbarHostState.showSnackbar(context.getString(R.string.todo_remind_past))
							}
						}
					}
				}
			)
			Column(modifier = Modifier.weight(1f)) {
				Text(
					text = task.title,
					style = MaterialTheme.typography.titleSmall,
					// 已完成整行调浅
					color = if (task.done) MaterialTheme.colorScheme.onSurfaceVariant
					else MaterialTheme.colorScheme.onSurface
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
		ConfirmDialog(
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

// 未完成在前（按开始时刻升序、无时刻的排最后），已完成整体沉底
private fun sortForToday(tasks: List<TodoTask>): List<TodoTask> {
	val undone = tasks.filter { !it.done }.sortedBy { it.startAtMillis ?: Long.MAX_VALUE }
	val done = tasks.filter { it.done }
	return undone + done
}
