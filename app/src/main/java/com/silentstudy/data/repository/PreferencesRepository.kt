package com.silentstudy.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.silentstudy.gestures.DeskGesture
import com.silentstudy.gestures.GestureAction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "silentstudy_preferences")

data class AppThemeSettings(
    val isDarkMode: Boolean = true,
    val isUltraDimMode: Boolean = false
)

data class AiSettings(
    val provider: String = "gemini",
    val apiKey: String = "",
    val model: String = "gemini-2.5-flash",
    val baseUrl: String = "http://localhost:11434/v1",
    val fallbackProvider: String = "openai",
    val fallbackApiKey: String = "",
    val fallbackModel: String = "gpt-4o-mini",
    val conciseMode: Boolean = true,
    val lazyExplanation: Boolean = true,
    val cheapMode: Boolean = false,
    val maxImagesPerQuestion: Int = 3,
    val maxTokensAnswer: Int = 400,
    val maxTokensShortAnswer: Int = 150,
    val maxTokensSimple: Int = 400,
    val maxTokensQuiz: Int = 300,
    val maxTokensFollowUp: Int = 400,
    val answerStyle: String = "direct" // "direct", "short_reason", "show_working"
)

data class BudgetSettings(
    val dailyTokenCap: Long = 0L,
    val monthlyTokenCap: Long = 0L,
    val dailyCostCapUsd: Double = 0.0,
    val monthlyCostCapUsd: Double = 0.0,
    val warnThresholdPercent: Int = 80,
    val pricePerMInputUsd: Double = 0.15,
    val pricePerMOutputUsd: Double = 0.60
)

data class AudioSettings(
    val speechRate: Float = 1.0f,
    val mathSlowdown: Boolean = true,
    val mathSlowdownRate: Float = 0.75f,
    val spokenMenuEnabled: Boolean = true,
    val silentTextMode: Boolean = false,
    val testModeEnabled: Boolean = false,  // Allows speaker audio temporarily for testing
    val spokenPromptsEnabled: Boolean = true
)

data class GestureSettings(
    val sensitivity: Int = 6,
    val hapticsEnabled: Boolean = true,
    val ignorePhoneTouches: Boolean = false,
    val calibratedThreshold: Float = 0.50f,
    val tapWindowMs: Long = 650L,
    val minTapGapMs: Long = 80L,
    val preset: String = "default",
    val customMappings: Map<String, String> = defaultGestureMap()
)

fun defaultGestureMap(): Map<String, String> {
    return mapOf(
        DeskGesture.SINGLE_TAP.name to GestureAction.PLAY_PAUSE.name,
        DeskGesture.DOUBLE_TAP.name to GestureAction.NEXT_ANSWER.name,
        DeskGesture.TRIPLE_TAP.name to GestureAction.PREV_ANSWER.name,
        DeskGesture.TAP_PAUSE_TAP.name to GestureAction.REPEAT.name,
        DeskGesture.FOUR_TAPS.name to GestureAction.STOP_SILENT.name,
        DeskGesture.FIVE_TAPS.name to GestureAction.JUMP_TO_N.name,
        DeskGesture.SIX_TAPS.name to GestureAction.CYCLE_SPEED.name,
        DeskGesture.TAP_PAUSE_TAP_TAP.name to GestureAction.REPEAT_LAST_PART.name
    )
}

fun minimalPresetMap(): Map<String, String> {
    return mapOf(
        DeskGesture.SINGLE_TAP.name to GestureAction.PLAY_PAUSE.name,
        DeskGesture.DOUBLE_TAP.name to GestureAction.NEXT_ANSWER.name,
        DeskGesture.TRIPLE_TAP.name to GestureAction.PREV_ANSWER.name,
        DeskGesture.FOUR_TAPS.name to GestureAction.STOP_SILENT.name
    )
}

fun oneHandedPresetMap(): Map<String, String> {
    return mapOf(
        DeskGesture.SINGLE_TAP.name to GestureAction.PLAY_PAUSE.name,
        DeskGesture.DOUBLE_TAP.name to GestureAction.NEXT_ANSWER.name,
        DeskGesture.TRIPLE_TAP.name to GestureAction.REPEAT.name,
        DeskGesture.FOUR_TAPS.name to GestureAction.STOP_SILENT.name,
        DeskGesture.TAP_PAUSE_TAP_TAP.name to GestureAction.REPEAT_LAST_PART.name
    )
}

