package com.dailycontrol.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

// 运动记录表：一次运动占一行，例如「慢跑 17:30-18:00 中等强度 5km」
@Entity(tableName = "exercise_record")
data class ExerciseRecord(
	@PrimaryKey(autoGenerate = true) val id: Long = 0,
	val typeText: String,       // 运动类型，如“慢跑”
	val intensityText: String,  // 强度的粗略描述
	val totalText: String,      // 总量描述，如“5km”
	val startAt: Long,          // 开始时间，epoch 毫秒
	val endAt: Long,            // 结束时间，epoch 毫秒
	val recordedAt: Long        // 录入时间，epoch 毫秒
)
