package com.silentstudy.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silentstudy.gestures.DeskGesture
import com.silentstudy.gestures.DeskTapSensorManager
import com.silentstudy.ui.theme.LocalAppColors

/**
 * Rebuilt Live Desk Tap Pad (200Hz Jerk Sensing).
 * Shows real-time jerk wave signal, dynamic adaptive threshold guideline,
 * tap detection ripple pulses, building pattern counter ("2 taps..."),
 * tap markers, and sensitivity slider with 5-tap calibration.
 */
@Composable
fun DeskKnockPad(
    sensorManager: DeskTapSensorManager,
    sensitivity: Int,
    onSensitivityChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    showQuickButtons: Boolean = true
) {
    val colors = LocalAppColors.current

    val waveform by sensorManager.liveWaveform.collectAsState()
    val patternLabel by sensorManager.currentPatternLabel.collectAsState()
    val lastRecognized by sensorManager.lastRecognized.collectAsState()
    val noiseFloor by sensorManager.noiseFloor.collectAsState()
    val currentThreshold by sensorManager.currentThresholdDisplay.collectAsState()
    val tapMarkers by sensorManager.tapMarkers.collectAsState()
    val calibrationState by sensorManager.calibrationState.collectAsState()

    var rippleOffset by remember { mutableStateOf<Offset?>(null) }

    // Tap pulse animation
    val pulseAnim = remember { Animatable(0f) }

    LaunchedEffect(lastRecognized) {
        if (lastRecognized != null) {
            pulseAnim.snapTo(1f)
            pulseAnim.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 350, easing = LinearEasing)
            )
        }
    }

    AppCard(modifier = modifier.fillMaxWidth()) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Hearing,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "DESK TAP SENSOR & LIVE WAVE (200Hz)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    fontFamily = FontFamily.Monospace
                )
            }

            if (lastRecognized != null && (System.currentTimeMillis() - lastRecognized!!.second) < 3500) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.accent.copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = lastRecognized!!.first.label,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.accent
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Small controlled taps on the desk next to the phone. Jerk threshold adapts dynamically to ambient noise floor.",
            fontSize = 11.sp,
            color = colors.textSecondary
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Live Waves Canvas Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(115.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(colors.control)
                .border(1.dp, colors.border, RoundedCornerShape(14.dp))
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = { offset ->
                            rippleOffset = offset
                            val released = tryAwaitRelease()
                            if (released) {
                                rippleOffset = null
                            }
                        },
                        onTap = { offset ->
                            rippleOffset = offset
                            sensorManager.triggerSoftTap()
                        }
                    )
                }
        ) {
            val thresholdRatio = (currentThreshold / 2.0f).coerceIn(0.12f, 0.88f)

            Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp)) {
                val width = size.width
                val height = size.height

                // 1. Draw horizontal threshold guideline (accent line)
                val thresholdY = height * (1f - thresholdRatio)
                drawLine(
                    color = Color(0xFF5B8DEF).copy(alpha = 0.55f),
                    start = Offset(0f, thresholdY),
                    end = Offset(width, thresholdY),
                    strokeWidth = 1.5f
                )

                // 2. Draw live waveform jerk line
                if (waveform.isNotEmpty()) {
                    val path = Path()
                    val stepX = width / (waveform.size - 1).coerceAtLeast(1)

                    waveform.forEachIndexed { index, mag ->
                        val x = index * stepX
                        val y = height - ((mag / 100f) * height * 0.90f).coerceIn(4f, height - 4f)
                        if (index == 0) {
                            path.moveTo(x, y)
                        } else {
                            path.lineTo(x, y)
                        }
                    }

                    drawPath(
                        path = path,
                        color = Color(0xFF5B8DEF),
                        style = Stroke(width = 2.5f)
                    )
                }

                // 3. Draw tap markers as vertical tick dots
                tapMarkers.takeLast(6).forEach { marker ->
                    val x = (marker.index.toFloat() / 39f).coerceIn(0f, 1f) * width
                    drawCircle(
                        color = Color(0xFF5B8DEF),
                        radius = 4.dp.toPx(),
                        center = Offset(x, height * 0.2f)
                    )
                }

                // 4. Draw touch ripple marker if active
                rippleOffset?.let { pos ->
                    drawCircle(
                        color = Color(0xFF5B8DEF).copy(alpha = 0.35f),
                        radius = 24.dp.toPx(),
                        center = pos
                    )
                }

                // 5. Pulse border effect on recognized tap
                if (pulseAnim.value > 0.05f) {
                    drawRect(
                        color = Color(0xFF5B8DEF).copy(alpha = pulseAnim.value * 0.25f),
                        size = size
                    )
                }
            }

            // Top overlay: Threshold & Noise Floor labels
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopStart)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "THRESHOLD ${String.format("%.2f", currentThreshold)} m/s²",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = colors.accent
                )
                Text(
                    text = "NOISE ${String.format("%.3f", noiseFloor)} m/s²",
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    color = colors.textSecondary
                )
            }

            // Center overlay: Pattern Building status
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (patternLabel.isNotEmpty()) {
                    Text(
                        text = patternLabel,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.accent
                    )
                } else {
                    Text(
                        text = "Tap desk next to phone or touch pad",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Sensitivity Control & Calibrate Button Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Sensitivity (Jerk)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "$sensitivity / 10",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.accent
                    )
                }
                AppSlider(
                    value = sensitivity.toFloat(),
                    onValueChange = { onSensitivityChange(it.toInt()) },
                    valueRange = 1f..10f,
                    label = "",
                    valueDisplay = ""
                )
            }

            // Calibrate Button
            AppButton(
                onClick = {
                    if (calibrationState.inProgress) {
                        sensorManager.cancelCalibration()
                    } else {
                        sensorManager.start5TapCalibration()
                    }
                },
                modifier = Modifier.padding(top = 8.dp)
            ) {
                if (calibrationState.inProgress) {
                    CircularProgressIndicator(
                        color = colors.accent,
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("${calibrationState.tapCount}/5", fontSize = 11.sp, color = colors.accent)
                } else {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("5-Tap Calibrate", fontSize = 11.sp, color = colors.textPrimary)
                }
            }
        }

        // Calibration status banner
        if (calibrationState.inProgress) {
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.accent.copy(alpha = 0.15f))
                    .padding(8.dp)
            ) {
                Text(
                    text = "Tap the desk 5 times, softly, the way you will use it (${calibrationState.tapCount}/5 recorded)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.accent
                )
            }
        } else if (calibrationState.isComplete) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Optimal threshold: ${String.format("%.2f", calibrationState.computedThreshold)} m/s² · Sensitivity: ${calibrationState.recommendedSensitivity}/10",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = colors.accent
            )
        }

        // Quick simulation buttons
        if (showQuickButtons) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    "1 Tap" to DeskGesture.SINGLE_TAP,
                    "2 Taps" to DeskGesture.DOUBLE_TAP,
                    "3 Taps" to DeskGesture.TRIPLE_TAP,
                    "Tap-Pause-Tap" to DeskGesture.TAP_PAUSE_TAP
                ).forEach { (label, gesture) ->
                    AppButton(
                        onClick = { sensorManager.simulateKnock(gesture) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = label,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary
                        )
                    }
                }
            }
        }
    }
}
