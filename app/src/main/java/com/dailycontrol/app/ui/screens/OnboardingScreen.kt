package com.dailycontrol.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dailycontrol.app.R
import com.dailycontrol.app.notify.Notifier
import com.dailycontrol.app.schedule.ReminderScheduler

// 首启权限引导向导（2026-10-19 批次4）：5 步分页，可检测的步骤实时显示授权状态
// 「跳过」与「完成」等效：都通过 onFinish 让调用方置 onboardingDone=true，此后不再自动出现
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
	val context = LocalContext.current
	var step by remember { mutableIntStateOf(0) }
	val lastStep = 4

	// 步骤2·通知权限：系统弹窗的授权结果实时回流；授权成功顺手发条测试通知自证链路
	var notifyGranted by remember { mutableStateOf(Notifier.hasPostPermission(context)) }
	val permissionLauncher = rememberLauncherForActivityResult(
		ActivityResultContracts.RequestPermission()
	) { granted ->
		notifyGranted = granted
		if (granted) {
			Notifier.sendTestNotification(context)
		}
	}

	// 步骤3·精确闹钟：从系统授权页回来后无法自动感知，提供「重新检测」并随步骤切换刷新
	var exactGranted by remember { mutableStateOf(ReminderScheduler.canScheduleExact(context)) }
	LaunchedEffect(step) {
		if (step == 2) {
			exactGranted = ReminderScheduler.canScheduleExact(context)
		}
	}

	Scaffold { innerPadding ->
		Column(
			modifier = Modifier.fillMaxSize()
				.padding(innerPadding)
				.verticalScroll(rememberScrollState())
				.padding(24.dp),
			verticalArrangement = Arrangement.spacedBy(16.dp)
		) {
			// 标题带步骤指示，用户随时知道自己在第几步、共几步
			Text(
				text = stringResource(R.string.onboarding_title) + "（${step + 1}/5）",
				style = MaterialTheme.typography.headlineMedium
			)

			when (step) {
				0 -> {
					Text(
						text = stringResource(R.string.onboarding_welcome_text),
						style = MaterialTheme.typography.bodyLarge
					)
				}

				1 -> {
					Text(
						text = stringResource(R.string.onboarding_notify_text),
						style = MaterialTheme.typography.bodyLarge
					)
					if (notifyGranted) {
						Text(
							text = stringResource(R.string.onboarding_notify_done),
							style = MaterialTheme.typography.titleMedium,
							color = MaterialTheme.colorScheme.primary
						)
					} else {
						Button(onClick = {
							permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
						}) {
							Text(stringResource(R.string.onboarding_notify_action))
						}
					}
				}

				2 -> {
					Text(
						text = stringResource(R.string.onboarding_exact_text),
						style = MaterialTheme.typography.bodyLarge
					)
					if (exactGranted) {
						Text(
							text = stringResource(R.string.onboarding_exact_done),
							style = MaterialTheme.typography.titleMedium,
							color = MaterialTheme.colorScheme.primary
						)
					} else {
						Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
							Button(onClick = {
								// 个别 ROM 无此系统页时静默失败即可，设置页也有兜底入口
								runCatching {
									context.startActivity(
										Intent(
											Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
											Uri.parse("package:" + context.packageName)
										)
									)
								}
							}) {
								Text(stringResource(R.string.onboarding_exact_action))
							}
							TextButton(onClick = {
								exactGranted = ReminderScheduler.canScheduleExact(context)
							}) {
								Text(stringResource(R.string.onboarding_exact_recheck))
							}
						}
					}
				}

				3 -> {
					Text(
						text = stringResource(R.string.onboarding_hyperos_text),
						style = MaterialTheme.typography.bodyLarge
					)
					Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
						Button(onClick = {
							runCatching {
								context.startActivity(
									Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
										.putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
								)
							}
						}) {
							Text(stringResource(R.string.onboarding_hyperos_action))
						}
						OutlinedButton(onClick = { Notifier.sendTestNotification(context) }) {
							Text(stringResource(R.string.onboarding_hyperos_test))
						}
					}
				}

				4 -> {
					Text(
						text = stringResource(R.string.onboarding_battery_text),
						style = MaterialTheme.typography.bodyLarge
					)
					Button(onClick = {
						runCatching {
							context.startActivity(
								Intent(
									Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
									Uri.parse("package:" + context.packageName)
								)
							)
						}
					}) {
						Text(stringResource(R.string.onboarding_battery_action))
					}
				}
			}

			// 占位把底部按钮压到页面下端
			Spacer(modifier = Modifier.weight(1f))
			Spacer(modifier = Modifier.height(8.dp))

			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.spacedBy(12.dp),
				verticalAlignment = Alignment.CenterVertically
			) {
				TextButton(onClick = onFinish) {
					Text(stringResource(R.string.onboarding_skip))
				}
				Spacer(modifier = Modifier.weight(1f))
				if (step > 0) {
					OutlinedButton(onClick = { step-- }) {
						Text(stringResource(R.string.onboarding_prev))
					}
				}
				Button(onClick = {
					if (step < lastStep) {
						step++
					} else {
						onFinish()
					}
				}) {
					Text(
						text = stringResource(
							if (step < lastStep) R.string.onboarding_next else R.string.onboarding_finish
						)
					)
				}
			}
		}
	}
}
