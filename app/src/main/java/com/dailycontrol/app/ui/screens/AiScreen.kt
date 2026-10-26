package com.dailycontrol.app.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailycontrol.app.R
import com.dailycontrol.app.ai.AiAnswers
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

	// 生成中标志与结果提示（文本 + 是否成功的标记，配色用）；详情/档案/历史用选中态切换（null/false=列表态）
	var generating by remember { mutableStateOf(false) }
	var resultHint by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
	var selectedReport by remember { mutableStateOf<AiReport?>(null) }
	var showProfile by remember { mutableStateOf(false) }
	var showHistory by remember { mutableStateOf(false) }

	// 懒检查兜底：进页发现昨日报告缺失（闹钟被吞/刚配好接口/恢复备份后）时静默补生成；
	// 与凌晨闹钟共用生成器内部的单飞锁，并发触发时后到者按水位线自动跳过
	LaunchedEffect(configured) {
		if (configured) {
			generating = true
			AiReportGenerator.generateIfDue(context)
			generating = false
		}
	}

	// 详情/档案/历史是页内状态而非导航路由：拦截系统返回键先关闭子视图，
	// 否则会直接退出 AI 页（用户在详情里按返回的直觉是回列表）
	BackHandler(enabled = selectedReport != null || showProfile || showHistory) {
		selectedReport = null
		showProfile = false
		showHistory = false
	}

	val selected = selectedReport
	if (selected != null) {
		AiReportDetail(report = selected, onBack = { selectedReport = null })
		return
	}
	if (showProfile) {
		AiProfileView(onBack = { showProfile = false })
		return
	}
	if (showHistory) {
		AiHistoryView(reports = reports, onBack = { showHistory = false })
		return
	}

	// 追问统计：横幅计数与「点击直达最新含未答问题的报告」共用一套判定（问题无对应非空答案即未答）
	fun unansweredQuestionsOf(row: AiReport): Int {
		val questions = AiAnswers.parseQuestions(row.questionsJson)
		val answered = AiAnswers.parseAnswers(row.answersJson).filter { it.answer.isNotBlank() }
		return questions.count { question -> answered.none { it.question == question.text } }
	}
	val unansweredCount = reports.sumOf { unansweredQuestionsOf(it) }
	val latestWithUnanswered = reports.firstOrNull { unansweredQuestionsOf(it) > 0 }

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
			// 追问横幅：有未答问题时置顶提醒，点击直达最新含未答问题的报告详情
			if (unansweredCount > 0 && latestWithUnanswered != null) {
				Card(
					modifier = Modifier.fillMaxWidth().clickable { selectedReport = latestWithUnanswered },
					colors = CardDefaults.cardColors(
						containerColor = MaterialTheme.colorScheme.secondaryContainer,
						contentColor = MaterialTheme.colorScheme.onSecondaryContainer
					)
				) {
					Column(
						modifier = Modifier.fillMaxWidth().padding(16.dp),
						verticalArrangement = Arrangement.spacedBy(4.dp)
					) {
						Text(
							text = stringResource(R.string.ai_qa_banner_title, unansweredCount),
							style = MaterialTheme.typography.titleMedium
						)
						Text(
							text = stringResource(R.string.ai_qa_banner_hint),
							style = MaterialTheme.typography.bodySmall
						)
					}
				}
			}
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
			Row(
				modifier = Modifier.fillMaxWidth(),
				verticalAlignment = Alignment.CenterVertically
			) {
				Text(
					text = stringResource(R.string.ai_report_list_title),
					style = MaterialTheme.typography.titleMedium,
					modifier = Modifier.weight(1f)
				)
				// 档案与问答历史入口（0.4.1）
				TextButton(onClick = { showProfile = true }) {
					Text(stringResource(R.string.ai_profile_entry))
				}
				TextButton(onClick = { showHistory = true }) {
					Text(stringResource(R.string.ai_history_entry))
				}
			}
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

