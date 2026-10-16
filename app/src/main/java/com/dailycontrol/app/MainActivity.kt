package com.dailycontrol.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailycontrol.app.data.settings.SettingsRepository
import com.dailycontrol.app.data.settings.ThemeMode
import com.dailycontrol.app.ui.DailyControlAppRoot
import com.dailycontrol.app.ui.theme.DailyControlTheme

// 唯一的主界面 Activity：所有页面都在它内部的 Compose 导航容器中切换
// 通知点击通过 EXTRA_ROUTE 深链进来，由 AppRoot 消费后导航到对应页面
class MainActivity : ComponentActivity() {

	private val pendingRouteState = mutableStateOf<String?>(null)

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		pendingRouteState.value = intent?.getStringExtra(EXTRA_ROUTE)
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
				DailyControlAppRoot(
					pendingRoute = pendingRouteState.value,
					onRouteConsumed = { pendingRouteState.value = null }
				)
			}
		}
	}

	// singleTop 模式下已运行的实例收到新 Intent（如点通知）走这里
	override fun onNewIntent(intent: Intent) {
		super.onNewIntent(intent)
		pendingRouteState.value = intent.getStringExtra(EXTRA_ROUTE)
	}

	companion object {
		const val EXTRA_ROUTE = "navigate_route"
		const val ROUTE_TODAY = "today"
		const val ROUTE_RECORD = "record"
	}
}
