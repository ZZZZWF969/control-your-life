package com.dailycontrol.app.notify

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.dailycontrol.app.MainActivity
import com.dailycontrol.app.R
import com.dailycontrol.app.data.entity.MealType

// 通知发送的统一入口：界面层与调度层只调用这里，不直接拼装 Notification
object Notifier {

	const val NOTIFY_ID_TEST = 1
	const val NOTIFY_ID_TODO_TEST = 9001
	// 三餐询问通知 id：9002 起按餐别序号错开，避免互相覆盖
	const val NOTIFY_ID_MEAL_BASE = 9002

	// 检查是否已获得安卓 13+ 的通知运行时权限
	fun hasPostPermission(context: Context): Boolean {
		return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
			PackageManager.PERMISSION_GRANTED
	}

	// 发送一条测试通知，验证「渠道注册 → 权限 → 通知栏展示 → 点击回应用」整条链路
	fun sendTestNotification(context: Context) {
		if (!hasPostPermission(context)) {
			return
		}
		val notification = NotificationCompat.Builder(context, NotifyChannels.GENERAL_ID)
			.setSmallIcon(R.drawable.ic_stat_dailycontrol)
			.setContentTitle(context.getString(R.string.test_notify_title))
			.setContentText(context.getString(R.string.test_notify_text))
			.setContentIntent(buildTapIntent(context, MainActivity.ROUTE_TODAY, NOTIFY_ID_TEST))
			.setAutoCancel(true)
			.build()
		NotificationManagerCompat.from(context).notify(NOTIFY_ID_TEST, notification)
	}

	// 测试待办提醒：验证「排程 → 系统闹钟触发 → 通知 → 点击深链回应用」全链路
	fun sendTodoNotification(context: Context) {
		if (!hasPostPermission(context)) {
			return
		}
		val notification = NotificationCompat.Builder(context, NotifyChannels.TODO_ID)
			.setSmallIcon(R.drawable.ic_stat_dailycontrol)
			.setContentTitle(context.getString(R.string.test_todo_title))
			.setContentText(context.getString(R.string.test_todo_text))
			.setContentIntent(buildTapIntent(context, MainActivity.ROUTE_TODAY, NOTIFY_ID_TODO_TEST))
			.setAutoCancel(true)
			.build()
		NotificationManagerCompat.from(context).notify(NOTIFY_ID_TODO_TEST, notification)
	}

	// 三餐询问通知：点击深链到记录页并自动弹出该餐的预填表单
	fun sendMealNotification(context: Context, mealType: MealType) {
		if (!hasPostPermission(context)) {
			return
		}
		val label = context.getString(mealType.labelRes)
		val notification = NotificationCompat.Builder(context, NotifyChannels.MEAL_ID)
			.setSmallIcon(R.drawable.ic_stat_dailycontrol)
			.setContentTitle(context.getString(R.string.meal_notify_title, label))
			.setContentText(context.getString(R.string.meal_notify_text, label))
			.setContentIntent(
				buildTapIntent(
					context,
					MainActivity.ROUTE_RECORD + "?meal=" + mealType.storageName,
					NOTIFY_ID_MEAL_BASE + mealType.ordinal
				)
			)
			.setAutoCancel(true)
			.build()
		NotificationManagerCompat.from(context)
			.notify(NOTIFY_ID_MEAL_BASE + mealType.ordinal, notification)
	}

	// 通知点击意图：带导航路由参数，由 MainActivity 解析后转发给应用内导航
	private fun buildTapIntent(context: Context, route: String, notifyId: Int): PendingIntent {
		val intent = Intent(context, MainActivity::class.java)
			.putExtra(MainActivity.EXTRA_ROUTE, route)
			.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
		return PendingIntent.getActivity(
			context,
			notifyId,
			intent,
			PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
		)
	}
}
