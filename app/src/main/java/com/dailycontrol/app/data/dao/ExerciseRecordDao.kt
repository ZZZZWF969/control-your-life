package com.dailycontrol.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.dailycontrol.app.data.entity.ExerciseRecord
import kotlinx.coroutines.flow.Flow

// 运动记录的数据访问接口：挂起函数由 Room 自动放到后台线程执行
@Dao
interface ExerciseRecordDao {

	@Insert
	suspend fun insert(record: ExerciseRecord): Long

	@Update
	suspend fun update(record: ExerciseRecord)

	@Delete
	suspend fun delete(record: ExerciseRecord)

	@Query("SELECT COUNT(*) FROM exercise_record")
	suspend fun count(): Int

	// 查询某天（时间区间内，按开始时间归日）的运动记录；返回 Flow 使界面在数据变化时自动刷新
	@Query("SELECT * FROM exercise_record WHERE startAt BETWEEN :startMillis AND :endMillis ORDER BY startAt ASC")
	fun observeBetween(startMillis: Long, endMillis: Long): Flow<List<ExerciseRecord>>
}
