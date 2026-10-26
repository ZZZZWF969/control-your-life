package com.dailycontrol.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailycontrol.app.R
import com.dailycontrol.app.ai.AiClient
import com.dailycontrol.app.ai.AiPromptBuilder
import com.dailycontrol.app.data.settings.SettingsRepository
import com.dailycontrol.app.schedule.AiReportPlanner
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

// 设置页的「AI 接口」分组：地址 / Key（掩码）/ 模型名三要素 + 测试连接。
// 三项都非空即视为 AI 功能已启用（每日自动排程在批次三接入）
@Composable
fun AiConfigSection() {
	val context = LocalContext.current
	val scope = rememberCoroutineScope()
	val settings = SettingsRepository.get(context)

	// 与背单词包名一致的「本地编辑优先」模式：避免 DataStore 回流打断输入
	val savedUrl by settings.aiBaseUrl.collectAsStateWithLifecycle(initialValue = "")
	val savedKey by settings.aiApiKey.collectAsStateWithLifecycle(initialValue = "")
	val savedModel by settings.aiModel.collectAsStateWithLifecycle(initialValue = "")
	var editingUrl by remember { mutableStateOf<String?>(null) }
	var editingKey by remember { mutableStateOf<String?>(null) }
	var editingModel by remember { mutableStateOf<String?>(null) }

	// Key 默认掩码显示，「显示/隐藏」按钮切换
	var keyVisible by remember { mutableStateOf(false) }

	// 测试连接：testing=true 进行中；testResult 为结果文本（成功/失败各一种措辞）
	var testing by remember { mutableStateOf(false) }
	var testResult by remember { mutableStateOf<String?>(null) }

	val urlText = editingUrl ?: savedUrl
	val keyText = editingKey ?: savedKey
	val modelText = editingModel ?: savedModel

	// 三要素「齐整↔残缺」切换时重排/撤销凌晨 4:00 的报告闹钟（齐整状态下改字段不影响闹钟存在性）
	LaunchedEffect(Unit) {
		snapshotFlow { savedUrl.isNotBlank() && savedKey.isNotBlank() && savedModel.isNotBlank() }
			.distinctUntilChanged()
			.collect { AiReportPlanner.reschedule(context) }
	}

	Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
		OutlinedTextField(
			value = urlText,
			onValueChange = { value ->
				editingUrl = value
				scope.launch { settings.setAiBaseUrl(value) }
			},
			label = { Text(stringResource(R.string.ai_url_label)) },
			supportingText = { Text(stringResource(R.string.ai_url_hint)) },
			singleLine = true,
			modifier = Modifier.fillMaxWidth()
		)
		OutlinedTextField(
			value = keyText,
			onValueChange = { value ->
				editingKey = value
				scope.launch { settings.setAiApiKey(value) }
			},
			label = { Text(stringResource(R.string.ai_key_label)) },
			singleLine = true,
			visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
			trailingIcon = {
				TextButton(onClick = { keyVisible = !keyVisible }) {
					Text(stringResource(if (keyVisible) R.string.ai_key_visibility_hide else R.string.ai_key_visibility_show))
				}
			},
			modifier = Modifier.fillMaxWidth()
		)
		OutlinedTextField(
			value = modelText,
			onValueChange = { value ->
				editingModel = value
				scope.launch { settings.setAiModel(value) }
			},
			label = { Text(stringResource(R.string.ai_model_label)) },
			supportingText = { Text(stringResource(R.string.ai_model_hint)) },
			singleLine = true,
			modifier = Modifier.fillMaxWidth()
		)
		OutlinedButton(
			onClick = {
				testing = true
				testResult = null
				val url = urlText.trim()
				val key = keyText.trim()
				val model = modelText.trim()
				scope.launch {
					val result = AiClient.testConnection(url, key, model)
					testing = false
					testResult = when (result) {
						is AiClient.CallResult.Success -> context.getString(R.string.ai_test_ok)
						is AiClient.CallResult.HttpError ->
							context.getString(R.string.ai_test_fail, "HTTP ${result.httpCode}，${result.reasonText}")
						is AiClient.CallResult.NetworkError ->
							context.getString(R.string.ai_test_fail, result.reasonText)
					}
				}
			},
			// 三要素填齐才可测，避免必然失败的请求
			enabled = !testing && urlText.isNotBlank() && keyText.isNotBlank() && modelText.isNotBlank()
		) {
			Text(stringResource(if (testing) R.string.ai_test_running else R.string.ai_test_connection))
		}
		testResult?.let { result ->
			Text(
				text = result,
				style = MaterialTheme.typography.bodySmall,
				color = if (result == context.getString(R.string.ai_test_ok)) {
					MaterialTheme.colorScheme.primary
				} else {
					MaterialTheme.colorScheme.error
				}
			)
		}
		Text(
			text = stringResource(R.string.ai_security_note),
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.onSurfaceVariant
		)

		// 提示词定制（高级）：非空保存后整体覆盖内置 system 提示词，立即影响下一次报告；
		// 只覆盖这一层，喂给 AI 的数据组装（JSON 报文契约）不受影响
		var promptExpanded by remember { mutableStateOf(false) }
		Column(modifier = Modifier.fillMaxWidth()) {
			Row(
				modifier = Modifier.fillMaxWidth()
					.clickable { promptExpanded = !promptExpanded }
					.padding(vertical = 8.dp),
				verticalAlignment = Alignment.CenterVertically
			) {
				Text(
					text = stringResource(R.string.ai_prompt_custom_title),
					style = MaterialTheme.typography.titleSmall,
					modifier = Modifier.weight(1f)
				)
				Icon(
					imageVector = if (promptExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
					contentDescription = null,
					tint = MaterialTheme.colorScheme.onSurfaceVariant
				)
			}
			if (promptExpanded) {
				PromptCustomEditor(settings = settings)
			}
		}
	}
}

