package com.dailycontrol.app.data.settings

import androidx.annotation.StringRes
import com.dailycontrol.app.R

// 主题模式：SYSTEM 跟随系统，LIGHT / DARK 为用户手动指定
enum class ThemeMode(val storageName: String, @StringRes val labelRes: Int) {
	SYSTEM("system", R.string.theme_mode_system),
	LIGHT("light", R.string.theme_mode_light),
	DARK("dark", R.string.theme_mode_dark);

	companion object {
		// 从 DataStore 读出的字符串还原为枚举；无法识别时回到跟随系统
		fun fromStorage(value: String?): ThemeMode {
			return entries.firstOrNull { it.storageName == value } ?: SYSTEM
		}
	}
}
