package com.silentstudy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silentstudy.SilentStudyApp
import com.silentstudy.ai.ConnectionResult
import com.silentstudy.ai.ModelInfo
import com.silentstudy.ui.components.AppButton
import com.silentstudy.ui.components.AppCard
import com.silentstudy.ui.components.AppTextField
import com.silentstudy.ui.components.DeskKnockPad
import com.silentstudy.ui.theme.LocalAppColors
import kotlinx.coroutines.launch

@Composable
fun FirstRunWizard(
    app: SilentStudyApp,
    onComplete: () -> Unit
) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()

    var step by remember { mutableStateOf(1) } // 1..4

    // Form state
    var provider by remember { mutableStateOf("gemini") }
    var apiKey by remember { mutableStateOf("") }
    var selectedModel by remember { mutableStateOf("gemini-2.5-flash") }
    var modelList by remember { mutableStateOf<List<ModelInfo>>(emptyList()) }
    var isLoadingModels by remember { mutableStateOf(false) }

    var testResult by remember { mutableStateOf<ConnectionResult?>(null) }
    var isTestingConnection by remember { mutableStateOf(false) }
    var sensitivity by remember { mutableStateOf(6) }

    // Fetch models when moving to step 2 or when provider/key changes
    val fetchModelsForProvider = {
        isLoadingModels = true
        scope.launch {
            val list = app.aiProviderManager.fetchModels(provider, apiKey, "http://localhost:11434/v1")
            modelList = list
            if (list.isNotEmpty()) {
                val best = list.firstOrNull { it.isVision } ?: list.first()
                selectedModel = best.id
            }
            isLoadingModels = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Wizard Header
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "SilentStudy Setup Wizard",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "Step $step of 4 · Private Library Companion",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.accent
                        )
                    }

                    Text(
                        text = "${(step * 25)}%",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.accent
                    )
                }
            }

            when (step) {
                1 -> {
                    // STEP 1: Provider & Key
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "1. Pick AI Provider & Enter Key",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.accent
                        )
                        Text(
                            text = "Zero lock-in. Your API key never leaves this device.",
                            fontSize = 11.sp,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                        )

                        val providers = listOf(
                            "gemini" to "Google Gemini (Recommended)",
                            "openai" to "OpenAI (GPT-4o)",
                            "openrouter" to "OpenRouter (Free vision models)",
                            "claude" to "Anthropic Claude",
                            "custom" to "Custom / Ollama Local"
                        )

                        providers.forEach { (pId, pName) ->
                            val isSel = provider == pId
                            AppButton(
                                onClick = {
                                    provider = pId
                                    if (pId == "openrouter" && apiKey.isBlank()) {
                                        // OpenRouter works without key
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                isAccent = isSel
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(pName, fontSize = 11.sp, color = if (isSel) colors.accent else colors.textPrimary)
                                    if (isSel) Icon(Icons.Default.Check, contentDescription = null, tint = colors.accent, modifier = Modifier.size(14.dp))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        AppTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            label = "API Key",
                            placeholder = if (provider == "openrouter") "Optional for OpenRouter free models" else "Paste your API key here"
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        AppButton(
                            onClick = {
                                fetchModelsForProvider()
                                step = 2
                            },
                            modifier = Modifier.fillMaxWidth(),
                            isAccent = true
                        ) {
                            Text("Next: Pick Model", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.accent)
                        }
                    }
                }

                2 -> {
                    // STEP 2: Pick Model & Test Connection
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "2. Pick Model & Test Connection",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.accent
                            )
                            AppButton(
                                onClick = { fetchModelsForProvider() }
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(12.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        if (isLoadingModels) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(color = colors.accent, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Fetching live models from $provider...", fontSize = 11.sp, color = colors.textSecondary)
                            }
                        } else {
                            Text("Available models:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                            Spacer(modifier = Modifier.height(6.dp))

                            val displayModels = if (modelList.isNotEmpty()) modelList.take(8) else app.aiProviderManager.getDefaultFallbackModels(provider)
                            displayModels.forEach { model ->
                                val isSel = selectedModel == model.id
                                AppButton(
                                    onClick = { selectedModel = model.id },
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    isAccent = isSel
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(model.name, fontSize = 11.sp, color = if (isSel) colors.accent else colors.textPrimary)
                                        if (model.isFree) {
                                            Text("FREE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = colors.accent)
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        AppTextField(
                            value = selectedModel,
                            onValueChange = { selectedModel = it },
                            label = "Selected Model ID",
                            placeholder = "model-id"
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        AppButton(
                            onClick = {
                                isTestingConnection = true
                                testResult = null
                                scope.launch {
                                    val res = app.aiProviderManager.testConnection(
                                        provider, apiKey, selectedModel, "http://localhost:11434/v1"
                                    )
                                    testResult = res
                                    isTestingConnection = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isTestingConnection) {
                                CircularProgressIndicator(color = colors.accent, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Testing...", fontSize = 11.sp, color = colors.textPrimary)
                            } else {
                                Text("Test Connection", fontSize = 11.sp, color = colors.textPrimary)
                            }
                        }

                        testResult?.let { res ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = res.message,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (res.success) colors.accent else colors.error
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppButton(onClick = { step = 1 }, modifier = Modifier.weight(1f)) {
                                Text("Back", fontSize = 11.sp, color = colors.textSecondary)
                            }
                            AppButton(
                                onClick = {
                                    scope.launch {
                                        app.preferencesRepository.saveAiConfig(provider, apiKey, selectedModel, "http://localhost:11434/v1")
                                    }
                                    step = 3
                                },
                                modifier = Modifier.weight(1f),
                                isAccent = true
                            ) {
                                Text("Next: Calibrate Taps", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.accent)
                            }
                        }
                    }
                }

                3 -> {
                    // STEP 3: Calibrate Desk Taps with Live Waves
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        DeskKnockPad(
                            sensorManager = app.deskTapSensor,
                            sensitivity = sensitivity,
                            onSensitivityChange = {
                                sensitivity = it
                                app.deskTapSensor.setSensitivity(it)
                                scope.launch { app.preferencesRepository.setGestureSensitivity(it) }
                            }
                        )

                        AppCard(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Desk Taps Ready",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "Rest the phone flat on your desk and knock near it to verify the wave pulses.",
                                fontSize = 11.sp,
                                color = colors.textSecondary,
                                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                            )

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AppButton(onClick = { step = 2 }, modifier = Modifier.weight(1f)) {
                                    Text("Back", fontSize = 11.sp, color = colors.textSecondary)
                                }
                                AppButton(onClick = { step = 4 }, modifier = Modifier.weight(1f), isAccent = true) {
                                    Text("Next: Voice Test", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.accent)
                                }
                            }
                        }
                    }
                }

                4 -> {
                    // STEP 4: Voice Test & Done
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "4. Private Earbuds Voice Test",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.accent
                        )
                        Text(
                            text = "Connect earbuds or turn on Speech Test Mode to verify natural earbud speech playback.",
                            fontSize = 11.sp,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(top = 2.dp, bottom = 14.dp)
                        )

                        AppButton(
                            onClick = { app.ttsManager.testVoice(1.0f) },
                            modifier = Modifier.fillMaxWidth(),
                            isAccent = true
                        ) {
                            Icon(Icons.Default.VolumeUp, contentDescription = null, tint = colors.accent, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Test Voice in Earbuds", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.accent)
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        AppButton(
                            onClick = {
                                app.ttsManager.enableTestMode()
                                app.ttsManager.playTestAnswer(1.0f)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Test Through Phone Speaker (Speech Test Mode)", fontSize = 11.sp, color = colors.textPrimary)
                        }

                        Spacer(modifier = Modifier.height(20.dp))
                        AppButton(
                            onClick = {
                                scope.launch {
                                    app.preferencesRepository.setFirstRunCompleted(true)
                                    onComplete()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            isAccent = true
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = colors.accent, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Complete Setup & Start Studying", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.accent)
                        }
                    }
                }
            }
        }
    }
}
