package com.dailycontrol.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

// 待办任务表：晚间规划（或当日加记）的任务
// startAtMillis 为空表示只记录不提醒；提醒时刻 = startAtMillis - remindAheadMinutes
@Entity(tableName = "todo_task")
data class TodoTask(
	@PrimaryKey(autoGenerate = true) val id: Long = 0,
	val taskDateEpochDay: Long,   // 任务所属日期（LocalDate.toEpochDay）
	val title: String,            // 任务名，如「背单词」「交作业」
	val startAtMillis: Long?,     // 开始时间（epoch 毫秒），null=无时间仅记录
	val remindAheadMinutes: Int,  // 提前提醒的分钟数
	val done: Boolean = false,    // 完成标记（当日列表可勾选）
	val createdAt: Long           // 创建时间
)
