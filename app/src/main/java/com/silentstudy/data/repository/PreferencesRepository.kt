package com.silentstudy.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "silentstudy_preferences")

data class AppThemeSettings(
    val isDarkMode: Boolean = true,
    val isUltraDimMode: Boolean = false   // App is dark by default; Ultra Dim mode for night use
)

data class AiSettings(
    val provider: String = "gemini",
    val apiKey: String = "",
    val model: String = "gemini-2.5-flash",
    val baseUrl: String = "http://localhost:11434/v1",
    val fallbackProvider: String = "openai",
    val fallbackApiKey: String = "",
    val fallbackModel: String = "gpt-4o-mini"
)

data class AudioSettings(
    val speechRate: Float = 1.0f,
    val mathSlowdown: Boolean = true,
    val mathSlowdownRate: Float = 0.75f,
    val spokenMenuEnabled: Boolean = true,
    val silentTextMode: Boolean = false
)

data class GestureSettings(
    val sensitivity: Int = 6,
    val hapticsEnabled: Boolean = true
)

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val IS_ULTRA_DIM_MODE = booleanPreferencesKey("is_ultra_dim_mode")

        val AI_PROVIDER = stringPreferencesKey("ai_provider")
        val AI_API_KEY = stringPreferencesKey("ai_api_key")
        val AI_MODEL = stringPreferencesKey("ai_model")
        val AI_BASE_URL = stringPreferencesKey("ai_base_url")

        val SPEECH_RATE = floatPreferencesKey("speech_rate")
        val MATH_SLOWDOWN = booleanPreferencesKey("math_slowdown")
        val MATH_SLOWDOWN_RATE = floatPreferencesKey("math_slowdown_rate")
        val SPOKEN_MENU = booleanPreferencesKey("spoken_menu")
        val SILENT_TEXT_MODE = booleanPreferencesKey("silent_text_mode")

        val GESTURE_SENSITIVITY = intPreferencesKey("gesture_sensitivity")
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
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
            baseUrl = prefs[Keys.AI_BASE_URL] ?: "http://localhost:11434/v1"
        )
    }

    val audioSettings: Flow<AudioSettings> = context.dataStore.data.map { prefs ->
        AudioSettings(
            speechRate = prefs[Keys.SPEECH_RATE] ?: 1.0f,
            mathSlowdown = prefs[Keys.MATH_SLOWDOWN] ?: true,
            mathSlowdownRate = prefs[Keys.MATH_SLOWDOWN_RATE] ?: 0.75f,
            spokenMenuEnabled = prefs[Keys.SPOKEN_MENU] ?: true,
            silentTextMode = prefs[Keys.SILENT_TEXT_MODE] ?: false
        )
    }

    val gestureSettings: Flow<GestureSettings> = context.dataStore.data.map { prefs ->
        GestureSettings(
            sensitivity = prefs[Keys.GESTURE_SENSITIVITY] ?: 6,
            hapticsEnabled = prefs[Keys.HAPTICS_ENABLED] ?: true
        )
    }

    suspend fun setDarkMode(enabled: Boolean) {
        // App is permanently dark by design
    }

    suspend fun setUltraDimMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.IS_ULTRA_DIM_MODE] = enabled }
    }

    suspend fun saveAiConfig(provider: String, apiKey: String, model: String, baseUrl: String) {
        context.dataStore.edit {
            it[Keys.AI_PROVIDER] = provider
            it[Keys.AI_API_KEY] = apiKey
            it[Keys.AI_MODEL] = model
            it[Keys.AI_BASE_URL] = baseUrl
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

    suspend fun setSilentTextMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SILENT_TEXT_MODE] = enabled }
    }

    suspend fun setGestureSensitivity(sensitivity: Int) {
        context.dataStore.edit { it[Keys.GESTURE_SENSITIVITY] = sensitivity }
    }
}
