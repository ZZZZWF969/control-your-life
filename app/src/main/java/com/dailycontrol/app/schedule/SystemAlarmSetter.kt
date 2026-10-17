package com.dailycontrol.app.schedule

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.util.Log
import kotlin.random.Random

// 起床闹钟写入系统时钟应用的封装：
// 安卓不允许第三方应用直接改时钟数据库，官方途径是发 SET_ALARM 请求由时钟应用创建
// EXTRA_SKIP_UI=true 时多数实现可免确认静默创建；HyperOS 行为待真机验证
// 22 点规划时设次日 07:30~08:00 的闹钟：时钟应用按「下一次该时刻」创建，天然落在明早
object SystemAlarmSetter {

	// 早八（08:30 上课）起闹区间：07:30–08:00；早十（10:15 上课）：09:15–09:45（分钟数含两端）
	private val MORNING_CLASS_RANGE = 450..480
	private val LATE_MORNING_RANGE = 555..585

	// 在区间内取随机时刻写入系统闹钟；返回是否成功发起（时钟应用不存在等场景返回 false）
	fun setWakeAlarm(context: Context, morningClass: Boolean): Boolean {
		val range = if (morningClass) MORNING_CLASS_RANGE else LATE_MORNING_RANGE
		val minuteOfDay = Random.nextInt(range.first, range.last + 1)
		val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
			putExtra(AlarmClock.EXTRA_HOUR, minuteOfDay / 60)
			putExtra(AlarmClock.EXTRA_MINUTES, minuteOfDay % 60)
			putExtra(AlarmClock.EXTRA_MESSAGE, context.getString(com.dailycontrol.app.R.string.wake_alarm_label))
			putExtra(AlarmClock.EXTRA_SKIP_UI, true)
			putExtra(AlarmClock.EXTRA_VIBRATE, true)
			addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
		}
		return runCatching {
			context.startActivity(intent)
			true
		}.getOrElse {
			// 真机联调时凭这条日志定位（时钟应用缺失/不支持 SET_ALARM 等）
			Log.w("SystemAlarmSetter", "setWakeAlarm failed", it)
			false
		}
	}
}
