package com.dailycontrol.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.dailycontrol.app.data.entity.NutrientItem

// 营养素明细行的数据访问接口
@Dao
interface NutrientItemDao {

	@Insert
	suspend fun insertAll(items: List<NutrientItem>)

	// 保存前先清空该记录的旧明细，再整批写入（整体替换策略）
	@Query("DELETE FROM nutrient_item WHERE dietRecordId = :recordId")
	suspend fun deleteByRecordId(recordId: Long)
}