// 建议条目：主题词 + 级别 + 行动摘要（headline，常显）+ 展开说明（detail）+ 依据（basis）。
// headline 为本批新增字段，旧报告缺失时由解析层回退为旧 content 正文
private data class SuggestionItem(
	val topic: String,
	val headline: String,
	val detail: String,
	val level: String,
	val basis: String
)

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
			// 总评：关键要点分点卡（模型按分号分隔输出要点；旧报告一句话无分隔符时自然单行）
			if (report.summary.isNotBlank()) {
				Card(
					modifier = Modifier.fillMaxWidth(),
					colors = CardDefaults.cardColors(
						containerColor = MaterialTheme.colorScheme.primaryContainer,
						contentColor = MaterialTheme.colorScheme.onPrimaryContainer
					)
				) {
					Column(
						modifier = Modifier.fillMaxWidth().padding(16.dp),
						verticalArrangement = Arrangement.spacedBy(6.dp)
					) {
						Text(
							text = stringResource(R.string.ai_section_summary),
							style = MaterialTheme.typography.labelMedium
						)
						splitSummaryPoints(report.summary).forEach { point ->
							Text(text = "· $point", style = MaterialTheme.typography.titleMedium)
						}
					}
				}
			}
			// 建议板块（默认收起，展开显示逐条建议卡）
			if (suggestions.isNotEmpty()) {
				CollapsibleSection(title = stringResource(R.string.ai_section_suggestions)) {
					suggestions.forEach { suggestion ->
						SuggestionCard(suggestion = suggestion)
					}
				}
			}
			// 追问板块（自带折叠头；无问题不渲染）
			AiQaSection(report = report)
			// 分析板块：饮食与运动两小节，默认收起（两段都空则整个板块不渲染）
			if (report.dietAnalysis.isNotBlank() || report.exerciseAnalysis.isNotBlank()) {
				CollapsibleSection(title = stringResource(R.string.ai_section_analysis)) {
					if (report.dietAnalysis.isNotBlank()) {
						SubSectionText(
							subTitle = stringResource(R.string.ai_analysis_diet_sub),
							body = report.dietAnalysis
						)
					}
					if (report.exerciseAnalysis.isNotBlank()) {
						SubSectionText(
							subTitle = stringResource(R.string.ai_analysis_exercise_sub),
							body = report.exerciseAnalysis
						)
					}
				}
			}
		}

		// 对话记录板块：发送数据与 AI 原文两小节，默认收起（格式异常的降级报告也可查）
		CollapsibleSection(title = stringResource(R.string.ai_section_dialog_log)) {
			SubSectionText(
				subTitle = stringResource(R.string.ai_sent_payload),
				body = report.sentPayload
			)
			SubSectionText(
				subTitle = stringResource(R.string.ai_raw_response),
				body = report.rawResponse
			)
		}
		Spacer(modifier = Modifier.height(8.dp))
	}
}

// ===== AI 追问机制（0.4.1）：详情追问板块 / 个人档案 / 问答历史 =====

// 追问板块：每题一卡，未答显示输入框、已答显示答案与提交状态；保存即落库
// （本地态与数据库同步：作答后 Flow 重发同 id 报告，remember(report.id) 不重置，两边一致）
@Composable
private fun AiQaSection(report: AiReport) {
	val questions = remember(report.id) { AiAnswers.parseQuestions(report.questionsJson) }
	if (questions.isEmpty()) {
		return
	}
	val context = LocalContext.current
	val scope = rememberCoroutineScope()
	var answers by remember(report.id) { mutableStateOf(AiAnswers.parseAnswers(report.answersJson)) }

	Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
		CollapsibleSection(title = stringResource(R.string.ai_qa_section_title)) {
			questions.forEach { question ->
				QaQuestionCard(
					question = question,
					current = answers.firstOrNull { it.question == question.text },
					onSave = { answerText ->
						answers = upsertAnswer(answers, question, answerText)
						scope.launch {
							DailyControlDatabase.get(context).aiReportDao()
								.updateAnswers(report.id, AiAnswers.answersToJson(answers))
						}
					},
					onDelete = {
						answers = answers.filterNot { it.question == question.text }.toMutableList()
						scope.launch {
							DailyControlDatabase.get(context).aiReportDao()
								.updateAnswers(report.id, AiAnswers.answersToJson(answers))
						}
					}
				)
			}
		}
	}
}

