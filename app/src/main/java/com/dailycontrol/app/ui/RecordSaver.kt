package com.dailycontrol.app.ui

import androidx.room.withTransaction
import com.dailycontrol.app.data.db.DailyControlDatabase
import com.dailycontrol.app.data.entity.DietRecord
import com.dailycontrol.app.data.entity.ExerciseRecord
import com.dailycontrol.app.data.entity.NutrientItem

// 记录保存的公共逻辑：今日页与记录页共用，避免两处各写一份事务代码（2026-10-19 页面整合批次抽取）
object RecordSaver {

	// 保存一条饮食记录及其营养素明细（同一事务：要么全成功要么全不动）
	// id 为 0 走新建（明细挂到新主记录 id），否则走编辑（明细整体替换）
	suspend fun saveDietRecordWithItems(
		db: DailyControlDatabase,
		record: DietRecord,
		nutrientItems: List<NutrientItem>
	) {
		db.withTransaction {
			if (record.id == 0L) {
				val newId = db.dietRecordDao().insert(record)
				if (nutrientItems.isNotEmpty()) {
					db.nutrientItemDao().insertAll(nutrientItems.map { it.copy(dietRecordId = newId) })
				}
			} else {
				db.dietRecordDao().update(record)
				db.nutrientItemDao().deleteByRecordId(record.id)
				if (nutrientItems.isNotEmpty()) {
					db.nutrientItemDao().insertAll(
						nutrientItems.map { it.copy(id = 0L, dietRecordId = record.id) }
					)
				}
			}
		}
	}

	// 保存一条运动记录：id 为 0 新建，否则更新
	suspend fun saveExerciseRecord(db: DailyControlDatabase, record: ExerciseRecord) {
		if (record.id == 0L) {
			db.exerciseRecordDao().insert(record)
		} else {
			db.exerciseRecordDao().update(record)
		}
	}
}
