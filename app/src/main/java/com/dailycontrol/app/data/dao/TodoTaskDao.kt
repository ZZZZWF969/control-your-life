package com.dailycontrol.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.dailycontrol.app.data.entity.TodoTask
import kotlinx.coroutines.flow.Flow

// 待办任务的数据访问接口
@Dao
interface TodoTaskDao {

	@Insert
	suspend fun insert(task: TodoTask): Long

	@Insert
	suspend fun insertAll(tasks: List<TodoTask>): List<Long>

	@Update
	suspend fun update(task: TodoTask)

	@Delete
	suspend fun delete(task: TodoTask)

	// 删某日全部「未完成」任务：规划页重复保存时整体替换草稿用
	@Query("DELETE FROM todo_task WHERE taskDateEpochDay = :epochDay AND done = 0")
	suspend fun deleteUndoneOfDate(epochDay: Long)

	@Query("SELECT * FROM todo_task WHERE taskDateEpochDay = :epochDay ORDER BY startAtMillis IS NULL, startAtMillis ASC")
	fun observeByDate(epochDay: Long): Flow<List<TodoTask>>

	// 查询仍需提醒的任务：未完成、有时间、提醒时刻尚未过去（重启自愈重排用）
	@Query(
		"SELECT * FROM todo_task " +
			"WHERE done = 0 AND startAtMillis IS NOT NULL " +
			"AND (startAtMillis - remindAheadMinutes * 60000) > :nowMillis"
	)
	suspend fun pendingReminders(nowMillis: Long): List<TodoTask>

	// 闹钟到点后按 requestCode 反查任务（id = requestCode - TODO_TASK_BASE）
	@Query("SELECT * FROM todo_task WHERE id = :taskId LIMIT 1")
	suspend fun getByReminderRequest(taskId: Long): TodoTask?

	// 一次性取某日全部任务（规划页载入草稿/替换旧任务用）
	@Query("SELECT * FROM todo_task WHERE taskDateEpochDay = :epochDay ORDER BY startAtMillis IS NULL, startAtMillis ASC")
	suspend fun getByDate(epochDay: Long): List<TodoTask>
}
