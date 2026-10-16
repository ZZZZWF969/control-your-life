package com.dailycontrol.app.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// 时间相关的公共小工具：日期边界计算与展示格式化，记录页与今日页共用
object TimeUtil {

	private val zone: ZoneId = ZoneId.systemDefault()

	// 应用为纯中文界面，日期格式固定使用中文 Locale
	private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

	private val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)

	// 某天 00:00:00.000 对应的 epoch 毫秒
	fun startOfDayMillis(date: LocalDate): Long {
		return date.atStartOfDay(zone).toInstant().toEpochMilli()
	}

	// 某天 23:59:59.999 对应的 epoch 毫秒
	fun endOfDayMillis(date: LocalDate): Long {
		return startOfDayMillis(date.plusDays(1)) - 1
	}

	// 把「日期 + 时刻」合成为 epoch 毫秒
	fun combineToMillis(date: LocalDate, time: LocalTime): Long {
		return date.atTime(time).atZone(zone).toInstant().toEpochMilli()
	}

	fun formatTime(epochMillis: Long): String {
		return Instant.ofEpochMilli(epochMillis).atZone(zone).format(timeFormat)
	}

	fun formatDate(date: LocalDate): String {
		return date.format(dateFormat)
	}
}
