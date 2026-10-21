package com.silentstudy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silentstudy.data.model.Answer
import com.silentstudy.gestures.DeskTapSensorManager
import com.silentstudy.ui.components.*
import com.silentstudy.ui.theme.LocalAppColors
import kotlinx.coroutines.delay

@Composable
fun StudyScreen(
    answers: List<Answer>,
    currentPlayingAnswer: Answer? = null,
    isPlaying: Boolean = false,
    currentPartIndex: Int = 0,
    lastGestureText: String = "Ready for desk taps",
    isSessionActive: Boolean,
    sessionDurationSeconds: Long,
    answersHeardCount: Int,
    sensorManager: DeskTapSensorManager,
    sensitivity: Int,
    onSensitivityChange: (Int) -> Unit,
    onToggleSession: () -> Unit,
    onPlayAnswer: (Answer) -> Unit = {},
    onPause: () -> Unit = {},
    onRepeatLastPart: () -> Unit = {},
    onStartTableReading: (Answer) -> Unit = {}
) {
    val colors = LocalAppColors.current
    var studyTab by remember { mutableStateOf("session") } // session, quiz, pomodoro

    val suggestAutoTune by sensorManager.suggestAutoTune.collectAsState()

    // Pomodoro timer state
    var pomoMinutes by remember { mutableStateOf(25) }
    var pomoSeconds by remember { mutableStateOf(0) }
    var pomoRunning by remember { mutableStateOf(false) }

    // Quiz mode state
    var quizIndex by remember { mutableStateOf(0) }
    var knewCount by remember { mutableStateOf(0) }
    var missedCount by remember { mutableStateOf(0) }
    var isFlipped by remember { mutableStateOf(false) }

    LaunchedEffect(pomoRunning) {
        while (pomoRunning) {
            delay(1000)
            if (pomoSeconds == 0) {
                if (pomoMinutes > 0) {
                    pomoMinutes -= 1
                    pomoSeconds = 59
                } else {
                    pomoRunning = false
                }
            } else {
                pomoSeconds -= 1
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Prominent "Last gesture" feedback line
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(colors.surface)
                .border(1.dp, colors.accent.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp, vertical = 9.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Hearing,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "LAST GESTURE:",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textSecondary,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = lastGestureText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.accent
                    )
                }
            }
        }

        // Auto-tune sensitivity prompt banner
        if (suggestAutoTune) {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-Tune Sensitivity?",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.accent
                        )
                        Text(
                            text = "Frequent tap re-tries detected. Adjust sensitivity for this desk surface?",
                            fontSize = 10.sp,
                            color = colors.textSecondary
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AppButton(
                            onClick = {
                                val newSens = (sensitivity + 1).coerceAtMost(10)
                                onSensitivityChange(newSens)
                                sensorManager.dismissAutoTune()
                            },
                            isAccent = true
                        ) {
                            Text("Adjust +1", fontSize = 10.sp, color = colors.accent)
                        }
                        AppButton(onClick = { sensorManager.dismissAutoTune() }) {
                            Text("Dismiss", fontSize = 10.sp, color = colors.textSecondary)
                        }
                    }
                }
            }
        }

        // Study Navigation Selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AppButton(
                onClick = { studyTab = "session" },
                isAccent = studyTab == "session",
                modifier = Modifier.weight(1f)
            ) {
                Text("Session", fontSize = 12.sp, color = if (studyTab == "session") colors.accent else colors.textPrimary)
            }
            AppButton(
                onClick = { studyTab = "quiz" },
                isAccent = studyTab == "quiz",
                modifier = Modifier.weight(1f)
            ) {
                Text("Quiz Mode", fontSize = 12.sp, color = if (studyTab == "quiz") colors.accent else colors.textPrimary)
            }
            AppButton(
                onClick = { studyTab = "pomodoro" },
                isAccent = studyTab == "pomodoro",
                modifier = Modifier.weight(1f)
            ) {
                Text("Pomodoro", fontSize = 12.sp, color = if (studyTab == "pomodoro") colors.accent else colors.textPrimary)
            }
        }

        when (studyTab) {
            "session" -> {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Library Study Session",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.accent
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    val mins = sessionDurationSeconds / 60
                    val secs = sessionDurationSeconds % 60
                    Text(
                        text = String.format("%02d:%02d", mins, secs),
                        fontSize = 50.sp,
                        fontWeight = FontWeight.Light,
                        color = colors.textPrimary,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    Text(
                        text = "Phone flat on desk · Screen off & earbuds active",
                        fontSize = 11.sp,
                        color = colors.textSecondary,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "$answersHeardCount", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                            Text(text = "Answers Heard", fontSize = 11.sp, color = colors.textSecondary)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "${answers.size}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                            Text(text = "Total in Library", fontSize = 11.sp, color = colors.textSecondary)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    AppButton(
                        onClick = onToggleSession,
                        modifier = Modifier.fillMaxWidth(),
                        isAccent = !isSessionActive
                    ) {
                        Icon(
                            imageVector = if (isSessionActive) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isSessionActive) "Pause Session" else "Start Study Session",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                    }
                }

                // Currently Playing Answer Block / Parts
                if (currentPlayingAnswer != null) {
                    val ans = currentPlayingAnswer
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "ACTIVE PLAYBACK #${ans.id} · ${ans.subject}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.accent,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = ans.shortAnswer,
                                    fontSize = 12.sp,
                                    color = colors.textPrimary,
                                    maxLines = 2
                                )
                            }
                            IconButton(onClick = { if (isPlaying) onPause() else onPlayAnswer(ans) }) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = colors.accent
                                )
                            }
                        }

                        // Answer Parts sequence display
                        val parts = ans.answerParts.ifEmpty {
                            ans.blocks.map { com.silentstudy.data.model.AnswerPart(display = it.display, spoken = it.spoken) }
                        }
                        if (parts.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                parts.forEachIndexed { idx, part ->
                                    val isCurrent = idx == currentPartIndex
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isCurrent) colors.accent.copy(alpha = 0.20f) else colors.control)
                                            .border(
                                                1.dp,
                                                if (isCurrent) colors.accent else colors.border,
                                                RoundedCornerShape(6.dp)
                                            )
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (part.label.isNotBlank()) {
                                                    Text(
                                                        text = part.label,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isCurrent) colors.accent else colors.textSecondary
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                }
                                                Text(
                                                    text = part.display,
                                                    fontSize = 11.sp,
                                                    color = colors.textPrimary,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }
                                            if (isCurrent && isPlaying) {
                                                Text("Speaking", fontSize = 9.sp, color = colors.accent, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            AppButton(
                                onClick = onRepeatLastPart,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Repeat Last Part Only", fontSize = 11.sp, color = colors.textSecondary)
                            }
                        }

                        // Table display if present
                        if (ans.table != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            TableView(
                                table = ans.table,
                                onReadTable = { onStartTableReading(ans) }
                            )
                        }

                        // Graph display if present
                        if (ans.graph != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            GraphChartView(graph = ans.graph)
                        }
                    }
                }

                // Live Jerk Waves Box right on Study Screen
                DeskKnockPad(
                    sensorManager = sensorManager,
                    sensitivity = sensitivity,
                    onSensitivityChange = onSensitivityChange
                )
            }

            "quiz" -> {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Audio Flashcard Quiz", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.accent)
                        Text("Score: $knewCount / ${knewCount + missedCount}", fontSize = 12.sp, color = colors.textSecondary)
                    }

                    val currentQuizAnswer = answers.getOrNull(quizIndex)
                    if (currentQuizAnswer != null) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "#${currentQuizAnswer.id} ${currentQuizAnswer.subject}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.accent
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = currentQuizAnswer.questionText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        if (isFlipped) {
                            Text(
                                text = currentQuizAnswer.shortAnswer,
                                fontSize = 13.sp,
                                color = colors.textPrimary
                            )
                        } else {
                            AppButton(
                                onClick = { isFlipped = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Reveal Answer", fontSize = 12.sp, color = colors.accent)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            AppButton(
                                onClick = {
                                    knewCount++
                                    isFlipped = false
                                    quizIndex = (quizIndex + 1) % answers.size
                                },
                                isAccent = true,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Knew It", fontSize = 12.sp, color = colors.accent)
                            }
                            AppButton(
                                onClick = {
                                    missedCount++
                                    isFlipped = false
                                    quizIndex = (quizIndex + 1) % answers.size
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Missed", fontSize = 12.sp, color = colors.error)
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text("No answers saved yet. Capture questions to practice.", fontSize = 12.sp, color = colors.textSecondary)
                    }
                }
            }

            "pomodoro" -> {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Silent Pomodoro Focus", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.accent)
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = String.format("%02d:%02d", pomoMinutes, pomoSeconds),
                        fontSize = 52.sp,
                        fontWeight = FontWeight.Light,
                        color = colors.textPrimary,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    AppButton(
                        onClick = { pomoRunning = !pomoRunning },
                        modifier = Modifier.fillMaxWidth(),
                        isAccent = !pomoRunning
                    ) {
                        Text(if (pomoRunning) "Pause Focus" else "Start 25-Min Focus", fontSize = 12.sp, color = colors.textPrimary)
                    }
                }
            }
        }
    }
}
