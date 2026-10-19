package com.silentstudy.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.silentstudy.data.model.ApiUsageLog
import kotlinx.coroutines.flow.Flow

@Dao
interface UsageDao {
    @Insert
    suspend fun insertLog(log: ApiUsageLog): Long

    @Query("SELECT * FROM api_usage_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<ApiUsageLog>>

    @Query("SELECT SUM(totalTokens) FROM api_usage_logs WHERE timestamp >= :sinceTimestamp")
    suspend fun getTotalTokensSince(sinceTimestamp: Long): Long?

    @Query("SELECT SUM(estimatedCostUsd) FROM api_usage_logs WHERE timestamp >= :sinceTimestamp")
    suspend fun getTotalCostSince(sinceTimestamp: Long): Double?

    @Query("DELETE FROM api_usage_logs")
    suspend fun clearLogs()
}
