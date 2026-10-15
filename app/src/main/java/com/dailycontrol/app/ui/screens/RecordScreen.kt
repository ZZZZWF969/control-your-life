package com.dailycontrol.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.dailycontrol.app.R

// 记录页：饮食与运动的手动记录入口（第二阶段实现）
@Composable
fun RecordScreen() {
	PlaceholderPage(
		title = stringResource(R.string.tab_record),
		description = stringResource(R.string.placeholder_record_desc)
	)
}
