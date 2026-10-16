package com.dailycontrol.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.dailycontrol.app.data.dao.DietRecordDao
import com.dailycontrol.app.data.dao.ExerciseRecordDao
import com.dailycontrol.app.data.dao.NutrientItemDao
import com.dailycontrol.app.data.entity.DietRecord
import com.dailycontrol.app.data.entity.ExerciseRecord
import com.dailycontrol.app.data.entity.NutrientItem

// Room 数据库：饮食记录（含营养素明细子表）与运动记录，后续阶段在此追加新表并递增 version
@Database(
	entities = [DietRecord::class, ExerciseRecord::class, NutrientItem::class],
	version = 3,
	exportSchema = true
)
abstract class DailyControlDatabase : RoomDatabase() {

	abstract fun dietRecordDao(): DietRecordDao

	abstract fun exerciseRecordDao(): ExerciseRecordDao

	abstract fun nutrientItemDao(): NutrientItemDao

	companion object {
		// 1→2：饮食记录新增「重量/容量」与「营养素明细」两列（零食与营养品需要）
		private val MIGRATION_1_2 = object : Migration(1, 2) {
			override fun migrate(db: SupportSQLiteDatabase) {
				db.execSQL("ALTER TABLE diet_record ADD COLUMN weightText TEXT")
				db.execSQL("ALTER TABLE diet_record ADD COLUMN nutrientText TEXT")
			}
		}

		// 2→3：新增营养素明细子表（结构与 Room 期望的建表语句严格一致，含外键级联与索引）
		private val MIGRATION_2_3 = object : Migration(2, 3) {
			override fun migrate(db: SupportSQLiteDatabase) {
				db.execSQL(
					"CREATE TABLE IF NOT EXISTS `nutrient_item` (" +
						"`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
						"`dietRecordId` INTEGER NOT NULL, " +
						"`name` TEXT NOT NULL, " +
						"`amountText` TEXT NOT NULL, " +
						"FOREIGN KEY(`dietRecordId`) REFERENCES `diet_record`(`id`) " +
						"ON UPDATE NO ACTION ON DELETE CASCADE )"
				)
				db.execSQL("CREATE INDEX IF NOT EXISTS `index_nutrient_item_dietRecordId` ON `nutrient_item` (`dietRecordId`)")
			}
		}

		// 单例入口：全应用共享同一个数据库连接，避免重复打开的开销与竞争
		@Volatile
		private var instance: DailyControlDatabase? = null

		fun get(context: Context): DailyControlDatabase {
			return instance ?: synchronized(this) {
				instance ?: Room.databaseBuilder(
					context.applicationContext,
					DailyControlDatabase::class.java,
					"daily_control.db"
				).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
			}
		}
	}
}
