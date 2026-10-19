package com.silentstudy.ai

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.silentstudy.data.model.AnswerBlock
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
    val conciseMode: Boolean = true,
    val maxOutputTokens: Int = 1200,
    val lazyExplanation: Boolean = true
)

data class AnswerResult(
    val subject: String,
    val questionText: String,
    val shortAnswer: String,
    val blocks: List<AnswerBlock>,
    val simpleExplanation: String,
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

    private fun buildSystemInstruction(concise: Boolean, lazyExplanation: Boolean): String {
        val brevity = if (concise) "Be concise, avoid repetitive filler, and provide crisp, direct logic." else "Provide clear, comprehensive guidance."
        val explanationField = if (lazyExplanation) {
            "\"simple_explanation\": \"(Generated on demand)\""
        } else {
            "\"simple_explanation\": \"Explain-like-I'm-new conceptual explanation in clear, everyday language.\""
        }

        return """
            You are SilentStudy, an elite private AI study companion for students in libraries.
            $brevity
            Read the captured question photo(s) (including handwriting, equations, and diagrams) and return STRICT JSON with this exact schema:
            {
              "subject": "Mathematics" | "Physics" | "Chemistry" | "Biology" | "History" | "General",
              "question_text": "Exact text of the question",
              "short_answer": "1-2 sentence TL;DR direct answer",
              "blocks": [
                {
                  "type": "text" | "math" | "step",
                  "display": "Formatted written form (LaTeX for math)",
                  "spoken": "CRITICAL: Natural-language verbalized reading for earbud TTS. Spell out all equations phonetically (e.g. 'x squared plus 3 equals 7'). For steps, say 'Step 1: ...'",
                  "stepNumber": 1
                }
              ],
              $explanationField
            }
            Respond ONLY with raw valid JSON without markdown formatting or code blocks.
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
            // Return cached or fallback list on failure
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

            // Check if model supports generateContent
            val methods = obj.getAsJsonArray("supportedGenerationMethods")
            val supportsContent = methods?.any { it.asString == "generateContent" } ?: false
            if (!supportsContent) continue

            val isVision = id.contains("flash") || id.contains("pro") || id.contains("vision") || id.contains("2.0") || id.contains("2.5") || id.contains("1.5")
            val isFree = id.contains("flash") // Generous free tier
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
        val req = Request.Builder().url(url)
            .header("Authorization", "Bearer $apiKey")
            .get().build()
        val res = client.newCall(req).execute()
        if (!res.isSuccessful) throw IOException("HTTP ${res.code}: ${res.message}")

        val json = res.body?.string() ?: return getDefaultFallbackModels("openai")
        val root = gson.fromJson(json, JsonObject::class.java)
        val dataArr = root.getAsJsonArray("data") ?: return getDefaultFallbackModels("openai")

        val result = mutableListOf<ModelInfo>()
        for (item in dataArr) {
            val id = item.asJsonObject.get("id")?.asString ?: continue
            if (id.startsWith("text-embedding") || id.startsWith("whisper") || id.startsWith("tts") || id.startsWith("dall-e")) continue
            val isVision = id.contains("4o") || id.contains("vision") || id.contains("4-turbo") || id.startsWith("o1")
            result.add(
                ModelInfo(
                    id = id,
                    name = id,
                    isVision = isVision,
                    isFree = false,
                    promptPricePerM = if (id.contains("mini")) 0.15 else 2.50,
                    completionPricePerM = if (id.contains("mini")) 0.60 else 10.00
                )
            )
        }
        return if (result.isNotEmpty()) result else getDefaultFallbackModels("openai")
    }

    private fun fetchAnthropicModels(apiKey: String): List<ModelInfo> {
        if (apiKey.isBlank()) return getDefaultFallbackModels("claude")
        val url = "https://api.anthropic.com/v1/models"
        val req = Request.Builder().url(url)
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .get().build()
        val res = client.newCall(req).execute()
        if (!res.isSuccessful) return getDefaultFallbackModels("claude")

        val json = res.body?.string() ?: return getDefaultFallbackModels("claude")
        val root = gson.fromJson(json, JsonObject::class.java)
        val dataArr = root.getAsJsonArray("data") ?: return getDefaultFallbackModels("claude")

        val result = mutableListOf<ModelInfo>()
        for (item in dataArr) {
            val id = item.asJsonObject.get("id")?.asString ?: continue
            val displayName = item.asJsonObject.get("display_name")?.asString ?: id
            result.add(
                ModelInfo(
                    id = id,
                    name = displayName,
                    isVision = true,
                    isFree = false,
                    promptPricePerM = if (id.contains("haiku")) 0.80 else 3.00,
                    completionPricePerM = if (id.contains("haiku")) 4.00 else 15.00
                )
            )
        }
        return if (result.isNotEmpty()) result else getDefaultFallbackModels("claude")
    }

    private fun fetchOpenRouterModels(): List<ModelInfo> {
        // OpenRouter models can be fetched without an API key
        val url = "https://openrouter.ai/api/v1/models"
        val req = Request.Builder().url(url).get().build()
        val res = client.newCall(req).execute()
        if (!res.isSuccessful) return getDefaultFallbackModels("openrouter")

        val json = res.body?.string() ?: return getDefaultFallbackModels("openrouter")
        val root = gson.fromJson(json, JsonObject::class.java)
        val dataArr = root.getAsJsonArray("data") ?: return getDefaultFallbackModels("openrouter")

        val result = mutableListOf<ModelInfo>()
        for (item in dataArr) {
            val obj = item.asJsonObject
            val id = obj.get("id")?.asString ?: continue
            val name = obj.get("name")?.asString ?: id

            // Check modality
            val arch = obj.getAsJsonObject("architecture")
            val modality = arch?.get("modality")?.asString ?: ""
            val isVision = modality.contains("image") || id.contains("vision") || id.contains("gemini") || id.contains("gpt-4o") || id.contains("claude-3") || id.contains("-vl")

            // Pricing
            val pricing = obj.getAsJsonObject("pricing")
            val promptRate = pricing?.get("prompt")?.asString?.toDoubleOrNull() ?: 0.0
            val compRate = pricing?.get("completion")?.asString?.toDoubleOrNull() ?: 0.0
            val promptPricePerM = promptRate * 1_000_000
            val compPricePerM = compRate * 1_000_000
            val isFree = promptPricePerM <= 0.0001 && compPricePerM <= 0.0001

            result.add(
                ModelInfo(
                    id = id,
                    name = name,
                    isVision = isVision,
                    isFree = isFree,
                    promptPricePerM = promptPricePerM,
                    completionPricePerM = compPricePerM
                )
            )
        }
        return if (result.isNotEmpty()) result else getDefaultFallbackModels("openrouter")
    }

    private fun fetchCustomModels(baseUrl: String, apiKey: String): List<ModelInfo> {
        val cleanBase = baseUrl.trimEnd('/')
        val url = "$cleanBase/models"
        val reqBuilder = Request.Builder().url(url)
        if (apiKey.isNotBlank()) reqBuilder.header("Authorization", "Bearer $apiKey")
        val req = reqBuilder.get().build()

        return try {
            val res = client.newCall(req).execute()
            if (!res.isSuccessful) return getDefaultFallbackModels("custom")
            val json = res.body?.string() ?: return getDefaultFallbackModels("custom")
            val root = gson.fromJson(json, JsonObject::class.java)
            val dataArr = root.getAsJsonArray("data") ?: root.getAsJsonArray("models")
            if (dataArr != null && dataArr.size() > 0) {
                val list = mutableListOf<ModelInfo>()
                for (item in dataArr) {
                    val id = item.asJsonObject.get("id")?.asString
                        ?: item.asJsonObject.get("name")?.asString ?: continue
                    val isVision = id.contains("vision") || id.contains("vl") || id.contains("llava")
                    list.add(ModelInfo(id = id, name = id, isVision = isVision, isFree = true))
                }
                list
            } else {
                getDefaultFallbackModels("custom")
            }
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
                ModelInfo("gemini-1.5-pro", "Gemini 1.5 Pro (Deep Reasoning)", isVision = true, isFree = false, promptPricePerM = 1.25, completionPricePerM = 5.00)
            )
            "openai" -> listOf(
                ModelInfo("gpt-4o-mini", "GPT-4o Mini (Fast Vision)", isVision = true, isFree = false, promptPricePerM = 0.15, completionPricePerM = 0.60),
                ModelInfo("gpt-4o", "GPT-4o (Omni Vision)", isVision = true, isFree = false, promptPricePerM = 2.50, completionPricePerM = 10.00),
                ModelInfo("o1", "o1 Reasoning Vision", isVision = true, isFree = false, promptPricePerM = 15.00, completionPricePerM = 60.00)
            )
            "claude" -> listOf(
                ModelInfo("claude-3-5-haiku-20241022", "Claude 3.5 Haiku (Fast Vision)", isVision = true, isFree = false, promptPricePerM = 0.80, completionPricePerM = 4.00),
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
                // Try fallback provider
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

    /**
     * On-Demand Lazy Generation: Simple Explanation
     */
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

    /**
     * On-Demand Lazy Generation: Quiz Question
     */
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

    /**
     * On-Demand Follow-up Q&A
     */
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
        val systemText = buildSystemInstruction(request.conciseMode, request.lazyExplanation)
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

        val payload = JsonObject().apply {
            add("contents", gson.toJsonTree(listOf(contentObj)))
            val genConfig = JsonObject().apply {
                addProperty("responseMimeType", "application/json")
                addProperty("temperature", 0.2)
                addProperty("maxOutputTokens", request.maxOutputTokens)
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

        // Parse token usage
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
            addProperty("content", buildSystemInstruction(request.conciseMode, request.lazyExplanation))
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

        val payload = JsonObject().apply {
            addProperty("model", model)
            add("messages", gson.toJsonTree(messages))
            val format = JsonObject().apply { addProperty("type", "json_object") }
            add("response_format", format)
            addProperty("temperature", 0.2)
            addProperty("max_tokens", request.maxOutputTokens)
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

        val sysText = buildSystemInstruction(request.conciseMode, request.lazyExplanation)
        contentList.add(JsonObject().apply {
            addProperty("type", "text")
            addProperty("text", "${request.promptNote ?: "Solve this question."}\n\n$sysText")
        })

        val messageObj = JsonObject().apply {
            addProperty("role", "user")
            add("content", gson.toJsonTree(contentList))
        }

        val payload = JsonObject().apply {
            addProperty("model", model)
            addProperty("max_tokens", request.maxOutputTokens)
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
            addProperty("content", buildSystemInstruction(request.conciseMode, request.lazyExplanation))
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

        val payload = JsonObject().apply {
            addProperty("model", model)
            add("messages", gson.toJsonTree(messages))
            val format = JsonObject().apply { addProperty("type", "json_object") }
            add("response_format", format)
            addProperty("temperature", 0.2)
            addProperty("max_tokens", request.maxOutputTokens)
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
            addProperty("content", buildSystemInstruction(request.conciseMode, request.lazyExplanation))
        })
        val userContent = JsonObject().apply {
            addProperty("role", "user")
            addProperty("content", request.promptNote ?: "Analyze and solve this question.")
        }
        messages.add(userContent)

        val payload = JsonObject().apply {
            addProperty("model", model)
            add("messages", gson.toJsonTree(messages))
            addProperty("temperature", 0.2)
            addProperty("max_tokens", request.maxOutputTokens)
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

        val obj = gson.fromJson(clean, JsonObject::class.java)
        val subject = obj.get("subject")?.asString ?: "General"
        val questionText = obj.get("question_text")?.asString ?: "Photo Question"
        val shortAnswer = obj.get("short_answer")?.asString ?: ""
        val simpleExplanation = obj.get("simple_explanation")?.asString ?: ""

        val blocks = mutableListOf<AnswerBlock>()
        val blocksArray = obj.getAsJsonArray("blocks")
        if (blocksArray != null && blocksArray.size() > 0) {
            for (elem in blocksArray) {
                val b = elem.asJsonObject
                blocks.add(
                    AnswerBlock(
                        type = b.get("type")?.asString ?: "text",
                        display = b.get("display")?.asString ?: "",
                        spoken = b.get("spoken")?.asString ?: b.get("display")?.asString ?: "",
                        stepNumber = b.get("stepNumber")?.asInt
                    )
                )
            }
        } else {
            blocks.add(AnswerBlock("text", shortAnswer, shortAnswer))
        }

        return AnswerResult(
            subject = subject,
            questionText = questionText,
            shortAnswer = shortAnswer,
            blocks = blocks,
            simpleExplanation = simpleExplanation,
            usage = usage,
            rawModelUsed = model
        )
    }
}
