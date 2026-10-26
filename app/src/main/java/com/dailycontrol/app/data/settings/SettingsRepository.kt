package com.dailycontrol.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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

	// 最近一次备份（手动或自动）的时刻：设置页展示用；0 表示从未备份过
	val lastBackupAt: Flow<Long> = context.dataStore.data.map { prefs ->
		prefs[KEY_LAST_BACKUP_AT] ?: 0L
	}

	// 下一次自动备份的计划时刻（周排程的「下一棒」存档）：
	// 启动自愈按它幂等重排，防止「每次启动一律 now+7天」把备份无限推后
	val nextAutoBackupAt: Flow<Long> = context.dataStore.data.map { prefs ->
		prefs[KEY_NEXT_AUTO_BACKUP] ?: 0L
	}

	// 首启权限引导是否已完成（完成或跳过都算）：false 时应用以向导整屏替代主界面。
	// 有意不入备份快照——换机/恢复备份后向导重现一次，新环境权限本就要重新配
	val onboardingDone: Flow<Boolean> = context.dataStore.data.map { prefs ->
		prefs[KEY_ONBOARDING_DONE] ?: false
	}

	// ===== AI 接口与报告状态（0.4.0 第四阶段）=====
	// 三要素：地址 / Key / 模型名，三者都非空视为 AI 功能已启用。
	// Key 红线：只存本机私有目录（卸载应用即清除）、不随备份导出、只发往用户所填的这一个地址

	val aiBaseUrl: Flow<String> = context.dataStore.data.map { prefs ->
		prefs[KEY_AI_BASE_URL] ?: ""
	}

	val aiApiKey: Flow<String> = context.dataStore.data.map { prefs ->
		prefs[KEY_AI_API_KEY] ?: ""
	}

	val aiModel: Flow<String> = context.dataStore.data.map { prefs ->
		prefs[KEY_AI_MODEL] ?: ""
	}

	// 报告水位线：最后一份「成功」报告覆盖的日期（epochDay）；0 = 从未生成过。
	// 由它判定生成模式：无值=首次（附近30天明细）、等于昨天=日常、更早=中断补交
	val aiLastReportEpochDay: Flow<Long> = context.dataStore.data.map { prefs ->
		prefs[KEY_AI_LAST_REPORT_EPOCH_DAY] ?: 0L
	}

	// 最近一次尝试的失败原因与时刻（AI 页状态卡展示；成功时错误文本清空）
	val aiLastErrorText: Flow<String> = context.dataStore.data.map { prefs ->
		prefs[KEY_AI_LAST_ERROR_TEXT] ?: ""
	}

	val aiLastAttemptAt: Flow<Long> = context.dataStore.data.map { prefs ->
		prefs[KEY_AI_LAST_ATTEMPT_AT] ?: 0L
	}

	// 个人档案 JSON（0.4.1）：AI 档案型追问的答案按主题覆盖存档，随每次报告携带
	// 结构 [{topic,question,answer,updatedAt}]；同 ai_* 键约定不入备份
	val aiProfileJson: Flow<String> = context.dataStore.data.map { prefs ->
		prefs[KEY_AI_PROFILE_JSON] ?: "[]"
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

	suspend fun setLastBackupAt(millis: Long) {
		context.dataStore.edit { it[KEY_LAST_BACKUP_AT] = millis }
	}

	suspend fun setNextAutoBackupAt(millis: Long) {
		context.dataStore.edit { it[KEY_NEXT_AUTO_BACKUP] = millis }
	}

	suspend fun setOnboardingDone(done: Boolean) {
		context.dataStore.edit { it[KEY_ONBOARDING_DONE] = done }
	}

	suspend fun setAiBaseUrl(url: String) {
		context.dataStore.edit { it[KEY_AI_BASE_URL] = url }
	}

	suspend fun setAiApiKey(key: String) {
		context.dataStore.edit { it[KEY_AI_API_KEY] = key }
	}

	suspend fun setAiModel(model: String) {
		context.dataStore.edit { it[KEY_AI_MODEL] = model }
	}

	suspend fun setAiLastReportEpochDay(epochDay: Long) {
		context.dataStore.edit { it[KEY_AI_LAST_REPORT_EPOCH_DAY] = epochDay }
	}

	suspend fun setAiLastErrorText(text: String) {
		context.dataStore.edit { it[KEY_AI_LAST_ERROR_TEXT] = text }
	}

	suspend fun setAiLastAttemptAt(millis: Long) {
		context.dataStore.edit { it[KEY_AI_LAST_ATTEMPT_AT] = millis }
	}

	suspend fun setAiProfileJson(json: String) {
		context.dataStore.edit { it[KEY_AI_PROFILE_JSON] = json }
	}

	// ===== 备份支持：设置的全量快照与还原 =====
	// 键名与 DataStore 键一一对应（字符串化），备份文件里人类可读；
	// last_backup_at 属于运行痕迹，不入备份

	suspend fun backupSettings(): Map<String, String> {
		val prefs = context.dataStore.data.first()
		return buildMap {
			put("theme_mode", prefs[KEY_THEME_MODE] ?: "system")
			put("breakfast_minute", (prefs[KEY_BREAKFAST_MINUTE] ?: 570).toString())
			put("lunch_minute", (prefs[KEY_LUNCH_MINUTE] ?: 810).toString())
			put("dinner_minute", (prefs[KEY_DINNER_MINUTE] ?: 1170).toString())
			put("plan_minute", (prefs[KEY_PLAN_MINUTE] ?: 1320).toString())
			put("vocab_package", prefs[KEY_VOCAB_PACKAGE] ?: "")
			put("vocab_done_date", prefs[KEY_VOCAB_DONE_DATE] ?: "")
			put("last_planned_date", prefs[KEY_LAST_PLANNED_DATE] ?: "")
			put("wake_date", prefs[KEY_WAKE_DATE] ?: "")
			put("wake_times", prefs[KEY_WAKE_TIMES] ?: "")
			put("wake_clock_confirmed", prefs[KEY_WAKE_CLOCK_CONFIRMED] ?: "")
		}
	}

	// 逐键还原：只写备份里存在的键，类型不符的键跳过（坏备份不损坏现有配置）
	suspend fun restoreSettings(values: Map<String, String>) {
		context.dataStore.edit { prefs ->
			values["theme_mode"]?.let { prefs[KEY_THEME_MODE] = it }
			values["breakfast_minute"]?.toIntOrNull()?.let { prefs[KEY_BREAKFAST_MINUTE] = it }
			values["lunch_minute"]?.toIntOrNull()?.let { prefs[KEY_LUNCH_MINUTE] = it }
			values["dinner_minute"]?.toIntOrNull()?.let { prefs[KEY_DINNER_MINUTE] = it }
			values["plan_minute"]?.toIntOrNull()?.let { prefs[KEY_PLAN_MINUTE] = it }
			values["vocab_package"]?.let { prefs[KEY_VOCAB_PACKAGE] = it }
			values["vocab_done_date"]?.let { prefs[KEY_VOCAB_DONE_DATE] = it }
			values["last_planned_date"]?.let { prefs[KEY_LAST_PLANNED_DATE] = it }
			values["wake_date"]?.let { prefs[KEY_WAKE_DATE] = it }
			values["wake_times"]?.let { prefs[KEY_WAKE_TIMES] = it }
			values["wake_clock_confirmed"]?.let { prefs[KEY_WAKE_CLOCK_CONFIRMED] = it }
		}
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
		private val KEY_LAST_BACKUP_AT = longPreferencesKey("last_backup_at")
		private val KEY_NEXT_AUTO_BACKUP = longPreferencesKey("next_auto_backup")
		private val KEY_ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
		// AI 接口（0.4.0）：全部刻意不入 backupSettings/restoreSettings——
		// Key 属敏感信息不随备份导出（安全红线）；水位线与错误文本属运行痕迹，恢复后按新环境重建
		private val KEY_AI_BASE_URL = stringPreferencesKey("ai_base_url")
		private val KEY_AI_API_KEY = stringPreferencesKey("ai_api_key")
		private val KEY_AI_MODEL = stringPreferencesKey("ai_model")
		private val KEY_AI_LAST_REPORT_EPOCH_DAY = longPreferencesKey("ai_last_report_epoch_day")
		private val KEY_AI_LAST_ERROR_TEXT = stringPreferencesKey("ai_last_error_text")
		private val KEY_AI_LAST_ATTEMPT_AT = longPreferencesKey("ai_last_attempt_at")
		private val KEY_AI_PROFILE_JSON = stringPreferencesKey("ai_profile_json")

		@Volatile
		private var instance: SettingsRepository? = null

		fun get(context: Context): SettingsRepository {
			return instance ?: synchronized(this) {
				instance ?: SettingsRepository(context.applicationContext).also { instance = it }
			}
		}
	}
}
