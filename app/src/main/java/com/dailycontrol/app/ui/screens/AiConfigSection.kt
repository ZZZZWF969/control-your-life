package com.dailycontrol.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailycontrol.app.R
import com.dailycontrol.app.ai.AiClient
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
	}
}
