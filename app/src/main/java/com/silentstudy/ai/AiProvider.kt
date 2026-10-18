package com.silentstudy.ai

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.silentstudy.data.model.AnswerBlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class AnswerRequest(
    val imagesBase64: List<String>,
    val promptNote: String? = null
)

data class AnswerResult(
    val subject: String,
    val questionText: String,
    val shortAnswer: String,
    val blocks: List<AnswerBlock>,
    val simpleExplanation: String
)

data class ConnectionResult(
    val success: Boolean,
    val message: String,
    val latencyMs: Long
)

interface AiProvider {
    val id: String
    val displayName: String
    suspend fun answer(request: AnswerRequest, apiKey: String, model: String, baseUrl: String): AnswerResult
    suspend fun testConnection(apiKey: String, model: String, baseUrl: String): ConnectionResult
}

class AiProviderManager {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    private val systemInstruction = """
        You are SilentStudy, an elite private AI study companion for students in libraries.
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
          "simple_explanation": "Explain-like-I'm-new conceptual explanation in clear, everyday language."
        }
        Respond ONLY with raw valid JSON without markdown wrapping.
    """.trimIndent()

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

    suspend fun testConnection(
        provider: String,
        apiKey: String,
        model: String,
        baseUrl: String
    ): ConnectionResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            when (provider.lowercase()) {
                "gemini" -> {
                    val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
                    val json = """{"contents":[{"parts":[{"text":"Say OK"}]}]}"""
                    val req = Request.Builder().url(url).post(json.toRequestBody("application/json".toMediaType())).build()
                    val res = client.newCall(req).execute()
                    if (!res.isSuccessful) throw Exception("HTTP ${res.code}: ${res.message}")
                }
                "openai" -> {
                    val url = "https://api.openai.com/v1/chat/completions"
                    val json = """{"model":"$model","messages":[{"role":"user","content":"Say OK"}],"max_tokens":5}"""
                    val req = Request.Builder().url(url)
                        .header("Authorization", "Bearer $apiKey")
                        .post(json.toRequestBody("application/json".toMediaType())).build()
                    val res = client.newCall(req).execute()
                    if (!res.isSuccessful) throw Exception("HTTP ${res.code}: ${res.message}")
                }
                "claude" -> {
                    val url = "https://api.anthropic.com/v1/messages"
                    val json = """{"model":"$model","max_tokens":10,"messages":[{"role":"user","content":"Say OK"}]}"""
                    val req = Request.Builder().url(url)
                        .header("x-api-key", apiKey)
                        .header("anthropic-version", "2023-06-01")
                        .post(json.toRequestBody("application/json".toMediaType())).build()
                    val res = client.newCall(req).execute()
                    if (!res.isSuccessful) throw Exception("HTTP ${res.code}: ${res.message}")
                }
                "custom" -> {
                    val cleanBase = baseUrl.trimEnd('/')
                    val url = "$cleanBase/chat/completions"
                    val json = """{"model":"$model","messages":[{"role":"user","content":"Say OK"}],"max_tokens":5}"""
                    val builder = Request.Builder().url(url)
                    if (apiKey.isNotEmpty()) builder.header("Authorization", "Bearer $apiKey")
                    val req = builder.post(json.toRequestBody("application/json".toMediaType())).build()
                    val res = client.newCall(req).execute()
                    if (!res.isSuccessful) throw Exception("HTTP ${res.code}: ${res.message}")
                }
                else -> {
                    // OpenRouter
                    val url = "https://openrouter.ai/api/v1/chat/completions"
                    val json = """{"model":"$model","messages":[{"role":"user","content":"Say OK"}],"max_tokens":5}"""
                    val req = Request.Builder().url(url)
                        .header("Authorization", "Bearer $apiKey")
                        .post(json.toRequestBody("application/json".toMediaType())).build()
                    val res = client.newCall(req).execute()
                    if (!res.isSuccessful) throw Exception("HTTP ${res.code}: ${res.message}")
                }
            }
            val latency = System.currentTimeMillis() - start
            ConnectionResult(true, "Connected successfully (${latency}ms)", latency)
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - start
            ConnectionResult(false, e.message ?: "Connection test failed", latency)
        }
    }

    private fun callGemini(apiKey: String, model: String, request: AnswerRequest): AnswerResult {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

        val partsArray = mutableListOf<JsonObject>()
        partsArray.add(JsonObject().apply { addProperty("text", systemInstruction) })
        request.promptNote?.let {
            partsArray.add(JsonObject().apply { addProperty("text", "User note: $it") })
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
            }
            add("generationConfig", genConfig)
        }

        val body = payload.toString().toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url(url).post(body).build()
        val res = client.newCall(req).execute()

        if (!res.isSuccessful) {
            throw Exception("Gemini error ${res.code}: ${res.body?.string()}")
        }

        val jsonResponse = res.body?.string() ?: throw Exception("Empty Gemini response")
        val root = gson.fromJson(jsonResponse, JsonObject::class.java)
        val text = root.getAsJsonArray("candidates")
            ?.get(0)?.asJsonObject
            ?.getAsJsonObject("content")
            ?.getAsJsonArray("parts")
            ?.get(0)?.asJsonObject
            ?.get("text")?.asString ?: throw Exception("Invalid response structure")

        return parseStructuredAnswer(text)
    }

    private fun callOpenAi(apiKey: String, model: String, request: AnswerRequest): AnswerResult {
        val url = "https://api.openai.com/v1/chat/completions"
        val messages = mutableListOf<JsonObject>()

        messages.add(JsonObject().apply {
            addProperty("role", "system")
            addProperty("content", systemInstruction)
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
        }

        val body = payload.toString().toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url(url)
            .header("Authorization", "Bearer $apiKey")
            .post(body).build()
        val res = client.newCall(req).execute()

        if (!res.isSuccessful) throw Exception("OpenAI error ${res.code}: ${res.body?.string()}")
        val json = res.body?.string() ?: throw Exception("Empty response")
        val root = gson.fromJson(json, JsonObject::class.java)
        val text = root.getAsJsonArray("choices")
            ?.get(0)?.asJsonObject
            ?.getAsJsonObject("message")
            ?.get("content")?.asString ?: throw Exception("No choice content")

        return parseStructuredAnswer(text)
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

        contentList.add(JsonObject().apply {
            addProperty("type", "text")
            addProperty("text", "${request.promptNote ?: "Solve this question."}\n\n$systemInstruction")
        })

        val messageObj = JsonObject().apply {
            addProperty("role", "user")
            add("content", gson.toJsonTree(contentList))
        }

        val payload = JsonObject().apply {
            addProperty("model", model)
            addProperty("max_tokens", 3500)
            add("messages", gson.toJsonTree(listOf(messageObj)))
        }

        val body = payload.toString().toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url(url)
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .post(body).build()
        val res = client.newCall(req).execute()

        if (!res.isSuccessful) throw Exception("Claude error ${res.code}: ${res.body?.string()}")
        val json = res.body?.string() ?: throw Exception("Empty response")
        val root = gson.fromJson(json, JsonObject::class.java)
        val text = root.getAsJsonArray("content")
            ?.get(0)?.asJsonObject
            ?.get("text")?.asString ?: throw Exception("No Claude text content")

        return parseStructuredAnswer(text)
    }

    private fun callOpenRouter(apiKey: String, model: String, request: AnswerRequest): AnswerResult {
        return callOpenAi(apiKey, model, request) // OpenAI compatible endpoint with OpenRouter baseUrl
    }

    private fun callCustomOpenAi(apiKey: String, model: String, baseUrl: String, request: AnswerRequest): AnswerResult {
        val cleanBase = baseUrl.trimEnd('/')
        val url = "$cleanBase/chat/completions"
        // Uses same schema as OpenAI
        val messages = mutableListOf<JsonObject>()
        messages.add(JsonObject().apply {
            addProperty("role", "system")
            addProperty("content", systemInstruction)
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
        }

        val builder = Request.Builder().url(url)
        if (apiKey.isNotEmpty()) builder.header("Authorization", "Bearer $apiKey")
        val req = builder.post(payload.toString().toRequestBody("application/json".toMediaType())).build()
        val res = client.newCall(req).execute()

        if (!res.isSuccessful) throw Exception("Custom server error ${res.code}: ${res.body?.string()}")
        val json = res.body?.string() ?: throw Exception("Empty response")
        val root = gson.fromJson(json, JsonObject::class.java)
        val text = root.getAsJsonArray("choices")
            ?.get(0)?.asJsonObject
            ?.getAsJsonObject("message")
            ?.get("content")?.asString ?: throw Exception("No content")

        return parseStructuredAnswer(text)
    }

    private fun parseStructuredAnswer(rawText: String): AnswerResult {
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
        val simpleExplanation = obj.get("simple_explanation")?.asString ?: shortAnswer

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
            simpleExplanation = simpleExplanation
        )
    }
}
