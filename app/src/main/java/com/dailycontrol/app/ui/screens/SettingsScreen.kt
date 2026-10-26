package com.dailycontrol.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailycontrol.app.R
import com.dailycontrol.app.data.settings.SettingsRepository
import com.dailycontrol.app.data.settings.ThemeMode
import com.dailycontrol.app.util.TimeUtil
import kotlinx.coroutines.launch

// 设置页（2026-10-26 分组化重构）：主页是六组设置入口的分组列表（每行带当前状态摘要），
// 点击进入各板块子页专门设置——子页为页内状态切换 + BackHandler（与 AI 页详情同模式）
@Composable
fun SettingsScreen() {
	val context = LocalContext.current
	val scope = rememberCoroutineScope()
	val settings = SettingsRepository.get(context)

	var section by remember { mutableStateOf<SettingsSection?>(null) }
	BackHandler(enabled = section != null) { section = null }

	// 主页摘要数据：订阅一次，带摘要的分组行实时状态从这里来
	val themeMode by settings.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
	val aiBaseUrl by settings.aiBaseUrl.collectAsStateWithLifecycle(initialValue = "")
	val aiApiKey by settings.aiApiKey.collectAsStateWithLifecycle(initialValue = "")
	val aiModel by settings.aiModel.collectAsStateWithLifecycle(initialValue = "")
	val lastBackupAt by settings.lastBackupAt.collectAsStateWithLifecycle(initialValue = 0L)

	val current = section
	if (current != null) {
		when (current) {
			SettingsSection.APPEARANCE -> SettingsSubPage(
				title = stringResource(R.string.settings_section_appearance),
				onBack = { section = null }
			) {
				AppearanceSection()
			}
			SettingsSection.REMINDER -> SettingsSubPage(
				title = stringResource(R.string.settings_section_reminder),
				onBack = { section = null }
			) {
				ReminderSection()
			}
			SettingsSection.LAUNCH -> SettingsSubPage(
				title = stringResource(R.string.settings_section_launch),
				onBack = { section = null }
			) {
				LaunchSection()
			}
			SettingsSection.AI -> SettingsSubPage(
				title = stringResource(R.string.settings_section_ai),
				onBack = { section = null }
			) {
				AiConfigSection()
			}
			SettingsSection.BACKUP -> SettingsSubPage(
				title = stringResource(R.string.settings_section_backup),
				onBack = { section = null }
			) {
				BackupSection()
			}
			SettingsSection.DEBUG -> SettingsSubPage(
				title = stringResource(R.string.settings_section_debug),
				onBack = { section = null }
			) {
				DebugSection()
			}
		}
		return
	}

	// ===== 设置主页：分组入口列表 =====
	Column(
		modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
		verticalArrangement = Arrangement.spacedBy(2.dp)
	) {
		Text(text = stringResource(R.string.tab_settings), style = MaterialTheme.typography.headlineMedium)
		Text(
			text = stringResource(R.string.settings_desc),
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant
		)

		HorizontalDivider(modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
		SettingsGroupRow(
			title = stringResource(R.string.settings_section_appearance),
			summary = stringResource(themeMode.labelRes),
			onClick = { section = SettingsSection.APPEARANCE }
		)
		SettingsGroupRow(
			title = stringResource(R.string.settings_section_reminder),
			summary = "",
			onClick = { section = SettingsSection.REMINDER }
		)
		SettingsGroupRow(
			title = stringResource(R.string.settings_section_launch),
			summary = "",
			onClick = { section = SettingsSection.LAUNCH }
		)
		SettingsGroupRow(
			title = stringResource(R.string.settings_section_ai),
			// 仅「已配置 ✓」一种摘要：未配置时不显示，引导用户点进去看引导文案
			summary = if (aiBaseUrl.isNotBlank() && aiApiKey.isNotBlank() && aiModel.isNotBlank()) {
				stringResource(R.string.settings_summary_ai_configured)
			} else {
				""
			},
			onClick = { section = SettingsSection.AI }
		)
		SettingsGroupRow(
			title = stringResource(R.string.settings_section_backup),
			summary = if (lastBackupAt > 0) {
				stringResource(R.string.settings_last_backup_at, TimeUtil.formatDateTime(lastBackupAt))
			} else {
				stringResource(R.string.settings_last_backup_never)
			},
			onClick = { section = SettingsSection.BACKUP }
		)
		SettingsGroupRow(
			title = stringResource(R.string.settings_section_debug),
			summary = "",
			onClick = { section = SettingsSection.DEBUG }
		)

		HorizontalDivider(modifier = Modifier.padding(top = 8.dp, bottom = 14.dp))
		// 重新查看首启权限引导（2026-10-19 批次4）：置回未完成标志即可让向导重现
		OutlinedButton(
			onClick = {
				scope.launch { settings.setOnboardingDone(false) }
			},
			modifier = Modifier.fillMaxWidth()
		) {
			Text(stringResource(R.string.settings_replay_onboarding))
		}
	}
}

// 六个设置板块的页内路由
private enum class SettingsSection {
	APPEARANCE, REMINDER, LAUNCH, AI, BACKUP, DEBUG
}

// 主页分组行：标题 + 当前状态摘要（实时值，不用进子页就能看状态）+ 右箭头
@Composable
private fun SettingsGroupRow(title: String, summary: String, onClick: () -> Unit) {
	Row(
		modifier = Modifier.fillMaxWidth()
			.clickable(onClick = onClick)
			.padding(vertical = 14.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		Text(
			text = title,
			style = MaterialTheme.typography.bodyLarge,
			modifier = Modifier.weight(1f)
		)
		if (summary.isNotBlank()) {
			Text(
				text = summary,
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)
		}
		Icon(
			imageVector = Icons.Filled.KeyboardArrowRight,
			contentDescription = null,
			tint = MaterialTheme.colorScheme.onSurfaceVariant
		)
	}
}

// 子页统一外壳：返回箭头 + 板块标题 + 可滚动内容
@Composable
private fun SettingsSubPage(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
	Column(
		modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)
	) {
		Row(verticalAlignment = Alignment.CenterVertically) {
			androidx.compose.material3.IconButton(onClick = onBack) {
				Icon(
					imageVector = Icons.AutoMirrored.Filled.ArrowBack,
					contentDescription = stringResource(R.string.ai_detail_back)
				)
			}
			Text(text = title, style = MaterialTheme.typography.titleLarge)
		}
		Column(
			modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
			verticalArrangement = Arrangement.spacedBy(12.dp)
		) {
			content()
		}
	}
}
