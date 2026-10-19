package com.dailycontrol.app.backup

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.room.withTransaction
import com.dailycontrol.app.data.db.DailyControlDatabase
import com.dailycontrol.app.data.entity.DietRecord
import com.dailycontrol.app.data.entity.ExerciseRecord
import com.dailycontrol.app.data.entity.NutrientItem
import com.dailycontrol.app.data.entity.TodoTask
import com.dailycontrol.app.data.settings.SettingsRepository
import com.dailycontrol.app.schedule.DailyPlanner
import com.dailycontrol.app.schedule.ReminderReason
import com.dailycontrol.app.schedule.ReminderScheduler
import com.dailycontrol.app.schedule.VocabPlanner
import com.dailycontrol.app.schedule.WakePlanner
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// 备份与恢复的唯一入口（2026-10-18 数据存储策略批次）：
// 导出 = 四张表全量 + 设置快照 → 单个 JSON 纯文本（带格式版本号，记事本可开可自检）
// 恢复 = 校验 → 自动先存「恢复前快照」→ 整体替换 → 重排全部提醒
// 自动备份 = 每周到点经 MediaStore 写入系统「下载」目录（卸载应用也不会被删），滚动保留最近若干份
// 层次说明：本文件协调 data 层与 schedule 层，故独立成 backup/ 包，不塞进任何一层
object BackupManager {

	// 备份文件格式版本：结构不兼容地变化时 +1，恢复端据此拒绝旧格式并提示
	const val FORMAT_VERSION = 1

	// 滚动保留的自动备份份数（恢复快照不占配额，永不自动清理）
	private const val KEEP_AUTO_BACKUPS = 4
	private val WEEK_MILLIS = 7L * 24 * 60 * 60 * 1000
	private val DAY_MILLIS = 24L * 60 * 60 * 1000
	private val HOUR_MILLIS = 60L * 60 * 1000

	// ---------- 导出 ----------

	// 组装备份 JSON：缩进 2 空格，人类可读（隐私承诺的佐证：用户能亲眼看到存了什么）
	suspend fun buildBackupJson(context: Context): String {
		val db = DailyControlDatabase.get(context)
		val root = JSONObject()
		root.put("formatVersion", FORMAT_VERSION)
		root.put("exportedAt", System.currentTimeMillis())
		root.put("appVersion", appVersionName(context))
		val data = JSONObject()
		data.put("dietRecords", JSONArray(db.dietRecordDao().getAllOnce().map { dietToJson(it) }))
		data.put("nutrientItems", JSONArray(db.nutrientItemDao().getAllOnce().map { nutrientToJson(it) }))
		data.put("exerciseRecords", JSONArray(db.exerciseRecordDao().getAllOnce().map { exerciseToJson(it) }))
		data.put("todoTasks", JSONArray(db.todoTaskDao().getAllOnce().map { todoToJson(it) }))
		root.put("data", data)
		root.put("settings", JSONObject(SettingsRepository.get(context).backupSettings()))
		return root.toString(2)
	}

