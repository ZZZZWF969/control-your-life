package com.dailycontrol.app.notify

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.dailycontrol.app.MainActivity
import com.dailycontrol.app.R
import com.dailycontrol.app.ai.AiReportGenerator
import com.dailycontrol.app.data.entity.MealType
import com.dailycontrol.app.data.entity.TodoTask
import com.dailycontrol.app.data.settings.SettingsRepository
import com.dailycontrol.app.util.ClockLinker
import com.dailycontrol.app.util.TimeUtil
import kotlinx.coroutines.flow.first

// 通知发送的统一入口：界面层与调度层只调用这里，不直接拼装 Notification
object Notifier {

	const val NOTIFY_ID_TEST = 1
	const val NOTIFY_ID_TODO_TEST = 9001
	// 三餐询问通知 id：9002 起按餐别序号错开，避免互相覆盖
	const val NOTIFY_ID_MEAL_BASE = 9002
	// 背单词循环提醒通知 id（循环内覆盖同一条）
	const val NOTIFY_ID_WORD = 9100
	// 晚间规划提醒（首次与二次共用一条，互相覆盖）
	const val NOTIFY_ID_PLAN = 9200
	// 待办提醒：9300 起按任务 id 错开
	const val NOTIFY_ID_TODO_BASE = 9300
	// 起床唤醒：每声覆盖前一声（只留最新未读）
	const val NOTIFY_ID_WAKE = 9400
	// AI 报告通知（成功与失败共用一条，互相覆盖）
	const val NOTIFY_ID_AI = 9500

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

	// 背单词循环提醒：通知栏只做「去背单词」快速入口（包名已配置且应用存在时）；
	// 「今日已背」必须进应用确认（点击通知进今日页），防止误触（2026-10-17 用户确认）
	suspend fun sendVocabNotification(context: Context) {
		if (!hasPostPermission(context)) {
			return
		}
		val builder = NotificationCompat.Builder(context, NotifyChannels.WORD_ID)
			.setSmallIcon(R.drawable.ic_stat_dailycontrol)
			.setContentTitle(context.getString(R.string.vocab_notify_title))
			.setContentText(context.getString(R.string.vocab_notify_text))
			.setContentIntent(buildTapIntent(context, MainActivity.ROUTE_TODAY, NOTIFY_ID_WORD))
			.setAutoCancel(true)

		// 配置了跳转包名且设备上存在该应用时，追加「去背单词」动作
		val packageName = SettingsRepository.get(context).vocabPackage.first()
		if (packageName.isNotBlank()) {
			val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
			if (launchIntent != null) {
				builder.addAction(
					0,
					context.getString(R.string.vocab_action_go),
					PendingIntent.getActivity(
						context,
						NOTIFY_ID_WORD + 1,
						launchIntent,
						PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
					)
				)
			}
		}
		NotificationManagerCompat.from(context).notify(NOTIFY_ID_WORD, builder.build())
	}

	fun cancelVocabNotification(context: Context) {
		NotificationManagerCompat.from(context).cancel(NOTIFY_ID_WORD)
	}

	// 晚间规划提醒：primary=22:00 首次，false=23:00 二次；点击深链到规划页
	fun sendPlanNotification(context: Context, primary: Boolean) {
		if (!hasPostPermission(context)) {
			return
		}
		val notification = NotificationCompat.Builder(context, NotifyChannels.PLAN_ID)
			.setSmallIcon(R.drawable.ic_stat_dailycontrol)
			.setContentTitle(
				context.getString(
					if (primary) R.string.plan_notify_title else R.string.plan_notify_second_title
				)
			)
			.setContentText(context.getString(R.string.plan_notify_text))
			.setContentIntent(buildTapIntent(context, MainActivity.ROUTE_PLAN, NOTIFY_ID_PLAN))
			.setAutoCancel(true)
			.build()
		NotificationManagerCompat.from(context).notify(NOTIFY_ID_PLAN, notification)
	}

