package com.silentstudy

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.silentstudy.ai.AnswerRequest
import com.silentstudy.data.model.Answer
import com.silentstudy.data.model.AnswerBlock
import com.silentstudy.gestures.DeskGesture
import com.silentstudy.gestures.GestureAction
import com.silentstudy.service.SilentStudyService
import com.silentstudy.ui.components.AfterAnswerMenu
import com.silentstudy.ui.components.StatusPill
import com.silentstudy.ui.screens.*
import com.silentstudy.ui.theme.LocalAppColors
import com.silentstudy.ui.theme.SilentStudyTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    private var batteryReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestRequiredPermissions()

        val app = application as SilentStudyApp

        setContent {
            val themeSettings by app.preferencesRepository.themeSettings.collectAsState(
                initial = com.silentstudy.data.repository.AppThemeSettings(isDarkMode = true, isUltraDimMode = false)
            )
            val aiSettings by app.preferencesRepository.aiSettings.collectAsState(
                initial = com.silentstudy.data.repository.AiSettings()
            )
            val budgetSettings by app.preferencesRepository.budgetSettings.collectAsState(
                initial = com.silentstudy.data.repository.BudgetSettings()
            )
            val audioSettings by app.preferencesRepository.audioSettings.collectAsState(
                initial = com.silentstudy.data.repository.AudioSettings()
            )
            val gestureSettings by app.preferencesRepository.gestureSettings.collectAsState(
                initial = com.silentstudy.data.repository.GestureSettings()
            )
            val firstRunCompleted by app.preferencesRepository.firstRunCompleted.collectAsState(
                initial = true
            )
            val lowBatteryCutoff by app.preferencesRepository.lowBatteryPercent.collectAsState(
                initial = 15
            )

            val answers by app.database.answerDao().getAllAnswers().collectAsState(initial = emptyList())
            val isEarbudsConnected by app.earbudsGuard.isEarbudsConnected.collectAsState()
            val earbudDeviceName by app.earbudsGuard.connectedDeviceName.collectAsState()

            val scope = rememberCoroutineScope()

            var activeTab by remember { mutableStateOf("capture") } // capture, answers, study, settings, tap_controls, usage, diagnostics
            var isDeskScreenOffOpen by remember { mutableStateOf(false) }
            var showFirstRunWizard by remember { mutableStateOf(false) }

            // Audio playback sequence state
            var currentPlayingAnswer by remember { mutableStateOf<Answer?>(null) }
            var currentBlockIndex by remember { mutableStateOf(0) }
            var isPlaying by remember { mutableStateOf(false) }
            var showAfterAnswerMenu by remember { mutableStateOf(false) }
            var afterMenuSeconds by remember { mutableStateOf(5) }

            // Study session stats
            var isSessionActive by remember { mutableStateOf(false) }
            var sessionDurationSeconds by remember { mutableStateOf(0L) }
            var answersHeardCount by remember { mutableStateOf(0) }

            // First run check
            LaunchedEffect(firstRunCompleted) {
                if (!firstRunCompleted) {
                    showFirstRunWizard = true
                }
            }

            // Restore interrupted playback on launch
            LaunchedEffect(Unit) {
                val lastPos = app.preferencesRepository.lastPlaybackPosition.first()
                if (lastPos.first > 0) {
                    val saved = app.database.answerDao().getAnswerById(lastPos.first)
                    if (saved != null) {
                        currentPlayingAnswer = saved
                        currentBlockIndex = lastPos.second.coerceAtMost((saved.blocks.size - 1).coerceAtLeast(0))
                    }
                }
            }

            // Low Battery Guard
            DisposableEffect(isSessionActive, lowBatteryCutoff) {
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                            val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale) else 100

                            if (batteryPct <= lowBatteryCutoff && isSessionActive) {
                                isSessionActive = false
                                app.ttsManager.spokenPrompt("Battery below $lowBatteryCutoff percent. Study session stopped.")
                            }
                        }
                    }
                }
                batteryReceiver = receiver
                registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                onDispose {
                    try {
                        unregisterReceiver(receiver)
                    } catch (_: Exception) {}
                }
            }

            // Active study session controller
            LaunchedEffect(isSessionActive) {
                if (isSessionActive) {
                    // Turn off speech test mode when study session starts (Section 5)
                    if (app.ttsManager.testModeSecondsRemaining.value != null || audioSettings.testModeEnabled) {
                        app.ttsManager.disableTestMode()
                        app.preferencesRepository.setTestModeEnabled(false)
                    }

                    SilentStudyService.startService(this@MainActivity)
                    while (isSessionActive) {
                        delay(1000)
                        sessionDurationSeconds += 1
                    }
                } else {
                    SilentStudyService.stopService(this@MainActivity)
                }
            }

            // Sequential audio block playback
            val playCurrentBlock: (Answer, Int) -> Unit = { answer, index ->
                val block = answer.blocks.getOrNull(index)
                if (block != null) {
                    val isMath = block.type == "math" || block.type == "step"
                    val rate = if (audioSettings.mathSlowdown && isMath) {
                        audioSettings.mathSlowdownRate
                    } else {
                        audioSettings.speechRate
                    }
                    val speechText = if (block.type == "step" && block.stepNumber != null) {
                        "Step ${block.stepNumber}. ${block.spoken}"
                    } else {
                        block.spoken
                    }
                    app.ttsManager.speak(speechText, "block_$index", speechRate = rate)

                    // Persist current position for crash recovery
                    scope.launch {
                        app.preferencesRepository.saveLastPlaybackPosition(answer.id, index)
                    }
                } else {
                    // Finished all blocks -> Trigger After-Answer Menu
                    isPlaying = false
                    showAfterAnswerMenu = true
                    answersHeardCount += 1
                    if (audioSettings.spokenMenuEnabled) {
                        app.ttsManager.speak("Tap once to repeat. Tap twice for next answer.", "after_menu")
                    }
                }
            }

            // Utterance completion progression
            DisposableEffect(Unit) {
                app.ttsManager.onUtteranceFinished = { utteranceId ->
                    if (utteranceId.startsWith("block_")) {
                        scope.launch {
                            delay(400) // Pause between steps/equations
                            val nextIndex = currentBlockIndex + 1
                            currentBlockIndex = nextIndex
                            currentPlayingAnswer?.let { ans ->
                                playCurrentBlock(ans, nextIndex)
                            }
                        }
                    }
                }
                onDispose {
                    app.ttsManager.onUtteranceFinished = null
                }
            }

            // 5-second countdown for after-answer menu
            LaunchedEffect(showAfterAnswerMenu) {
                if (showAfterAnswerMenu) {
                    afterMenuSeconds = 5
                    while (afterMenuSeconds > 0) {
                        delay(1000)
                        afterMenuSeconds -= 1
                    }
                    showAfterAnswerMenu = false
                }
            }

            // Global Customizable Desk Gesture Handler (Section 4)
            LaunchedEffect(gestureSettings.customMappings, showAfterAnswerMenu, isPlaying, answers, currentPlayingAnswer) {
                app.deskTapSensor.gestures.collectLatest { gesture ->
                    val actionName = gestureSettings.customMappings[gesture.name] ?: GestureAction.PLAY_PAUSE.name
                    val action = try {
                        GestureAction.valueOf(actionName)
                    } catch (_: Exception) {
                        GestureAction.PLAY_PAUSE
                    }

                    when (action) {
                        GestureAction.PLAY_PAUSE -> {
                            if (showAfterAnswerMenu && currentPlayingAnswer != null) {
                                showAfterAnswerMenu = false
                                currentBlockIndex = 0
                                isPlaying = true
                                playCurrentBlock(currentPlayingAnswer!!, 0)
                            } else if (isPlaying) {
                                app.ttsManager.stop()
                                isPlaying = false
                            } else if (currentPlayingAnswer != null) {
                                isPlaying = true
                                playCurrentBlock(currentPlayingAnswer!!, currentBlockIndex)
                            } else if (answers.isNotEmpty()) {
                                currentPlayingAnswer = answers.first()
                                currentBlockIndex = 0
                                isPlaying = true
                                playCurrentBlock(answers.first(), 0)
                            }
                        }
                        GestureAction.NEXT_ANSWER -> {
                            if (showAfterAnswerMenu) showAfterAnswerMenu = false
                            if (answers.isNotEmpty()) {
                                val currentIdx = answers.indexOfFirst { it.id == currentPlayingAnswer?.id }
                                val nextIdx = if (currentIdx != -1) (currentIdx + 1) % answers.size else 0
                                currentPlayingAnswer = answers[nextIdx]
                                currentBlockIndex = 0
                                isPlaying = true
                                playCurrentBlock(answers[nextIdx], 0)
                            }
                        }
                        GestureAction.PREV_ANSWER -> {
                            if (showAfterAnswerMenu) showAfterAnswerMenu = false
                            if (answers.isNotEmpty()) {
                                val currentIdx = answers.indexOfFirst { it.id == currentPlayingAnswer?.id }
                                val prevIdx = if (currentIdx > 0) currentIdx - 1 else answers.size - 1
                                currentPlayingAnswer = answers[prevIdx]
                                currentBlockIndex = 0
                                isPlaying = true
                                playCurrentBlock(answers[prevIdx], 0)
                            }
                        }
                        GestureAction.REPEAT -> {
                            if (showAfterAnswerMenu) showAfterAnswerMenu = false
                            currentPlayingAnswer?.let { ans ->
                                currentBlockIndex = 0
                                isPlaying = true
                                playCurrentBlock(ans, 0)
                            }
                        }
                        GestureAction.SHORT_SUMMARY -> {
                            currentPlayingAnswer?.let { ans ->
                                app.ttsManager.speak(ans.shortAnswer, "short_summary")
                            }
                        }
                        GestureAction.EXPLAIN_SIMPLER -> {
                            if (showAfterAnswerMenu) showAfterAnswerMenu = false
                            currentPlayingAnswer?.let { ans ->
                                if (ans.simpleExplanation.isNotEmpty() && !ans.simpleExplanation.contains("(Generated on demand)")) {
                                    app.ttsManager.speak(ans.simpleExplanation, "simple_explanation")
                                } else {
                                    // Generate simple explanation on demand
                                    scope.launch {
                                        val explanation = app.aiProviderManager.generateSimpleExplanation(
                                            provider = aiSettings.provider,
                                            apiKey = aiSettings.apiKey,
                                            model = aiSettings.model,
                                            baseUrl = aiSettings.baseUrl,
                                            questionText = ans.questionText,
                                            shortAnswer = ans.shortAnswer
                                        )
                                        val updated = ans.copy(simpleExplanation = explanation)
                                        app.database.answerDao().updateAnswer(updated)
                                        currentPlayingAnswer = updated
                                        app.ttsManager.speak(explanation, "simple_explanation")
                                    }
                                }
                            }
                        }
                        GestureAction.STOP_SILENT -> {
                            app.ttsManager.stop()
                            isPlaying = false
                            showAfterAnswerMenu = false
                            isDeskScreenOffOpen = false
                        }
                        GestureAction.JUMP_TO_N -> {
                            if (answers.isNotEmpty()) {
                                val currentIdx = answers.indexOfFirst { it.id == currentPlayingAnswer?.id }
                                val jumpIdx = (currentIdx + 5) % answers.size
                                currentPlayingAnswer = answers[jumpIdx]
                                currentBlockIndex = 0
                                isPlaying = true
                                playCurrentBlock(answers[jumpIdx], 0)
                            }
                        }
                        GestureAction.SPEED_UP -> {
                            val newRate = (audioSettings.speechRate + 0.25f).coerceAtMost(2.0f)
                            scope.launch { app.preferencesRepository.setSpeechRate(newRate) }
                            app.ttsManager.spokenPrompt("Speed ${String.format("%.2f", newRate)}")
                        }
                        GestureAction.SPEED_DOWN -> {
                            val newRate = (audioSettings.speechRate - 0.25f).coerceAtLeast(0.75f)
                            scope.launch { app.preferencesRepository.setSpeechRate(newRate) }
                            app.ttsManager.spokenPrompt("Speed ${String.format("%.2f", newRate)}")
                        }
                        GestureAction.START_END_SESSION -> {
                            isSessionActive = !isSessionActive
                        }
                        GestureAction.GESTURE_HELP -> {
                            val summary = gestureSettings.customMappings.entries.joinToString(". ") {
                                "${it.key.replace('_', ' ')}: ${it.value.replace('_', ' ')}"
                            }
                            app.ttsManager.speak("Gesture controls: $summary", "gesture_help")
                        }
                        else -> {}
                    }
                }
            }

            SilentStudyTheme(
                isDarkMode = themeSettings.isDarkMode,
                isUltraDimMode = themeSettings.isUltraDimMode
            ) {
                val appColors = LocalAppColors.current

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = appColors.background
                ) {
                    if (showFirstRunWizard) {
                        FirstRunWizard(
                            app = app,
                            onComplete = { showFirstRunWizard = false }
                        )
                    } else if (isDeskScreenOffOpen) {
                        DeskScreenOffScreen(
                            currentAnswerSummary = currentPlayingAnswer?.let { "#${it.id} ${it.subject}" },
                            onWake = { isDeskScreenOffOpen = false }
                        )
                    } else if (activeTab == "tap_controls") {
                        TapControlsScreen(
                            app = app,
                            gestureSettings = gestureSettings,
                            onBack = { activeTab = "settings" }
                        )
                    } else if (activeTab == "usage") {
                        UsageScreen(
                            app = app,
                            budgetSettings = budgetSettings,
                            cheapMode = aiSettings.cheapMode,
                            onToggleCheapMode = { isCheap ->
                                scope.launch {
                                    val bestModel = if (isCheap) {
                                        val fallback = app.aiProviderManager.getDefaultFallbackModels(aiSettings.provider)
                                        fallback.firstOrNull { it.isFree }?.id ?: fallback.first().id
                                    } else {
                                        aiSettings.model
                                    }
                                    app.preferencesRepository.saveAiConfig(
                                        aiSettings.provider,
                                        aiSettings.apiKey,
                                        bestModel,
                                        aiSettings.baseUrl,
                                        cheapMode = isCheap
                                    )
                                }
                            },
                            onBack = { activeTab = "settings" }
                        )
                    } else if (activeTab == "diagnostics") {
                        HealthCheckScreen(
                            app = app,
                            onBack = { activeTab = "settings" }
                        )
                    } else {
                        Scaffold(
                            containerColor = appColors.background,
                            topBar = {
                                StatusPill(
                                    isEarbudsConnected = isEarbudsConnected,
                                    deviceName = earbudDeviceName,
                                    isSensorActive = true,
                                    sessionDurationSeconds = sessionDurationSeconds,
                                    onDeskModeClick = { isDeskScreenOffOpen = true }
                                )
                            },
                            bottomBar = {
                                NavigationBar(
                                    containerColor = appColors.surface,
                                    tonalElevation = 0.dp
                                ) {
                                    val navItems = listOf(
                                        Triple("capture", "Capture", Icons.Default.CameraAlt),
                                        Triple("answers", "Answers", Icons.Default.Book),
                                        Triple("study", "Study", Icons.Default.School),
                                        Triple("settings", "Settings", Icons.Default.Settings)
                                    )
                                    navItems.forEach { (tabId, label, icon) ->
                                        val isSel = activeTab == tabId
                                        NavigationBarItem(
                                            selected = isSel,
                                            onClick = { activeTab = tabId },
                                            icon = {
                                                Icon(
                                                    imageVector = icon,
                                                    contentDescription = label,
                                                    tint = if (isSel) appColors.accent else appColors.textSecondary
                                                )
                                            },
                                            label = {
                                                Text(
                                                    text = label,
                                                    fontSize = 10.sp,
                                                    color = if (isSel) appColors.accent else appColors.textSecondary
                                                )
                                            },
                                            colors = NavigationBarItemDefaults.colors(
                                                indicatorColor = appColors.control,
                                                selectedIconColor = appColors.accent,
                                                unselectedIconColor = appColors.textSecondary,
                                                selectedTextColor = appColors.accent,
                                                unselectedTextColor = appColors.textSecondary
                                            )
                                        )
                                    }
                                }
                            }
                        ) { paddingValues ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(paddingValues)
                            ) {
                                when (activeTab) {
                                    "capture" -> {
                                        CaptureScreen(
                                            app = app,
                                            onAnswerGenerated = { newAns ->
                                                scope.launch {
                                                    val id = if (newAns.id > 0) {
                                                        app.database.answerDao().updateAnswer(newAns)
                                                        newAns.id
                                                    } else {
                                                        app.database.answerDao().insertAnswer(newAns)
                                                    }
                                                    val saved = newAns.copy(id = id)
                                                    currentPlayingAnswer = saved
                                                    currentBlockIndex = 0
                                                    isPlaying = true
                                                    playCurrentBlock(saved, 0)
                                                    activeTab = "answers"
                                                }
                                            },
                                            onAnalyzeError = {}
                                        )
                                    }
                                    "answers" -> {
                                        Column(modifier = Modifier.fillMaxSize()) {
                                            if (showAfterAnswerMenu) {
                                                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                                    AfterAnswerMenu(
                                                        countdownSeconds = afterMenuSeconds,
                                                        onRepeat = {
                                                            showAfterAnswerMenu = false
                                                            currentPlayingAnswer?.let { ans ->
                                                                currentBlockIndex = 0
                                                                isPlaying = true
                                                                playCurrentBlock(ans, 0)
                                                            }
                                                        },
                                                        onNext = {
                                                            showAfterAnswerMenu = false
                                                            if (answers.isNotEmpty()) {
                                                                val currentIdx = answers.indexOfFirst { it.id == currentPlayingAnswer?.id }
                                                                val nextIdx = (currentIdx + 1) % answers.size
                                                                currentPlayingAnswer = answers[nextIdx]
                                                                currentBlockIndex = 0
                                                                isPlaying = true
                                                                playCurrentBlock(answers[nextIdx], 0)
                                                            }
                                                        },
                                                        onSimpler = {
                                                            showAfterAnswerMenu = false
                                                            currentPlayingAnswer?.let { ans ->
                                                                app.ttsManager.speak(ans.simpleExplanation, "simple_explanation")
                                                            }
                                                        }
                                                    )
                                                }
                                            }

                                            AnswersScreen(
                                                answers = answers,
                                                onPlayAnswer = { ans ->
                                                    currentPlayingAnswer = ans
                                                    currentBlockIndex = 0
                                                    isPlaying = true
                                                    playCurrentBlock(ans, 0)
                                                },
                                                onToggleStar = { id ->
                                                    scope.launch {
                                                        app.database.answerDao().toggleStar(id)
                                                    }
                                                },
                                                onDeleteAnswer = { ans ->
                                                    scope.launch {
                                                        app.database.answerDao().deleteAnswer(ans)
                                                    }
                                                },
                                                onRestoreAnswer = { ans ->
                                                    scope.launch {
                                                        app.database.answerDao().insertAnswer(ans)
                                                    }
                                                },
                                                onRetryAnswer = { failedAns ->
                                                    scope.launch {
                                                        val res = app.aiProviderManager.generateAnswer(
                                                            provider = aiSettings.provider,
                                                            apiKey = aiSettings.apiKey,
                                                            model = aiSettings.model,
                                                            baseUrl = aiSettings.baseUrl,
                                                            request = AnswerRequest(
                                                                imagesBase64 = failedAns.imagePaths,
                                                                promptNote = failedAns.questionText,
                                                                conciseMode = aiSettings.conciseMode,
                                                                lazyExplanation = aiSettings.lazyExplanation
                                                            )
                                                        )
                                                        val retried = failedAns.copy(
                                                            subject = res.subject,
                                                            questionText = res.questionText,
                                                            shortAnswer = res.shortAnswer,
                                                            blocks = res.blocks,
                                                            simpleExplanation = res.simpleExplanation,
                                                            status = "completed",
                                                            errorMessage = null
                                                        )
                                                        app.database.answerDao().updateAnswer(retried)
                                                    }
                                                },
                                                onGenerateSimpleExplanation = { ans ->
                                                    scope.launch {
                                                        val exp = app.aiProviderManager.generateSimpleExplanation(
                                                            provider = aiSettings.provider,
                                                            apiKey = aiSettings.apiKey,
                                                            model = aiSettings.model,
                                                            baseUrl = aiSettings.baseUrl,
                                                            questionText = ans.questionText,
                                                            shortAnswer = ans.shortAnswer
                                                        )
                                                        val updated = ans.copy(simpleExplanation = exp)
                                                        app.database.answerDao().updateAnswer(updated)
                                                        currentPlayingAnswer = updated
                                                        app.ttsManager.speak(exp, "simple_explanation")
                                                    }
                                                }
                                            )
                                        }
                                    }
                                    "study" -> {
                                        StudyScreen(
                                            answers = answers,
                                            isSessionActive = isSessionActive,
                                            sessionDurationSeconds = sessionDurationSeconds,
                                            answersHeardCount = answersHeardCount,
                                            sensorManager = app.deskTapSensor,
                                            sensitivity = gestureSettings.sensitivity,
                                            onSensitivityChange = { sens ->
                                                scope.launch {
                                                    app.preferencesRepository.setGestureSensitivity(sens)
                                                    app.deskTapSensor.setSensitivity(sens)
                                                }
                                            },
                                            onToggleSession = { isSessionActive = !isSessionActive }
                                        )
                                    }
                                    "settings" -> {
                                        SettingsScreen(
                                            app = app,
                                            themeSettings = themeSettings,
                                            aiSettings = aiSettings,
                                            budgetSettings = budgetSettings,
                                            audioSettings = audioSettings,
                                            gestureSettings = gestureSettings,
                                            onOpenTapControls = { activeTab = "tap_controls" },
                                            onOpenUsageScreen = { activeTab = "usage" },
                                            onOpenDiagnostics = { activeTab = "diagnostics" },
                                            onSaveUltraDim = { isUltraDim ->
                                                scope.launch {
                                                    app.preferencesRepository.setUltraDimMode(isUltraDim)
                                                }
                                            },
                                            onSaveAi = { provider, key, model, baseUrl, fbProvider, fbKey, fbModel, concise, lazyExp, cheap ->
                                                scope.launch {
                                                    app.preferencesRepository.saveAiConfig(
                                                        provider = provider,
                                                        apiKey = key,
                                                        model = model,
                                                        baseUrl = baseUrl,
                                                        fallbackProvider = fbProvider,
                                                        fallbackApiKey = fbKey,
                                                        fallbackModel = fbModel,
                                                        conciseMode = concise,
                                                        lazyExplanation = lazyExp,
                                                        cheapMode = cheap
                                                    )
                                                }
                                            },
                                            onSaveAudio = { rate, slowdown, spokenMenu, silentMode, testMode, spokenPrompts ->
                                                scope.launch {
                                                    app.preferencesRepository.setSpeechRate(rate)
                                                    app.preferencesRepository.setMathSlowdown(slowdown)
                                                    app.preferencesRepository.setSpokenMenu(spokenMenu)
                                                    app.preferencesRepository.setSilentTextMode(silentMode)
                                                    app.preferencesRepository.setTestModeEnabled(testMode)
                                                    app.preferencesRepository.setSpokenPrompts(spokenPrompts)
                                                }
                                            },
                                            onSaveGesture = { sens ->
                                                scope.launch {
                                                    app.preferencesRepository.setGestureSensitivity(sens)
                                                    app.deskTapSensor.setSensitivity(sens)
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    override fun onDestroy() {
        batteryReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: Exception) {}
        }
        super.onDestroy()
    }
}
