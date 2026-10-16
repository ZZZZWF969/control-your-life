package com.dailycontrol.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

// 全局形状规范：卡片大圆角是本应用设计语言的一部分
private val AppShapes = Shapes(
	extraSmall = RoundedCornerShape(8.dp),
	small = RoundedCornerShape(12.dp),
	medium = RoundedCornerShape(16.dp),
	large = RoundedCornerShape(24.dp),
	extraLarge = RoundedCornerShape(32.dp)
)

// 应用主题：darkTheme 由调用方根据「用户设置 + 系统深浅色」解析后传入
@Composable
fun DailyControlTheme(
	darkTheme: Boolean = isSystemInDarkTheme(),
	content: @Composable () -> Unit
) {
	MaterialTheme(
		colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
		shapes = AppShapes,
		content = content
	)
}