// 单题卡：类型徽标 + 问题正文；未答/编辑态是输入框，已答态是答案 + 状态 + 修改/删除
@Composable
private fun QaQuestionCard(
	question: AiAnswers.QuestionItem,
	current: AiAnswers.AnswerItem?,
	onSave: (String) -> Unit,
	onDelete: () -> Unit
) {
	val answered = current != null && current.answer.isNotBlank()
	var editing by remember(question.text) { mutableStateOf(!answered) }
	var text by remember(question.text) { mutableStateOf(current?.answer ?: "") }
	Card(modifier = Modifier.fillMaxWidth()) {
		Column(
			modifier = Modifier.fillMaxWidth().padding(16.dp),
			verticalArrangement = Arrangement.spacedBy(8.dp)
		) {
			Text(
				text = stringResource(
					if (question.type == "profile") R.string.ai_qa_type_profile else R.string.ai_qa_type_oneoff
				),
				style = MaterialTheme.typography.labelSmall,
				color = if (question.type == "profile") {
					MaterialTheme.colorScheme.tertiary
				} else {
					MaterialTheme.colorScheme.onSurfaceVariant
				}
			)
			Text(text = question.text, style = MaterialTheme.typography.bodyLarge)
			if (editing) {
				OutlinedTextField(
					value = text,
					onValueChange = { text = it },
					label = { Text(stringResource(R.string.ai_qa_answer_hint)) },
					modifier = Modifier.fillMaxWidth()
				)
				Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
					Button(
						onClick = {
							onSave(text.trim())
							editing = false
						},
						enabled = text.isNotBlank()
					) {
						Text(stringResource(R.string.ai_qa_answer_save))
					}
					if (answered) {
						TextButton(onClick = {
							text = current!!.answer
							editing = false
						}) {
							Text(stringResource(R.string.ai_qa_answer_cancel))
						}
					}
				}
			} else {
				Text(text = current!!.answer, style = MaterialTheme.typography.bodyMedium)
				Text(
					text = if (current.submittedEpochDay > 0) {
						stringResource(
							R.string.ai_qa_status_submitted,
							TimeUtil.formatDate(LocalDate.ofEpochDay(current.submittedEpochDay))
						)
					} else {
						stringResource(R.string.ai_qa_status_pending)
					},
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)
				Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
					TextButton(onClick = { editing = true }) {
						Text(stringResource(R.string.ai_qa_answer_edit))
					}
					TextButton(onClick = onDelete) {
						Text(stringResource(R.string.ai_qa_answer_delete))
					}
				}
			}
		}
	}
}

// 作答落库前的列表更新：同题覆盖（保留提交标记），新答追加
private fun upsertAnswer(
	items: List<AiAnswers.AnswerItem>,
	question: AiAnswers.QuestionItem,
	answerText: String
): MutableList<AiAnswers.AnswerItem> {
	val existing = items.firstOrNull { it.question == question.text }
	return if (existing == null) {
		(items + AiAnswers.AnswerItem(
			question = question.text,
			type = question.type,
			answer = answerText,
			answeredAt = System.currentTimeMillis(),
			submittedEpochDay = 0L
		)).toMutableList()
	} else {
		items.map {
			if (it.question == question.text) {
				it.copy(answer = answerText, answeredAt = System.currentTimeMillis())
			} else {
				it
			}
		}.toMutableList()
	}
}

// 个人档案：档案型追问答案的长期存档，可改可删（改 = 按 topic 覆盖回写）
@Composable
private fun AiProfileView(onBack: () -> Unit) {
	val context = LocalContext.current
	val scope = rememberCoroutineScope()
	val settings = SettingsRepository.get(context)
	val profileJson by settings.aiProfileJson.collectAsStateWithLifecycle(initialValue = "[]")
	val items = remember(profileJson) { AiAnswers.parseProfile(profileJson) }

	Column(
		modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
		verticalArrangement = Arrangement.spacedBy(16.dp)
	) {
		Row(verticalAlignment = Alignment.CenterVertically) {
			IconButton(onClick = onBack) {
				Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.ai_detail_back))
			}
			Text(text = stringResource(R.string.ai_profile_title), style = MaterialTheme.typography.titleLarge)
		}
		if (items.isEmpty()) {
			Text(
				text = stringResource(R.string.ai_profile_empty),
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)
		} else {
			items.forEach { item ->
				ProfileItemCard(
					item = item,
					onSave = { newAnswer ->
						scope.launch {
							settings.setAiProfileJson(
								AiAnswers.profileToJson(AiAnswers.mergeProfileItem(items, item.topic, item.question, newAnswer))
							)
						}
					},
					onDelete = {
						scope.launch {
							settings.setAiProfileJson(
								AiAnswers.profileToJson(items.filterNot { it.topic == item.topic })
							)
						}
					}
				)
			}
		}
	}
}