class PreferencesRepository(private val context: Context) {

    private val gson = Gson()

    private object Keys {
        val IS_ULTRA_DIM_MODE = booleanPreferencesKey("is_ultra_dim_mode")

        // AI
        val AI_PROVIDER = stringPreferencesKey("ai_provider")
        val AI_API_KEY = stringPreferencesKey("ai_api_key")
        val AI_MODEL = stringPreferencesKey("ai_model")
        val AI_BASE_URL = stringPreferencesKey("ai_base_url")
        val AI_FALLBACK_PROVIDER = stringPreferencesKey("ai_fallback_provider")
        val AI_FALLBACK_API_KEY = stringPreferencesKey("ai_fallback_api_key")
        val AI_FALLBACK_MODEL = stringPreferencesKey("ai_fallback_model")
        val AI_CONCISE_MODE = booleanPreferencesKey("ai_concise_mode")
        val AI_LAZY_EXPLANATION = booleanPreferencesKey("ai_lazy_explanation")
        val AI_CHEAP_MODE = booleanPreferencesKey("ai_cheap_mode")
        val AI_MAX_IMAGES = intPreferencesKey("ai_max_images")
        val AI_MAX_TOKENS_ANSWER = intPreferencesKey("ai_max_tokens_answer")
        val AI_MAX_TOKENS_SHORT = intPreferencesKey("ai_max_tokens_short")
        val AI_MAX_TOKENS_SIMPLE = intPreferencesKey("ai_max_tokens_simple")
        val AI_MAX_TOKENS_QUIZ = intPreferencesKey("ai_max_tokens_quiz")
        val AI_MAX_TOKENS_FOLLOWUP = intPreferencesKey("ai_max_tokens_followup")
        val AI_ANSWER_STYLE = stringPreferencesKey("ai_answer_style")

        // Budget
        val BUDGET_DAILY_TOKENS = longPreferencesKey("budget_daily_tokens")
        val BUDGET_MONTHLY_TOKENS = longPreferencesKey("budget_monthly_tokens")
        val BUDGET_DAILY_COST = doublePreferencesKey("budget_daily_cost")
        val BUDGET_MONTHLY_COST = doublePreferencesKey("budget_monthly_cost")
        val BUDGET_PRICE_INPUT = doublePreferencesKey("budget_price_input")
        val BUDGET_PRICE_OUTPUT = doublePreferencesKey("budget_price_output")
        val BUDGET_WARN_PERCENT = intPreferencesKey("budget_warn_percent")

        // Audio
        val SPEECH_RATE = floatPreferencesKey("speech_rate")
        val MATH_SLOWDOWN = booleanPreferencesKey("math_slowdown")
        val MATH_SLOWDOWN_RATE = floatPreferencesKey("math_slowdown_rate")
        val SPOKEN_MENU = booleanPreferencesKey("spoken_menu")
        val SILENT_TEXT_MODE = booleanPreferencesKey("silent_text_mode")
        val TEST_MODE_ENABLED = booleanPreferencesKey("test_mode_enabled")
        val SPOKEN_PROMPTS = booleanPreferencesKey("spoken_prompts")

        // Gestures
        val GESTURE_SENSITIVITY = intPreferencesKey("gesture_sensitivity")
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        val IGNORE_PHONE_TOUCHES = booleanPreferencesKey("ignore_phone_touches")
        val CALIBRATED_THRESHOLD = floatPreferencesKey("calibrated_threshold")
        val TAP_WINDOW_MS = longPreferencesKey("tap_window_ms")
        val MIN_TAP_GAP_MS = longPreferencesKey("min_tap_gap_ms")
        val GESTURE_PRESET = stringPreferencesKey("gesture_preset")
        val GESTURE_MAP_JSON = stringPreferencesKey("gesture_map_json")

