package com.dailycontrol.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.dailycontrol.app.data.entity.DietRecord
import com.dailycontrol.app.data.entity.DietRecordWithItems
import kotlinx.coroutines.flow.Flow

// 饮食记录的数据访问接口：挂起函数由 Room 自动放到后台线程执行
@Dao
interface DietRecordDao {

	@Insert
	suspend fun insert(record: DietRecord): Long

	@Update
	suspend fun update(record: DietRecord)

	@Delete
	suspend fun delete(record: DietRecord)

	@Query("SELECT COUNT(*) FROM diet_record")
	suspend fun count(): Int

	// 查询某天（时间区间内）的饮食记录及其营养素明细；返回 Flow 使界面在数据变化时自动刷新
	@Transaction
	@Query("SELECT * FROM diet_record WHERE eatenAt BETWEEN :startMillis AND :endMillis ORDER BY eatenAt ASC")
	fun observeBetween(startMillis: Long, endMillis: Long): Flow<List<DietRecordWithItems>>
}
