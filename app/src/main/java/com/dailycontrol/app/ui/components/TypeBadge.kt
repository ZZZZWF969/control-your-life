package com.dailycontrol.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// 通用圆形字标徽章：浅底深字，深浅两套主题下都可读；用于记录卡片与类型选择行
@Composable
fun TypeBadge(
	badgeChar: String,
	background: Color,
	content: Color
) {
	Box(
		modifier = Modifier.size(44.dp).background(background, CircleShape),
		contentAlignment = Alignment.Center
	) {
		Text(
			text = badgeChar,
			style = MaterialTheme.typography.titleMedium,
			color = content
		)
	}
}