// 提示词编辑器：预填当前生效文本（自定义 or 内置默认）；保存即自定义、恢复默认一键清空。
// 定制文本随备份导出（用户资产）；改坏输出约定的后果由宽容解析兜底（原文保存不丢数据）
@Composable
private fun PromptCustomEditor(settings: SettingsRepository) {
	val context = LocalContext.current
	val scope = rememberCoroutineScope()
	val savedCustom by settings.aiCustomSystemPrompt.collectAsStateWithLifecycle(initialValue = "")
	val defaultText = remember { AiPromptBuilder.buildFullDefaultText() }
	var editing by remember { mutableStateOf<String?>(null) }
	var savedHint by remember { mutableStateOf(false) }
	var resetHint by remember { mutableStateOf(false) }
	// 契约被改动的红色二次确认：true 时弹出警告框，确认后才真正保存
	var contractDanger by remember { mutableStateOf(false) }
	// 编辑框显示：正在编辑的文本 > 已存自定义 > 内置默认
	val fieldText = editing ?: savedCustom.ifBlank { defaultText }

	Text(
		text = stringResource(R.string.ai_prompt_custom_hint),
		style = MaterialTheme.typography.bodySmall,
		color = MaterialTheme.colorScheme.onSurfaceVariant
	)
	OutlinedTextField(
		value = fieldText,
		onValueChange = { editing = it },
		minLines = 6,
		modifier = Modifier.fillMaxWidth()
	)
	Text(
		text = stringResource(R.string.ai_prompt_custom_length, fieldText.length),
		style = MaterialTheme.typography.bodySmall,
		color = MaterialTheme.colorScheme.onSurfaceVariant
	)
	// 统一的真正保存动作（二次确认通过或契约未动时到达这里）
	fun doSave(text: String) {
		scope.launch {
			settings.setAiCustomSystemPrompt(text.trim())
			editing = null
			savedHint = true
			resetHint = false
		}
	}
	Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
		Button(
			onClick = {
				if (AiPromptBuilder.keepsContract(fieldText)) {
					doSave(fieldText)
				} else {
					// JSON 输出契约被改动：红色警告二次确认后才能保存
					contractDanger = true
				}
			},
			enabled = fieldText.isNotBlank() && fieldText.trim() != savedCustom
		) {
			Text(stringResource(R.string.action_save))
		}
		OutlinedButton(
			onClick = {
				scope.launch {
					settings.setAiCustomSystemPrompt("")
					editing = defaultText
					resetHint = true
					savedHint = false
				}
			},
			// 已是默认时无需恢复
			enabled = savedCustom.isNotBlank()
		) {
			Text(stringResource(R.string.ai_prompt_custom_reset))
		}
	}
	if (savedHint) {
		Text(
			text = stringResource(R.string.ai_prompt_custom_saved),
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.primary
		)
	}
	if (resetHint) {
		Text(
			text = stringResource(R.string.ai_prompt_custom_reset_done),
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.primary
		)
	}
	// 红色警告二次确认：JSON 输出契约被改动时拦截保存，明确后果后才放行
	if (contractDanger) {
		androidx.compose.material3.AlertDialog(
			onDismissRequest = { contractDanger = false },
			title = {
				Text(
					text = stringResource(R.string.ai_prompt_danger_title),
					color = MaterialTheme.colorScheme.error
				)
			},
			text = {
				Text(
					text = stringResource(R.string.ai_prompt_danger_text),
					color = MaterialTheme.colorScheme.error
				)
			},
			confirmButton = {
				TextButton(onClick = {
					contractDanger = false
					doSave(fieldText)
				}) {
					Text(
						text = stringResource(R.string.ai_prompt_danger_confirm),
						color = MaterialTheme.colorScheme.error
					)
				}
			},
			dismissButton = {
				TextButton(onClick = { contractDanger = false }) {
					Text(stringResource(R.string.action_cancel))
				}
			}
		)
	}
}
