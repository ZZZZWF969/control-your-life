package com.dailycontrol.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.dailycontrol.app.data.dao.DietRecordDao
import com.dailycontrol.app.data.dao.ExerciseRecordDao
import com.dailycontrol.app.data.entity.DietRecord
import com.dailycontrol.app.data.entity.ExerciseRecord

// Room 数据库：当前只包含饮食与运动两张表，后续阶段在此追加新表并递增 version
@Database(
	entities = [DietRecord::class, ExerciseRecord::class],
	version = 1,
	exportSchema = true
)
abstract class DailyControlDatabase : RoomDatabase() {

	abstract fun dietRecordDao(): DietRecordDao

	abstract fun exerciseRecordDao(): ExerciseRecordDao

	companion object {
		// 单例入口：全应用共享同一个数据库连接，避免重复打开的开销与竞争
		@Volatile
		private var instance: DailyControlDatabase? = null

		fun get(context: Context): DailyControlDatabase {
			return instance ?: synchronized(this) {
				instance ?: Room.databaseBuilder(
					context.applicationContext,
					DailyControlDatabase::class.java,
					"daily_control.db"
				).build().also { instance = it }
			}
		}
	}
}
