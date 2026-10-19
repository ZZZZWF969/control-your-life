package com.dailycontrol.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dailycontrol.app.R

// 极简背单词卡（2026-10-19 用户确认）：一句话说明今天背没背 + 跳转入口 + 记录按键，不带频率说明
@Composable
fun TodayVocabCard(
	doneToday: Boolean,
	showGoEntry: Boolean,
	onMarkDone: () -> Unit,
	onGoVocab: () -> Unit
) {
	Card(modifier = Modifier.fillMaxWidth()) {
		Row(
			modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
			horizontalArrangement = Arrangement.spacedBy(8.dp),
			verticalAlignment = Alignment.CenterVertically
		) {
			Text(
				text = stringResource(if (doneToday) R.string.today_vocab_done else R.string.today_vocab_undone),
				style = MaterialTheme.typography.titleSmall,
				// 与待办一致：已完成调浅、未完成用正常强调色
				color = if (doneToday) MaterialTheme.colorScheme.onSurfaceVariant
				else MaterialTheme.colorScheme.onSurface,
				modifier = Modifier.weight(1f)
			)
			// 已背之后记录按键失去意义，直接隐藏（2026-10-19 默认决策）
			if (!doneToday) {
				Button(onClick = onMarkDone) {
					Text(stringResource(R.string.vocab_action_done))
				}
			}
			if (showGoEntry) {
				OutlinedButton(onClick = onGoVocab) {
					Text(stringResource(R.string.vocab_action_go))
				}
			}
		}
	}
}
