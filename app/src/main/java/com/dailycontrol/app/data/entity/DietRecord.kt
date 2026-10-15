package com.dailycontrol.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

// 饮食记录表：一餐正餐或一次零食/营养品摄入占一行
@Entity(tableName = "diet_record")
data class DietRecord(
	@PrimaryKey(autoGenerate = true) val id: Long = 0,
	val mealType: String,    // 餐别：breakfast / lunch / dinner / snack / supplement
	val foodText: String,    // 食材内容，自由文本
	val portionText: String, // 饭量的粗略估计，自由文本
	val eatenAt: Long,       // 进食时间，epoch 毫秒
	val recordedAt: Long     // 录入时间，epoch 毫秒
)
