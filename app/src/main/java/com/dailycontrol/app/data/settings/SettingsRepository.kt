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

	// 背单词最近一次「今日已背」的确认日期（ISO 格式），与今天比对即得完成状态
	val vocabDoneDate: Flow<String> = context.dataStore.data.map { prefs ->
		prefs[KEY_VOCAB_DONE_DATE] ?: ""
	}

	// 最近一次保存「明日规划」的日期：22:00 提醒与 23:00 二次提醒据此判断当晚是否已规划
	val lastPlannedDate: Flow<String> = context.dataStore.data.map { prefs ->
		prefs[KEY_LAST_PLANNED_DATE] ?: ""
	}

	// 起床唤醒计划：目标日期 + 各声的触发时刻（重启自愈时按时刻重排）
	val wakePlanDate: Flow<String> = context.dataStore.data.map { prefs ->
		prefs[KEY_WAKE_DATE] ?: ""
	}

	val wakePlanTimes: Flow<List<Long>> = context.dataStore.data.map { prefs ->
		(prefs[KEY_WAKE_TIMES] ?: "")
			.split(",")
			.filter { it.isNotBlank() }
			.map { it.toLong() }
	}

	// 用户确认「时钟闹钟已开」的唤醒日期：应用读不到系统时钟，靠自记收起提醒入口；
	// 与 wakePlanDate 不一致（新一天的计划）时入口自动重新出现
	val wakeClockConfirmedDate: Flow<String> = context.dataStore.data.map { prefs ->
		prefs[KEY_WAKE_CLOCK_CONFIRMED] ?: ""
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

	suspend fun setVocabDoneDate(dateIso: String) {
		context.dataStore.edit { it[KEY_VOCAB_DONE_DATE] = dateIso }
	}

	suspend fun setLastPlannedDate(dateIso: String) {
		context.dataStore.edit { it[KEY_LAST_PLANNED_DATE] = dateIso }
	}

	// 空日期表示清除起床计划
	suspend fun setWakePlan(dateIso: String, timesMillis: List<Long>) {
		context.dataStore.edit { prefs ->
			prefs[KEY_WAKE_DATE] = dateIso
			prefs[KEY_WAKE_TIMES] = timesMillis.joinToString(",")
		}
	}

	suspend fun setWakeClockConfirmedDate(dateIso: String) {
		context.dataStore.edit { it[KEY_WAKE_CLOCK_CONFIRMED] = dateIso }
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
		private val KEY_VOCAB_DONE_DATE = stringPreferencesKey("vocab_done_date")
		private val KEY_LAST_PLANNED_DATE = stringPreferencesKey("last_planned_date")
		private val KEY_WAKE_DATE = stringPreferencesKey("wake_date")
		private val KEY_WAKE_TIMES = stringPreferencesKey("wake_times")
		private val KEY_WAKE_CLOCK_CONFIRMED = stringPreferencesKey("wake_clock_confirmed")

		@Volatile
		private var instance: SettingsRepository? = null

		fun get(context: Context): SettingsRepository {
			return instance ?: synchronized(this) {
				instance ?: SettingsRepository(context.applicationContext).also { instance = it }
			}
		}
	}
}