	// 待办到点提醒：任务名 + 开始时间；点击深链到今日页（任务清单在那）
	fun sendTodoTaskNotification(context: Context, task: TodoTask) {
		if (!hasPostPermission(context)) {
			return
		}
		val timeText = task.startAtMillis?.let { TimeUtil.formatTime(it) } ?: ""
		val notification = NotificationCompat.Builder(context, NotifyChannels.TODO_ID)
			.setSmallIcon(R.drawable.ic_stat_dailycontrol)
			.setContentTitle(task.title)
			.setContentText(
				if (timeText.isNotBlank()) {
					context.getString(R.string.todo_notify_text_with_time, timeText)
				} else {
					context.getString(R.string.todo_notify_text)
				}
			)
			.setContentIntent(
				buildTapIntent(
					context,
					MainActivity.ROUTE_TODAY,
					(NOTIFY_ID_TODO_BASE + task.id).toInt()
				)
			)
			.setAutoCancel(true)
			.build()
		NotificationManagerCompat.from(context).notify((NOTIFY_ID_TODO_BASE + task.id).toInt(), notification)
	}

	// 起床唤醒通知：第几声/共几声；total 为 0 时省略总数（调试模拟场景）
	// isLast = 末声（双保险方案）：持续震动直到处理，并附「检查时钟闹钟」兜底入口
	fun sendWakeNotification(context: Context, index: Int, total: Int, isLast: Boolean) {
		if (!hasPostPermission(context)) {
			return
		}
		val text = when {
			isLast -> context.getString(R.string.wake_notify_last_text)
			total > 0 -> context.getString(R.string.wake_notify_text, index, total)
			else -> context.getString(R.string.wake_notify_text_no_total, index)
		}
		val builder = NotificationCompat.Builder(context, NotifyChannels.WAKE_ID)
			.setSmallIcon(R.drawable.ic_stat_dailycontrol)
			.setContentTitle(context.getString(R.string.wake_notify_title))
			.setContentText(text)
			.setContentIntent(buildTapIntent(context, MainActivity.ROUTE_TODAY, NOTIFY_ID_WAKE))
			.setAutoCancel(true)
			.setCategory(NotificationCompat.CATEGORY_ALARM)
		if (isLast) {
			// 时钟闹钟没开时的直达入口：一点就到闹钟列表
			builder.addAction(
				0,
				context.getString(R.string.wake_action_check_clock),
				PendingIntent.getActivity(
					context,
					NOTIFY_ID_WAKE + 1,
					ClockLinker.alarmListIntent(),
					PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
				)
			)
		}
		val notification = builder.build()
		if (isLast) {
			// FLAG_INSISTENT：通知未被处理前系统持续间歇重复提醒；构建器无对应 API，须在成品上改标志位
			notification.flags = notification.flags or Notification.FLAG_INSISTENT
		}
		NotificationManagerCompat.from(context).notify(NOTIFY_ID_WAKE, notification)
	}

	// AI 报告通知：成功带总评（格式异常时说明已按原文保存）、失败带原因；点击深链到 AI 页。
	// 凌晨 4:00 发出，渠道为 DEFAULT 无声级，不会响铃；「一切正常/未配置」不通知
	fun sendAiReportNotification(context: Context, outcome: AiReportGenerator.Outcome) {
		if (!hasPostPermission(context)) {
			return
		}
		val (titleRes, text) = when (outcome) {
			is AiReportGenerator.Outcome.Success -> {
				val summary = if (outcome.summary.isNotBlank()) {
					outcome.summary
				} else {
					context.getString(R.string.ai_report_summary_fallback)
				}
				R.string.ai_notify_title to context.getString(R.string.ai_notify_text, summary)
			}
			is AiReportGenerator.Outcome.Failed ->
				R.string.ai_notify_fail_title to context.getString(R.string.ai_notify_fail_text, outcome.reasonText)
			else -> return
		}
		val notification = NotificationCompat.Builder(context, NotifyChannels.AI_ID)
			.setSmallIcon(R.drawable.ic_stat_dailycontrol)
			.setContentTitle(context.getString(titleRes))
			.setContentText(text)
			.setContentIntent(buildTapIntent(context, MainActivity.ROUTE_AI, NOTIFY_ID_AI))
			.setAutoCancel(true)
			.setStyle(NotificationCompat.BigTextStyle().bigText(text))
			.build()
		NotificationManagerCompat.from(context).notify(NOTIFY_ID_AI, notification)
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
