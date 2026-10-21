package com.silentstudy.ai

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.silentstudy.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

data class ModelInfo(
    val id: String,
    val name: String,
    val isVision: Boolean = true,
    val isFree: Boolean = false,
    val promptPricePerM: Double = 0.0,
    val completionPricePerM: Double = 0.0
)

data class TokenUsage(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0
)

data class AnswerRequest(
    val imagesBase64: List<String>,
    val promptNote: String? = null,
    val answerStyle: String = "direct", // "direct", "short_reason", "show_working"
    val captureMode: String = "auto",   // "auto", "direct", "table", "graph"
    val conciseMode: Boolean = true,
    val maxOutputTokens: Int = 400,
    val lazyExplanation: Boolean = true
)

data class AnswerResult(
    val subject: String,
    val questionText: String,
    val shortAnswer: String,
    val blocks: List<AnswerBlock> = emptyList(),
    val answerParts: List<AnswerPart> = emptyList(),
    val table: TableData? = null,
    val graph: GraphData? = null,
    val simpleExplanation: String = "",
    val usage: TokenUsage = TokenUsage(),
    val rawModelUsed: String = ""
)

data class ConnectionResult(
    val success: Boolean,
    val message: String,
    val latencyMs: Long,
    val httpCode: Int? = null,
    val rawError: String? = null
)