// 单条档案卡：主题 + 当初的问题（小字）+ 答案 + 更新时间，可编辑可删除
@Composable
private fun ProfileItemCard(
	item: AiAnswers.ProfileItem,
	onSave: (String) -> Unit,
	onDelete: () -> Unit
) {
	var editing by remember { mutableStateOf(false) }
	var text by remember(item.answer) { mutableStateOf(item.answer) }
	Card(modifier = Modifier.fillMaxWidth()) {
		Column(
			modifier = Modifier.fillMaxWidth().padding(16.dp),
			verticalArrangement = Arrangement.spacedBy(4.dp)
		) {
			Text(text = item.topic, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
			if (item.question.isNotBlank()) {
				Text(
					text = item.question,
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)
			}
			if (editing) {
				OutlinedTextField(
					value = text,
					onValueChange = { text = it },
					modifier = Modifier.fillMaxWidth()
				)
				Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
					Button(onClick = { onSave(text.trim()); editing = false }, enabled = text.isNotBlank()) {
						Text(stringResource(R.string.action_save))
					}
					TextButton(onClick = { text = item.answer; editing = false }) {
						Text(stringResource(R.string.ai_qa_answer_cancel))
					}
				}
			} else {
				Text(text = item.answer, style = MaterialTheme.typography.bodyMedium)
				Text(
					text = stringResource(R.string.ai_profile_updated_at, TimeUtil.formatDateTime(item.updatedAt)),
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)
				Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
					TextButton(onClick = { editing = true }) {
						Text(stringResource(R.string.ai_qa_answer_edit))
					}
					TextButton(onClick = onDelete) {
						Text(stringResource(R.string.ai_qa_answer_delete))
					}
				}
			}
		}
	}
}

// 问答历史：全部报告的已答条目按作答时间倒序，含提交状态标注
@Composable
private fun AiHistoryView(reports: List<AiReport>, onBack: () -> Unit) {
	data class HistoryEntry(val reportDateEpochDay: Long, val answer: AiAnswers.AnswerItem)
	val entries = remember(reports) {
		reports.flatMap { row ->
			AiAnswers.parseAnswers(row.answersJson)
				.filter { it.answer.isNotBlank() }
				.map { HistoryEntry(row.reportDateEpochDay, it) }
		}.sortedByDescending { it.answer.answeredAt }
	}

	Column(
		modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
		verticalArrangement = Arrangement.spacedBy(12.dp)
	) {
		Row(verticalAlignment = Alignment.CenterVertically) {
			IconButton(onClick = onBack) {
				Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.ai_detail_back))
			}
			Text(text = stringResource(R.string.ai_history_title), style = MaterialTheme.typography.titleLarge)
		}
		if (entries.isEmpty()) {
			Text(
				text = stringResource(R.string.ai_history_empty),
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)
		} else {
			entries.forEach { entry ->
				Card(modifier = Modifier.fillMaxWidth()) {
					Column(
						modifier = Modifier.fillMaxWidth().padding(16.dp),
						verticalArrangement = Arrangement.spacedBy(4.dp)
					) {
						Text(
							text = stringResource(
								if (entry.answer.type == "profile") R.string.ai_qa_type_profile else R.string.ai_qa_type_oneoff
							),
							style = MaterialTheme.typography.labelSmall,
							color = MaterialTheme.colorScheme.onSurfaceVariant
						)
						Text(text = entry.answer.question, style = MaterialTheme.typography.bodyMedium)
						Text(
							text = entry.answer.answer,
							style = MaterialTheme.typography.bodyMedium,
							color = MaterialTheme.colorScheme.primary
						)
						Text(
							text = (if (entry.answer.submittedEpochDay > 0) {
								stringResource(
									R.string.ai_qa_status_submitted,
									TimeUtil.formatDate(LocalDate.ofEpochDay(entry.answer.submittedEpochDay))
								)
							} else {
								stringResource(R.string.ai_qa_status_pending)
							}) + " · " + TimeUtil.formatDateTime(entry.answer.answeredAt),
							style = MaterialTheme.typography.bodySmall,
							color = MaterialTheme.colorScheme.onSurfaceVariant
						)
					}
				}
			}
		}
	}
}

