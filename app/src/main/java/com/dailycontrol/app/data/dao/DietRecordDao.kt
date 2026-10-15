package com.dailycontrol.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.dailycontrol.app.data.entity.DietRecord

// 饮食记录的数据访问接口：挂起函数由 Room 自动放到后台线程执行
@Dao
interface DietRecordDao {

	@Insert
	suspend fun insert(record: DietRecord): Long

	@Query("SELECT COUNT(*) FROM diet_record")
	suspend fun count(): Int
}