	// 手动导出：写入用户经系统文件选择器指定的位置
	suspend fun exportToUri(context: Context, uri: Uri): Boolean {
		val json = buildBackupJson(context)
		return runCatching {
			context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
				out.write(json.toByteArray(Charsets.UTF_8))
			} != null
		}.also {
			if (it.getOrDefault(false)) {
				SettingsRepository.get(context).setLastBackupAt(System.currentTimeMillis())
			}
		}.getOrDefault(false)
	}

	// ---------- 自动备份（下载目录 + 滚动清理） ----------

	suspend fun autoBackupToDownloads(context: Context): Boolean {
		val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
		val ok = writeToDownloads(context, "daily-control-backup-$stamp.json", buildBackupJson(context))
		if (ok) {
			trimOldAutoBackups(context)
			SettingsRepository.get(context).setLastBackupAt(System.currentTimeMillis())
		}
		return ok
	}

	// 恢复前的现状快照：同样落在下载目录，命名区分，不参与滚动清理
	private suspend fun snapshotBeforeRestore(context: Context) {
		val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
		writeToDownloads(context, "daily-control-restore-snapshot-$stamp.json", buildBackupJson(context))
	}

	private fun writeToDownloads(context: Context, fileName: String, content: String): Boolean {
		val values = ContentValues().apply {
			put(MediaStore.Downloads.DISPLAY_NAME, fileName)
			put(MediaStore.Downloads.MIME_TYPE, "application/json")
		}
		val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
			?: return false
		return runCatching {
			context.contentResolver.openOutputStream(uri)?.use { out ->
				out.write(content.toByteArray(Charsets.UTF_8))
			} != null
		}.getOrDefault(false)
	}

	// 只统计 daily-control-backup- 前缀的自动备份，按入库时间倒序删除超出保留数的旧件
	private fun trimOldAutoBackups(context: Context) {
		runCatching {
			val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
			val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE 'daily-control-backup-%'"
			context.contentResolver.query(
				collection,
				arrayOf(MediaStore.MediaColumns._ID),
				selection,
				null,
				"${MediaStore.MediaColumns.DATE_ADDED} DESC"
			)?.use { cursor ->
				val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
				var rank = 0
				val toDelete = mutableListOf<Uri>()
				while (cursor.moveToNext()) {
					rank++
					if (rank > KEEP_AUTO_BACKUPS) {
						toDelete.add(Uri.withAppendedPath(collection, cursor.getLong(idCol).toString()))
					}
				}
				toDelete.forEach { context.contentResolver.delete(it, null, null) }
			}
		}
	}

	// ---------- 周排程（含防「天天开应用把备份无限推后」的存档机制） ----------

	// 到点触发：执行备份，然后把下一棒存档并排到一周后
	suspend fun rollWeeklySchedule(context: Context) {
		val next = System.currentTimeMillis() + WEEK_MILLIS
		SettingsRepository.get(context).setNextAutoBackupAt(next)
		ReminderScheduler.schedule(context, ReminderReason.BACKUP_WEEKLY, next)
	}

	// 启动/开机自愈：按存档的下一棒时刻幂等重排。
	// 从未安排 → 一天后首次备份；存档时刻已过（错过）→ 一小时内补做；未到点 → 按原时刻重排。
	// 不做「每次启动一律 now+7天」：天天开应用会把备份无限推后，永远不触发
	suspend fun ensureScheduledOnStartup(context: Context) {
		val repo = SettingsRepository.get(context)
		val stored = repo.nextAutoBackupAt.first()
		val now = System.currentTimeMillis()
		val target = when {
			stored <= 0L -> now + DAY_MILLIS
			stored <= now -> now + HOUR_MILLIS
			else -> stored
		}
		repo.setNextAutoBackupAt(target)
		ReminderScheduler.schedule(context, ReminderReason.BACKUP_WEEKLY, target)
	}

	// ---------- 恢复 ----------

	// 下载目录里一个备份文件的索引信息（恢复清单用；内容按需再读）
	data class BackupFileInfo(
		val uri: Uri,
		val displayName: String,
		val addedAtMillis: Long
	)

	// 检索下载目录里本应用的备份：按命名前缀识别（自动备份、恢复前快照、
	// 手动导出到 Download 的文件全部命中），按入库时间倒序。
	// 只会看到本应用自己创建的文件（MediaStore 对非媒体文件按归属过滤），无需存储权限
	fun listDownloadBackups(context: Context): List<BackupFileInfo> {
		val result = mutableListOf<BackupFileInfo>()
		runCatching {
			val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
			val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE 'daily-control-%'"
			context.contentResolver.query(
				collection,
				arrayOf(
					MediaStore.MediaColumns._ID,
					MediaStore.MediaColumns.DISPLAY_NAME,
					MediaStore.MediaColumns.DATE_ADDED
				),
				selection,
				null,
				"${MediaStore.MediaColumns.DATE_ADDED} DESC"
			)?.use { cursor ->
				val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
				val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
				val addedCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
				while (cursor.moveToNext()) {
					result.add(
						BackupFileInfo(
							uri = Uri.withAppendedPath(collection, cursor.getLong(idCol).toString()),
							displayName = cursor.getString(nameCol) ?: "?",
							addedAtMillis = cursor.getLong(addedCol) * 1000L
						)
					)
				}
			}
		}
		return result
	}

	// 读取单个备份并解析为摘要；坏文件抛 BackupFormatException（列表行标「无法读取」）
	fun readSummary(context: Context, uri: Uri): BackupSummary {
		val text = context.contentResolver.openInputStream(uri)?.use {
			it.readBytes().toString(Charsets.UTF_8)
		} ?: throw BackupFormatException("文件无法读取")
		return parseAndValidate(text)
	}

	// 解析并校验备份文件：坏文件抛 BackupFormatException（界面转成提示，不崩溃）
	fun parseAndValidate(jsonText: String): BackupSummary {
		val root = try {
			JSONObject(jsonText)
		} catch (e: JSONException) {
			throw BackupFormatException("不是有效的 JSON 文件")
		}
		if (root.optInt("formatVersion", -1) != FORMAT_VERSION) {
			throw BackupFormatException("不支持的备份格式版本（需要 v$FORMAT_VERSION）")
		}
		val data = root.optJSONObject("data") ?: throw BackupFormatException("备份缺少数据区")
		return try {
			val diets = data.optJSONArray("dietRecords")?.toRecordList { dietFromJson(it) } ?: emptyList()
			val nutrients = data.optJSONArray("nutrientItems")?.toRecordList { nutrientFromJson(it) } ?: emptyList()
			val exercises = data.optJSONArray("exerciseRecords")?.toRecordList { exerciseFromJson(it) } ?: emptyList()
			val todos = data.optJSONArray("todoTasks")?.toRecordList { todoFromJson(it) } ?: emptyList()
			val settings = HashMap<String, String>()
			root.optJSONObject("settings")?.let { obj ->
				obj.keys().forEach { key -> settings[key] = obj.optString(key) }
			}
			BackupSummary(
				exportedAt = root.optLong("exportedAt", 0L),
				appVersion = root.optString("appVersion", "?"),
				dietRecords = diets,
				nutrientItems = nutrients,
				exerciseRecords = exercises,
				todoTasks = todos,
				settings = settings
			)
		} catch (e: JSONException) {
			throw BackupFormatException("数据结构不完整")
		}
	}

	// 执行恢复：快照 → 事务整体替换 → 覆盖设置 → 重排提醒
	suspend fun restore(context: Context, summary: BackupSummary) {
		val db = DailyControlDatabase.get(context)
		snapshotBeforeRestore(context)
		// 整体替换：先清子表再清主表（外键），写入保留原 id（营养素行的归属关系、待办闹钟映射都靠 id）
		db.withTransaction {
			db.nutrientItemDao().clearAll()
			db.dietRecordDao().clearAll()
			db.exerciseRecordDao().clearAll()
			db.todoTaskDao().clearAll()
			db.dietRecordDao().insertAll(summary.dietRecords)
			db.nutrientItemDao().insertAll(summary.nutrientItems)
			db.exerciseRecordDao().insertAll(summary.exerciseRecords)
			db.todoTaskDao().insertAll(summary.todoTasks)
		}
		SettingsRepository.get(context).restoreSettings(summary.settings)
		// 恢复的数据可能改变各类提醒的依据：五类全部重排
		DailyPlanner.rescheduleMeals(context)
		DailyPlanner.reschedulePlan(context)
		DailyPlanner.rescheduleTodoTasks(context)
		VocabPlanner.reschedule(context)
		WakePlanner.rescheduleFromStore(context)
	}

	private fun appVersionName(context: Context): String {
		return runCatching {
			context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
		}.getOrDefault("?")
	}

	// JSONArray → 实体列表的小工具（JSONArray 没有 map，手动遍历）
	private fun <T> JSONArray.toRecordList(mapper: (JSONObject) -> T): List<T> {
		return List(length()) { i -> mapper(getJSONObject(i)) }
	}
}

