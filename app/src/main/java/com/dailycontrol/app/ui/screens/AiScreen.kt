package com.dailycontrol.app.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailycontrol.app.R
import com.dailycontrol.app.ai.AiReportGenerator
import com.dailycontrol.app.data.db.DailyControlDatabase
import com.dailycontrol.app.data.entity.AiReport
import com.dailycontrol.app.data.settings.SettingsRepository
import com.dailycontrol.app.util.TimeUtil
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.time.LocalDate

// AI 页：未配置时引导去设置；已配置时显示生成状态、立即生成按钮与 14 天报告列表，
// 点报告进入详情（总评/饮食/运动/建议 + 原始返回与发送数据的折叠自查入口）。
// 详情为页内两级视图（选中态切换），不新增导航路由
@Composable
fun AiScreen(onGoToSettings: () -> Unit) {
	val context = LocalContext.current
	val scope = rememberCoroutineScope()
	val settings = SettingsRepository.get(context)
	val db = remember { DailyControlDatabase.get(context) }

	val baseUrl by settings.aiBaseUrl.collectAsStateWithLifecycle(initialValue = "")
	val apiKey by settings.aiApiKey.collectAsStateWithLifecycle(initialValue = "")
	val modelName by settings.aiModel.collectAsStateWithLifecycle(initialValue = "")
	val lastReportEpochDay by settings.aiLastReportEpochDay.collectAsStateWithLifecycle(initialValue = 0L)
	val lastError by settings.aiLastErrorText.collectAsStateWithLifecycle(initialValue = "")

	val configured = baseUrl.isNotBlank() && apiKey.isNotBlank() && modelName.isNotBlank()
	val today = remember { LocalDate.now() }
	val reports by db.aiReportDao().observeRecent(today.toEpochDay() - 13)
		.collectAsStateWithLifecycle(initialValue = emptyList())

	// 生成中标志与结果提示（文本 + 是否成功的标记，配色用）；详情用选中报告切换（null=列表态）
	var generating by remember { mutableStateOf(false) }
	var resultHint by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
	var selectedReport by remember { mutableStateOf<AiReport?>(null) }

	// 懒检查兜底：进页发现昨日报告缺失（闹钟被吞/刚配好接口/恢复备份后）时静默补生成；
	// 与凌晨闹钟共用生成器内部的单飞锁，并发触发时后到者按水位线自动跳过
	LaunchedEffect(configured) {
		if (configured) {
			generating = true
			AiReportGenerator.generateIfDue(context)
			generating = false
		}
	}

	val selected = selectedReport
	if (selected != null) {
		AiReportDetail(report = selected, onBack = { selectedReport = null })
		return
	}

	Column(
		modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
		verticalArrangement = Arrangement.spacedBy(16.dp)
	) {
		Text(text = stringResource(R.string.tab_ai), style = MaterialTheme.typography.headlineMedium)

		if (!configured) {
			// 未配置：引导卡（AI 功能对配置三要素的依赖直接讲清楚）
			Card(modifier = Modifier.fillMaxWidth()) {
				Column(
					modifier = Modifier.fillMaxWidth().padding(20.dp),
					verticalArrangement = Arrangement.spacedBy(12.dp)
				) {
					Text(
						text = stringResource(R.string.ai_unconfigured_title),
						style = MaterialTheme.typography.titleMedium
					)
					Text(
						text = stringResource(R.string.ai_unconfigured_hint),
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
					Button(onClick = onGoToSettings) {
						Text(stringResource(R.string.ai_go_settings))
					}
				}
			}
		} else {
			// 状态卡：上次报告 + 自动排程说明 + 最近失败原因（如有）
			Card(modifier = Modifier.fillMaxWidth()) {
				Column(
					modifier = Modifier.fillMaxWidth().padding(20.dp),
					verticalArrangement = Arrangement.spacedBy(8.dp)
				) {
					Text(
						text = if (lastReportEpochDay > 0) {
							stringResource(
								R.string.ai_status_last_report,
								TimeUtil.formatDate(LocalDate.ofEpochDay(lastReportEpochDay))
							)
						} else {
							stringResource(R.string.ai_status_never)
						},
						style = MaterialTheme.typography.titleSmall
					)
					Text(
						text = stringResource(R.string.ai_status_next),
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
					if (lastError.isNotBlank()) {
						Text(
							text = lastError,
							style = MaterialTheme.typography.bodySmall,
							color = MaterialTheme.colorScheme.error
						)
					}
				}
			}

			// 立即生成：手动触发（调试与补看主力；凌晨自动排程在批次三接入）
			Button(
				onClick = {
					generating = true
					resultHint = null
					scope.launch {
						val outcome = AiReportGenerator.generateNow(context)
						generating = false
						resultHint = when (outcome) {
							AiReportGenerator.Outcome.NotConfigured ->
								context.getString(R.string.ai_unconfigured_title) to false
							AiReportGenerator.Outcome.AlreadyUpToDate ->
								context.getString(R.string.ai_generate_uptodate) to false
							is AiReportGenerator.Outcome.Failed ->
								context.getString(R.string.ai_generate_fail, outcome.reasonText) to false
							is AiReportGenerator.Outcome.Success -> {
								val dateText = TimeUtil.formatDate(LocalDate.ofEpochDay(outcome.targetDateEpochDay))
								if (outcome.parseOk) {
									context.getString(R.string.ai_generate_done, dateText) to true
								} else {
									context.getString(R.string.ai_generate_done_raw, dateText) to true
								}
							}
						}
					}
				},
				enabled = !generating
			) {
				if (generating) {
					CircularProgressIndicator(
						modifier = Modifier.size(16.dp),
						strokeWidth = 2.dp,
						color = MaterialTheme.colorScheme.onPrimary
					)
					Spacer(modifier = Modifier.width(8.dp))
				}
				Text(stringResource(R.string.ai_generate_now))
			}
			if (generating) {
				Text(
					text = stringResource(R.string.ai_generating),
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)
			}
			resultHint?.let { (hint, success) ->
				Text(
					text = hint,
					style = MaterialTheme.typography.bodySmall,
					color = if (success) {
						MaterialTheme.colorScheme.primary
					} else {
						MaterialTheme.colorScheme.error
					}
				)
			}

			HorizontalDivider()
			Text(
				text = stringResource(R.string.ai_report_list_title),
				style = MaterialTheme.typography.titleMedium
			)
			if (reports.isEmpty()) {
				Text(
					text = stringResource(R.string.ai_report_empty),
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)
			} else {
				reports.forEach { report ->
					ReportListRow(report = report, onClick = { selectedReport = report })
				}
			}
		}
	}
}

// 报告列表行：日期 + 首份/回顾徽标 + 总评（或格式异常降级文案）
@Composable
private fun ReportListRow(report: AiReport, onClick: () -> Unit) {
	Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
		Column(
			modifier = Modifier.fillMaxWidth().padding(16.dp),
			verticalArrangement = Arrangement.spacedBy(6.dp)
		) {
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.spacedBy(8.dp),
				verticalAlignment = Alignment.CenterVertically
			) {
				Text(
					text = TimeUtil.formatDate(LocalDate.ofEpochDay(report.reportDateEpochDay)),
					style = MaterialTheme.typography.titleSmall
				)
				if (report.kind == "first") {
					BadgeText(stringResource(R.string.ai_report_badge_first))
				}
				if (report.kind == "backfill") {
					BadgeText(stringResource(R.string.ai_report_badge_backfill))
				}
				if (!report.parseOk) {
					BadgeText(stringResource(R.string.ai_report_parse_bad))
				}
			}
			Text(
				text = if (report.parseOk && report.summary.isNotBlank()) {
					report.summary
				} else {
					stringResource(R.string.ai_report_summary_fallback)
				},
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				maxLines = 2,
				overflow = TextOverflow.Ellipsis
			)
		}
	}
}

