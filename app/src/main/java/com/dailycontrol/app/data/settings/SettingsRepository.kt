package com.dailycontrol.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// 应用级设置仓库：目前只有主题模式，后续提醒时间、AI 接口等配置都在此扩展
class SettingsRepository private constructor(private val context: Context) {

	// 主题模式的响应式读取：界面订阅后，写入会自动推送新值
	val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
		ThemeMode.fromStorage(prefs[KEY_THEME_MODE])
	}

	suspend fun setThemeMode(mode: ThemeMode) {
		context.dataStore.edit { prefs ->
			prefs[KEY_THEME_MODE] = mode.storageName
		}
	}

	companion object {
		// DataStore 实例必须全局唯一，用顶层委托挂在 Context 上由系统保证
		private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

		private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")

		@Volatile
		private var instance: SettingsRepository? = null

		fun get(context: Context): SettingsRepository {
			return instance ?: synchronized(this) {
				instance ?: SettingsRepository(context.applicationContext).also { instance = it }
			}
		}
	}
}
