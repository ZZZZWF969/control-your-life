package com.dailycontrol.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dailycontrol.app.data.entity.AiReport
import kotlinx.coroutines.flow.Flow

// AI 报告的数据访问接口：生成器写入，AI 页订阅展示
@Dao
interface AiReportDao {

	// 同一归属日重新生成时整行替换（唯一索引兜底）
	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun insert(report: AiReport): Long

	// 报告列表：只取保留窗内的，新→旧；Flow 使界面在生成/清理后自动刷新
	@Query("SELECT * FROM ai_report WHERE reportDateEpochDay >= :fromEpochDay ORDER BY reportDateEpochDay DESC")
	fun observeRecent(fromEpochDay: Long): Flow<List<AiReport>>

	@Query("SELECT * FROM ai_report WHERE reportDateEpochDay = :epochDay LIMIT 1")
	suspend fun getByDate(epochDay: Long): AiReport?

	// 最近若干份「解析成功」的报告：提示词据此附「近几天已给过的建议」防重复
	@Query("SELECT * FROM ai_report WHERE parseOk = 1 ORDER BY reportDateEpochDay DESC LIMIT :count")
	suspend fun recentParsed(count: Int): List<AiReport>

	// 滚动清理：删除保留窗之前的旧报告
	@Query("DELETE FROM ai_report WHERE reportDateEpochDay < :beforeEpochDay")
	suspend fun deleteOlderThan(beforeEpochDay: Long)

	// 调试与测试灌数用
	@Query("DELETE FROM ai_report")
	suspend fun clearAll()
}
