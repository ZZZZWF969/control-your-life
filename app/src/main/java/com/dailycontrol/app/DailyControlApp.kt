package com.dailycontrol.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.dailycontrol.app.notify.NotifyChannels

// 应用入口：早于所有界面创建，适合做一次性的全局初始化
class DailyControlApp : Application() {

	override fun onCreate() {
		super.onCreate()
		createNotificationChannels()
	}

	// 注册全部通知渠道；重复注册是安全的，系统会忽略已存在的同 ID 渠道
	private fun createNotificationChannels() {
		val manager = getSystemService(NotificationManager::class.java)
		val general = NotificationChannel(
			NotifyChannels.GENERAL_ID,
			getString(R.string.channel_general_name),
			NotificationManager.IMPORTANCE_HIGH
		)
		manager.createNotificationChannel(general)
	}
}