        // Wizard & Interruption
        val FIRST_RUN_COMPLETED = booleanPreferencesKey("first_run_completed")
        val LAST_PLAYED_ANSWER_ID = longPreferencesKey("last_played_answer_id")
        val LAST_PLAYED_BLOCK_INDEX = intPreferencesKey("last_played_block_index")
        val LOW_BATTERY_PERCENT = intPreferencesKey("low_battery_percent")
    }

    val themeSettings: Flow<AppThemeSettings> = context.dataStore.data.map { prefs ->
        AppThemeSettings(
            isUltraDimMode = prefs[Keys.IS_ULTRA_DIM_MODE] ?: false
        )
    }

    val aiSettings: Flow<AiSettings> = context.dataStore.data.map { prefs ->
        AiSettings(
            provider = prefs[Keys.AI_PROVIDER] ?: "gemini",
            apiKey = prefs[Keys.AI_API_KEY] ?: "",
            model = prefs[Keys.AI_MODEL] ?: "gemini-2.5-flash",
            baseUrl = prefs[Keys.AI_BASE_URL] ?: "http://localhost:11434/v1",
            fallbackProvider = prefs[Keys.AI_FALLBACK_PROVIDER] ?: "openai",
            fallbackApiKey = prefs[Keys.AI_FALLBACK_API_KEY] ?: "",
            fallbackModel = prefs[Keys.AI_FALLBACK_MODEL] ?: "gpt-4o-mini",
            conciseMode = prefs[Keys.AI_CONCISE_MODE] ?: true,
            lazyExplanation = prefs[Keys.AI_LAZY_EXPLANATION] ?: true,
            cheapMode = prefs[Keys.AI_CHEAP_MODE] ?: false,
            maxImagesPerQuestion = prefs[Keys.AI_MAX_IMAGES] ?: 3,
            maxTokensAnswer = prefs[Keys.AI_MAX_TOKENS_ANSWER] ?: 400,
            maxTokensShortAnswer = prefs[Keys.AI_MAX_TOKENS_SHORT] ?: 150,
            maxTokensSimple = prefs[Keys.AI_MAX_TOKENS_SIMPLE] ?: 400,
            maxTokensQuiz = prefs[Keys.AI_MAX_TOKENS_QUIZ] ?: 300,
            maxTokensFollowUp = prefs[Keys.AI_MAX_TOKENS_FOLLOWUP] ?: 400,
            answerStyle = prefs[Keys.AI_ANSWER_STYLE] ?: "direct"
        )
    }

    val budgetSettings: Flow<BudgetSettings> = context.dataStore.data.map { prefs ->
        BudgetSettings(
            dailyTokenCap = prefs[Keys.BUDGET_DAILY_TOKENS] ?: 0L,
            monthlyTokenCap = prefs[Keys.BUDGET_MONTHLY_TOKENS] ?: 0L,
            dailyCostCapUsd = prefs[Keys.BUDGET_DAILY_COST] ?: 0.0,
            monthlyCostCapUsd = prefs[Keys.BUDGET_MONTHLY_COST] ?: 0.0,
            warnThresholdPercent = prefs[Keys.BUDGET_WARN_PERCENT] ?: 80,
            pricePerMInputUsd = prefs[Keys.BUDGET_PRICE_INPUT] ?: 0.15,
            pricePerMOutputUsd = prefs[Keys.BUDGET_PRICE_OUTPUT] ?: 0.60
        )
    }

    val audioSettings: Flow<AudioSettings> = context.dataStore.data.map { prefs ->
        AudioSettings(
            speechRate = prefs[Keys.SPEECH_RATE] ?: 1.0f,
            mathSlowdown = prefs[Keys.MATH_SLOWDOWN] ?: true,
            mathSlowdownRate = prefs[Keys.MATH_SLOWDOWN_RATE] ?: 0.75f,
            spokenMenuEnabled = prefs[Keys.SPOKEN_MENU] ?: true,
            silentTextMode = prefs[Keys.SILENT_TEXT_MODE] ?: false,
            testModeEnabled = prefs[Keys.TEST_MODE_ENABLED] ?: false,
            spokenPromptsEnabled = prefs[Keys.SPOKEN_PROMPTS] ?: true
        )
    }

