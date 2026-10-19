package com.silentstudy.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class AnswerBlock(
    val type: String,     // "text", "math", "step"
    val display: String,  // Formatted written form (LaTeX / numbered step)
    val spoken: String,   // Natural language verbalized reading for earbud TTS
    val stepNumber: Int? = null
)

@Entity(tableName = "answers")
@TypeConverters(AnswerConverters::class)
data class Answer(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,               // Permanent auto-incrementing ID (#1, #2, #3...)
    val subject: String,            // Mathematics, Physics, Chemistry, Biology...
    val questionText: String,
    val imagePaths: List<String>,   // URIs or file paths to captured photos
    val shortAnswer: String,        // 1-2 sentence TL;DR
    val blocks: List<AnswerBlock>,
    val simpleExplanation: String,  // Explain-like-I'm-new
    val starred: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val lastPlayedAt: Long? = null,
    val timesReviewed: Int = 0,
    val timesCorrect: Int = 0,
    val photoHash: String? = null,  // Deduplication hash
    val status: String = "completed", // "completed", "pending_offline", "failed"
    val errorMessage: String? = null
)

@Entity(tableName = "api_usage_logs")
data class ApiUsageLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val provider: String,
    val model: String,
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int,
    val estimatedCostUsd: Double
)

class AnswerConverters {
    private val gson = Gson()

    @TypeConverter
    fun fromBlockList(blocks: List<AnswerBlock>?): String {
        return gson.toJson(blocks ?: emptyList<AnswerBlock>())
    }

    @TypeConverter
    fun toBlockList(json: String?): List<AnswerBlock> {
        if (json.isNullOrEmpty()) return emptyList()
        val type = object : TypeToken<List<AnswerBlock>>() {}.type
        return gson.fromJson(json, type)
    }

    @TypeConverter
    fun fromStringList(list: List<String>?): String {
        return gson.toJson(list ?: emptyList<String>())
    }

    @TypeConverter
    fun toStringList(json: String?): List<String> {
        if (json.isNullOrEmpty()) return emptyList()
        val type = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(json, type)
    }
}
