package com.silentstudy.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silentstudy.SilentStudyApp
import com.silentstudy.ai.ConnectionResult
import com.silentstudy.ai.ModelInfo
import com.silentstudy.data.repository.*
import com.silentstudy.ui.components.*
import com.silentstudy.ui.theme.LocalAppColors
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    app: SilentStudyApp,
    themeSettings: AppThemeSettings,
    aiSettings: AiSettings,
    budgetSettings: BudgetSettings,
    audioSettings: AudioSettings,
    gestureSettings: GestureSettings,
    onOpenTapControls: () -> Unit,
    onOpenUsageScreen: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onSaveUltraDim: (isUltraDim: Boolean) -> Unit,
    onSaveAi: (
        provider: String,
        key: String,
        model: String,
        baseUrl: String,
        fallbackProvider: String?,
        fallbackKey: String?,
        fallbackModel: String?,
        concise: Boolean,
        lazyExp: Boolean,
        cheapMode: Boolean,
        answerStyle: String
    ) -> Unit,
    onSaveAudio: (rate: Float, slowdown: Boolean, spokenMenu: Boolean, silentMode: Boolean, testMode: Boolean, spokenPrompts: Boolean) -> Unit,
    onSaveGesture: (sensitivity: Int) -> Unit
) {
    val context = LocalContext.current
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()

    var activeTab by remember { mutableStateOf("ai") } // ai, audio, gestures, tokens, appearance, diagnostics

    // AI Form State
    var selectedProvider by remember { mutableStateOf(aiSettings.provider) }
    var apiKeyInput by remember { mutableStateOf(aiSettings.apiKey) }
    var modelInput by remember { mutableStateOf(aiSettings.model) }
    var baseUrlInput by remember { mutableStateOf(aiSettings.baseUrl) }
    var fallbackProviderInput by remember { mutableStateOf(aiSettings.fallbackProvider) }
    var fallbackKeyInput by remember { mutableStateOf(aiSettings.fallbackApiKey) }
    var fallbackModelInput by remember { mutableStateOf(aiSettings.fallbackModel) }

    var conciseMode by remember { mutableStateOf(aiSettings.conciseMode) }
    var lazyExplanation by remember { mutableStateOf(aiSettings.lazyExplanation) }
    var cheapMode by remember { mutableStateOf(aiSettings.cheapMode) }
    var answerStyle by remember { mutableStateOf(aiSettings.answerStyle) }

    val lastGestureText by app.playbackController.lastGestureText.collectAsState()

    // Model dropdown & fetching state
    var fetchedModels by remember { mutableStateOf<List<ModelInfo>>(emptyList()) }
    var isFetchingModels by remember { mutableStateOf(false) }
    var isDropdownOpen by remember { mutableStateOf(false) }
    var visionOnlyFilter by remember { mutableStateOf(true) }
    var freeOnlyFilter by remember { mutableStateOf(false) }
    var modelSearchQuery by remember { mutableStateOf("") }
    var isManualModelInput by remember { mutableStateOf(false) }

    // Connection testing
    var testResult by remember { mutableStateOf<ConnectionResult?>(null) }
    var isTestingConnection by remember { mutableStateOf(false) }

    // Audio test mode remaining countdown
    val testModeSeconds by app.ttsManager.testModeSecondsRemaining.collectAsState()

    // Backup & Export notice
    var backupNotice by remember { mutableStateOf<String?>(null) }

    // Fetch models function
    val reloadModels = {
        isFetchingModels = true
        scope.launch {
            val list = app.aiProviderManager.fetchModels(selectedProvider, apiKeyInput, baseUrlInput)
            fetchedModels = list
            isFetchingModels = false
            if (list.isNotEmpty() && modelInput.isBlank()) {
                val defaultChoice = list.firstOrNull { it.isVision } ?: list.first()
                modelInput = defaultChoice.id
            }
        }
    }

    LaunchedEffect(selectedProvider, apiKeyInput) {
        if (apiKeyInput.isNotBlank() || selectedProvider == "openrouter") {
            reloadModels()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Speech Test Mode Banner (Section 5)
        if (audioSettings.testModeEnabled || testModeSeconds != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.accent.copy(alpha = 0.2f))
                    .border(1.dp, colors.accent, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "TEST MODE: speaker audio enabled",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.accent
                        )
                        val mins = (testModeSeconds ?: 600) / 60
                        val secs = (testModeSeconds ?: 600) % 60
                        Text(
                            text = "Auto-turns off in ${String.format("%02d:%02d", mins, secs)} or when study session starts",
                            fontSize = 10.sp,
                            color = colors.textSecondary
                        )
                    }

                    AppButton(
                        onClick = {
                            app.ttsManager.disableTestMode()
                            onSaveAudio(
                                audioSettings.speechRate,
                                audioSettings.mathSlowdown,
                                audioSettings.spokenMenuEnabled,
                                audioSettings.silentTextMode,
                                false,
                                audioSettings.spokenPromptsEnabled
                            )
                        }
                    ) {
                        Text("Turn Off", fontSize = 10.sp, color = colors.error)
                    }
                }
            }
        }

        // Header Card with Savage Tech Credits
        AppCard(modifier = Modifier.fillMaxWidth()) {
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
                        text = "Earbuds AI Companion · Savage Tech",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.accent
                    )
                }

                AppButton(
                    onClick = onOpenDiagnostics,
                    isAccent = true
                ) {
                    Icon(Icons.Default.HealthAndSafety, contentDescription = null, tint = colors.accent, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Diagnostics", fontSize = 11.sp, color = colors.accent, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Horizontal Category Tabs
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf(
                "ai" to "AI",
                "audio" to "Audio",
                "gestures" to "Taps",
                "tokens" to "Tokens",
                "appearance" to "Theme"
            ).forEach { (tab, label) ->
                val isSelected = activeTab == tab
                AppButton(
                    onClick = { activeTab = tab },
                    isAccent = isSelected,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(label, fontSize = 10.sp, color = if (isSelected) colors.accent else colors.textPrimary)
                }
            }
        }

        when (activeTab) {
            "ai" -> {
                // Section 1: AI Provider & Live Model Dropdown
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "AI Provider & Live Model Selection",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.accent
                    )
                    Text(
                        text = "No lock-in. Pick your provider, fetch live vision models, and test connections.",
                        fontSize = 11.sp,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                    )

                    // Provider Choices
                    val providers = listOf(
                        "gemini" to "Google Gemini",
                        "openrouter" to "OpenRouter (Free vision models)",
                        "openai" to "OpenAI (GPT-4o)",
                        "claude" to "Anthropic Claude",
                        "custom" to "Custom / Ollama Local"
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        providers.forEach { (pId, pName) ->
                            val isSel = selectedProvider.equals(pId, ignoreCase = true)
                            AppButton(
                                onClick = {
                                    selectedProvider = pId
                                    when (pId) {
                                        "gemini" -> modelInput = "gemini-2.5-flash"
                                        "openai" -> modelInput = "gpt-4o-mini"
                                        "claude" -> modelInput = "claude-3-5-haiku-20241022"
                                        "openrouter" -> modelInput = "google/gemini-2.0-flash-001"
                                        "custom" -> modelInput = "llama3.2-vision"
                                    }
                                    reloadModels()
                                },
                                modifier = Modifier.fillMaxWidth(),
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
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    AppTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        label = "API Key (Stored locally)",
                        placeholder = if (selectedProvider == "openrouter") "Optional for OpenRouter free models" else "Paste your API key here"
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Model Selection Header with Search & Filters
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Model Selection", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            AppButton(
                                onClick = { visionOnlyFilter = !visionOnlyFilter },
                                isAccent = visionOnlyFilter
                            ) {
                                Text("Vision Only", fontSize = 9.sp, color = if (visionOnlyFilter) colors.accent else colors.textSecondary)
                            }
                            if (selectedProvider == "openrouter") {
                                AppButton(
                                    onClick = { freeOnlyFilter = !freeOnlyFilter },
                                    isAccent = freeOnlyFilter
                                ) {
                                    Text("Free Only", fontSize = 9.sp, color = if (freeOnlyFilter) colors.accent else colors.textSecondary)
                                }
                            }
                            AppButton(onClick = { reloadModels() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh Models", tint = colors.textPrimary, modifier = Modifier.size(12.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Filtered Models List
                    val availableList = if (fetchedModels.isNotEmpty()) fetchedModels else app.aiProviderManager.getDefaultFallbackModels(selectedProvider)
                    val filteredModels = availableList.filter { m ->
                        (!visionOnlyFilter || m.isVision) &&
                        (!freeOnlyFilter || m.isFree) &&
                        (modelSearchQuery.isEmpty() || m.id.contains(modelSearchQuery, ignoreCase = true) || m.name.contains(modelSearchQuery, ignoreCase = true))
                    }

                    // Search box for models
                    AppTextField(
                        value = modelSearchQuery,
                        onValueChange = { modelSearchQuery = it },
                        label = "",
                        placeholder = "Search model by name or ID..."
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Model Dropdown Selector
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.control)
                            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                            .clickable { isDropdownOpen = !isDropdownOpen }
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = modelInput.ifEmpty { "Pick a model from dropdown" },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.accent,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = if (isFetchingModels) "Fetching live models..." else "${filteredModels.size} vision models available",
                                    fontSize = 10.sp,
                                    color = colors.textSecondary
                                )
                            }
                            Icon(
                                imageVector = if (isDropdownOpen) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = colors.accent
                            )
                        }
                    }

                    // Dropdown Options
                    if (isDropdownOpen) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.surface)
                                .border(1.dp, colors.accent.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            filteredModels.take(15).forEach { m ->
                                val isSelected = modelInput == m.id
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) colors.accent.copy(alpha = 0.2f) else colors.control)
                                        .clickable {
                                            modelInput = m.id
                                            isDropdownOpen = false
                                        }
                                        .padding(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(m.id, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                                            if (m.name != m.id) {
                                                Text(m.name, fontSize = 9.sp, color = colors.textSecondary)
                                            }
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            if (m.isVision) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(colors.accent.copy(alpha = 0.3f))
                                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                                ) {
                                                    Text("Vision", fontSize = 8.sp, color = colors.accent, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                            if (m.isFree) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(Color(0xFF4CAF50).copy(alpha = 0.3f))
                                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                                ) {
                                                    Text("Free", fontSize = 8.sp, color = Color(0xFF81C784), fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Manual fallback option
                            AppButton(
                                onClick = {
                                    isManualModelInput = true
                                    isDropdownOpen = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Type model ID manually...", fontSize = 10.sp, color = colors.textSecondary)
                            }
                        }
                    }

                    if (isManualModelInput) {
                        Spacer(modifier = Modifier.height(8.dp))
                        AppTextField(
                            value = modelInput,
                            onValueChange = { modelInput = it },
                            label = "Manual Model ID",
                            placeholder = "e.g. gemini-2.0-flash"
                        )
                    }

                    if (selectedProvider == "custom") {
                        Spacer(modifier = Modifier.height(8.dp))
                        AppTextField(
                            value = baseUrlInput,
                            onValueChange = { baseUrlInput = it },
                            label = "Custom Base URL",
                            placeholder = "http://localhost:11434/v1"
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Answer Style Selection (Direct by default, Direct + Reason, Show Working)
                    Text(
                        text = "Answer Delivery Style",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "Control how answers are structured and verbalized via TTS.",
                        fontSize = 10.sp,
                        color = colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    val styles = listOf(
                        Triple("direct", "Direct Answer (Default)", "Only the final answer, no fluff, split into sequential parts with 600ms pauses"),
                        Triple("direct_reason", "Direct + Short Reason", "Final answer first, followed by a single concise justification sentence"),
                        Triple("working", "Show Working", "Full step-by-step breakdown and working process")
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        styles.forEach { (styleKey, title, desc) ->
                            val isSel = answerStyle == styleKey
                            AppButton(
                                onClick = { answerStyle = styleKey },
                                modifier = Modifier.fillMaxWidth(),
                                isAccent = isSel
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (isSel) colors.accent else colors.textPrimary)
                                        Text(desc, fontSize = 9.sp, color = colors.textSecondary)
                                    }
                                    if (isSel) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = colors.accent, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Concise, Lazy & Cheap Mode Toggles
                    AppToggle(
                        checked = conciseMode,
                        onCheckedChange = { conciseMode = it },
                        label = "Concise Mode",
                        sublabel = "Strict token reduction and shortest possible answers"
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    AppToggle(
                        checked = lazyExplanation,
                        onCheckedChange = { lazyExplanation = it },
                        label = "Lazy Explanation",
                        sublabel = "Defers detailed explanation until explicitly requested via gestures"
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    AppToggle(
                        checked = cheapMode,
                        onCheckedChange = { cheapMode = it },
                        label = "Cheap / Economical Token Mode",
                        sublabel = "Limits output token budget to reduce API costs"
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Fallback provider section
                    Text(
                        text = "Automatic Failover / Fallback Provider",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "If primary request fails with 404, 429, or 5xx, the app automatically retries with this fallback.",
                        fontSize = 10.sp,
                        color = colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(modifier = Modifier.weight(1f)) {
                            AppTextField(
                                value = fallbackProviderInput,
                                onValueChange = { fallbackProviderInput = it },
                                label = "Fallback Provider",
                                placeholder = "openai, gemini, etc."
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            AppTextField(
                                value = fallbackModelInput,
                                onValueChange = { fallbackModelInput = it },
                                label = "Fallback Model",
                                placeholder = "gpt-4o-mini"
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    AppTextField(
                        value = fallbackKeyInput,
                        onValueChange = { fallbackKeyInput = it },
                        label = "Fallback API Key",
                        placeholder = "Paste fallback API key here"
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Test Connection Button with selected model & Save Configuration
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppButton(
                            onClick = {
                                isTestingConnection = true
                                testResult = null
                                scope.launch {
                                    val res = app.aiProviderManager.testConnection(
                                        selectedProvider, apiKeyInput, modelInput, baseUrlInput
                                    )
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

                        AppButton(
                            onClick = {
                                onSaveAi(
                                    selectedProvider,
                                    apiKeyInput,
                                    modelInput,
                                    baseUrlInput,
                                    fallbackProviderInput,
                                    fallbackKeyInput,
                                    fallbackModelInput,
                                    conciseMode,
                                    lazyExplanation,
                                    cheapMode,
                                    answerStyle
                                )
                            },
                            modifier = Modifier.weight(1f),
                            isAccent = true
                        ) {
                            Text("Save Configuration", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.accent)
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
                }
            }

            "audio" -> {
                // Section 5: Audio & Speech Test Mode
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Audio & Earbuds Privacy Guard",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.accent
                    )
                    Text(
                        text = "Strict privacy: Audio never plays through phone speaker unless Test Mode is active.",
                        fontSize = 11.sp,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                    )

                    // Speech Test Mode (Section 5)
                    AppToggle(
                        checked = audioSettings.testModeEnabled,
                        onCheckedChange = { isEnabled ->
                            if (isEnabled) {
                                app.ttsManager.enableTestMode()
                            } else {
                                app.ttsManager.disableTestMode()
                            }
                            onSaveAudio(
                                audioSettings.speechRate,
                                audioSettings.mathSlowdown,
                                audioSettings.spokenMenuEnabled,
                                audioSettings.silentTextMode,
                                isEnabled,
                                audioSettings.spokenPromptsEnabled
                            )
                        },
                        label = "Speech Test Mode (Speaker Allowed)",
                        sublabel = "Temporary 10-minute window allowing phone speaker for testing without earbuds. Auto-turns off on study session."
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppButton(
                            onClick = { app.ttsManager.testVoice(audioSettings.speechRate) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Test Voice in Earbuds", fontSize = 11.sp, color = colors.textPrimary)
                        }
                        AppButton(
                            onClick = { app.ttsManager.playTestAnswer(audioSettings.speechRate) },
                            modifier = Modifier.weight(1f),
                            isAccent = true
                        ) {
                            Text("Play Test Answer", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.accent)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    AppToggle(
                        checked = audioSettings.mathSlowdown,
                        onCheckedChange = {
                            onSaveAudio(audioSettings.speechRate, it, audioSettings.spokenMenuEnabled, audioSettings.silentTextMode, audioSettings.testModeEnabled, audioSettings.spokenPromptsEnabled)
                        },
                        label = "Math Equation Auto-Slowdown",
                        sublabel = "Slows down to 0.75x with pauses on formulas and step proofs"
                    )

                    AppToggle(
                        checked = audioSettings.spokenMenuEnabled,
                        onCheckedChange = {
                            onSaveAudio(audioSettings.speechRate, audioSettings.mathSlowdown, it, audioSettings.silentTextMode, audioSettings.testModeEnabled, audioSettings.spokenPromptsEnabled)
                        },
                        label = "Spoken After-Answer Menu",
                        sublabel = "Speaks 'Tap once to repeat...' after each answer completes"
                    )

                    AppToggle(
                        checked = audioSettings.spokenPromptsEnabled,
                        onCheckedChange = {
                            onSaveAudio(audioSettings.speechRate, audioSettings.mathSlowdown, audioSettings.spokenMenuEnabled, audioSettings.silentTextMode, audioSettings.testModeEnabled, it)
                        },
                        label = "Earbud Status Announcements",
                        sublabel = "Speaks 'Answer saved' and 'Earbuds disconnected' in earbuds"
                    )

                    AppToggle(
                        checked = audioSettings.silentTextMode,
                        onCheckedChange = {
                            onSaveAudio(audioSettings.speechRate, audioSettings.mathSlowdown, audioSettings.spokenMenuEnabled, it, audioSettings.testModeEnabled, audioSettings.spokenPromptsEnabled)
                        },
                        label = "Silent Text Mode",
                        sublabel = "Displays answers on screen only without TTS synthesis"
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    AppSlider(
                        value = audioSettings.speechRate,
                        onValueChange = {
                            onSaveAudio(it, audioSettings.mathSlowdown, audioSettings.spokenMenuEnabled, audioSettings.silentTextMode, audioSettings.testModeEnabled, audioSettings.spokenPromptsEnabled)
                        },
                        valueRange = 0.75f..2.0f,
                        label = "Speech Speed",
                        valueDisplay = String.format("%.2fx", audioSettings.speechRate)
                    )
                }
            }

            "gestures" -> {
                // Section 3 & 4: Desk Gestures & Live Waves Box
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Last gesture live feedback banner
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Text("LAST GESTURE DETECTED", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = colors.accent)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (lastGestureText.isNotBlank()) lastGestureText else "No desk taps detected yet",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary
                            )
                        }
                    }

                    // Live Wave Pad (Section 3 RESTORE)
                    DeskKnockPad(
                        sensorManager = app.deskTapSensor,
                        sensitivity = gestureSettings.sensitivity,
                        onSensitivityChange = {
                            app.deskTapSensor.setSensitivity(it)
                            onSaveGesture(it)
                        }
                    )

                    // Navigation to Full Customization Screen
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Custom Tap Controls & Actions",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary
                                )
                                Text(
                                    text = "Remap all actions, record custom knock patterns, configure tap windows, and practice.",
                                    fontSize = 11.sp,
                                    color = colors.textSecondary
                                )
                            }
                            AppButton(
                                onClick = onOpenTapControls,
                                isAccent = true
                            ) {
                                Text("Configure Taps", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.accent)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        AppToggle(
                            checked = gestureSettings.hapticsEnabled,
                            onCheckedChange = {
                                app.deskTapSensor.hapticsEnabled = it
                                scope.launch { app.preferencesRepository.setHapticsEnabled(it) }
                            },
                            label = "Haptic Vibration Confirmation",
                            sublabel = "Vibrates gently when a desk knock pattern is recognized"
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        AppToggle(
                            checked = gestureSettings.ignorePhoneTouches,
                            onCheckedChange = {
                                app.deskTapSensor.ignorePhoneTouches = it
                                scope.launch { app.preferencesRepository.setIgnorePhoneTouches(it) }
                            },
                            label = "Ignore Screen & Phone Touches",
                            sublabel = "Rejects vibrations occurring within 200ms of touching the phone screen"
                        )
                    }
                }
            }

            "tokens" -> {
                // Section 2: Token and Cost Control Settings
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Token & Budget Controls",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.accent
                            )
                            Text(
                                text = "Telemetry, budget caps, and cost protection",
                                fontSize = 11.sp,
                                color = colors.textSecondary
                            )
                        }

                        AppButton(
                            onClick = onOpenUsageScreen,
                            isAccent = true
                        ) {
                            Text("Open Usage & Logs", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.accent)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    AppToggle(
                        checked = conciseMode,
                        onCheckedChange = {
                            conciseMode = it
                            onSaveAi(selectedProvider, apiKeyInput, modelInput, baseUrlInput, fallbackProviderInput, fallbackKeyInput, fallbackModelInput, it, lazyExplanation, cheapMode)
                        },
                        label = "Concise Mode (Token Saver)",
                        sublabel = "Instructs AI to be brief, compact, and avoid repetitive phrasing."
                    )

                    AppToggle(
                        checked = lazyExplanation,
                        onCheckedChange = {
                            lazyExplanation = it
                            onSaveAi(selectedProvider, apiKeyInput, modelInput, baseUrlInput, fallbackProviderInput, fallbackKeyInput, fallbackModelInput, conciseMode, it, cheapMode)
                        },
                        label = "Lazy Explanation Generation",
                        sublabel = "Initial request only returns direct answer & steps. Simple explanations are generated only when requested."
                    )

                    AppToggle(
                        checked = cheapMode,
                        onCheckedChange = {
                            cheapMode = it
                            onSaveAi(selectedProvider, apiKeyInput, modelInput, baseUrlInput, fallbackProviderInput, fallbackKeyInput, fallbackModelInput, conciseMode, lazyExplanation, it)
                        },
                        label = "Cheap Mode",
                        sublabel = "Picks the lowest cost vision model and lowest output token limit."
                    )
                }
            }

            "appearance" -> {
                // Appearance & Night Theme
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Appearance & Low-Glare Design",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.accent
                    )
                    Text(
                        text = "Strict low-glare dark theme (#121418 / #1B1E24). Zero white everywhere.",
                        fontSize = 11.sp,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                    )

                    AppToggle(
                        checked = themeSettings.isUltraDimMode,
                        onCheckedChange = { onSaveUltraDim(it) },
                        label = "Ultra Dim Mode (Night Library)",
                        sublabel = "Uses even deeper tones (#0C0E11 background, #14171C cards) for maximum eye comfort."
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Settings & Backup", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppButton(
                            onClick = {
                                val json = app.preferencesRepository.exportSettingsJson()
                                backupNotice = "Settings exported (${json.length} bytes)."
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Export Settings (No Keys)", fontSize = 10.sp, color = colors.textPrimary)
                        }
                    }

                    if (backupNotice != null) {
                        Text(
                            text = backupNotice!!,
                            fontSize = 11.sp,
                            color = colors.accent,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