// 小徽标文本（列表行的「首份/回顾/格式异常」标记）
@Composable
private fun BadgeText(text: String) {
	Text(
		text = text,
		style = MaterialTheme.typography.labelSmall,
		color = MaterialTheme.colorScheme.primary
	)
}

// 建议条目：主题词 + 级别 + 正文
private data class SuggestionItem(val topic: String, val content: String, val level: String)

@Composable
private fun AiReportDetail(report: AiReport, onBack: () -> Unit) {
	// 建议列表解析一次即可，随报告行缓存
	val suggestions = remember(report.id) { parseSuggestions(report.suggestionsJson) }

	Column(
		modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
		verticalArrangement = Arrangement.spacedBy(16.dp)
	) {
		Row(verticalAlignment = Alignment.CenterVertically) {
			IconButton(onClick = onBack) {
				Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.ai_detail_back))
			}
			Text(
				text = TimeUtil.formatDate(LocalDate.ofEpochDay(report.reportDateEpochDay)),
				style = MaterialTheme.typography.titleLarge
			)
		}

		if (!report.parseOk) {
			// 格式异常降级：原文直接展示，数据不丢
			Card(modifier = Modifier.fillMaxWidth()) {
				Column(
					modifier = Modifier.fillMaxWidth().padding(20.dp),
					verticalArrangement = Arrangement.spacedBy(8.dp)
				) {
					Text(
						text = stringResource(R.string.ai_raw_parse_bad_hint),
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
					Text(text = report.rawResponse, style = MaterialTheme.typography.bodyMedium)
				}
			}
		} else {
			SectionCard(title = stringResource(R.string.ai_section_summary), body = report.summary)
			if (report.dietAnalysis.isNotBlank()) {
				SectionCard(title = stringResource(R.string.ai_section_diet), body = report.dietAnalysis)
			}
			if (report.exerciseAnalysis.isNotBlank()) {
				SectionCard(title = stringResource(R.string.ai_section_exercise), body = report.exerciseAnalysis)
			}
			if (suggestions.isNotEmpty()) {
				Text(
					text = stringResource(R.string.ai_section_suggestions),
					style = MaterialTheme.typography.titleMedium
				)
				suggestions.forEach { suggestion ->
					Card(modifier = Modifier.fillMaxWidth()) {
						Column(
							modifier = Modifier.fillMaxWidth().padding(16.dp),
							verticalArrangement = Arrangement.spacedBy(4.dp)
						) {
							Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
								Text(
									text = suggestion.topic,
									style = MaterialTheme.typography.titleSmall,
									color = MaterialTheme.colorScheme.primary
								)
								Text(
									text = suggestion.level,
									style = MaterialTheme.typography.labelSmall,
									color = if (suggestion.level == "提醒") {
										MaterialTheme.colorScheme.error
									} else {
										MaterialTheme.colorScheme.onSurfaceVariant
									}
								)
							}
							Text(text = suggestion.content, style = MaterialTheme.typography.bodyMedium)
						}
					}
				}
			}
		}

		// 两个原始查看入口：AI 回复原文 + 本次发送的数据（隐私自查承诺的可验证入口）
		ExpandableTextCard(
			title = stringResource(R.string.ai_raw_response),
			body = report.rawResponse
		)
		ExpandableTextCard(
			title = stringResource(R.string.ai_sent_payload),
			body = report.sentPayload
		)
		Spacer(modifier = Modifier.height(8.dp))
	}
}

