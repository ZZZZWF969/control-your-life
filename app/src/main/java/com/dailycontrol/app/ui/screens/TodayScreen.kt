package com.dailycontrol.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.dailycontrol.app.R

// 今日页：概览当天的饮食、运动与待办完成情况（第二阶段实现）
@Composable
fun TodayScreen() {
	PlaceholderPage(
		title = stringResource(R.string.tab_today),
		description = stringResource(R.string.placeholder_today_desc)
	)
}
