package com.dailycontrol.app.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// 营养素明细行：挂在某条饮食记录（营养品）下，如「VA 800μg」「钙 300mg」
// 删除所属饮食记录时级联删除，避免孤儿数据
@Entity(
	tableName = "nutrient_item",
	foreignKeys = [ForeignKey(
		entity = DietRecord::class,
		parentColumns = ["id"],
		childColumns = ["dietRecordId"],
		onDelete = ForeignKey.CASCADE
	)],
	indices = [Index("dietRecordId")]
)
data class NutrientItem(
	@PrimaryKey(autoGenerate = true) val id: Long = 0,
	val dietRecordId: Long, // 所属饮食记录 id
	val name: String,       // 成分名，如：VA、钙、维生素D3
	val amountText: String  // 含量，如：800μg、300mg、800IU
)
