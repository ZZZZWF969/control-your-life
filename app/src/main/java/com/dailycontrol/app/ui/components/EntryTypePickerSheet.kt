package com.dailycontrol.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dailycontrol.app.R

// 「记一笔」的类型选择弹层：饮食 / 运动两张卡片；今日页与记录页共用（2026-10-19 从记录页抽出）
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryTypePickerSheet(
	onDismiss: () -> Unit,
	onPickDiet: () -> Unit,
	onPickExercise: () -> Unit
) {
	ModalBottomSheet(onDismissRequest = onDismiss) {
		Column(
			modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
			verticalArrangement = Arrangement.spacedBy(8.dp)
		) {
			Text(
				text = stringResource(R.string.record_choose_type),
				style = MaterialTheme.typography.titleLarge
			)
			Card(
				onClick = onPickDiet,
				modifier = Modifier.fillMaxWidth()
			) {
				EntryTypeRow(
					badgeChar = "餐",
					badgeColor = Color(0xFFC2F0E9),
					charColor = Color(0xFF042F2C),
					titleRes = R.string.record_type_diet,
					descRes = R.string.record_type_diet_desc
				)
			}
			Card(
				onClick = onPickExercise,
				modifier = Modifier.fillMaxWidth()
			) {
				EntryTypeRow(
					badgeChar = "动",
					badgeColor = Color(0xFFC7CBF8),
					charColor = Color(0xFF1B1F65),
					titleRes = R.string.record_type_exercise,
					descRes = R.string.record_type_exercise_desc
				)
			}
		}
	}
}

// 弹层里的一行类型选项：圆形字标 + 标题 + 描述
@Composable
private fun EntryTypeRow(
	badgeChar: String,
	badgeColor: Color,
	charColor: Color,
	titleRes: Int,
	descRes: Int
) {
	Row(
		modifier = Modifier.fillMaxWidth().padding(16.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		TypeBadge(badgeChar = badgeChar, background = badgeColor, content = charColor)
		Spacer(modifier = Modifier.size(14.dp))
		Column(modifier = Modifier.weight(1f)) {
			Text(
				text = stringResource(titleRes),
				style = MaterialTheme.typography.titleSmall
			)
			Text(
				text = stringResource(descRes),
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)
		}
	}
}
