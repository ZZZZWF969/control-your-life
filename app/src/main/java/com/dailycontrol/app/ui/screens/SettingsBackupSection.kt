package com.dailycontrol.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailycontrol.app.R
import com.dailycontrol.app.backup.BackupManager
import com.dailycontrol.app.backup.BackupFormatException
import com.dailycontrol.app.backup.BackupSummary
import com.dailycontrol.app.data.settings.SettingsRepository
import com.dailycontrol.app.util.TimeUtil
import kotlinx.coroutines.launch

// 设置子页·备份与恢复（2026-10-26 分组化重构时从旧设置页整体搬移）
// 导出/恢复/应用内备份清单/恢复确认弹窗，内容与逻辑零变化
@Composable
internal fun BackupSection() {
	val context = LocalContext.current
	val scope = rememberCoroutineScope()
	val settings = SettingsRepository.get(context)
	val lastBackupAt by settings.lastBackupAt.collectAsStateWithLifecycle(initialValue = 0L)

	// 备份区状态：结果提示文本 + 恢复流程的预览（选文件解析出、确认后才执行）
	var backupResult by remember { mutableStateOf<String?>(null) }
	var restorePreview by remember { mutableStateOf<BackupSummary?>(null) }
	// 应用内备份清单：null=未打开；打开后逐项尝试解析，summary 为 null 表示该文件已损坏
	var showBackupList by remember { mutableStateOf(false) }
	var backupEntries by remember { mutableStateOf<List<Pair<BackupManager.BackupFileInfo, BackupSummary?>>>(emptyList()) }

	// 手动导出：系统文件选择器定位置，默认文件名带日期
	val exportLauncher = rememberLauncherForActivityResult(
		ActivityResultContracts.CreateDocument("application/json")
	) { uri: Uri? ->
		if (uri != null) {
			scope.launch {
				backupResult = if (BackupManager.exportToUri(context, uri)) {
					context.getString(R.string.backup_done)
				} else {
					context.getString(R.string.backup_failed)
				}
			}
		}
	}

	// 恢复第一步：选文件读文本并解析校验，坏文件给可读提示、不动任何数据
	val restoreLauncher = rememberLauncherForActivityResult(
		ActivityResultContracts.OpenDocument()
	) { uri: Uri? ->
		if (uri != null) {
			scope.launch {
				backupResult = try {
					val text = context.contentResolver.openInputStream(uri)?.use {
						it.readBytes().toString(Charsets.UTF_8)
					} ?: throw BackupFormatException("文件无法读取")
					restorePreview = BackupManager.parseAndValidate(text)
					null
				} catch (e: BackupFormatException) {
					context.getString(R.string.backup_bad_file) + "（" + e.message + "）"
				}
			}
		}
	}

	Text(
		text = if (lastBackupAt > 0) {
			stringResource(R.string.settings_last_backup_at, TimeUtil.formatDateTime(lastBackupAt))
		} else {
			stringResource(R.string.settings_last_backup_never)
		},
		style = MaterialTheme.typography.bodySmall,
		color = MaterialTheme.colorScheme.onSurfaceVariant
	)
	Text(
		text = stringResource(R.string.settings_backup_hint, 4),
		style = MaterialTheme.typography.bodySmall,
		color = MaterialTheme.colorScheme.onSurfaceVariant
	)
	Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
		Button(
			onClick = {
				backupResult = null
				val stamp = java.time.LocalDateTime.now()
					.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
				exportLauncher.launch("daily-control-backup-$stamp.json")
			},
			modifier = Modifier.weight(1f)
		) {
			Text(stringResource(R.string.settings_backup_now))
		}
		OutlinedButton(
			onClick = {
				backupResult = null
				// 先检索下载目录并逐份解析摘要，再弹应用内清单（用户不用翻系统目录）
				scope.launch {
					backupEntries = BackupManager.listDownloadBackups(context).map { info ->
						info to runCatching { BackupManager.readSummary(context, info.uri) }.getOrNull()
					}
					showBackupList = true
				}
			},
			modifier = Modifier.weight(1f)
		) {
			Text(stringResource(R.string.settings_backup_restore))
		}
	}
	backupResult?.let { result ->
		Text(
			text = result,
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.error
		)
	}

	// 应用内备份清单弹窗：列出下载目录里本应用的备份与摘要；
	// 底部保留系统文件选择器兜底（备份可能存在网盘/其他目录）
	if (showBackupList) {
		AlertDialog(
			onDismissRequest = { showBackupList = false },
			title = { Text(stringResource(R.string.backup_list_title)) },
			text = {
				Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
					if (backupEntries.isEmpty()) {
						Text(
							text = stringResource(R.string.backup_list_empty),
							style = MaterialTheme.typography.bodySmall,
							color = MaterialTheme.colorScheme.onSurfaceVariant
						)
					} else {
						backupEntries.forEach { (info, summary) ->
							Column(
								modifier = Modifier
									.fillMaxWidth()
									.clickable(enabled = summary != null) {
										showBackupList = false
										restorePreview = summary
									}
									.padding(vertical = 10.dp)
							) {
								val kindRes = if (info.displayName.startsWith("daily-control-restore-snapshot-")) {
									R.string.backup_kind_snapshot
								} else {
									R.string.backup_kind_backup
								}
								Text(
									text = stringResource(kindRes) + " · " + TimeUtil.formatDateTime(info.addedAtMillis),
									style = MaterialTheme.typography.titleSmall,
									color = if (summary != null) MaterialTheme.colorScheme.onSurface
									else MaterialTheme.colorScheme.onSurfaceVariant
								)
								Text(
									text = if (summary != null) {
										stringResource(
											R.string.backup_entry_summary,
											summary.dietRecords.size,
											summary.exerciseRecords.size,
											summary.todoTasks.size
										)
									} else {
										stringResource(R.string.backup_unreadable) + "（" + info.displayName + "）"
									},
									style = MaterialTheme.typography.bodySmall,
									color = MaterialTheme.colorScheme.onSurfaceVariant
								)
							}
						}
					}
					TextButton(onClick = {
						showBackupList = false
						restoreLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
					}) {
						Text(stringResource(R.string.backup_pick_other))
					}
				}
			},
			confirmButton = {},
			dismissButton = {
				TextButton(onClick = { showBackupList = false }) {
					Text(stringResource(R.string.action_cancel))
				}
			}
		)
	}

	// 恢复确认弹窗：展示备份内容预览，确认后执行（快照在 restore 内部第一步自动生成）
	restorePreview?.let { summary ->
		AlertDialog(
			onDismissRequest = { restorePreview = null },
			title = { Text(stringResource(R.string.backup_restore_confirm_title)) },
			text = {
				Text(
					stringResource(
						R.string.backup_restore_confirm_text,
						TimeUtil.formatDateTime(summary.exportedAt),
						summary.dietRecords.size,
						summary.nutrientItems.size,
						summary.exerciseRecords.size,
						summary.todoTasks.size
					)
				)
			},
			confirmButton = {
				TextButton(onClick = {
					val toRestore = summary
					restorePreview = null
					scope.launch {
						BackupManager.restore(context, toRestore)
						backupResult = context.getString(R.string.restore_done)
					}
				}) { Text(stringResource(R.string.action_confirm)) }
			},
			dismissButton = {
				TextButton(onClick = { restorePreview = null }) {
					Text(stringResource(R.string.action_cancel))
				}
			}
		)
	}
}
