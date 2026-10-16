package com.dailycontrol.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// ============ 浅色主题「清新健康风」 ============
// 品牌青绿贯穿，白净底色带一点薄荷色调，卡片与容器用低饱和浅青

private val LightPrimary = Color(0xFF0F766E)
private val LightOnPrimary = Color(0xFFFFFFFF)
private val LightPrimaryContainer = Color(0xFFC2F0E9)
private val LightOnPrimaryContainer = Color(0xFF042F2C)

private val LightSecondary = Color(0xFF4A635F)
private val LightSecondaryContainer = Color(0xFFCCE8E3)
private val LightOnSecondaryContainer = Color(0xFF06201D)

// 暖琥珀色作为第三色：留给饮食/能量类内容的强调
private val LightTertiary = Color(0xFF775C2E)
private val LightTertiaryContainer = Color(0xFFFFDF9E)
private val LightOnTertiaryContainer = Color(0xFF271A00)

private val LightBackground = Color(0xFFF4FBF8)
private val LightOnBackground = Color(0xFF161D1B)
private val LightSurface = Color(0xFFF8FCFA)
private val LightSurfaceVariant = Color(0xFFDBE5E2)
private val LightOnSurfaceVariant = Color(0xFF3F4947)
private val LightOutline = Color(0xFF6F7976)
private val LightOutlineVariant = Color(0xFFBEC9C6)

val LightColorScheme = lightColorScheme(
	primary = LightPrimary,
	onPrimary = LightOnPrimary,
	primaryContainer = LightPrimaryContainer,
	onPrimaryContainer = LightOnPrimaryContainer,
	secondary = LightSecondary,
	secondaryContainer = LightSecondaryContainer,
	onSecondaryContainer = LightOnSecondaryContainer,
	tertiary = LightTertiary,
	tertiaryContainer = LightTertiaryContainer,
	onTertiaryContainer = LightOnTertiaryContainer,
	background = LightBackground,
	onBackground = LightOnBackground,
	surface = LightSurface,
	onSurface = LightOnBackground,
	surfaceVariant = LightSurfaceVariant,
	onSurfaceVariant = LightOnSurfaceVariant,
	outline = LightOutline,
	outlineVariant = LightOutlineVariant
)

// ============ 深色主题 ============
// 深灰蓝底（非纯黑，夜间更柔和），青绿提亮为高亮色

private val DarkPrimary = Color(0xFF7BD8C9)
private val DarkOnPrimary = Color(0xFF003733)
private val DarkPrimaryContainer = Color(0xFF1E5049)
private val DarkOnPrimaryContainer = Color(0xFF93F4E5)

private val DarkSecondary = Color(0xFFB0CCC7)
private val DarkSecondaryContainer = Color(0xFF324B47)
private val DarkOnSecondaryContainer = Color(0xFFCCE8E3)

private val DarkTertiary = Color(0xFFE8C24D)
private val DarkTertiaryContainer = Color(0xFF5B4300)
private val DarkOnTertiaryContainer = Color(0xFFFFDF9E)

private val DarkBackground = Color(0xFF0E1514)
private val DarkOnBackground = Color(0xFFDDE4E2)
private val DarkSurface = Color(0xFF111918)
private val DarkSurfaceVariant = Color(0xFF3F4947)
private val DarkOnSurfaceVariant = Color(0xFFBEC9C6)
private val DarkOutline = Color(0xFF89938F)
private val DarkOutlineVariant = Color(0xFF3F4947)

val DarkColorScheme = darkColorScheme(
	primary = DarkPrimary,
	onPrimary = DarkOnPrimary,
	primaryContainer = DarkPrimaryContainer,
	onPrimaryContainer = DarkOnPrimaryContainer,
	secondary = DarkSecondary,
	secondaryContainer = DarkSecondaryContainer,
	onSecondaryContainer = DarkOnSecondaryContainer,
	tertiary = DarkTertiary,
	tertiaryContainer = DarkTertiaryContainer,
	onTertiaryContainer = DarkOnTertiaryContainer,
	background = DarkBackground,
	onBackground = DarkOnBackground,
	surface = DarkSurface,
	onSurface = DarkOnBackground,
	surfaceVariant = DarkSurfaceVariant,
	onSurfaceVariant = DarkOnSurfaceVariant,
	outline = DarkOutline,
	outlineVariant = DarkOutlineVariant
)
