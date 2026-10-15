// 顶层构建脚本：只集中声明插件版本，不在根工程应用任何插件
// 注：AGP 9 起内置 Kotlin 支持，无需再单独声明 kotlin-android 插件
plugins {
	alias(libs.plugins.android.application) apply false
	alias(libs.plugins.kotlin.compose) apply false
	alias(libs.plugins.ksp) apply false
}
