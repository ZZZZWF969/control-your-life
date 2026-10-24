package com.dailycontrol.app.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// AI 报告表：一次成功生成占一行，按「报告归属日」唯一（同一天重新生成则整行替换）
// 归属日通常 = 数据被汇总的那一天（昨日）；中断补交的回顾报告 = 缺口的最末一天
@Entity(tableName = "ai_report", indices = [Index(value = ["reportDateEpochDay"], unique = true)])
data class AiReport(
	@PrimaryKey(autoGenerate = true) val id: Long = 0,
	val reportDateEpochDay: Long, // 报告归属日（LocalDate.toEpochDay）
	val kind: String,             // first=首份 / daily=日常 / backfill=中断补交的回顾报告
	val generatedAt: Long,        // 生成时刻，epoch 毫秒
	val summary: String,          // 一句话总评
	val dietAnalysis: String,     // 饮食板块分析正文
	val exerciseAnalysis: String, // 运动板块分析正文
	val suggestionsJson: String,  // 建议列表的 JSON 文本：[{topic,content,level}]
	val questionsJson: String,    // AI 追问列表（0.4.1 启用，本版恒为空数组）
	val parseOk: Boolean,         // 返回是否成功拆解；false 时界面降级展示 rawResponse
	val rawResponse: String,      // AI 原始返回全文（排查问题与降级展示的兜底）
	val sentPayload: String       // 本次发送给 AI 的完整数据文本（「发送了什么」自查入口）
)
