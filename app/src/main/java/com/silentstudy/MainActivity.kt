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

    override fun dispatchTouchEvent(ev: android.view.MotionEvent?): Boolean {
        (application as? SilentStudyApp)?.deskTapSensor?.notifyScreenTouched()
        return super.dispatchTouchEvent(ev)
    }

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

            // Authoritative audio playback and gesture state from PlaybackController singleton
            val currentPlayingAnswer by app.playbackController.currentPlayingAnswer.collectAsState()
            val currentPartIndex by app.playbackController.currentPartIndex.collectAsState()
            val isPlaying by app.playbackController.isPlaying.collectAsState()
            val showAfterAnswerMenu by app.playbackController.showAfterAnswerMenu.collectAsState()
            val afterMenuSeconds by app.playbackController.afterMenuSeconds.collectAsState()
            val answersHeardCount by app.playbackController.answersHeardCount.collectAsState()
            val lastGestureText by app.playbackController.lastGestureText.collectAsState()

            // Study session stats
            var isSessionActive by remember { mutableStateOf(false) }
            var sessionDurationSeconds by remember { mutableStateOf(0L) }

            // First run check
            LaunchedEffect(firstRunCompleted) {
                if (!firstRunCompleted) {
                    showFirstRunWizard = true
                }
            }

            // Restore interrupted playback on launch
            LaunchedEffect(Unit) {
                app.playbackController.restoreInterruptedPlayback()
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
                                                    app.playbackController.playAnswer(saved, 0)
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
                                                        onRepeat = { app.playbackController.repeatCurrent() },
                                                        onNext = { app.playbackController.nextAnswer() },
                                                        onSimpler = { app.playbackController.speakSimpleExplanation() }
                                                    )
                                                }
                                            }

                                            AnswersScreen(
                                                answers = answers,
                                                onPlayAnswer = { ans ->
                                                    app.playbackController.playAnswer(ans, 0)
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
                                                                answerStyle = aiSettings.answerStyle,
                                                                conciseMode = aiSettings.conciseMode,
                                                                lazyExplanation = aiSettings.lazyExplanation
                                                            )
                                                        )
                                                        val retried = failedAns.copy(
                                                            subject = res.subject,
                                                            questionText = res.questionText,
                                                            shortAnswer = res.shortAnswer,
                                                            blocks = res.blocks,
                                                            answerParts = res.answerParts,
                                                            table = res.table,
                                                            graph = res.graph,
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
                                                        app.ttsManager.speak(exp, "simple_explanation")
                                                    }
                                                }
                                            )
                                        }
                                    }
                                    "study" -> {
                                        StudyScreen(
                                            answers = answers,
                                            currentPlayingAnswer = currentPlayingAnswer,
                                            isPlaying = isPlaying,
                                            currentPartIndex = currentPartIndex,
                                            lastGestureText = lastGestureText,
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
                                            onToggleSession = { isSessionActive = !isSessionActive },
                                            onPlayAnswer = { ans -> app.playbackController.playAnswer(ans, 0) },
                                            onPause = {
                                                if (isPlaying) app.playbackController.pause() else app.playbackController.resume()
                                            },
                                            onRepeatLastPart = { app.playbackController.repeatLastPart() },
                                            onStartTableReading = { ans -> app.playbackController.readTableAnswer(ans) }
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
                                            onSaveAi = { provider, key, model, baseUrl, fbProvider, fbKey, fbModel, concise, lazyExp, cheap, style ->
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
                                                        cheapMode = cheap,
                                                        answerStyle = style
                                                    )
                                                }
                                            },
                                            onSaveAudio = { rate, slowdown, spokenMenu, silentMode, testMode, spokenPrompts ->
                                                scope.launch {
                                                    app.preferencesRepository.setSpeechRate(rate)
                                                    app.preferencesRepository.setMathSlowdown(slowdown)
                                                    app.preferencesRepository.setSpokenMenuEnabled(spokenMenu)
                                                    app.preferencesRepository.setSilentTextMode(silentMode)
                                                    app.preferencesRepository.setTestModeEnabled(testMode)
                                                    app.preferencesRepository.setSpokenPromptsEnabled(spokenPrompts)
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
