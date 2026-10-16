package com.dailycontrol.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailycontrol.app.data.settings.SettingsRepository
import com.dailycontrol.app.data.settings.ThemeMode
import com.dailycontrol.app.ui.DailyControlAppRoot
import com.dailycontrol.app.ui.theme.DailyControlTheme

// 唯一的主界面 Activity：所有页面都在它内部的 Compose 导航容器中切换
class MainActivity : ComponentActivity() {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContent {
			// 订阅用户设置的主题模式，结合系统深浅色解析出当前应使用的主题
			val settings = SettingsRepository.get(this)
			val themeMode by settings.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
			val systemDark = isSystemInDarkTheme()
			val darkTheme = when (themeMode) {
				ThemeMode.SYSTEM -> systemDark
				ThemeMode.LIGHT -> false
				ThemeMode.DARK -> true
			}
			DailyControlTheme(darkTheme = darkTheme) {
				DailyControlAppRoot()
			}
		}
	}
}
