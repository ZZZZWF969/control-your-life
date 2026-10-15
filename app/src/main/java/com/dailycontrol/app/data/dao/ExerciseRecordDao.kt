package com.dailycontrol.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.dailycontrol.app.data.entity.ExerciseRecord

// 运动记录的数据访问接口：挂起函数由 Room 自动放到后台线程执行
@Dao
interface ExerciseRecordDao {

	@Insert
	suspend fun insert(record: ExerciseRecord): Long

	@Query("SELECT COUNT(*) FROM exercise_record")
	suspend fun count(): Int
}
