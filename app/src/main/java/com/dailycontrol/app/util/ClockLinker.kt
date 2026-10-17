package com.dailycontrol.app.util

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.util.Log

// 跳转系统时钟应用的封装（2026-10-17 双保险方案）：
// 只打开闹钟列表让用户手动开关，不代建不删除——安卓 API 读不到也删不掉时钟里的闹钟，代建会每日堆积
object ClockLinker {

	// SHOW_ALARMS 是标准只读入口：仅打开闹钟列表页，不写入任何数据（需 SET_ALARM 权限，Manifest 已声明）
	fun alarmListIntent(): Intent {
		return Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
	}

	// 从应用内打开闹钟列表；返回是否成功（无时钟应用/不支持该入口时 false）
	fun openAlarmList(context: Context): Boolean {
		return runCatching {
			context.startActivity(alarmListIntent())
			true
		}.getOrElse {
			// 真机联调时凭这条日志定位（时钟应用缺失/不支持 SHOW_ALARMS 等）
			Log.w("ClockLinker", "openAlarmList failed", it)
			false
		}
	}
}
