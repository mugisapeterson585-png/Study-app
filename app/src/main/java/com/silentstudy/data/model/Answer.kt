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

data class AnswerPart(
    val label: String = "",   // e.g. "Part 1", "(a)", "Final Result"
    val display: String = "", // Written formula / value
    val spoken: String = ""   // Spoken in earbuds phonetically
)

data class TableData(
    val title: String = "",
    val headers: List<String> = emptyList(),
    val rows: List<List<String>> = emptyList()
)

data class GraphPoint(
    val x: Double = 0.0,
    val y: Double = 0.0,
    val label: String? = null
)

data class GraphSeries(
    val name: String = "",
    val points: List<GraphPoint> = emptyList()
)

data class GraphAxis(
    val label: String = "",
    val unit: String = ""
)

data class GraphData(
    val type: String = "line", // "line", "bar", "pie", "scatter", "histogram"
    val title: String = "",
    val xAxis: GraphAxis? = null,
    val yAxis: GraphAxis? = null,
    val series: List<GraphSeries> = emptyList(),
    val keyPoints: List<String> = emptyList(),
    val trend: String = "",
    val isEstimated: Boolean = true
)

@Entity(tableName = "answers")
@TypeConverters(AnswerConverters::class)
data class Answer(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,               // Permanent auto-incrementing ID (#1, #2, #3...)
    val subject: String,            // Mathematics, Physics, Chemistry, Biology...
    val questionText: String,
    val imagePaths: List<String>,   // URIs or file paths to captured photos
    val shortAnswer: String,        // 1-2 sentence TL;DR direct answer
    val blocks: List<AnswerBlock> = emptyList(),
    val simpleExplanation: String = "",  // Explain-like-I'm-new (only generated when requested)
    val answerParts: List<AnswerPart> = emptyList(),
    val table: TableData? = null,
    val graph: GraphData? = null,
    val answerStyle: String = "direct", // "direct", "short_reason", "show_working"
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
    fun fromAnswerPartList(parts: List<AnswerPart>?): String {
        return gson.toJson(parts ?: emptyList<AnswerPart>())
    }

    @TypeConverter
    fun toAnswerPartList(json: String?): List<AnswerPart> {
        if (json.isNullOrEmpty()) return emptyList()
        val type = object : TypeToken<List<AnswerPart>>() {}.type
        return gson.fromJson(json, type)
    }

    @TypeConverter
    fun fromTableData(table: TableData?): String {
        return gson.toJson(table)
    }

    @TypeConverter
    fun toTableData(json: String?): TableData? {
        if (json.isNullOrEmpty() || json == "null") return null
        return gson.fromJson(json, TableData::class.java)
    }

    @TypeConverter
    fun fromGraphData(graph: GraphData?): String {
        return gson.toJson(graph)
    }

    @TypeConverter
    fun toGraphData(json: String?): GraphData? {
        if (json.isNullOrEmpty() || json == "null") return null
        return gson.fromJson(json, GraphData::class.java)
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
