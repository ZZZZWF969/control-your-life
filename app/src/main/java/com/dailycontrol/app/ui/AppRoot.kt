package com.dailycontrol.app.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dailycontrol.app.R
import com.dailycontrol.app.ui.screens.AiScreen
import com.dailycontrol.app.ui.screens.RecordScreen
import com.dailycontrol.app.ui.screens.RemindScreen
import com.dailycontrol.app.ui.screens.SettingsScreen
import com.dailycontrol.app.ui.screens.TodayScreen

// 底部导航的目标页定义：路由名 + 图标 + 标题资源
private data class NavDestination(
	val route: String,
	val icon: ImageVector,
	@StringRes val labelRes: Int
)

private val navDestinations = listOf(
	NavDestination("today", Icons.Filled.Home, R.string.tab_today),
	NavDestination("record", Icons.AutoMirrored.Filled.List, R.string.tab_record),
	NavDestination("remind", Icons.Filled.Notifications, R.string.tab_remind),
	NavDestination("ai", Icons.Filled.Star, R.string.tab_ai),
	NavDestination("settings", Icons.Filled.Settings, R.string.tab_settings)
)

// 应用根组件：底部导航栏 + 各页面容器
@Composable
fun DailyControlAppRoot() {
	val navController = rememberNavController()
	val backStackEntry by navController.currentBackStackEntryAsState()
	val currentRoute = backStackEntry?.destination?.route

	Scaffold(
		bottomBar = {
			NavigationBar {
				navDestinations.forEach { destination ->
					NavigationBarItem(
						selected = currentRoute == destination.route,
						onClick = {
							// launchSingleTop：重复点击当前页时不叠加新实例
							if (currentRoute != destination.route) {
								navController.navigate(destination.route) {
									launchSingleTop = true
								}
							}
						},
						icon = {
							Icon(destination.icon, contentDescription = stringResource(destination.labelRes))
						},
						label = { Text(stringResource(destination.labelRes)) }
					)
				}
			}
		}
	) { innerPadding ->
		NavHost(
			navController = navController,
			startDestination = "today",
			modifier = Modifier.padding(innerPadding)
		) {
			composable("today") {
				TodayScreen(onGoToRecord = {
					navController.navigate("record") {
						launchSingleTop = true
					}
				})
			}
			composable("record") { RecordScreen() }
			composable("remind") { RemindScreen() }
			composable("ai") { AiScreen() }
			composable("settings") { SettingsScreen() }
		}
	}
}
