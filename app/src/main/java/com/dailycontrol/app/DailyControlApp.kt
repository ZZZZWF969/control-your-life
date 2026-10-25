package com.dailycontrol.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.dailycontrol.app.ai.AiReportGenerator
import com.dailycontrol.app.backup.BackupManager
import com.dailycontrol.app.notify.NotifyChannels
import com.dailycontrol.app.notify.Notifier
import com.dailycontrol.app.schedule.AiReportPlanner
import com.dailycontrol.app.schedule.DailyPlanner
import com.dailycontrol.app.schedule.VocabPlanner
import com.dailycontrol.app.schedule.WakePlanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

// 应用入口：早于所有界面创建，适合做一次性的全局初始化
class DailyControlApp : Application() {

	override fun onCreate() {
		super.onCreate()
		createNotificationChannels()
		// 每次启动都重排每日提醒：自愈机制，覆盖闹钟丢失/时刻变更等异常
		appScope.launch {
			DailyPlanner.rescheduleMeals(this@DailyControlApp)
			DailyPlanner.reschedulePlan(this@DailyControlApp)
			DailyPlanner.rescheduleTodoTasks(this@DailyControlApp)
			VocabPlanner.reschedule(this@DailyControlApp)
			WakePlanner.rescheduleFromStore(this@DailyControlApp)
			BackupManager.ensureScheduledOnStartup(this@DailyControlApp)
			// AI 报告：重排凌晨 4:00 闹钟，并补做昨日缺失的报告（凌晨关机/闹钟被吞的兜底）
			AiReportPlanner.reschedule(this@DailyControlApp)
			val outcome = AiReportGenerator.generateIfDue(this@DailyControlApp)
			if (outcome is AiReportGenerator.Outcome.Success || outcome is AiReportGenerator.Outcome.Failed) {
				Notifier.sendAiReportNotification(this@DailyControlApp, outcome)
			}
		}
	}

	companion object {
		// 应用级协程作用域：启动自愈、以及 Receiver 挂载的长耗时后台任务（如 AI 生成）
		val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
	}

	// 注册全部通知渠道；重复注册是安全的，系统会忽略已存在的同 ID 渠道
	// 起床渠道：纯震动无声音——长脉冲连震约 3 秒（震0.8s歇0.4s×4），唤醒力强于普通短震
	private fun createNotificationChannels() {
		val manager = getSystemService(NotificationManager::class.java)
		val wakeChannel = NotificationChannel(
			NotifyChannels.WAKE_ID,
			getString(R.string.channel_wake_name),
			NotificationManager.IMPORTANCE_HIGH
		).apply {
			setSound(null, null)
			enableVibration(true)
			vibrationPattern = longArrayOf(0, 800, 400, 800, 400, 800, 400, 800)
		}
		val channels = listOf(
			NotificationChannel(
				NotifyChannels.GENERAL_ID,
				getString(R.string.channel_general_name),
				NotificationManager.IMPORTANCE_HIGH
			),
			NotificationChannel(
				NotifyChannels.MEAL_ID,
				getString(R.string.channel_meal_name),
				NotificationManager.IMPORTANCE_HIGH
			),
			NotificationChannel(
				NotifyChannels.WORD_ID,
				getString(R.string.channel_word_name),
				NotificationManager.IMPORTANCE_HIGH
			),
			NotificationChannel(
				NotifyChannels.PLAN_ID,
				getString(R.string.channel_plan_name),
				NotificationManager.IMPORTANCE_HIGH
			),
			NotificationChannel(
				NotifyChannels.TODO_ID,
				getString(R.string.channel_todo_name),
				NotificationManager.IMPORTANCE_HIGH
			),
			// AI 报告渠道：DEFAULT 无声——4:00 出报告不该响铃；渠道行为创建后不可改，故必须新 ID
			NotificationChannel(
				NotifyChannels.AI_ID,
				getString(R.string.channel_ai_name),
				NotificationManager.IMPORTANCE_DEFAULT
			),
			wakeChannel
		)
		channels.forEach { manager.createNotificationChannel(it) }
	}
}
