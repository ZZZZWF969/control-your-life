package com.dailycontrol.app.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.dailycontrol.app.schedule.ReminderReason.EXTRA_REQUEST_CODE

// AlarmManager 精确排程的统一封装：全应用的定时提醒都从这里进出
// 概念：setExactAndAllowWhileIdle = 应用被杀/设备打盹时仍准点触发的系统级闹钟
object ReminderScheduler {

	// 排一个提醒；对同一 requestCode 重复 schedule 会整体覆盖旧时刻（先取消再排，防漂移残留）
	fun schedule(context: Context, requestCode: Int, triggerAtMillis: Long) {
		val alarmManager = context.getSystemService(AlarmManager::class.java)
		val pendingIntent = buildPendingIntent(context, requestCode)
		// Android 12+ 未授予「精确闹钟」权限时退化为普通窗口闹钟（时刻可能漂移几分钟）
		if (Build.VERSION.SDK_INT >= 31 && !alarmManager.canScheduleExactAlarms()) {
			alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
		} else {
			alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
		}
	}

	fun cancel(context: Context, requestCode: Int) {
		val alarmManager = context.getSystemService(AlarmManager::class.java)
		alarmManager.cancel(buildPendingIntent(context, requestCode))
	}

	// 是否已获得精确闹钟权限（未获得时提醒仍会触发，但时刻可能漂移）
	fun canScheduleExact(context: Context): Boolean {
		val alarmManager = context.getSystemService(AlarmManager::class.java)
		return Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms()
	}

	private fun buildPendingIntent(context: Context, requestCode: Int): PendingIntent {
		val intent = Intent(context, ReminderReceiver::class.java).putExtra(EXTRA_REQUEST_CODE, requestCode)
		return PendingIntent.getBroadcast(
			context,
			requestCode,
			intent,
			PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
		)
	}
}