// 建议卡：按等级做视觉分流——「提醒」级整卡警示色容器（扫一眼先看到），
// 「普通」级低饱和底色弱化。常显只有一行行动摘要（headline），「先做什么」；
// 展开说明与依据默认折叠（「再为什么」），点「展开详情」按需查看——列表保持一屏可扫。
// 旧报告（headline 由旧 content 回退而来、无 detail）自动退化为单行显示，不报错
@Composable
private fun SuggestionCard(suggestion: SuggestionItem) {
	val isAlert = suggestion.level == "提醒"
	var expanded by remember { mutableStateOf(false) }
	val hasDetail = suggestion.detail.isNotBlank() || suggestion.basis.isNotBlank()
	Card(
		modifier = Modifier.fillMaxWidth(),
		colors = CardDefaults.cardColors(
			containerColor = if (isAlert) {
				MaterialTheme.colorScheme.errorContainer
			} else {
				MaterialTheme.colorScheme.surfaceVariant
			},
			contentColor = if (isAlert) {
				MaterialTheme.colorScheme.onErrorContainer
			} else {
				MaterialTheme.colorScheme.onSurfaceVariant
			}
		)
	) {
		Column(
			modifier = Modifier.fillMaxWidth().animateContentSize().padding(16.dp),
			verticalArrangement = Arrangement.spacedBy(4.dp)
		) {
			Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
				Text(
					text = suggestion.topic,
					style = MaterialTheme.typography.titleSmall,
					color = if (isAlert) {
						MaterialTheme.colorScheme.error
					} else {
						MaterialTheme.colorScheme.primary
					}
				)
				Text(
					text = suggestion.level,
					style = MaterialTheme.typography.labelSmall
				)
			}
			Text(text = suggestion.headline, style = MaterialTheme.typography.bodyLarge)
			if (hasDetail) {
				Row(
					modifier = Modifier.fillMaxWidth()
						.clickable { expanded = !expanded }
						.padding(top = 2.dp),
					horizontalArrangement = Arrangement.spacedBy(4.dp),
					verticalAlignment = Alignment.CenterVertically
				) {
					Text(
						text = stringResource(if (expanded) R.string.ai_suggestion_collapse else R.string.ai_suggestion_expand),
						style = MaterialTheme.typography.labelSmall
					)
					Icon(
						imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
						contentDescription = null,
						modifier = Modifier.size(16.dp)
					)
				}
				if (expanded) {
					if (suggestion.detail.isNotBlank()) {
						Text(
							text = suggestion.detail,
							style = MaterialTheme.typography.bodySmall
						)
					}
					if (suggestion.basis.isNotBlank()) {
						Text(
							text = stringResource(R.string.ai_suggestion_basis_label, suggestion.basis),
							style = MaterialTheme.typography.bodySmall,
							color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f)
						)
					}
				}
			}
		}
	}
}

// 折叠板块：标题行（主色+箭头）整行可点，展开显示该板块的卡片们；默认收起——
// 报告详情四板块（建议/追问/分析/对话记录）统一用它，页面结构一眼可辨
@Composable
private fun CollapsibleSection(title: String, content: @Composable () -> Unit) {
	var expanded by remember { mutableStateOf(false) }
	Column(modifier = Modifier.fillMaxWidth().animateContentSize()) {
		Row(
			modifier = Modifier.fillMaxWidth()
				.clickable { expanded = !expanded }
				.padding(vertical = 10.dp),
			verticalAlignment = Alignment.CenterVertically
		) {
			Text(
				text = title,
				style = MaterialTheme.typography.titleMedium,
				color = MaterialTheme.colorScheme.primary,
				modifier = Modifier.weight(1f)
			)
			Icon(
				imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
				contentDescription = null,
				tint = MaterialTheme.colorScheme.onSurfaceVariant
			)
		}
		if (expanded) {
			Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
				content()
			}
		}
	}
}

// 折叠板块内的小节：小标题（次要色）+ 小字正文——分析板块的饮食/运动、
// 对话记录板块的发送数据/AI 原文都用它
@Composable
private fun SubSectionText(subTitle: String, body: String) {
	Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
		Text(
			text = subTitle,
			style = MaterialTheme.typography.titleSmall,
			color = MaterialTheme.colorScheme.onSurfaceVariant
		)
		Text(text = body, style = MaterialTheme.typography.bodySmall)
	}
}

// 总评分点：模型按中文分号分隔输出要点；兼容旧报告的一句话（无分隔符则整段单行）
private fun splitSummaryPoints(summary: String): List<String> {
	return summary.split('；', ';', '\n').map { it.trim() }.filter { it.isNotEmpty() }
}

// 建议列表解析：坏字段跳过，不拖垮整页；headline/detail 为新字段，
// 旧数据只有 content 时回退为 headline（detail 为空 → 界面退化为单行显示）
private fun parseSuggestions(suggestionsJson: String): List<SuggestionItem> {
	return try {
		val array = JSONArray(suggestionsJson)
		List(array.length()) { index ->
			val item = array.optJSONObject(index)
			val legacyContent = item?.optString("content", "") ?: ""
			SuggestionItem(
				topic = item?.optString("topic", "") ?: "",
				headline = (item?.optString("headline", "") ?: "").ifBlank { legacyContent },
				detail = item?.optString("detail", "") ?: "",
				level = item?.optString("level", "普通") ?: "普通",
				basis = item?.optString("basis", "") ?: ""
			)
		}.filter { it.headline.isNotBlank() }
	} catch (e: Exception) {
		emptyList()
	}
}