// 板块卡：标题（主题色小标题）+ 正文
@Composable
private fun SectionCard(title: String, body: String) {
	if (body.isBlank()) {
		return
	}
	Card(modifier = Modifier.fillMaxWidth()) {
		Column(
			modifier = Modifier.fillMaxWidth().padding(20.dp),
			verticalArrangement = Arrangement.spacedBy(8.dp)
		) {
			Text(text = title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
			Text(text = body, style = MaterialTheme.typography.bodyMedium)
		}
	}
}

// 折叠文本卡：默认收起，点标题展开（原始返回 / 发送数据可能很长，收起时不碍眼）
@Composable
private fun ExpandableTextCard(title: String, body: String) {
	var expanded by remember { mutableStateOf(false) }
	Card(modifier = Modifier.fillMaxWidth()) {
		Column(modifier = Modifier.fillMaxWidth().animateContentSize()) {
			Row(
				modifier = Modifier.fillMaxWidth()
					.clickable { expanded = !expanded }
					.padding(16.dp),
				verticalAlignment = Alignment.CenterVertically
			) {
				Text(
					text = title,
					style = MaterialTheme.typography.titleSmall,
					modifier = Modifier.weight(1f)
				)
				Icon(
					imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
					contentDescription = null,
					tint = MaterialTheme.colorScheme.onSurfaceVariant
				)
			}
			if (expanded) {
				Text(
					text = body,
					style = MaterialTheme.typography.bodySmall,
					modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
				)
			}
		}
	}
}

// 建议列表解析：坏字段跳过，不拖垮整页
private fun parseSuggestions(suggestionsJson: String): List<SuggestionItem> {
	return try {
		val array = JSONArray(suggestionsJson)
		List(array.length()) { index ->
			val item = array.optJSONObject(index)
			SuggestionItem(
				topic = item?.optString("topic", "") ?: "",
				content = item?.optString("content", "") ?: "",
				level = item?.optString("level", "普通") ?: "普通"
			)
		}.filter { it.content.isNotBlank() }
	} catch (e: Exception) {
		emptyList()
	}
}
