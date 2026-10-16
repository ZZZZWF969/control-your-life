package com.dailycontrol.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

// 饮食记录表：一餐正餐或一次零食/营养品摄入占一行
@Entity(tableName = "diet_record")
data class DietRecord(
	@PrimaryKey(autoGenerate = true) val id: Long = 0,
	val mealType: String,    // 餐别：breakfast / lunch / dinner / snack / supplement
	val foodText: String,    // 食材内容（营养品则为品名），自由文本
	val portionText: String, // 饭量的粗略估计：少/适中/多，仅三餐使用
	val eatenAt: Long,       // 进食时间，epoch 毫秒
	val recordedAt: Long,    // 录入时间，epoch 毫秒
	val weightText: String? = null,  // 零食/营养品的重量或容量，如“30g”“250ml”
	val nutrientText: String? = null // 营养品的营养素明细自由文本，如“维生素D3 800IU；钙 300mg”
)
