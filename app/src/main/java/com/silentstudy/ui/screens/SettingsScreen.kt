package com.silentstudy.ui.screens

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silentstudy.ai.AiProviderManager
import com.silentstudy.ai.ConnectionResult
import com.silentstudy.audio.SilentStudyTtsManager
import com.silentstudy.data.repository.AiSettings
import com.silentstudy.data.repository.AppThemeSettings
import com.silentstudy.data.repository.AudioSettings
import com.silentstudy.data.repository.GestureSettings
import com.silentstudy.ui.components.*
import com.silentstudy.ui.theme.LocalNeuColors
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    themeSettings: AppThemeSettings,
    aiSettings: AiSettings,
    audioSettings: AudioSettings,
    gestureSettings: GestureSettings,
    ttsManager: SilentStudyTtsManager,
    aiManager: AiProviderManager,
    onSaveTheme: (isDark: Boolean, isUltraDim: Boolean) -> Unit,
    onSaveAi: (provider: String, key: String, model: String, baseUrl: String) -> Unit,
    onSaveAudio: (rate: Float, slowdown: Boolean, spokenMenu: Boolean, silentMode: Boolean) -> Unit,
    onSaveGesture: (sensitivity: Int) -> Unit
) {
    val context = LocalContext.current
    val colors = LocalNeuColors.current
    val scope = rememberCoroutineScope()

    var activeTab by remember { mutableStateOf("ai") } // ai, audio, appearance, gestures

    // AI Form State
    var selectedProvider by remember { mutableStateOf(aiSettings.provider) }
    var apiKeyInput by remember { mutableStateOf(aiSettings.apiKey) }
    var modelInput by remember { mutableStateOf(aiSettings.model) }
    var baseUrlInput by remember { mutableStateOf(aiSettings.baseUrl) }
    var testResult by remember { mutableStateOf<ConnectionResult?>(null) }
    var isTestingConnection by remember { mutableStateOf(false) }

    // TTS status state
    val ttsError by ttsManager.hasError.collectAsState()
    val isTtsReady by ttsManager.isInitialized.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Settings Header with Savage Tech Credits (FIX 2)
        NeuCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "SilentStudy Settings",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "Built by Savage Tech · Earbuds Companion",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.accent
                    )
                }
                Icon(
                    imageVector = Icons.Default.VerifiedUser,
                    contentDescription = "Verified Native",
                    tint = colors.accent,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Horizontal Category Tabs
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("ai" to "AI Providers", "audio" to "Audio", "appearance" to "Theme", "gestures" to "Gestures").forEach { (tab, label) ->
                val isSelected = activeTab == tab
                NeuButton(
                    onClick = { activeTab = tab },
                    isAccent = isSelected,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(label, fontSize = 11.sp, color = colors.textPrimary)
                }
            }
        }

        when (activeTab) {
            "ai" -> {
                // Section 4A: Provider Agnostic AI Layer
                NeuCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "AI Provider Layer (No Vendor Lock-in)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.accent
                    )
                    Text(
                        text = "Choose your own AI model and enter your API key. Keys stay on this device.",
                        fontSize = 11.sp,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                    )

                    // Provider Choices
                    val providers = listOf(
                        "gemini" to "Google Gemini",
                        "openai" to "OpenAI (GPT-4o)",
                        "claude" to "Anthropic Claude",
                        "openrouter" to "OpenRouter",
                        "custom" to "Custom / Ollama"
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        providers.forEach { (pId, pName) ->
                            val isSel = selectedProvider.equals(pId, ignoreCase = true)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedProvider = pId
                                        when (pId) {
                                            "gemini" -> modelInput = "gemini-2.5-flash"
                                            "openai" -> modelInput = "gpt-4o-mini"
                                            "claude" -> modelInput = "claude-3-5-haiku-20241022"
                                            "custom" -> modelInput = "llama3.2-vision"
                                        }
                                    }
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(pName, fontSize = 12.sp, color = if (isSel) colors.accent else colors.textPrimary, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal)
                                if (isSel) Icon(Icons.Default.Check, contentDescription = null, tint = colors.accent, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    NeuTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        label = "API Key (Stored locally)",
                        placeholder = if (apiKeyInput.isNotEmpty()) "••••${apiKeyInput.takeLast(4)}" else "Paste your API key here"
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    NeuTextField(
                        value = modelInput,
                        onValueChange = { modelInput = it },
                        label = "Model Identifier",
                        placeholder = "e.g. gemini-2.5-flash, gpt-4o-mini"
                    )

                    if (selectedProvider.equals("custom", ignoreCase = true)) {
                        Spacer(modifier = Modifier.height(8.dp))
                        NeuTextField(
                            value = baseUrlInput,
                            onValueChange = { baseUrlInput = it },
                            label = "Custom Base URL",
                            placeholder = "http://localhost:11434/v1"
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NeuButton(
                            onClick = {
                                isTestingConnection = true
                                testResult = null
                                scope.launch {
                                    val res = aiManager.testConnection(selectedProvider, apiKeyInput, modelInput, baseUrlInput)
                                    testResult = res
                                    isTestingConnection = false
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isTestingConnection) {
                                CircularProgressIndicator(color = colors.accent, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Testing...", fontSize = 11.sp, color = colors.textPrimary)
                            } else {
                                Text("Test Connection", fontSize = 11.sp, color = colors.textPrimary)
                            }
                        }

                        NeuButton(
                            onClick = {
                                onSaveAi(selectedProvider, apiKeyInput, modelInput, baseUrlInput)
                            },
                            modifier = Modifier.weight(1f),
                            isAccent = true
                        ) {
                            Text("Save Key & Model", fontSize = 11.sp, color = colors.textPrimary, fontWeight = FontWeight.Bold)
                        }
                    }

                    testResult?.let { res ->
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = res.message,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (res.success) colors.accent else Color(0xFFEF4444)
                        )
                    }
                }
            }

            "audio" -> {
                // FIX 1: Native TextToSpeech Engine Controls & Earbuds Guard
                NeuCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Earbuds Audio & Android TTS (FIX 1)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.accent
                    )

                    if (ttsError != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = ttsError ?: "",
                            fontSize = 11.sp,
                            color = Color(0xFFEF4444),
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        NeuButton(
                            onClick = {
                                try {
                                    context.startActivity(ttsManager.getOpenTtsSettingsIntent())
                                } catch (_: Exception) {}
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Open Android TTS Settings", fontSize = 11.sp, color = colors.accent)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    // Test Voice Button (FIX 1 requirement)
                    NeuButton(
                        onClick = { ttsManager.testVoice(audioSettings.speechRate) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.VolumeUp, contentDescription = null, tint = colors.accent, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Test Voice (Speaks in Earbuds)", fontSize = 12.sp, color = colors.textPrimary)
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    NeuToggle(
                        checked = audioSettings.mathSlowdown,
                        onCheckedChange = {
                            onSaveAudio(audioSettings.speechRate, it, audioSettings.spokenMenuEnabled, audioSettings.silentTextMode)
                        },
                        label = "Math Equation Auto-Slowdown",
                        sublabel = "Slows down to 0.75x with pauses on formulas and calculation steps"
                    )

                    NeuToggle(
                        checked = audioSettings.spokenMenuEnabled,
                        onCheckedChange = {
                            onSaveAudio(audioSettings.speechRate, audioSettings.mathSlowdown, it, audioSettings.silentTextMode)
                        },
                        label = "Spoken After-Answer Menu",
                        sublabel = "Speaks 'Tap once to repeat...' after each answer completes"
                    )

                    NeuToggle(
                        checked = audioSettings.silentTextMode,
                        onCheckedChange = {
                            onSaveAudio(audioSettings.speechRate, audioSettings.mathSlowdown, audioSettings.spokenMenuEnabled, it)
                        },
                        label = "Silent Text Mode",
                        sublabel = "Displays answers on screen only without audio playback"
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    NeuSlider(
                        value = audioSettings.speechRate,
                        onValueChange = {
                            onSaveAudio(it, audioSettings.mathSlowdown, audioSettings.spokenMenuEnabled, audioSettings.silentTextMode)
                        },
                        valueRange = 0.75f..2.0f,
                        label = "Speech Speed",
                        valueDisplay = String.format("%.2fx", audioSettings.speechRate)
                    )
                }
            }

            "appearance" -> {
                // FIX 2: Dark Neumorphic Theme as Default & Ultra Dim Option
                NeuCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Appearance & Theme (FIX 2)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.accent
                    )
                    Text(
                        text = "Dark theme is default. Pure white is eliminated to prevent eye glare in dim libraries.",
                        fontSize = 11.sp,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                    )

                    NeuToggle(
                        checked = themeSettings.isDarkMode,
                        onCheckedChange = { onSaveTheme(it, themeSettings.isUltraDimMode) },
                        label = "Dark Neumorphism (Default)",
                        sublabel = "Surface #1E2127 with glare-free off-white text #C9CED6"
                    )

                    NeuToggle(
                        checked = themeSettings.isUltraDimMode,
                        onCheckedChange = { onSaveTheme(themeSettings.isDarkMode, it) },
                        label = "Ultra Dim Mode (Night Library)",
                        sublabel = "Deep warm dark tones (#141619) for zero-glare study at night"
                    )
                }
            }

            "gestures" -> {
                // Section 8: Native Sensor Settings
                NeuCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Desk-Tap Accelerometer Sensor",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.accent
                    )
                    Text(
                        text = "Calibrates vibration spike sensitivity when phone rests flat on a table.",
                        fontSize = 11.sp,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                    )

                    NeuSlider(
                        value = gestureSettings.sensitivity.toFloat(),
                        onValueChange = { onSaveGesture(it.toInt()) },
                        valueRange = 1f..10f,
                        label = "Knock Sensitivity",
                        valueDisplay = "${gestureSettings.sensitivity} / 10"
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Gesture Mapping Table:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    listOf(
                        "1 tap" to "Play / Pause",
                        "2 taps" to "Next answer",
                        "3 taps" to "Previous answer",
                        "Tap, pause, tap" to "Repeat answer",
                        "Long knock" to "Short summary",
                        "4 taps" to "Stop & silent"
                    ).forEach { (gesture, action) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(gesture, fontSize = 11.sp, color = colors.accent, fontWeight = FontWeight.SemiBold)
                            Text(action, fontSize = 11.sp, color = colors.textSecondary)
                        }
                    }
                }
            }
        }
    }
}
