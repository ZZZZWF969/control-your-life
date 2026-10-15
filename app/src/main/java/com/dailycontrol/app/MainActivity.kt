package com.dailycontrol.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.dailycontrol.app.ui.DailyControlAppRoot
import com.dailycontrol.app.ui.theme.DailyControlTheme

// 唯一的主界面 Activity：所有页面都在它内部的 Compose 导航容器中切换
class MainActivity : ComponentActivity() {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContent {
			DailyControlTheme {
				DailyControlAppRoot()
			}
		}
	}
}
