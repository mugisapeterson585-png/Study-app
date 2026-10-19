package com.silentstudy.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silentstudy.data.model.Answer
import com.silentstudy.gestures.DeskTapSensorManager
import com.silentstudy.ui.components.AppButton
import com.silentstudy.ui.components.AppCard
import com.silentstudy.ui.components.DeskKnockPad
import com.silentstudy.ui.theme.LocalAppColors
import kotlinx.coroutines.delay

@Composable
fun StudyScreen(
    answers: List<Answer>,
    isSessionActive: Boolean,
    sessionDurationSeconds: Long,
    answersHeardCount: Int,
    sensorManager: DeskTapSensorManager,
    sensitivity: Int,
    onSensitivityChange: (Int) -> Unit,
    onToggleSession: () -> Unit
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
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
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
                    Spacer(modifier = Modifier.height(16.dp))

                    val mins = sessionDurationSeconds / 60
                    val secs = sessionDurationSeconds % 60
                    Text(
                        text = String.format("%02d:%02d", mins, secs),
                        fontSize = 54.sp,
                        fontWeight = FontWeight.Light,
                        color = colors.textPrimary,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    Text(
                        text = "Phone resting flat · Earbuds audio & desk taps active",
                        fontSize = 11.sp,
                        color = colors.textSecondary,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )

                    Spacer(modifier = Modifier.height(20.dp))
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

                    Spacer(modifier = Modifier.height(20.dp))
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

                // Live Tap Waves Box right on Study Screen (Section 3 RESTORE)
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
                                fontSize = 12.sp,
                                color = colors.textSecondary
                            )
                        } else {
                            Text(
                                text = "Tap 'Reveal' or listen in earbuds. Answer with 1 tap (knew it) or 2 taps (missed).",
                                fontSize = 11.sp,
                                color = colors.textSecondary
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppButton(
                                onClick = {
                                    knewCount += 1
                                    isFlipped = false
                                    if (quizIndex < answers.size - 1) quizIndex += 1
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("1 Tap: Knew It", fontSize = 11.sp, color = colors.accent)
                            }
                            AppButton(
                                onClick = { isFlipped = !isFlipped },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (isFlipped) "Hide" else "Reveal", fontSize = 11.sp, color = colors.textPrimary)
                            }
                            AppButton(
                                onClick = {
                                    missedCount += 1
                                    isFlipped = false
                                    if (quizIndex < answers.size - 1) quizIndex += 1
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("2 Taps: Missed", fontSize = 11.sp, color = colors.textSecondary)
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("No answers saved yet to review.", fontSize = 12.sp, color = colors.textSecondary)
                    }
                }
            }

            "pomodoro" -> {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Library Pomodoro (25/5 Cycle)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.accent)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = String.format("%02d:%02d", pomoMinutes, pomoSeconds),
                        fontSize = 54.sp,
                        fontWeight = FontWeight.Light,
                        color = colors.textPrimary,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    Text(
                        text = "Gentle tone signals completion in earbuds",
                        fontSize = 11.sp,
                        color = colors.textSecondary,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )

                    Spacer(modifier = Modifier.height(20.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppButton(
                            onClick = { pomoRunning = !pomoRunning },
                            modifier = Modifier.weight(1f),
                            isAccent = !pomoRunning
                        ) {
                            Text(if (pomoRunning) "Pause" else "Start Focus", fontSize = 12.sp, color = colors.textPrimary)
                        }
                        AppButton(
                            onClick = {
                                pomoRunning = false
                                pomoMinutes = 25
                                pomoSeconds = 0
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Reset", fontSize = 12.sp, color = colors.textSecondary)
                        }
                    }
                }
            }
        }
    }
}
