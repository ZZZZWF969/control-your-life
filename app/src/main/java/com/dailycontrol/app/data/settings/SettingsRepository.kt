package com.dailycontrol.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// 提醒时刻配置：以「当日分钟数」存储（如 570 = 9:30），展示层再转 HH:mm
data class ReminderTimes(
	val breakfastMinute: Int,
	val lunchMinute: Int,
	val dinnerMinute: Int,
	val planMinute: Int
)

// 应用级设置仓库：主题模式、提醒时刻、跳转应用等配置都在此扩展
class SettingsRepository private constructor(private val context: Context) {

	// 主题模式的响应式读取：界面订阅后，写入会自动推送新值
	val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
		ThemeMode.fromStorage(prefs[KEY_THEME_MODE])
	}

	// 提醒时刻（默认：早 9:30 / 午 13:30 / 晚 19:30 / 规划 22:00）
	val reminderTimes: Flow<ReminderTimes> = context.dataStore.data.map { prefs ->
		ReminderTimes(
			breakfastMinute = prefs[KEY_BREAKFAST_MINUTE] ?: 570,
			lunchMinute = prefs[KEY_LUNCH_MINUTE] ?: 810,
			dinnerMinute = prefs[KEY_DINNER_MINUTE] ?: 1170,
			planMinute = prefs[KEY_PLAN_MINUTE] ?: 1320
		)
	}

	// 背单词跳转目标的应用包名；空串表示未配置（真机联调时用 adb 读取后填入）
	val vocabPackage: Flow<String> = context.dataStore.data.map { prefs ->
		prefs[KEY_VOCAB_PACKAGE] ?: ""
	}

	suspend fun setThemeMode(mode: ThemeMode) {
		context.dataStore.edit { prefs ->
			prefs[KEY_THEME_MODE] = mode.storageName
		}
	}

	suspend fun setBreakfastMinute(minute: Int) {
		context.dataStore.edit { it[KEY_BREAKFAST_MINUTE] = minute }
	}

	suspend fun setLunchMinute(minute: Int) {
		context.dataStore.edit { it[KEY_LUNCH_MINUTE] = minute }
	}

	suspend fun setDinnerMinute(minute: Int) {
		context.dataStore.edit { it[KEY_DINNER_MINUTE] = minute }
	}

	suspend fun setPlanMinute(minute: Int) {
		context.dataStore.edit { it[KEY_PLAN_MINUTE] = minute }
	}

	suspend fun setVocabPackage(packageName: String) {
		context.dataStore.edit { it[KEY_VOCAB_PACKAGE] = packageName }
	}

	companion object {
		// DataStore 实例必须全局唯一，用顶层委托挂在 Context 上由系统保证
		private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

		private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
		private val KEY_BREAKFAST_MINUTE = intPreferencesKey("breakfast_minute")
		private val KEY_LUNCH_MINUTE = intPreferencesKey("lunch_minute")
		private val KEY_DINNER_MINUTE = intPreferencesKey("dinner_minute")
		private val KEY_PLAN_MINUTE = intPreferencesKey("plan_minute")
		private val KEY_VOCAB_PACKAGE = stringPreferencesKey("vocab_package")

		@Volatile
		private var instance: SettingsRepository? = null

		fun get(context: Context): SettingsRepository {
			return instance ?: synchronized(this) {
				instance ?: SettingsRepository(context.applicationContext).also { instance = it }
			}
		}
	}
}
