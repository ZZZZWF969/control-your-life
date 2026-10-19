package com.dailycontrol.app.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dailycontrol.app.R
import com.dailycontrol.app.ui.screens.AiScreen
import com.dailycontrol.app.ui.screens.OnboardingScreen
import com.dailycontrol.app.ui.screens.PlanScreen
import com.dailycontrol.app.ui.screens.RecordScreen
import com.dailycontrol.app.ui.screens.SettingsScreen
import com.dailycontrol.app.ui.screens.TodayScreen

// 底部导航的目标页定义：路由名 + 图标 + 标题资源
// 2026-10-19 页面整合：提醒页取消（职责并入今日页），底栏四格
private data class NavDestination(
	val route: String,
	val icon: ImageVector,
	@StringRes val labelRes: Int
)

private val navDestinations = listOf(
	NavDestination("today", Icons.Filled.Home, R.string.tab_today),
	NavDestination("record", Icons.AutoMirrored.Filled.List, R.string.tab_record),
	NavDestination("ai", Icons.Filled.Star, R.string.tab_ai),
	NavDestination("settings", Icons.Filled.Settings, R.string.tab_settings)
)

// 应用根组件：底部导航栏 + 各页面容器；pendingRoute 用于消费通知深链的导航请求；
// 首启向导未完成时以向导整屏替代主界面（深链请求挂起至完成，2026-10-19 批次4）
@Composable
fun DailyControlAppRoot(
	pendingRoute: String? = null,
	onRouteConsumed: () -> Unit = {},
	onboardingDone: Boolean = true,
	onOnboardingDone: () -> Unit = {}
) {
	if (!onboardingDone) {
		OnboardingScreen(onFinish = onOnboardingDone)
		return
	}

	val navController = rememberNavController()
	val backStackEntry by navController.currentBackStackEntryAsState()
	val currentRoute = backStackEntry?.destination?.route

	// 深链消费：通知带来的路由请求执行一次即清空
	LaunchedEffect(pendingRoute) {
		pendingRoute?.let { route ->
			navController.navigate(route) {
				launchSingleTop = true
			}
			onRouteConsumed()
		}
	}

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
				TodayScreen(
					onGoToRecord = {
						navController.navigate("record") {
							launchSingleTop = true
						}
					},
					onGoToPlan = {
						navController.navigate("plan") {
							launchSingleTop = true
						}
					}
				)
			}
			// record 路由支持可选餐别参数：三餐询问通知深链 record?meal=breakfast 直接弹预填表单
			composable(
				route = "record?meal={meal}",
				arguments = listOf(
					navArgument("meal") {
						type = NavType.StringType
						nullable = true
						defaultValue = null
					}
				)
			) { entry ->
				RecordScreen(initialMeal = entry.arguments?.getString("meal"))
			}
			composable("ai") { AiScreen() }
			composable("settings") { SettingsScreen() }
			// 规划页：不占底栏，由提醒页入口与 22:00 通知深链进入
			composable("plan") {
				PlanScreen(onDone = { navController.popBackStack() })
			}
		}
	}
}
