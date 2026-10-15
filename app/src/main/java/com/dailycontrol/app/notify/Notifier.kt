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

// 通知发送的统一入口：界面层只调用这里，不直接拼装 Notification
object Notifier {

	const val NOTIFY_ID_TEST = 1

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
		val tapIntent = PendingIntent.getActivity(
			context,
			0,
			Intent(context, MainActivity::class.java),
			PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
		)
		val notification = NotificationCompat.Builder(context, NotifyChannels.GENERAL_ID)
			.setSmallIcon(R.drawable.ic_stat_dailycontrol)
			.setContentTitle(context.getString(R.string.test_notify_title))
			.setContentText(context.getString(R.string.test_notify_text))
			.setContentIntent(tapIntent)
			.setAutoCancel(true)
			.build()
		NotificationManagerCompat.from(context).notify(NOTIFY_ID_TEST, notification)
	}
}
