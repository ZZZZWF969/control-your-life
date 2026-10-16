package com.dailycontrol.app.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.dailycontrol.app.R

// AI 页：AI 生活建议与每日报告（第四阶段实现）
@Composable
fun AiScreen() {
	PlaceholderPage(
		icon = Icons.Filled.Star,
		title = stringResource(R.string.tab_ai),
		description = stringResource(R.string.placeholder_ai_desc)
	)
}
