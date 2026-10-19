package com.dailycontrol.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dailycontrol.app.R
import com.dailycontrol.app.data.entity.ExerciseRecord
import com.dailycontrol.app.ui.components.TypeBadge
import com.dailycontrol.app.util.TimeUtil
import java.time.Duration

// 单条运动记录的卡片：点击进入编辑（删除收进编辑表单，2026-10-19 批次2）；尾部箭头提示可点
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseRecordCard(
	record: ExerciseRecord,
	onClick: () -> Unit
) {
	val durationMinutes = Duration.between(
		java.time.Instant.ofEpochMilli(record.startAt),
		java.time.Instant.ofEpochMilli(record.endAt)
	).toMinutes()
	Card(
		onClick = onClick,
		modifier = Modifier.fillMaxWidth()
	) {
		Row(
			modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
			verticalAlignment = Alignment.CenterVertically
		) {
			TypeBadge(badgeChar = "动", background = Color(0xFFC7CBF8), content = Color(0xFF1B1F65))
			Spacer(modifier = Modifier.size(14.dp))
			Column(modifier = Modifier.weight(1f)) {
				Text(
					text = record.typeText,
					style = MaterialTheme.typography.titleSmall,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis
				)
				Spacer(modifier = Modifier.height(4.dp))
				Text(
					text = buildString {
						append(TimeUtil.formatTime(record.startAt))
						append("-")
						append(TimeUtil.formatTime(record.endAt))
						append(" · ").append(durationMinutes).append(stringResource(R.string.ex_duration_unit))
						if (record.intensityText.isNotBlank()) {
							append(" · ").append(record.intensityText)
						}
						if (record.totalText.isNotBlank()) {
							append(" · ").append(record.totalText)
						}
					},
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis
				)
			}
			// 「可进入编辑」的通用视觉暗示
			Icon(
				imageVector = Icons.Filled.KeyboardArrowRight,
				contentDescription = null,
				tint = MaterialTheme.colorScheme.onSurfaceVariant
			)
		}
	}
}