    val gestureSettings: Flow<GestureSettings> = context.dataStore.data.map { prefs ->
        val mapJson = prefs[Keys.GESTURE_MAP_JSON]
        val parsedMap: Map<String, String> = if (!mapJson.isNullOrEmpty()) {
            try {
                val type = object : TypeToken<Map<String, String>>() {}.type
                gson.fromJson(mapJson, type) ?: defaultGestureMap()
            } catch (_: Exception) {
                defaultGestureMap()
            }
        } else {
            defaultGestureMap()
        }

        GestureSettings(
            sensitivity = prefs[Keys.GESTURE_SENSITIVITY] ?: 6,
            hapticsEnabled = prefs[Keys.HAPTICS_ENABLED] ?: true,
            ignorePhoneTouches = prefs[Keys.IGNORE_PHONE_TOUCHES] ?: false,
            calibratedThreshold = prefs[Keys.CALIBRATED_THRESHOLD] ?: 0.50f,
            tapWindowMs = prefs[Keys.TAP_WINDOW_MS] ?: 650L,
            minTapGapMs = prefs[Keys.MIN_TAP_GAP_MS] ?: 80L,
            preset = prefs[Keys.GESTURE_PRESET] ?: "default",
            customMappings = parsedMap
        )
    }

    val firstRunCompleted: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.FIRST_RUN_COMPLETED] ?: false
    }

    val lastPlaybackPosition: Flow<Pair<Long, Int>> = context.dataStore.data.map { prefs ->
        Pair(
            prefs[Keys.LAST_PLAYED_ANSWER_ID] ?: -1L,
            prefs[Keys.LAST_PLAYED_BLOCK_INDEX] ?: 0
        )
    }

    val lowBatteryPercent: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[Keys.LOW_BATTERY_PERCENT] ?: 15
    }

    suspend fun setUltraDimMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.IS_ULTRA_DIM_MODE] = enabled }
    }

    suspend fun saveAiConfig(
        provider: String,
        apiKey: String,
        model: String,
        baseUrl: String,
        fallbackProvider: String? = null,
        fallbackApiKey: String? = null,
        fallbackModel: String? = null,
        conciseMode: Boolean = true,
        lazyExplanation: Boolean = true,
        cheapMode: Boolean = false,
        answerStyle: String = "direct"
    ) {
        context.dataStore.edit {
            it[Keys.AI_PROVIDER] = provider
            it[Keys.AI_API_KEY] = apiKey
            it[Keys.AI_MODEL] = model
            it[Keys.AI_BASE_URL] = baseUrl
            fallbackProvider?.let { fp -> it[Keys.AI_FALLBACK_PROVIDER] = fp }
            fallbackApiKey?.let { fk -> it[Keys.AI_FALLBACK_API_KEY] = fk }
            fallbackModel?.let { fm -> it[Keys.AI_FALLBACK_MODEL] = fm }
            it[Keys.AI_CONCISE_MODE] = conciseMode
            it[Keys.AI_LAZY_EXPLANATION] = lazyExplanation
            it[Keys.AI_CHEAP_MODE] = cheapMode
            it[Keys.AI_ANSWER_STYLE] = answerStyle
        }
    }

    suspend fun setAnswerStyle(style: String) {
        context.dataStore.edit { it[Keys.AI_ANSWER_STYLE] = style }
    }

    suspend fun saveBudgetCaps(
        dailyTokens: Long,
        monthlyTokens: Long,
        dailyCost: Double,
        monthlyCost: Double,
        priceInput: Double,
        priceOutput: Double
    ) {
        context.dataStore.edit {
            it[Keys.BUDGET_DAILY_TOKENS] = dailyTokens
            it[Keys.BUDGET_MONTHLY_TOKENS] = monthlyTokens
            it[Keys.BUDGET_DAILY_COST] = dailyCost
            it[Keys.BUDGET_MONTHLY_COST] = monthlyCost
            it[Keys.BUDGET_PRICE_INPUT] = priceInput
            it[Keys.BUDGET_PRICE_OUTPUT] = priceOutput
        }
    }

    suspend fun setSpeechRate(rate: Float) {
        context.dataStore.edit { it[Keys.SPEECH_RATE] = rate }
    }

    suspend fun setMathSlowdown(enabled: Boolean) {
        context.dataStore.edit { it[Keys.MATH_SLOWDOWN] = enabled }
    }

    suspend fun setSpokenMenu(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SPOKEN_MENU] = enabled }
    }

    suspend fun setSpokenMenuEnabled(enabled: Boolean) = setSpokenMenu(enabled)

    suspend fun setSilentTextMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SILENT_TEXT_MODE] = enabled }
    }

    suspend fun setTestModeEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.TEST_MODE_ENABLED] = enabled }
    }

    suspend fun setSpokenPrompts(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SPOKEN_PROMPTS] = enabled }
    }

    suspend fun setSpokenPromptsEnabled(enabled: Boolean) = setSpokenPrompts(enabled)

    suspend fun setWarnThresholdPercent(percent: Int) {
        context.dataStore.edit { it[Keys.BUDGET_WARN_PERCENT] = percent }
    }

    suspend fun setGestureSensitivity(sensitivity: Int) {
        context.dataStore.edit { it[Keys.GESTURE_SENSITIVITY] = sensitivity }
    }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.HAPTICS_ENABLED] = enabled }
    }

    suspend fun setIgnorePhoneTouches(enabled: Boolean) {
        context.dataStore.edit { it[Keys.IGNORE_PHONE_TOUCHES] = enabled }
    }

    suspend fun setCalibratedThreshold(threshold: Float) {
        context.dataStore.edit { it[Keys.CALIBRATED_THRESHOLD] = threshold }
    }

    suspend fun saveGestureTiming(tapWindowMs: Long, minTapGapMs: Long) {
        context.dataStore.edit {
            it[Keys.TAP_WINDOW_MS] = tapWindowMs
            it[Keys.MIN_TAP_GAP_MS] = minTapGapMs
        }
    }

    suspend fun saveGestureMapping(gesture: String, action: String) {
        context.dataStore.edit { prefs ->
            val currentJson = prefs[Keys.GESTURE_MAP_JSON]
            val map = if (!currentJson.isNullOrEmpty()) {
                val type = object : TypeToken<Map<String, String>>() {}.type
                gson.fromJson<Map<String, String>>(currentJson, type).toMutableMap()
            } else {
                defaultGestureMap().toMutableMap()
            }
            map[gesture] = action
            prefs[Keys.GESTURE_MAP_JSON] = gson.toJson(map)
        }
    }

    suspend fun applyPreset(presetName: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.GESTURE_PRESET] = presetName
            val map = when (presetName.lowercase()) {
                "minimal" -> minimalPresetMap()
                "one_handed" -> oneHandedPresetMap()
                else -> defaultGestureMap()
            }
            prefs[Keys.GESTURE_MAP_JSON] = gson.toJson(map)
        }
    }

    suspend fun resetGestureDefaults() {
        context.dataStore.edit { prefs ->
            prefs[Keys.GESTURE_PRESET] = "default"
            prefs[Keys.GESTURE_MAP_JSON] = gson.toJson(defaultGestureMap())
            prefs[Keys.TAP_WINDOW_MS] = 650L
            prefs[Keys.MIN_TAP_GAP_MS] = 80L
            prefs[Keys.IGNORE_PHONE_TOUCHES] = false
        }
    }

    suspend fun setFirstRunCompleted(completed: Boolean) {
        context.dataStore.edit { it[Keys.FIRST_RUN_COMPLETED] = completed }
    }

    suspend fun saveLastPlaybackPosition(answerId: Long, blockIndex: Int) {
        context.dataStore.edit {
            it[Keys.LAST_PLAYED_ANSWER_ID] = answerId
            it[Keys.LAST_PLAYED_BLOCK_INDEX] = blockIndex
        }
    }

    suspend fun setLowBatteryPercent(percent: Int) {
        context.dataStore.edit { it[Keys.LOW_BATTERY_PERCENT] = percent }
    }

    fun exportSettingsJson(): String {
        val safeExport = mapOf(
            "app" to "SilentStudy",
            "version" to "1.0.0",
            "exportTimestamp" to System.currentTimeMillis()
        )
        return gson.toJson(safeExport)
    }
}