// 恢复预览：解析结果一次性携带（界面弹确认框展示条数，确认后原样交给 restore）
data class BackupSummary(
	val exportedAt: Long,
	val appVersion: String,
	val dietRecords: List<DietRecord>,
	val nutrientItems: List<NutrientItem>,
	val exerciseRecords: List<ExerciseRecord>,
	val todoTasks: List<TodoTask>,
	val settings: Map<String, String>
)

// 备份文件无法识别/不兼容时抛出，由界面转成用户可读的提示
class BackupFormatException(message: String) : Exception(message)

// ---------- 实体 ↔ JSON 的字段映射（独立顶层函数，实体类保持干净） ----------

private fun dietToJson(record: DietRecord): JSONObject {
	return JSONObject()
		.put("id", record.id)
		.put("mealType", record.mealType)
		.put("foodText", record.foodText)
		.put("portionText", record.portionText)
		.put("eatenAt", record.eatenAt)
		.put("recordedAt", record.recordedAt)
		.put("weightText", record.weightText ?: JSONObject.NULL)
		.put("nutrientText", record.nutrientText ?: JSONObject.NULL)
}

private fun dietFromJson(obj: JSONObject): DietRecord {
	return DietRecord(
		id = obj.optLong("id", 0L),
		mealType = obj.optString("mealType", "snack"),
		foodText = obj.optString("foodText", ""),
		portionText = obj.optString("portionText", ""),
		eatenAt = obj.optLong("eatenAt", 0L),
		recordedAt = obj.optLong("recordedAt", 0L),
		weightText = if (obj.isNull("weightText")) null else obj.optString("weightText"),
		nutrientText = if (obj.isNull("nutrientText")) null else obj.optString("nutrientText")
	)
}

