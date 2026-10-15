package com.dailycontrol.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(primary = BrandPrimary)

private val DarkColorScheme = darkColorScheme(primary = BrandPrimaryLight)

// 应用主题：跟随系统深浅色切换，其余颜色暂用 Material 3 默认方案
@Composable
fun DailyControlTheme(
	darkTheme: Boolean = isSystemInDarkTheme(),
	content: @Composable () -> Unit
) {
	MaterialTheme(
		colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
		content = content
	)
}
