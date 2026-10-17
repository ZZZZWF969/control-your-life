package com.dailycontrol.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.room.withTransaction
import com.dailycontrol.app.R
import com.dailycontrol.app.data.db.DailyControlDatabase
import com.dailycontrol.app.data.entity.TodoTask
import com.dailycontrol.app.data.settings.SettingsRepository
import com.dailycontrol.app.schedule.ReminderReason
import com.dailycontrol.app.schedule.ReminderScheduler
import com.dailycontrol.app.schedule.SystemAlarmSetter
import com.dailycontrol.app.ui.components.TimePickerDialog
import com.dailycontrol.app.util.TimeUtil
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// 规划页的草稿行：未保存前的任务输入状态
private data class TodoDraft(
	val title: String = "",
	val startTime: LocalTime? = null,
	val aheadMinutes: Int = 15
)

// 起床选择：无课 / 早八 / 早十
private enum class WakePick {
	NONE, EARLY8, EARLY10
}

// 晚间规划页：为今天或明天安排待办（可选提醒）与起床闹钟
// 保存语义：整体替换所选日期的「未完成任务」（已完成的保留）；当晚已规划标记用于 22:00/23:00 提醒判断
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlanScreen(onDone: () -> Unit) {
	val context = LocalContext.current
	val scope = rememberCoroutineScope()
	val db = remember { DailyControlDatabase.get(context) }
	val zone = ZoneId.systemDefault()

	var forTomorrow by remember { mutableStateOf(true) }
	var drafts by remember { mutableStateOf(listOf(TodoDraft())) }
	var wakePick by remember { mutableStateOf(WakePick.NONE) }
	var timeEditIndex by remember { mutableStateOf<Int?>(null) }
	var saving by remember { mutableStateOf(false) }

	// 切换目标日期时载入该日的未完成任务作为草稿（编辑语义）
	LaunchedEffect(forTomorrow) {
		val epochDay = targetDate(forTomorrow).toEpochDay()
		val existing = db.todoTaskDao().getByDate(epochDay).filter { !it.done }
		drafts = existing.map { task ->
			TodoDraft(
				title = task.title,
				startTime = task.startAtMillis
					?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalTime() },
				aheadMinutes = task.remindAheadMinutes
			)
		}.ifEmpty { listOf(TodoDraft()) }
	}

	Column(
		modifier = Modifier.fillMaxSize()
			.verticalScroll(rememberScrollState())
			.imePadding()
			.padding(20.dp),
		verticalArrangement = Arrangement.spacedBy(14.dp)
	) {
		Text(text = stringResource(R.string.plan_title), style = MaterialTheme.typography.headlineMedium)

		// 目标日期：默认明天（22:00 提醒场景），也可安排今天
		SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
			SegmentedButton(
				selected = !forTomorrow,
				onClick = { forTomorrow = false },
				shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
				label = { Text(stringResource(R.string.plan_day_today)) }
			)
			SegmentedButton(
				selected = forTomorrow,
				onClick = { forTomorrow = true },
				shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
				label = { Text(stringResource(R.string.plan_day_tomorrow)) }
			)
		}

		Text(
			text = stringResource(R.string.plan_section_todo),
			style = MaterialTheme.typography.titleMedium
		)
		drafts.forEachIndexed { index, draft ->
			Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
				Row(verticalAlignment = Alignment.CenterVertically) {
					OutlinedTextField(
						value = draft.title,
						onValueChange = { value ->
							drafts = drafts.toMutableList().also { it[index] = draft.copy(title = value) }
						},
						label = { Text(stringResource(R.string.plan_task_title_hint)) },
						singleLine = true,
						modifier = Modifier.weight(1f)
					)
					IconButton(onClick = {
						drafts = drafts.toMutableList().also { it.removeAt(index) }
					}) {
						Icon(
							imageVector = Icons.Outlined.Close,
							contentDescription = stringResource(R.string.plan_remove_task),
							tint = MaterialTheme.colorScheme.onSurfaceVariant
						)
					}
				}
				Row(
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(8.dp)
				) {
					OutlinedButton(onClick = { timeEditIndex = index }) {
						Text(
							text = draft.startTime?.format(DateTimeFormatter.ofPattern("HH:mm"))
								?: stringResource(R.string.plan_task_no_time)
						)
					}
					Text(
						text = stringResource(R.string.plan_task_ahead),
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
					FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
						listOf(5, 10, 15, 30).forEach { minutes ->
							FilterChip(
								selected = draft.aheadMinutes == minutes,
								onClick = {
									drafts = drafts.toMutableList()
										.also { it[index] = draft.copy(aheadMinutes = minutes) }
								},
								label = { Text(stringResource(R.string.plan_ahead_option, minutes)) }
							)
						}
					}
				}
			}
		}
		OutlinedButton(
			onClick = { drafts = drafts + TodoDraft() },
			modifier = Modifier.fillMaxWidth()
		) {
			Icon(imageVector = Icons.Filled.AddCircle, contentDescription = null)
			Spacer(modifier = Modifier.width(8.dp))
			Text(stringResource(R.string.plan_add_task))
		}

		Text(
			text = stringResource(R.string.plan_section_wake),
			style = MaterialTheme.typography.titleMedium
		)
		FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
			listOf(
				WakePick.NONE to R.string.plan_wake_none,
				WakePick.EARLY8 to R.string.plan_wake_early8,
				WakePick.EARLY10 to R.string.plan_wake_early10
			).forEach { (pick, res) ->
				FilterChip(
					selected = wakePick == pick,
					onClick = { wakePick = pick },
					label = { Text(stringResource(res)) }
				)
			}
		}
		Text(
			text = stringResource(R.string.plan_wake_hint),
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.onSurfaceVariant
		)

		Button(
			onClick = {
				if (saving) {
					return@Button
				}
				saving = true
				scope.launch {
					savePlan(context, db, targetDate(forTomorrow), drafts, wakePick)
					saving = false
					onDone()
				}
			},
			modifier = Modifier.fillMaxWidth()
		) {
			Text(stringResource(R.string.plan_save))
		}
	}

	// 某行的开始时间选择器
	timeEditIndex?.let { index ->
		TimePickerDialog(
			initial = drafts.getOrNull(index)?.startTime ?: LocalTime.of(9, 0),
			onConfirm = { time ->
				drafts = drafts.toMutableList().also {
					if (index < it.size) {
						it[index] = it[index].copy(startTime = time)
					}
				}
				timeEditIndex = null
			},
			onDismiss = { timeEditIndex = null }
		)
	}
}