class AiProviderManager {

    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(50, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    // In-memory cache for fetched models
    private val modelCache = mutableMapOf<String, List<ModelInfo>>()

    private fun buildSystemInstruction(answerStyle: String, captureMode: String): String {
        val styleInstruction = when (answerStyle.lowercase()) {
            "show_working" -> """
                ANSWER STYLE: Show working. Provide the final answer first, followed by clear numbered steps or working.
            """.trimIndent()
            "short_reason" -> """
                ANSWER STYLE: Direct answer + short reason. Output the direct final answer, and exactly one brief sentence explaining why.
            """.trimIndent()
            else -> """
                CRITICAL - DIRECT ANSWER MODE:
                Output ONLY the final answer. No explanation, no restating the question, no reasoning, no working, no introductions, no 'the answer is'.
                - For multiple choice: give ONLY the letter and answer text (e.g. 'B. Photosynthesis').
                - For short questions: give ONLY the direct answer.
                - For equations, calculations, or multi-part questions: give the FINAL result only, broken into short sequential parts in 'answer_parts' so the student can easily hear and write it down.
                  Example: Part 1: x = 3.5, Part 2: y = -2.
                  Do NOT explain how to get the answer. Do NOT show steps.
                - For questions with sub-questions: label parts as '(a)', '(b)', etc.
            """.trimIndent()
        }

        val tableGraphInstruction = when (captureMode.lowercase()) {
            "table" -> """
                FOCUS: The captured image is a TABLE. Extract all structured table data into the "table" JSON field with title, headers, and rows. If asked a specific question about the table, provide the straight answer first in 'short_answer' and 'answer_parts', and also extract the table.
            """.trimIndent()
            "graph" -> """
                FOCUS: The captured image is a GRAPH or CHART. Extract all structured data into the "graph" JSON field with type, title, x_axis, y_axis, series (with numerical points), key_points, and trend. If asked a question about the graph, provide the straight answer first in 'short_answer', followed by key values.
            """.trimIndent()
            else -> """
                If the image contains a TABLE, extract structured table data in the optional "table" field.
                If the image contains a GRAPH/CHART (line, bar, pie, scatter, histogram), extract structured chart data in the optional "graph" field.
            """.trimIndent()
        }

        return """
            You are SilentStudy, an ultra-fast private library study assistant.
            $styleInstruction
            $tableGraphInstruction

            Return STRICT RAW JSON with this schema (no markdown formatting, no code blocks):
            {
              "subject": "Mathematics" | "Physics" | "Chemistry" | "Biology" | "History" | "General",
              "question_text": "Exact text of the question",
              "short_answer": "Final direct answer in 1 sentence or formula",
              "answer_parts": [
                {
                  "label": "Part 1" | "(a)" | "Result",
                  "display": "Formatted written form (e.g. 'x = 3.5')",
                  "spoken": "Natural language phonetic pronunciation for earbud TTS without LaTeX symbols (e.g. 'x equals three point five')"
                }
              ],
              "table": {
                "title": "Table Title",
                "headers": ["Header 1", "Header 2"],
                "rows": [["Val 1", "Val 2"]]
              },
              "graph": {
                "type": "line" | "bar" | "pie" | "scatter" | "histogram",
                "title": "Graph Title",
                "x_axis": {"label": "X Label", "unit": "Unit"},
                "y_axis": {"label": "Y Label", "unit": "Unit"},
                "series": [
                  {
                    "name": "Series 1",
                    "points": [{"x": 0.0, "y": 0.0, "label": "Origin"}]
                  }
                ],
                "key_points": ["Highest point at ...", "Intercept at ..."],
                "trend": "Overall trend description"
              }
            }
        """.trimIndent()
    }

    /**
     * Live Model List Fetching from providers
     */
    suspend fun fetchModels(
        provider: String,
        apiKey: String,
        baseUrl: String
    ): List<ModelInfo> = withContext(Dispatchers.IO) {
        val cacheKey = "${provider.lowercase()}_${apiKey.hashCode()}_${baseUrl}"
        modelCache[cacheKey]?.let { if (it.isNotEmpty()) return@withContext it }

        try {
            val list = when (provider.lowercase()) {
                "gemini" -> fetchGeminiModels(apiKey)
                "openai" -> fetchOpenAiModels(apiKey)
                "claude" -> fetchAnthropicModels(apiKey)
                "openrouter" -> fetchOpenRouterModels()
                "custom" -> fetchCustomModels(baseUrl, apiKey)
                else -> fetchGeminiModels(apiKey)
            }
            if (list.isNotEmpty()) {
                modelCache[cacheKey] = list
            }
            list
        } catch (e: Exception) {
            modelCache[cacheKey] ?: getDefaultFallbackModels(provider)
        }
    }

    private fun fetchGeminiModels(apiKey: String): List<ModelInfo> {
        if (apiKey.isBlank()) return getDefaultFallbackModels("gemini")
        val url = "https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey"
        val req = Request.Builder().url(url).get().build()
        val res = client.newCall(req).execute()
        if (!res.isSuccessful) throw IOException("HTTP ${res.code}: ${res.message}")

        val json = res.body?.string() ?: return getDefaultFallbackModels("gemini")
        val root = gson.fromJson(json, JsonObject::class.java)
        val modelsArr = root.getAsJsonArray("models") ?: return getDefaultFallbackModels("gemini")

        val result = mutableListOf<ModelInfo>()
        for (item in modelsArr) {
            val obj = item.asJsonObject
            val rawName = obj.get("name")?.asString ?: continue
            val id = rawName.removePrefix("models/")
            val displayName = obj.get("displayName")?.asString ?: id

            val methods = obj.getAsJsonArray("supportedGenerationMethods")
            val supportsContent = methods?.any { it.asString == "generateContent" } ?: false
            if (!supportsContent) continue

            val isVision = id.contains("flash") || id.contains("pro") || id.contains("vision") || id.contains("2.0") || id.contains("2.5") || id.contains("1.5")
            val isFree = id.contains("flash")
            result.add(
                ModelInfo(
                    id = id,
                    name = displayName,
                    isVision = isVision,
                    isFree = isFree,
                    promptPricePerM = if (id.contains("flash")) 0.10 else 1.25,
                    completionPricePerM = if (id.contains("flash")) 0.40 else 5.00
                )
            )
        }
        return if (result.isNotEmpty()) result else getDefaultFallbackModels("gemini")
    }

    private fun fetchOpenAiModels(apiKey: String): List<ModelInfo> {
        if (apiKey.isBlank()) return getDefaultFallbackModels("openai")
        val url = "https://api.openai.com/v1/models"
        val req = Request.Builder().url(url).header("Authorization", "Bearer $apiKey").get().build()
        val res = client.newCall(req).execute()
        if (!res.isSuccessful) throw IOException("HTTP ${res.code}: ${res.message}")

        val json = res.body?.string() ?: return getDefaultFallbackModels("openai")
        val root = gson.fromJson(json, JsonObject::class.java)
        val dataArr = root.getAsJsonArray("data") ?: return getDefaultFallbackModels("openai")

        val result = mutableListOf<ModelInfo>()
        for (item in dataArr) {
            val id = item.asJsonObject.get("id")?.asString ?: continue
            if (!id.contains("gpt") && !id.contains("o1")) continue

            val isVision = id.contains("4o") || id.contains("vision") || id.contains("o1")
            val (inPrice, outPrice) = when {
                id.contains("mini") -> Pair(0.15, 0.60)
                id.contains("4o") -> Pair(2.50, 10.00)
                id.contains("o1") -> Pair(15.00, 60.00)
                else -> Pair(0.50, 1.50)
            }
            result.add(
                ModelInfo(
                    id = id,
                    name = id,
                    isVision = isVision,
                    isFree = false,
                    promptPricePerM = inPrice,
                    completionPricePerM = outPrice
                )
            )
        }
        return if (result.isNotEmpty()) result else getDefaultFallbackModels("openai")
    }

    private fun fetchAnthropicModels(apiKey: String): List<ModelInfo> {
        if (apiKey.isBlank()) return getDefaultFallbackModels("claude")
        try {
            val url = "https://api.anthropic.com/v1/models"
            val req = Request.Builder()
                .url(url)
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .get().build()
            val res = client.newCall(req).execute()
            if (res.isSuccessful) {
                val json = res.body?.string() ?: ""
                val root = gson.fromJson(json, JsonObject::class.java)
                val data = root.getAsJsonArray("data")
                if (data != null && data.size() > 0) {
                    val list = mutableListOf<ModelInfo>()
                    for (item in data) {
                        val id = item.asJsonObject.get("id")?.asString ?: continue
                        val name = item.asJsonObject.get("display_name")?.asString ?: id
                        list.add(
                            ModelInfo(
                                id = id,
                                name = name,
                                isVision = true,
                                isFree = false,
                                promptPricePerM = if (id.contains("haiku")) 0.80 else 3.00,
                                completionPricePerM = if (id.contains("haiku")) 4.00 else 15.00
                            )
                        )
                    }
                    if (list.isNotEmpty()) return list
                }
            }
        } catch (_: Exception) {}
        return getDefaultFallbackModels("claude")
    }

    private fun fetchOpenRouterModels(): List<ModelInfo> {
        val url = "https://openrouter.ai/api/v1/models"
        val req = Request.Builder().url(url).get().build()
        val res = client.newCall(req).execute()
        if (!res.isSuccessful) throw IOException("HTTP ${res.code}: ${res.message}")

        val json = res.body?.string() ?: return getDefaultFallbackModels("openrouter")
        val root = gson.fromJson(json, JsonObject::class.java)
        val dataArr = root.getAsJsonArray("data") ?: return getDefaultFallbackModels("openrouter")

        val result = mutableListOf<ModelInfo>()
        for (item in dataArr) {
            val obj = item.asJsonObject
            val id = obj.get("id")?.asString ?: continue
            val name = obj.get("name")?.asString ?: id

            val architecture = obj.getAsJsonObject("architecture")
            val modality = architecture?.get("modality")?.asString ?: ""
            val isVision = modality.contains("image->text") ||
                    modality.contains("multimodal") ||
                    id.contains("vision") || id.contains("flash") || id.contains("4o")

            val pricing = obj.getAsJsonObject("pricing")
            val promptRate = pricing?.get("prompt")?.asDouble ?: 0.0
            val compRate = pricing?.get("completion")?.asDouble ?: 0.0
            val promptPerM = promptRate * 1_000_000.0
            val compPerM = compRate * 1_000_000.0
            val isFree = id.endsWith(":free") || (promptRate == 0.0 && compRate == 0.0)

            result.add(
                ModelInfo(
                    id = id,
                    name = name,
                    isVision = isVision,
                    isFree = isFree,
                    promptPricePerM = promptPerM,
                    completionPricePerM = compPerM
                )
            )
        }
        return if (result.isNotEmpty()) result else getDefaultFallbackModels("openrouter")
    }

    private fun fetchCustomModels(baseUrl: String, apiKey: String): List<ModelInfo> {
        val cleanBase = baseUrl.trimEnd('/')
        val url = if (cleanBase.endsWith("/v1")) "$cleanBase/models" else "$cleanBase/v1/models"
        val builder = Request.Builder().url(url).get()
        if (apiKey.isNotBlank()) builder.header("Authorization", "Bearer $apiKey")
        return try {
            val res = client.newCall(builder.build()).execute()
            if (!res.isSuccessful) return getDefaultFallbackModels("custom")
            val json = res.body?.string() ?: return getDefaultFallbackModels("custom")
            val root = gson.fromJson(json, JsonObject::class.java)
            val dataArr = root.getAsJsonArray("data") ?: return getDefaultFallbackModels("custom")

            val list = mutableListOf<ModelInfo>()
            for (item in dataArr) {
                val id = item.asJsonObject.get("id")?.asString ?: continue
                list.add(ModelInfo(id = id, name = id, isVision = true, isFree = true))
            }
            if (list.isNotEmpty()) list else getDefaultFallbackModels("custom")
        } catch (_: Exception) {
            getDefaultFallbackModels("custom")
        }
    }

    fun getDefaultFallbackModels(provider: String): List<ModelInfo> {
        return when (provider.lowercase()) {
            "gemini" -> listOf(
                ModelInfo("gemini-2.5-flash", "Gemini 2.5 Flash (Fast Vision)", isVision = true, isFree = true, promptPricePerM = 0.10, completionPricePerM = 0.40),
                ModelInfo("gemini-2.0-flash", "Gemini 2.0 Flash", isVision = true, isFree = true, promptPricePerM = 0.10, completionPricePerM = 0.40),
                ModelInfo("gemini-1.5-flash", "Gemini 1.5 Flash", isVision = true, isFree = true, promptPricePerM = 0.075, completionPricePerM = 0.30),
                ModelInfo("gemini-1.5-pro", "Gemini 1.5 Pro", isVision = true, isFree = false, promptPricePerM = 1.25, completionPricePerM = 5.00)
            )
            "openai" -> listOf(
                ModelInfo("gpt-4o-mini", "GPT-4o Mini (Fast Vision)", isVision = true, isFree = false, promptPricePerM = 0.15, completionPricePerM = 0.60),
                ModelInfo("gpt-4o", "GPT-4o (Omni Vision)", isVision = true, isFree = false, promptPricePerM = 2.50, completionPricePerM = 10.00),
                ModelInfo("o1", "o1 Reasoning Vision", isVision = true, isFree = false, promptPricePerM = 15.00, completionPricePerM = 60.00)
            )
            "claude" -> listOf(
                ModelInfo("claude-3-5-haiku-20241022", "Claude 3.5 Haiku", isVision = true, isFree = false, promptPricePerM = 0.80, completionPricePerM = 4.00),
                ModelInfo("claude-3-5-sonnet-20241022", "Claude 3.5 Sonnet", isVision = true, isFree = false, promptPricePerM = 3.00, completionPricePerM = 15.00)
            )
            "openrouter" -> listOf(
                ModelInfo("google/gemini-2.0-flash-001", "Gemini 2.0 Flash (OpenRouter)", isVision = true, isFree = false, promptPricePerM = 0.10, completionPricePerM = 0.40),
                ModelInfo("meta-llama/llama-3.2-11b-vision-instruct:free", "Llama 3.2 11B Vision (Free)", isVision = true, isFree = true, promptPricePerM = 0.0, completionPricePerM = 0.0),
                ModelInfo("anthropic/claude-3.5-haiku", "Claude 3.5 Haiku", isVision = true, isFree = false, promptPricePerM = 0.80, completionPricePerM = 4.00)
            )
            else -> listOf(
                ModelInfo("llama3.2-vision", "Llama 3.2 Vision (Local)", isVision = true, isFree = true),
                ModelInfo("llava", "LLaVA MultiModal", isVision = true, isFree = true)
            )
        }
    }

    /**
     * Test connection with plain-language diagnostic messages and fix hints
     */
    suspend fun testConnection(
        provider: String,
        apiKey: String,
        model: String,
        baseUrl: String
    ): ConnectionResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            val response = when (provider.lowercase()) {
                "gemini" -> {
                    val cleanModel = model.removePrefix("models/")
                    val url = "https://generativelanguage.googleapis.com/v1beta/models/$cleanModel:generateContent?key=$apiKey"
                    val json = """{"contents":[{"parts":[{"text":"Say OK"}]}],"generationConfig":{"maxOutputTokens":5}}"""
                    val req = Request.Builder().url(url).post(json.toRequestBody("application/json".toMediaType())).build()
                    client.newCall(req).execute()
                }
                "openai" -> {
                    val url = "https://api.openai.com/v1/chat/completions"
                    val json = """{"model":"$model","messages":[{"role":"user","content":"Say OK"}],"max_tokens":5}"""
                    val req = Request.Builder().url(url)
                        .header("Authorization", "Bearer $apiKey")
                        .post(json.toRequestBody("application/json".toMediaType())).build()
                    client.newCall(req).execute()
                }
                "claude" -> {
                    val url = "https://api.anthropic.com/v1/messages"
                    val json = """{"model":"$model","max_tokens":5,"messages":[{"role":"user","content":"Say OK"}]}"""
                    val req = Request.Builder().url(url)
                        .header("x-api-key", apiKey)
                        .header("anthropic-version", "2023-06-01")
                        .post(json.toRequestBody("application/json".toMediaType())).build()
                    client.newCall(req).execute()
                }
                "openrouter" -> {
                    val url = "https://openrouter.ai/api/v1/chat/completions"
                    val json = """{"model":"$model","messages":[{"role":"user","content":"Say OK"}],"max_tokens":5}"""
                    val req = Request.Builder().url(url)
                        .header("Authorization", "Bearer $apiKey")
                        .header("HTTP-Referer", "https://silentstudy.app")
                        .header("X-Title", "SilentStudy")
                        .post(json.toRequestBody("application/json".toMediaType())).build()
                    client.newCall(req).execute()
                }
                "custom" -> {
                    val cleanBase = baseUrl.trimEnd('/')
                    val url = "$cleanBase/chat/completions"
                    val json = """{"model":"$model","messages":[{"role":"user","content":"Say OK"}],"max_tokens":5}"""
                    val builder = Request.Builder().url(url)
                    if (apiKey.isNotBlank()) builder.header("Authorization", "Bearer $apiKey")
                    val req = builder.post(json.toRequestBody("application/json".toMediaType())).build()
                    client.newCall(req).execute()
                }
                else -> throw IllegalArgumentException("Unknown provider: $provider")
            }

            val latency = System.currentTimeMillis() - start
            val code = response.code

            if (response.isSuccessful) {
                ConnectionResult(true, "Connected successfully (${latency}ms)", latency, code)
            } else {
                val errBody = response.body?.string() ?: ""
                val friendlyMessage = when (code) {
                    401, 403 -> "API key is wrong or has no access. Check your key in Settings."
                    404 -> "Model '$model' not found (HTTP 404). Pick another model from the dropdown."
                    429 -> "Rate limit or no quota (HTTP 429). Try later or switch model."
                    in 500..599 -> "Provider problem (HTTP $code). The provider servers are down, try again."
                    else -> "Connection failed (HTTP $code): $errBody"
                }
                ConnectionResult(false, friendlyMessage, latency, code, errBody)
            }
        } catch (e: IOException) {
            val latency = System.currentTimeMillis() - start
            ConnectionResult(false, "No connection: Check your internet or device network.", latency, null, e.message)
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - start
            ConnectionResult(false, e.message ?: "Connection failed", latency, null, e.message)
        }
    }

    /**
     * Primary Answer Generation with automatic fallback support
     */
    suspend fun generateAnswerWithFallback(
        provider: String,
        apiKey: String,
        model: String,
        baseUrl: String,
        fallbackProvider: String?,
        fallbackApiKey: String?,
        fallbackModel: String?,
        request: AnswerRequest
    ): AnswerResult = withContext(Dispatchers.IO) {
        try {
            generateAnswer(provider, apiKey, model, baseUrl, request)
        } catch (e: Exception) {
            val shouldFallback = !fallbackProvider.isNullOrBlank() &&
                    !fallbackModel.isNullOrBlank() &&
                    (e.message?.contains("404") == true ||
                     e.message?.contains("429") == true ||
                     e.message?.contains("500") == true ||
                     e.message?.contains("502") == true ||
                     e.message?.contains("503") == true)

            if (shouldFallback) {
                generateAnswer(
                    provider = fallbackProvider!!,
                    apiKey = fallbackApiKey ?: "",
                    model = fallbackModel!!,
                    baseUrl = baseUrl,
                    request = request
                )
            } else {
                throw e
            }
        }
    }

    suspend fun generateAnswer(
        provider: String,
        apiKey: String,
        model: String,
        baseUrl: String,
        request: AnswerRequest
    ): AnswerResult = withContext(Dispatchers.IO) {
        when (provider.lowercase()) {
            "gemini" -> callGemini(apiKey, model, request)
            "openai" -> callOpenAi(apiKey, model, request)
            "claude" -> callClaude(apiKey, model, request)
            "openrouter" -> callOpenRouter(apiKey, model, request)
            "custom" -> callCustomOpenAi(apiKey, model, baseUrl, request)
            else -> callGemini(apiKey, model, request)
        }
    }

    suspend fun generateSimpleExplanation(
        provider: String,
        apiKey: String,
        model: String,
        baseUrl: String,
        questionText: String,
        shortAnswer: String
    ): String = withContext(Dispatchers.IO) {
        val prompt = "Question: $questionText\nAnswer: $shortAnswer\nProvide a very simple, intuitive conceptual explanation in 2-3 sentences for a beginner."
        generateQuickText(provider, apiKey, model, baseUrl, prompt, maxTokens = 350)
    }

    suspend fun generateQuizQuestion(
        provider: String,
        apiKey: String,
        model: String,
        baseUrl: String,
        questionText: String,
        shortAnswer: String
    ): String = withContext(Dispatchers.IO) {
        val prompt = "Based on this study question: $questionText ($shortAnswer), write ONE rapid audio flashcard question to test mastery."
        generateQuickText(provider, apiKey, model, baseUrl, prompt, maxTokens = 250)
    }

    suspend fun generateFollowUp(
        provider: String,
        apiKey: String,
        model: String,
        baseUrl: String,
        originalQuestion: String,
        followUpQuery: String
    ): String = withContext(Dispatchers.IO) {
        val prompt = "Regarding the question: '$originalQuestion'\nStudent asks follow-up: '$followUpQuery'\nProvide a concise direct answer in 2 sentences."
        generateQuickText(provider, apiKey, model, baseUrl, prompt, maxTokens = 350)
    }

    private suspend fun generateQuickText(
        provider: String,
        apiKey: String,
        model: String,
        baseUrl: String,
        prompt: String,
        maxTokens: Int
    ): String = withContext(Dispatchers.IO) {
        try {
            when (provider.lowercase()) {
                "gemini" -> {
                    val cleanModel = model.removePrefix("models/")
                    val url = "https://generativelanguage.googleapis.com/v1beta/models/$cleanModel:generateContent?key=$apiKey"
                    val json = """{"contents":[{"parts":[{"text":${gson.toJson(prompt)}}]}],"generationConfig":{"maxOutputTokens":$maxTokens}}"""
                    val req = Request.Builder().url(url).post(json.toRequestBody("application/json".toMediaType())).build()
                    val res = client.newCall(req).execute()
                    val body = res.body?.string() ?: ""
                    val root = gson.fromJson(body, JsonObject::class.java)
                    root.getAsJsonArray("candidates")?.get(0)?.asJsonObject
                        ?.getAsJsonObject("content")?.getAsJsonArray("parts")?.get(0)?.asJsonObject
                        ?.get("text")?.asString ?: "No response generated."
                }
                "openrouter" -> {
                    val url = "https://openrouter.ai/api/v1/chat/completions"
                    val json = """{"model":"$model","messages":[{"role":"user","content":${gson.toJson(prompt)}}],"max_tokens":$maxTokens}"""
                    val req = Request.Builder().url(url)
                        .header("Authorization", "Bearer $apiKey")
                        .header("HTTP-Referer", "https://silentstudy.app")
                        .header("X-Title", "SilentStudy")
                        .post(json.toRequestBody("application/json".toMediaType())).build()
                    val res = client.newCall(req).execute()
                    val body = res.body?.string() ?: ""
                    val root = gson.fromJson(body, JsonObject::class.java)
                    root.getAsJsonArray("choices")?.get(0)?.asJsonObject
                        ?.getAsJsonObject("message")?.get("content")?.asString ?: "No response generated."
                }
                else -> {
                    val url = "https://api.openai.com/v1/chat/completions"
                    val json = """{"model":"$model","messages":[{"role":"user","content":${gson.toJson(prompt)}}],"max_tokens":$maxTokens}"""
                    val req = Request.Builder().url(url)
                        .header("Authorization", "Bearer $apiKey")
                        .post(json.toRequestBody("application/json".toMediaType())).build()
                    val res = client.newCall(req).execute()
                    val body = res.body?.string() ?: ""
                    val root = gson.fromJson(body, JsonObject::class.java)
                    root.getAsJsonArray("choices")?.get(0)?.asJsonObject
                        ?.getAsJsonObject("message")?.get("content")?.asString ?: "No response generated."
                }
            }
        } catch (e: Exception) {
            "Unable to generate explanation: ${e.message}"
        }
    }

    private fun callGemini(apiKey: String, model: String, request: AnswerRequest): AnswerResult {
        val cleanModel = model.removePrefix("models/")
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$cleanModel:generateContent?key=$apiKey"

        val partsArray = mutableListOf<JsonObject>()
        val systemText = buildSystemInstruction(request.answerStyle, request.captureMode)
        partsArray.add(JsonObject().apply { addProperty("text", systemText) })

        request.promptNote?.let {
            partsArray.add(JsonObject().apply { addProperty("text", "Student note: $it") })
        }

        for (img in request.imagesBase64) {
            val inlineData = JsonObject().apply {
                addProperty("mimeType", "image/jpeg")
                addProperty("data", img.substringAfter("base64,"))
            }
            partsArray.add(JsonObject().apply { add("inlineData", inlineData) })
        }

        val contentObj = JsonObject().apply {
            add("parts", gson.toJsonTree(partsArray))
        }

        val maxTokens = if (request.answerStyle == "direct") 400 else request.maxOutputTokens
        val payload = JsonObject().apply {
            add("contents", gson.toJsonTree(listOf(contentObj)))
            val genConfig = JsonObject().apply {
                addProperty("responseMimeType", "application/json")
                addProperty("temperature", 0.1)
                addProperty("maxOutputTokens", maxTokens)
            }
            add("generationConfig", genConfig)
        }

        val body = payload.toString().toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url(url).post(body).build()
        val res = client.newCall(req).execute()

        if (!res.isSuccessful) {
            val err = res.body?.string() ?: ""
            throw IOException("Gemini HTTP ${res.code}: $err")
        }

        val jsonResponse = res.body?.string() ?: throw IOException("Empty Gemini response")
        val root = gson.fromJson(jsonResponse, JsonObject::class.java)
        val text = root.getAsJsonArray("candidates")
            ?.get(0)?.asJsonObject
            ?.getAsJsonObject("content")
            ?.getAsJsonArray("parts")
            ?.get(0)?.asJsonObject
            ?.get("text")?.asString ?: throw IOException("Invalid response structure from Gemini")

        val usageObj = root.getAsJsonObject("usageMetadata")
        val promptTokens = usageObj?.get("promptTokenCount")?.asInt ?: 0
        val compTokens = usageObj?.get("candidatesTokenCount")?.asInt ?: 0
        val totalTokens = usageObj?.get("totalTokenCount")?.asInt ?: (promptTokens + compTokens)

        return parseStructuredAnswer(text, TokenUsage(promptTokens, compTokens, totalTokens), cleanModel)
    }

    private fun callOpenAi(apiKey: String, model: String, request: AnswerRequest): AnswerResult {
        val url = "https://api.openai.com/v1/chat/completions"
        val messages = mutableListOf<JsonObject>()

        messages.add(JsonObject().apply {
            addProperty("role", "system")
            addProperty("content", buildSystemInstruction(request.answerStyle, request.captureMode))
        })

        val contentList = mutableListOf<JsonObject>()
        contentList.add(JsonObject().apply {
            addProperty("type", "text")
            addProperty("text", request.promptNote ?: "Solve and analyze this question.")
        })

        for (img in request.imagesBase64) {
            val urlObj = JsonObject().apply {
                val formatted = if (img.startsWith("data:")) img else "data:image/jpeg;base64,$img"
                addProperty("url", formatted)
            }
            contentList.add(JsonObject().apply {
                addProperty("type", "image_url")
                add("image_url", urlObj)
            })
        }

        messages.add(JsonObject().apply {
            addProperty("role", "user")
            add("content", gson.toJsonTree(contentList))
        })

        val maxTokens = if (request.answerStyle == "direct") 400 else request.maxOutputTokens
        val payload = JsonObject().apply {
            addProperty("model", model)
            add("messages", gson.toJsonTree(messages))
            val format = JsonObject().apply { addProperty("type", "json_object") }
            add("response_format", format)
            addProperty("temperature", 0.1)
            addProperty("max_tokens", maxTokens)
        }

        val body = payload.toString().toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url(url)
            .header("Authorization", "Bearer $apiKey")
            .post(body).build()
        val res = client.newCall(req).execute()

        if (!res.isSuccessful) {
            val err = res.body?.string() ?: ""
            throw IOException("OpenAI HTTP ${res.code}: $err")
        }

        val json = res.body?.string() ?: throw IOException("Empty response from OpenAI")
        val root = gson.fromJson(json, JsonObject::class.java)
        val text = root.getAsJsonArray("choices")
            ?.get(0)?.asJsonObject
            ?.getAsJsonObject("message")
            ?.get("content")?.asString ?: throw IOException("No choice content from OpenAI")

        val usageObj = root.getAsJsonObject("usage")
        val promptTokens = usageObj?.get("prompt_tokens")?.asInt ?: 0
        val compTokens = usageObj?.get("completion_tokens")?.asInt ?: 0
        val totalTokens = usageObj?.get("total_tokens")?.asInt ?: (promptTokens + compTokens)

        return parseStructuredAnswer(text, TokenUsage(promptTokens, compTokens, totalTokens), model)
    }

    private fun callClaude(apiKey: String, model: String, request: AnswerRequest): AnswerResult {
        val url = "https://api.anthropic.com/v1/messages"
        val contentList = mutableListOf<JsonObject>()

        for (img in request.imagesBase64) {
            val base64Clean = img.substringAfter("base64,")
            val sourceObj = JsonObject().apply {
                addProperty("type", "base64")
                addProperty("media_type", "image/jpeg")
                addProperty("data", base64Clean)
            }
            contentList.add(JsonObject().apply {
                addProperty("type", "image")
                add("source", sourceObj)
            })
        }

        val sysText = buildSystemInstruction(request.answerStyle, request.captureMode)
        contentList.add(JsonObject().apply {
            addProperty("type", "text")
            addProperty("text", "${request.promptNote ?: "Solve this question."}\n\n$sysText")
        })

        val messageObj = JsonObject().apply {
            addProperty("role", "user")
            add("content", gson.toJsonTree(contentList))
        }

        val maxTokens = if (request.answerStyle == "direct") 400 else request.maxOutputTokens
        val payload = JsonObject().apply {
            addProperty("model", model)
            addProperty("max_tokens", maxTokens)
            add("messages", gson.toJsonTree(listOf(messageObj)))
        }

        val body = payload.toString().toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url(url)
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .post(body).build()
        val res = client.newCall(req).execute()

        if (!res.isSuccessful) {
            val err = res.body?.string() ?: ""
            throw IOException("Claude HTTP ${res.code}: $err")
        }

        val json = res.body?.string() ?: throw IOException("Empty response from Claude")
        val root = gson.fromJson(json, JsonObject::class.java)
        val text = root.getAsJsonArray("content")
            ?.get(0)?.asJsonObject
            ?.get("text")?.asString ?: throw IOException("No text content from Claude")

        val usageObj = root.getAsJsonObject("usage")
        val promptTokens = usageObj?.get("input_tokens")?.asInt ?: 0
        val compTokens = usageObj?.get("output_tokens")?.asInt ?: 0
        val totalTokens = promptTokens + compTokens

        return parseStructuredAnswer(text, TokenUsage(promptTokens, compTokens, totalTokens), model)
    }

    private fun callOpenRouter(apiKey: String, model: String, request: AnswerRequest): AnswerResult {
        val url = "https://openrouter.ai/api/v1/chat/completions"
        val messages = mutableListOf<JsonObject>()

        messages.add(JsonObject().apply {
            addProperty("role", "system")
            addProperty("content", buildSystemInstruction(request.answerStyle, request.captureMode))
        })

        val contentList = mutableListOf<JsonObject>()
        contentList.add(JsonObject().apply {
            addProperty("type", "text")
            addProperty("text", request.promptNote ?: "Solve and analyze this question.")
        })

        for (img in request.imagesBase64) {
            val urlObj = JsonObject().apply {
                val formatted = if (img.startsWith("data:")) img else "data:image/jpeg;base64,$img"
                addProperty("url", formatted)
            }
            contentList.add(JsonObject().apply {
                addProperty("type", "image_url")
                add("image_url", urlObj)
            })
        }

        messages.add(JsonObject().apply {
            addProperty("role", "user")
            add("content", gson.toJsonTree(contentList))
        })

        val maxTokens = if (request.answerStyle == "direct") 400 else request.maxOutputTokens
        val payload = JsonObject().apply {
            addProperty("model", model)
            add("messages", gson.toJsonTree(messages))
            val format = JsonObject().apply { addProperty("type", "json_object") }
            add("response_format", format)
            addProperty("temperature", 0.1)
            addProperty("max_tokens", maxTokens)
        }

        val body = payload.toString().toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url(url)
            .header("Authorization", "Bearer $apiKey")
            .header("HTTP-Referer", "https://silentstudy.app")
            .header("X-Title", "SilentStudy")
            .post(body).build()
        val res = client.newCall(req).execute()

        if (!res.isSuccessful) {
            val err = res.body?.string() ?: ""
            throw IOException("OpenRouter HTTP ${res.code}: $err")
        }

        val json = res.body?.string() ?: throw IOException("Empty response from OpenRouter")
        val root = gson.fromJson(json, JsonObject::class.java)
        val text = root.getAsJsonArray("choices")
            ?.get(0)?.asJsonObject
            ?.getAsJsonObject("message")
            ?.get("content")?.asString ?: throw IOException("No choice content from OpenRouter")

        val usageObj = root.getAsJsonObject("usage")
        val promptTokens = usageObj?.get("prompt_tokens")?.asInt ?: 0
        val compTokens = usageObj?.get("completion_tokens")?.asInt ?: 0
        val totalTokens = usageObj?.get("total_tokens")?.asInt ?: (promptTokens + compTokens)

        return parseStructuredAnswer(text, TokenUsage(promptTokens, compTokens, totalTokens), model)
    }

    private fun callCustomOpenAi(apiKey: String, model: String, baseUrl: String, request: AnswerRequest): AnswerResult {
        val cleanBase = baseUrl.trimEnd('/')
        val url = "$cleanBase/chat/completions"

        val messages = mutableListOf<JsonObject>()
        messages.add(JsonObject().apply {
            addProperty("role", "system")
            addProperty("content", buildSystemInstruction(request.answerStyle, request.captureMode))
        })
        val userContent = JsonObject().apply {
            addProperty("role", "user")
            addProperty("content", request.promptNote ?: "Analyze and solve this question.")
        }
        messages.add(userContent)

        val maxTokens = if (request.answerStyle == "direct") 400 else request.maxOutputTokens
        val payload = JsonObject().apply {
            addProperty("model", model)
            add("messages", gson.toJsonTree(messages))
            addProperty("temperature", 0.1)
            addProperty("max_tokens", maxTokens)
        }

        val builder = Request.Builder().url(url)
        if (apiKey.isNotBlank()) builder.header("Authorization", "Bearer $apiKey")
        val req = builder.post(payload.toString().toRequestBody("application/json".toMediaType())).build()
        val res = client.newCall(req).execute()

        if (!res.isSuccessful) {
            val err = res.body?.string() ?: ""
            throw IOException("Custom server HTTP ${res.code}: $err")
        }

        val json = res.body?.string() ?: throw IOException("Empty response")
        val root = gson.fromJson(json, JsonObject::class.java)
        val text = root.getAsJsonArray("choices")
            ?.get(0)?.asJsonObject
            ?.getAsJsonObject("message")
            ?.get("content")?.asString ?: throw IOException("No content")

        val usageObj = root.getAsJsonObject("usage")
        val promptTokens = usageObj?.get("prompt_tokens")?.asInt ?: 0
        val compTokens = usageObj?.get("completion_tokens")?.asInt ?: 0
        val totalTokens = usageObj?.get("total_tokens")?.asInt ?: (promptTokens + compTokens)

        return parseStructuredAnswer(text, TokenUsage(promptTokens, compTokens, totalTokens), model)
    }

    /**
     * Highly tolerant JSON parser that handles tables, graphs, answer parts,
     * and fallbacks when fields are missing or malformed.
     */
    private fun parseStructuredAnswer(rawText: String, usage: TokenUsage, model: String): AnswerResult {
        var clean = rawText.trim()
        if (clean.startsWith("```json")) {
            clean = clean.removePrefix("```json").removeSuffix("```").trim()
        } else if (clean.startsWith("```")) {
            clean = clean.removePrefix("```").removeSuffix("```").trim()
        }

        val firstBrace = clean.indexOf('{')
        val lastBrace = clean.lastIndexOf('}')
        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            clean = clean.substring(firstBrace, lastBrace + 1)
        }

        var subject = "General"
        var questionText = "Photo Question"
        var shortAnswer = ""
        var simpleExplanation = ""
        val answerParts = mutableListOf<AnswerPart>()
        val blocks = mutableListOf<AnswerBlock>()
        var tableData: TableData? = null
        var graphData: GraphData? = null

        try {
            val obj = gson.fromJson(clean, JsonObject::class.java)
            subject = obj.get("subject")?.asString ?: "General"
            questionText = obj.get("question_text")?.asString ?: "Photo Question"
            shortAnswer = obj.get("short_answer")?.asString ?: ""
            simpleExplanation = obj.get("simple_explanation")?.asString ?: ""

            // 1. Parse answer_parts
            val partsArray = obj.getAsJsonArray("answer_parts")
            if (partsArray != null && partsArray.size() > 0) {
                for (p in partsArray) {
                    if (p.isJsonObject) {
                        val pObj = p.asJsonObject
                        val lbl = pObj.get("label")?.asString ?: ""
                        val disp = pObj.get("display")?.asString ?: ""
                        val spk = pObj.get("spoken")?.asString ?: disp
                        answerParts.add(AnswerPart(label = lbl, display = disp, spoken = spk))
                        blocks.add(AnswerBlock("step", disp, spk))
                    }
                }
            }

            // Fallback for blocks if answer_parts was missing
            val blocksArray = obj.getAsJsonArray("blocks")
            if (answerParts.isEmpty() && blocksArray != null && blocksArray.size() > 0) {
                for (elem in blocksArray) {
                    val b = elem.asJsonObject
                    val disp = b.get("display")?.asString ?: ""
                    val spk = b.get("spoken")?.asString ?: disp
                    val stepNum = b.get("stepNumber")?.asInt
                    val label = if (stepNum != null) "Part $stepNum" else "Step"
                    blocks.add(AnswerBlock(b.get("type")?.asString ?: "step", disp, spk, stepNum))
                    answerParts.add(AnswerPart(label = label, display = disp, spoken = spk))
                }
            }

            // 2. Parse Table
            val tableObj = obj.getAsJsonObject("table")
            if (tableObj != null) {
                val title = tableObj.get("title")?.asString ?: "Data Table"
                val headers = mutableListOf<String>()
                tableObj.getAsJsonArray("headers")?.forEach { headers.add(it.asString) }

                val rows = mutableListOf<List<String>>()
                tableObj.getAsJsonArray("rows")?.forEach { rowElem ->
                    if (rowElem.isJsonArray) {
                        val row = mutableListOf<String>()
                        rowElem.asJsonArray.forEach { row.add(it.asString) }
                        rows.add(row)
                    }
                }
                if (headers.isNotEmpty() || rows.isNotEmpty()) {
                    tableData = TableData(title = title, headers = headers, rows = rows)
                }
            }

            // 3. Parse Graph
            val graphObj = obj.getAsJsonObject("graph")
            if (graphObj != null) {
                val type = graphObj.get("type")?.asString ?: "line"
                val title = graphObj.get("title")?.asString ?: "Chart"
                val xAxisObj = graphObj.getAsJsonObject("x_axis")
                val yAxisObj = graphObj.getAsJsonObject("y_axis")
                val xAxis = if (xAxisObj != null) GraphAxis(xAxisObj.get("label")?.asString ?: "", xAxisObj.get("unit")?.asString ?: "") else null
                val yAxis = if (yAxisObj != null) GraphAxis(yAxisObj.get("label")?.asString ?: "", yAxisObj.get("unit")?.asString ?: "") else null

                val seriesList = mutableListOf<GraphSeries>()
                graphObj.getAsJsonArray("series")?.forEach { sElem ->
                    if (sElem.isJsonObject) {
                        val sObj = sElem.asJsonObject
                        val sName = sObj.get("name")?.asString ?: ""
                        val points = mutableListOf<GraphPoint>()
                        sObj.getAsJsonArray("points")?.forEach { ptElem ->
                            if (ptElem.isJsonObject) {
                                val ptObj = ptElem.asJsonObject
                                val x = ptObj.get("x")?.asDouble ?: 0.0
                                val y = ptObj.get("y")?.asDouble ?: 0.0
                                val lbl = ptObj.get("label")?.asString
                                points.add(GraphPoint(x = x, y = y, label = lbl))
                            }
                        }
                        seriesList.add(GraphSeries(name = sName, points = points))
                    }
                }

                val keyPoints = mutableListOf<String>()
                graphObj.getAsJsonArray("key_points")?.forEach { keyPoints.add(it.asString) }
                val trend = graphObj.get("trend")?.asString ?: ""

                graphData = GraphData(
                    type = type,
                    title = title,
                    xAxis = xAxis,
                    yAxis = yAxis,
                    series = seriesList,
                    keyPoints = keyPoints,
                    trend = trend,
                    isEstimated = true
                )
            }
        } catch (_: Exception) {
            // If JSON fails to parse, extract raw text into shortAnswer
            shortAnswer = clean.take(300)
        }

        // If no parts were found, create default part from shortAnswer
        if (answerParts.isEmpty()) {
            val textToUse = shortAnswer.ifEmpty { "Result identified." }
            answerParts.add(AnswerPart(label = "Final Answer", display = textToUse, spoken = textToUse))
            blocks.add(AnswerBlock("text", textToUse, textToUse))
        }

        return AnswerResult(
            subject = subject,
            questionText = questionText,
            shortAnswer = shortAnswer.ifEmpty { answerParts.firstOrNull()?.display ?: "" },
            blocks = blocks,
            answerParts = answerParts,
            table = tableData,
            graph = graphData,
            simpleExplanation = simpleExplanation,
            usage = usage,
            rawModelUsed = model
        )
    }
}
