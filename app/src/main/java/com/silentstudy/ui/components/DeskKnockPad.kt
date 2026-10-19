package com.silentstudy.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import kotlinx.coroutines.launch

/**
 * Live Desk Knock Pad (Section 3 RESTORE)
 * Shows real-time accelerometer wave signal, horizontal threshold indicator,
 * tap detection ripple pulses, live building pattern counter ("2 taps..."),
 * sensitivity slider, desk calibration button, and direct touch testing.
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
    val scope = rememberCoroutineScope()

    val waveform by sensorManager.liveWaveform.collectAsState()
    val patternLabel by sensorManager.currentPatternLabel.collectAsState()
    val lastRecognized by sensorManager.lastRecognized.collectAsState()

    var isCalibrating by remember { mutableStateOf(false) }
    var calibrationMessage by remember { mutableStateOf<String?>(null) }
    var rippleOffset by remember { mutableStateOf<Offset?>(null) }
    var isLongPressing by remember { mutableStateOf(false) }

    // Tap pulse animation
    val pulseAnim = remember { Animatable(0f) }

    LaunchedEffect(lastRecognized) {
        if (lastRecognized != null) {
            pulseAnim.snapTo(1f)
            pulseAnim.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 400, easing = LinearEasing)
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
                    text = "LIVE DESK KNOCK PAD & SENSOR",
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
            text = "Phone flat on desk: knocks vibrate the table and register here. You can also tap this pad to test.",
            fontSize = 11.sp,
            color = colors.textSecondary
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Live Waves Canvas Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (isLongPressing) colors.accent.copy(alpha = 0.15f) else colors.control)
                .border(1.dp, colors.border, RoundedCornerShape(14.dp))
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = { offset ->
                            rippleOffset = offset
                            isLongPressing = false
                            val released = tryAwaitRelease()
                            if (released) {
                                isLongPressing = false
                                rippleOffset = null
                            }
                        },
                        onTap = { offset ->
                            rippleOffset = offset
                            sensorManager.triggerSoftTap()
                        },
                        onLongPress = { offset ->
                            isLongPressing = true
                            rippleOffset = offset
                            sensorManager.triggerLongKnock()
                        }
                    )
                }
        ) {
            val thresholdRatio = (sensorManager.threshold / 4.0f).coerceIn(0.1f, 0.9f)

            Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp)) {
                val width = size.width
                val height = size.height

                // 1. Draw horizontal threshold guideline
                val thresholdY = height * (1f - thresholdRatio)
                drawLine(
                    color = Color(0xFF5B8DEF).copy(alpha = 0.45f),
                    start = Offset(0f, thresholdY),
                    end = Offset(width, thresholdY),
                    strokeWidth = 1.5f
                )

                // 2. Draw live waveform line
                if (waveform.isNotEmpty()) {
                    val path = Path()
                    val stepX = width / (waveform.size - 1).coerceAtLeast(1)

                    waveform.forEachIndexed { index, mag ->
                        val x = index * stepX
                        // Normalize 0..100% to canvas height (inverted)
                        val y = height - ((mag / 100f) * height * 0.9f).coerceIn(4f, height - 4f)
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

                // 3. Draw touch ripple marker if active
                rippleOffset?.let { pos ->
                    drawCircle(
                        color = Color(0xFF5B8DEF).copy(alpha = 0.35f),
                        radius = 24.dp.toPx(),
                        center = pos
                    )
                }

                // 4. Pulse border effect on recognized tap
                if (pulseAnim.value > 0.05f) {
                    drawRect(
                        color = Color(0xFF5B8DEF).copy(alpha = pulseAnim.value * 0.25f),
                        size = size
                    )
                }
            }

            // Top overlay: Threshold label
            Text(
                text = "THRESHOLD ${(sensorManager.threshold * 10).toInt() / 10f} m/s²",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = colors.textSecondary.copy(alpha = 0.7f),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
            )

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
                        text = if (isLongPressing) "Sustained Long Knock..." else "Tap desk or touch pad",
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
                        text = "Sensitivity",
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
                    isCalibrating = true
                    calibrationMessage = null
                    sensorManager.calibrateDeskBaseline(
                        scope = scope,
                        onProgress = {},
                        onComplete = { recommended ->
                            isCalibrating = false
                            onSensitivityChange(recommended)
                            calibrationMessage = "Calibrated to $recommended/10"
                        }
                    )
                },
                modifier = Modifier.padding(top = 8.dp),
                enabled = !isCalibrating
            ) {
                if (isCalibrating) {
                    CircularProgressIndicator(
                        color = colors.accent,
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Calibrating...", fontSize = 11.sp, color = colors.textPrimary)
                } else {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Calibrate", fontSize = 11.sp, color = colors.textPrimary)
                }
            }
        }

        if (calibrationMessage != null) {
            Text(
                text = calibrationMessage!!,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = colors.accent,
                modifier = Modifier.padding(top = 2.dp)
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
                    "Long Knock" to DeskGesture.LONG_KNOCK
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
