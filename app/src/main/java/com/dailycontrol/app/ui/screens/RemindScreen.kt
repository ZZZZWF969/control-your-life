package com.dailycontrol.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.dailycontrol.app.R

// 提醒页：提醒规则与闹钟管理（第三阶段实现）
@Composable
fun RemindScreen() {
	PlaceholderPage(
		title = stringResource(R.string.tab_remind),
		description = stringResource(R.string.placeholder_remind_desc)
	)
}
