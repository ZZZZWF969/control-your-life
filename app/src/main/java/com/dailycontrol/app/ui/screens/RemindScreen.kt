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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailycontrol.app.R
import com.dailycontrol.app.data.settings.SettingsRepository
import com.dailycontrol.app.notify.Notifier
import com.dailycontrol.app.schedule.VocabPlanner
import kotlinx.coroutines.launch
import java.time.LocalDate

// 提醒页：背单词的当日状态与操作；晚间规划入口在第 3 批加入
@Composable
fun RemindScreen() {
	val context = LocalContext.current
	val scope = rememberCoroutineScope()
	val settings = SettingsRepository.get(context)
	val vocabDoneDate by settings.vocabDoneDate.collectAsStateWithLifecycle(initialValue = "")
	val vocabPackage by settings.vocabPackage.collectAsStateWithLifecycle(initialValue = "")
	val doneToday = vocabDoneDate == LocalDate.now().toString()

	Column(
		modifier = Modifier.fillMaxSize().padding(20.dp),
		verticalArrangement = Arrangement.spacedBy(16.dp)
	) {
		Text(text = stringResource(R.string.tab_remind), style = MaterialTheme.typography.headlineMedium)

		// 背单词状态卡：状态 + 两个操作（完成确认 / 跳转目标应用）
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

		Spacer(modifier = Modifier.height(4.dp))

		// 晚间规划入口与明日待办列表在第 3 批加入
		PlaceholderPage(
			icon = Icons.Filled.Notifications,
			title = stringResource(R.string.remind_plan_placeholder_title),
			description = stringResource(R.string.remind_plan_placeholder_desc)
		)
	}
}
