package com.dailycontrol.app.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// 开机重排接收器：手机重启后系统闹钟会被清空，这里负责恢复全部每日提醒
// 注：依赖「自启动」权限（HyperOS 需用户手动授予，真机批会引导）
class BootReceiver : BroadcastReceiver() {

	override fun onReceive(context: Context, intent: Intent) {
		if (intent.action != Intent.ACTION_BOOT_COMPLETED) {
			return
		}
		val pendingResult = goAsync()
		CoroutineScope(Dispatchers.IO).launch {
			try {
				// 开机恢复全部每日提醒；待办类提醒随第 3 批在此追加
				DailyPlanner.rescheduleMeals(context)
				VocabPlanner.reschedule(context)
			} finally {
				pendingResult.finish()
			}
		}
	}
}
