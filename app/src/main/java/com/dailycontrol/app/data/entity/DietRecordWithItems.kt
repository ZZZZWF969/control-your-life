package com.dailycontrol.app.data.entity

import androidx.room.Embedded
import androidx.room.Relation

// 饮食记录 + 其营养素明细行的组合查询结果：列表页一次查询即可拿到卡片所需的全部数据
data class DietRecordWithItems(
	@Embedded val record: DietRecord,
	@Relation(parentColumn = "id", entityColumn = "dietRecordId")
	val nutrientItems: List<NutrientItem>
)