private fun nutrientToJson(item: NutrientItem): JSONObject {
	return JSONObject()
		.put("id", item.id)
		.put("dietRecordId", item.dietRecordId)
		.put("name", item.name)
		.put("amountText", item.amountText)
}

private fun nutrientFromJson(obj: JSONObject): NutrientItem {
	return NutrientItem(
		id = obj.optLong("id", 0L),
		dietRecordId = obj.optLong("dietRecordId", 0L),
		name = obj.optString("name", ""),
		amountText = obj.optString("amountText", "")
	)
}

private fun exerciseToJson(record: ExerciseRecord): JSONObject {
	return JSONObject()
		.put("id", record.id)
		.put("typeText", record.typeText)
		.put("intensityText", record.intensityText)
		.put("totalText", record.totalText)
		.put("startAt", record.startAt)
		.put("endAt", record.endAt)
		.put("recordedAt", record.recordedAt)
}

private fun exerciseFromJson(obj: JSONObject): ExerciseRecord {
	return ExerciseRecord(
		id = obj.optLong("id", 0L),
		typeText = obj.optString("typeText", ""),
		intensityText = obj.optString("intensityText", ""),
		totalText = obj.optString("totalText", ""),
		startAt = obj.optLong("startAt", 0L),
		endAt = obj.optLong("endAt", 0L),
		recordedAt = obj.optLong("recordedAt", 0L)
	)
}

private fun todoToJson(task: TodoTask): JSONObject {
	return JSONObject()
		.put("id", task.id)
		.put("taskDateEpochDay", task.taskDateEpochDay)
		.put("title", task.title)
		.put("startAtMillis", task.startAtMillis ?: JSONObject.NULL)
		.put("remindAheadMinutes", task.remindAheadMinutes)
		.put("done", task.done)
		.put("createdAt", task.createdAt)
}

private fun todoFromJson(obj: JSONObject): TodoTask {
	return TodoTask(
		id = obj.optLong("id", 0L),
		taskDateEpochDay = obj.optLong("taskDateEpochDay", 0L),
		title = obj.optString("title", ""),
		startAtMillis = if (obj.isNull("startAtMillis")) null else obj.optLong("startAtMillis", 0L),
		remindAheadMinutes = obj.optInt("remindAheadMinutes", 15),
		done = obj.optBoolean("done", false),
		createdAt = obj.optLong("createdAt", 0L)
	)
}
