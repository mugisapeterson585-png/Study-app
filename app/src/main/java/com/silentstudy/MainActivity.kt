package com.silentstudy

import android.Manifest
import android.content.pm.PackageManager
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
import com.silentstudy.data.model.Answer
import com.silentstudy.data.model.AnswerBlock
import com.silentstudy.gestures.DeskGesture
import com.silentstudy.service.SilentStudyService
import com.silentstudy.ui.components.AfterAnswerMenu
import com.silentstudy.ui.components.StatusPill
import com.silentstudy.ui.screens.*
import com.silentstudy.ui.theme.LocalNeuColors
import com.silentstudy.ui.theme.SilentStudyTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Permissions handled
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
            val audioSettings by app.preferencesRepository.audioSettings.collectAsState(
                initial = com.silentstudy.data.repository.AudioSettings()
            )
            val gestureSettings by app.preferencesRepository.gestureSettings.collectAsState(
                initial = com.silentstudy.data.repository.GestureSettings()
            )

            val answers by app.database.answerDao().getAllAnswers().collectAsState(initial = emptyList())
            val isEarbudsConnected by app.earbudsGuard.isEarbudsConnected.collectAsState()
            val earbudDeviceName by app.earbudsGuard.connectedDeviceName.collectAsState()

            val scope = rememberCoroutineScope()

            var activeTab by remember { mutableStateOf("capture") } // capture, answers, study, settings
            var isDeskScreenOffOpen by remember { mutableStateOf(false) }

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

            // Timer for active study session
            LaunchedEffect(isSessionActive) {
                if (isSessionActive) {
                    SilentStudyService.startService(this@MainActivity)
                    while (isSessionActive) {
                        delay(1000)
                        sessionDurationSeconds += 1
                    }
                } else {
                    SilentStudyService.stopService(this@MainActivity)
                }
            }

            // Function to play sequential block
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

            // Utterance completion listener for block progression
            DisposableEffect(Unit) {
                app.ttsManager.onUtteranceFinished = { utteranceId ->
                    if (utteranceId.startsWith("block_")) {
                        scope.launch {
                            delay(400) // Pause between blocks / equations
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

            // Global Desk Gesture Handler
            LaunchedEffect(Unit) {
                app.deskTapSensor.gestures.collectLatest { gesture ->
                    when (gesture) {
                        DeskGesture.SINGLE_TAP -> {
                            if (showAfterAnswerMenu && currentPlayingAnswer != null) {
                                // 1 tap in after-menu = Repeat
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
                        DeskGesture.DOUBLE_TAP -> {
                            if (showAfterAnswerMenu) showAfterAnswerMenu = false
                            // Next answer
                            if (answers.isNotEmpty()) {
                                val currentIdx = answers.indexOfFirst { it.id == currentPlayingAnswer?.id }
                                val nextIdx = if (currentIdx != -1) (currentIdx + 1) % answers.size else 0
                                currentPlayingAnswer = answers[nextIdx]
                                currentBlockIndex = 0
                                isPlaying = true
                                playCurrentBlock(answers[nextIdx], 0)
                            }
                        }
                        DeskGesture.TRIPLE_TAP -> {
                            if (showAfterAnswerMenu) {
                                // 3 taps in after-menu = explain simpler
                                showAfterAnswerMenu = false
                                currentPlayingAnswer?.let { ans ->
                                    app.ttsManager.speak(ans.simpleExplanation, "simple_explanation")
                                }
                            } else if (answers.isNotEmpty()) {
                                val currentIdx = answers.indexOfFirst { it.id == currentPlayingAnswer?.id }
                                val prevIdx = if (currentIdx > 0) currentIdx - 1 else answers.size - 1
                                currentPlayingAnswer = answers[prevIdx]
                                currentBlockIndex = 0
                                isPlaying = true
                                playCurrentBlock(answers[prevIdx], 0)
                            }
                        }
                        DeskGesture.TAP_PAUSE_TAP -> {
                            // Repeat from beginning
                            currentPlayingAnswer?.let { ans ->
                                currentBlockIndex = 0
                                isPlaying = true
                                playCurrentBlock(ans, 0)
                            }
                        }
                        DeskGesture.LONG_KNOCK -> {
                            // Short summary
                            currentPlayingAnswer?.let { ans ->
                                app.ttsManager.speak(ans.shortAnswer, "short_summary")
                            }
                        }
                        DeskGesture.FOUR_TAPS -> {
                            // Stop & silent / Wake screen
                            app.ttsManager.stop()
                            isPlaying = false
                            showAfterAnswerMenu = false
                            isDeskScreenOffOpen = false
                        }
                        else -> {}
                    }
                }
            }

            SilentStudyTheme(
                isDarkMode = themeSettings.isDarkMode,
                isUltraDimMode = themeSettings.isUltraDimMode
            ) {
                val neuColors = LocalNeuColors.current

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = neuColors.background
                ) {
                    if (isDeskScreenOffOpen) {
                        DeskScreenOffScreen(
                            currentAnswerSummary = currentPlayingAnswer?.let { "#${it.id} ${it.subject}" },
                            onWake = { isDeskScreenOffOpen = false }
                        )
                    } else {
                        Scaffold(
                            containerColor = neuColors.background,
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
                                    containerColor = neuColors.surface,
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
                                                    tint = if (isSel) neuColors.accent else neuColors.textSecondary
                                                )
                                            },
                                            label = {
                                                Text(
                                                    text = label,
                                                    fontSize = 10.sp,
                                                    color = if (isSel) neuColors.accent else neuColors.textSecondary
                                                )
                                            },
                                            colors = NavigationBarItemDefaults.colors(
                                                indicatorColor = neuColors.darkShadow.copy(alpha = 0.5f)
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
                                            onAnswerGenerated = { newAns ->
                                                scope.launch {
                                                    val id = app.database.answerDao().insertAnswer(newAns)
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
                                            onToggleSession = { isSessionActive = !isSessionActive }
                                        )
                                    }
                                    "settings" -> {
                                        SettingsScreen(
                                            themeSettings = themeSettings,
                                            aiSettings = aiSettings,
                                            audioSettings = audioSettings,
                                            gestureSettings = gestureSettings,
                                            ttsManager = app.ttsManager,
                                            aiManager = app.aiProviderManager,
                                            onSaveTheme = { isDark, isUltraDim ->
                                                scope.launch {
                                                    app.preferencesRepository.setDarkMode(isDark)
                                                    app.preferencesRepository.setUltraDimMode(isUltraDim)
                                                }
                                            },
                                            onSaveAi = { provider, key, model, baseUrl ->
                                                scope.launch {
                                                    app.preferencesRepository.saveAiConfig(provider, key, model, baseUrl)
                                                }
                                            },
                                            onSaveAudio = { rate, slowdown, spokenMenu, silentMode ->
                                                scope.launch {
                                                    app.preferencesRepository.setSpeechRate(rate)
                                                    app.preferencesRepository.setMathSlowdown(slowdown)
                                                    app.preferencesRepository.setSpokenMenu(spokenMenu)
                                                    app.preferencesRepository.setSilentTextMode(silentMode)
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
        super.onDestroy()
    }
}
