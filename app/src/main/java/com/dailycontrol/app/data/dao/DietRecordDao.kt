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

	// 统计某天某餐别的记录数：三餐询问到点时判断「该餐是否已记录」
	@Query(
		"SELECT COUNT(*) FROM diet_record " +
			"WHERE mealType = :mealType AND eatenAt BETWEEN :startMillis AND :endMillis"
	)
	suspend fun countBetween(mealType: String, startMillis: Long, endMillis: Long): Int

	// 查询某天（时间区间内）的饮食记录及其营养素明细；返回 Flow 使界面在数据变化时自动刷新
	@Transaction
	@Query("SELECT * FROM diet_record WHERE eatenAt BETWEEN :startMillis AND :endMillis ORDER BY eatenAt ASC")
	fun observeBetween(startMillis: Long, endMillis: Long): Flow<List<DietRecordWithItems>>

	// AI 汇总支持：时间区间内的一次性读取（带营养素明细），供提示词组装，无需响应式刷新
	@Transaction
	@Query("SELECT * FROM diet_record WHERE eatenAt BETWEEN :startMillis AND :endMillis ORDER BY eatenAt ASC")
	suspend fun getBetweenOnce(startMillis: Long, endMillis: Long): List<DietRecordWithItems>

	// ===== 备份支持：全表一次性读取与清空（清空必须先于子表 nutrient_item，见外键） =====

	@Query("SELECT * FROM diet_record")
	suspend fun getAllOnce(): List<DietRecord>

	@Insert
	suspend fun insertAll(records: List<DietRecord>)

	@Query("DELETE FROM diet_record")
	suspend fun clearAll()
}
