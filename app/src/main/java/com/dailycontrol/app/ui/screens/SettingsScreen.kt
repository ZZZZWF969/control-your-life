package com.dailycontrol.app.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dailycontrol.app.R
import com.dailycontrol.app.data.db.DailyControlDatabase
import com.dailycontrol.app.data.entity.DietRecord
import com.dailycontrol.app.data.entity.ExerciseRecord
import com.dailycontrol.app.notify.Notifier
import kotlinx.coroutines.launch

// 设置页：将承载提醒时间、AI 接口、背单词目标等配置；当前提供链路自检工具
@Composable
fun SettingsScreen() {
	val context = LocalContext.current
	val scope = rememberCoroutineScope()
	var dbCheckResult by remember { mutableStateOf<String?>(null) }
	var notifyGranted by remember { mutableStateOf(Notifier.hasPostPermission(context)) }

	// 系统权限弹窗的结果回调：授权成功后立即补发测试通知
	val permissionLauncher = rememberLauncherForActivityResult(
		ActivityResultContracts.RequestPermission()
	) { granted ->
		notifyGranted = granted
		if (granted) {
			Notifier.sendTestNotification(context)
		}
	}

	Column(
		modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
		verticalArrangement = Arrangement.spacedBy(16.dp)
	) {
		Text(text = stringResource(R.string.tab_settings), style = MaterialTheme.typography.headlineMedium)
		Text(
			text = stringResource(R.string.settings_desc),
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant
		)
		HorizontalDivider()
		Text(text = stringResource(R.string.settings_section_diag), style = MaterialTheme.typography.titleMedium)

		Button(onClick = {
			if (notifyGranted) {
				Notifier.sendTestNotification(context)
			} else {
				permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
			}
		}) {
			Text(stringResource(R.string.settings_send_test_notify))
		}
		if (!notifyGranted) {
			Text(
				text = stringResource(R.string.settings_notify_no_permission),
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)
		}

		Button(onClick = {
			// Room 的挂起方法自带线程调度，这里串起「写入样例 → 统计条数」的自检流程
			scope.launch {
				val now = System.currentTimeMillis()
				val db = DailyControlDatabase.get(context)
				db.dietRecordDao().insert(
					DietRecord(
						mealType = "lunch",
						foodText = "样例：米饭、青菜",
						portionText = "七分饱",
						eatenAt = now,
						recordedAt = now
					)
				)
				db.exerciseRecordDao().insert(
					ExerciseRecord(
						typeText = "样例：慢跑",
						intensityText = "中等",
						totalText = "1km",
						startAt = now,
						endAt = now,
						recordedAt = now
					)
				)
				val dietCount = db.dietRecordDao().count()
				val exerciseCount = db.exerciseRecordDao().count()
				dbCheckResult = context.getString(R.string.settings_db_check_result, dietCount, exerciseCount)
			}
		}) {
			Text(stringResource(R.string.settings_db_check))
		}
		dbCheckResult?.let { result ->
			Text(text = result, style = MaterialTheme.typography.bodyMedium)
		}
	}
}