private fun targetDate(forTomorrow: Boolean): LocalDate {
	return if (forTomorrow) LocalDate.now().plusDays(1) else LocalDate.now()
}

// 保存：整体替换该日未完成任务 → 排/撤对应闹钟 → 需起床时写系统时钟 → 标记当晚已规划
private suspend fun savePlan(
	context: android.content.Context,
	db: DailyControlDatabase,
	date: LocalDate,
	drafts: List<TodoDraft>,
	wakePick: WakePick
) {
	val epochDay = date.toEpochDay()
	val now = System.currentTimeMillis()
	val dao = db.todoTaskDao()

	// 旧未完成任务先取出（用于撤销其闹钟），再整体替换
	val oldUndone = dao.getByDate(epochDay).filter { !it.done }
	db.withTransaction {
		dao.deleteUndoneOfDate(epochDay)
		val valid = drafts.filter { it.title.isNotBlank() }
		if (valid.isNotEmpty()) {
			val ids = dao.insertAll(
				valid.map { draft ->
					TodoTask(
						taskDateEpochDay = epochDay,
						title = draft.title.trim(),
						startAtMillis = draft.startTime?.let { TimeUtil.combineToMillis(date, it) },
						remindAheadMinutes = draft.aheadMinutes,
						done = false,
						createdAt = now
					)
				}
			)
			// 提醒时刻仍在未来的才排闹钟（当日已过时刻只记录）
			valid.forEachIndexed { i, draft ->
				val start = draft.startTime?.let { TimeUtil.combineToMillis(date, it) }
				if (start != null) {
					val remindAt = start - draft.aheadMinutes * 60_000L
					if (remindAt > now) {
						ReminderScheduler.schedule(
							context,
							ReminderReason.TODO_TASK_BASE + ids[i].toInt(),
							remindAt
						)
					}
				}
			}
		}
	}
	// 撤销被替换旧任务的闹钟
	oldUndone.forEach { task ->
		ReminderScheduler.cancel(context, ReminderReason.TODO_TASK_BASE + task.id.toInt())
	}

	// 起床闹钟：早八 07:30-08:00 随机 / 早十 09:15-09:45 随机，写入系统时钟应用
	when (wakePick) {
		WakePick.EARLY8 -> SystemAlarmSetter.setWakeAlarm(context, morningClass = true)
		WakePick.EARLY10 -> SystemAlarmSetter.setWakeAlarm(context, morningClass = false)
		WakePick.NONE -> Unit
	}

	// 标记今晚已完成规划（22:00 未规划提醒与 23:00 二次提醒据此判断）
	SettingsRepository.get(context).setLastPlannedDate(LocalDate.now().toString())
}
