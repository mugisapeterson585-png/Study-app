package com.silentstudy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silentstudy.SilentStudyApp
import com.silentstudy.data.repository.GestureSettings
import com.silentstudy.gestures.DeskGesture
import com.silentstudy.gestures.GestureAction
import com.silentstudy.ui.components.*
import com.silentstudy.ui.theme.LocalAppColors
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun TapControlsScreen(
    app: SilentStudyApp,
    gestureSettings: GestureSettings,
    onBack: () -> Unit
) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()

    var activeSubTab by remember { mutableStateOf("actions") } // actions, calibration, timing, practice
    var selectedActionForEdit by remember { mutableStateOf<GestureAction?>(null) }
    var isRecordingGestureFor by remember { mutableStateOf<GestureAction?>(null) }

    val lastGestureText by app.playbackController.lastGestureText.collectAsState()
    val calibrationState by app.deskTapSensor.calibrationState.collectAsState()

    // Timing state
    var tapWindow by remember { mutableStateOf(gestureSettings.tapWindowMs) }
    var minTapGap by remember { mutableStateOf(gestureSettings.minTapGapMs) }
    var ignoreTouches by remember { mutableStateOf(gestureSettings.ignorePhoneTouches) }

    // Practice mode state
    var practiceTargetGesture by remember { mutableStateOf(DeskGesture.DOUBLE_TAP) }
    var practiceFeedback by remember { mutableStateOf<String?>("Knock on desk to test: ${practiceTargetGesture.label}") }
    var practiceSuccess by remember { mutableStateOf<Boolean?>(null) }

    // Invert mapping for conflict detection: Gesture -> List of Actions
    val conflicts = remember(gestureSettings.customMappings) {
        val reverseMap = mutableMapOf<String, MutableList<String>>()
        gestureSettings.customMappings.forEach { (gestureStr, actionStr) ->
            val list = reverseMap.getOrPut(gestureStr) { mutableListOf() }
            list.add(actionStr)
        }
        reverseMap.filter { it.value.size > 1 }
    }

    // Listener when recording gesture
    LaunchedEffect(isRecordingGestureFor) {
        if (isRecordingGestureFor != null) {
            val targetAction = isRecordingGestureFor!!
            app.deskTapSensor.gestures.collectLatest { recordedGesture ->
                scope.launch {
                    app.preferencesRepository.saveGestureMapping(
                        recordedGesture.name,
                        targetAction.name
                    )
                    isRecordingGestureFor = null
                }
            }
        }
    }

    // Listener during practice mode
    LaunchedEffect(activeSubTab, practiceTargetGesture) {
        if (activeSubTab == "practice") {
            app.deskTapSensor.gestures.collectLatest { performed ->
                if (performed == practiceTargetGesture) {
                    practiceFeedback = "Correct! Recognized: ${performed.label}"
                    practiceSuccess = true
                } else {
                    practiceFeedback = "Recognized ${performed.label} instead. Try again for ${practiceTargetGesture.label}."
                    practiceSuccess = false
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Tap Controls & Gestures",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "Desk jerk tap detection & remappable actions",
                        fontSize = 11.sp,
                        color = colors.textSecondary
                    )
                }
                AppButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Back", fontSize = 11.sp, color = colors.textPrimary)
                }
            }
        }

        // Prominent Last Gesture feedback line
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(colors.surface)
                .border(1.dp, colors.accent.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp, vertical = 9.dp)
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
                    fontWeight = FontWeight.Bold,
                    color = colors.accent
                )
            }
        }

        // Sub-tabs
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                "actions" to "Actions",
                "calibration" to "Calibrate",
                "timing" to "Settings",
                "practice" to "Practice"
            ).forEach { (id, label) ->
                val isSel = activeSubTab == id
                AppButton(
                    onClick = { activeSubTab = id },
                    modifier = Modifier.weight(1f),
                    isAccent = isSel
                ) {
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        color = if (isSel) colors.accent else colors.textPrimary
                    )
                }
            }
        }

        when (activeSubTab) {
            "actions" -> {
                // Preset & Conflict Banner
                if (conflicts.isNotEmpty()) {
                    AppCard(modifier = Modifier.fillMaxWidth(), borderColor = colors.error) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = colors.error, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Conflict: ${conflicts.keys.joinToString()} mapped to multiple actions.",
                                fontSize = 11.sp,
                                color = colors.error
                            )
                        }
                    }
                }

                // Preset selector buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AppButton(
                        onClick = { scope.launch { app.preferencesRepository.applyPreset("default") } },
                        modifier = Modifier.weight(1f),
                        isAccent = gestureSettings.preset == "default"
                    ) {
                        Text("Default", fontSize = 10.sp, color = colors.textPrimary)
                    }
                    AppButton(
                        onClick = { scope.launch { app.preferencesRepository.applyPreset("minimal") } },
                        modifier = Modifier.weight(1f),
                        isAccent = gestureSettings.preset == "minimal"
                    ) {
                        Text("Minimal (4)", fontSize = 10.sp, color = colors.textPrimary)
                    }
                    AppButton(
                        onClick = { scope.launch { app.preferencesRepository.applyPreset("one_handed") } },
                        modifier = Modifier.weight(1f),
                        isAccent = gestureSettings.preset == "one_handed"
                    ) {
                        Text("One-Handed", fontSize = 10.sp, color = colors.textPrimary)
                    }
                }

                // Mapping list
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(GestureAction.values()) { action ->
                        // Find which gesture is mapped to this action
                        val mappedGestureStr = gestureSettings.customMappings.entries
                            .firstOrNull { it.value == action.name }?.key
                        val mappedGesture = mappedGestureStr?.let {
                            try { DeskGesture.valueOf(it) } catch (_: Exception) { null }
                        }

                        AppCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = action.displayName,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.textPrimary
                                    )
                                    Text(
                                        text = action.description,
                                        fontSize = 10.sp,
                                        color = colors.textSecondary
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    AppButton(
                                        onClick = { selectedActionForEdit = action },
                                        isAccent = mappedGesture != null
                                    ) {
                                        Text(
                                            text = mappedGesture?.label ?: "Unassigned",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (mappedGesture != null) colors.accent else colors.textSecondary
                                        )
                                    }

                                    AppButton(
                                        onClick = { isRecordingGestureFor = action }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Hearing,
                                            contentDescription = "Knock to assign",
                                            tint = if (isRecordingGestureFor == action) colors.accent else colors.textSecondary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            "calibration" -> {
                // 5-Tap Calibration Routine
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "5-Tap Desk Calibration",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.accent
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tap the desk 5 times, softly, the way you will use it. We record peak jerk values and tune your threshold.",
                        fontSize = 11.sp,
                        color = colors.textSecondary
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppButton(
                            onClick = {
                                if (calibrationState.inProgress) {
                                    app.deskTapSensor.cancelCalibration()
                                } else {
                                    app.deskTapSensor.start5TapCalibration()
                                }
                            },
                            isAccent = !calibrationState.inProgress
                        ) {
                            Icon(Icons.Default.Speed, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (calibrationState.inProgress) "Cancel (${calibrationState.tapCount}/5 recorded)" else "Start 5-Tap Calibration",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                        }

                        if (calibrationState.isComplete) {
                            Text(
                                text = "Tuned: ${String.format("%.2f", calibrationState.computedThreshold)} m/s²",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.accent
                            )
                        }
                    }

                    if (calibrationState.recordedPeaks.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Recorded peaks: " + calibrationState.recordedPeaks.joinToString(", ") { String.format("%.2f", it) },
                            fontSize = 10.sp,
                            color = colors.textSecondary,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Live Jerk Waves Box
                DeskKnockPad(
                    sensorManager = app.deskTapSensor,
                    sensitivity = gestureSettings.sensitivity,
                    onSensitivityChange = { sens ->
                        scope.launch {
                            app.preferencesRepository.setGestureSensitivity(sens)
                            app.deskTapSensor.setSensitivity(sens)
                        }
                    }
                )
            }

            "timing" -> {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Cadence & Rejection Settings",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.accent
                    )
                    Text(
                        text = "Fine-tune intervals between desk knocks to match your tapping cadence.",
                        fontSize = 11.sp,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                    )

                    AppSlider(
                        value = tapWindow.toFloat(),
                        onValueChange = {
                            tapWindow = it.toLong()
                            app.deskTapSensor.updateTimingParams(tapWindow, minTapGap)
                            scope.launch { app.preferencesRepository.saveGestureTiming(tapWindow, minTapGap) }
                        },
                        valueRange = 400f..1200f,
                        label = "Tap Window (Grouping Interval)",
                        valueDisplay = "${tapWindow}ms"
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    AppSlider(
                        value = minTapGap.toFloat(),
                        onValueChange = {
                            minTapGap = it.toLong()
                            app.deskTapSensor.updateTimingParams(tapWindow, minTapGap)
                            scope.launch { app.preferencesRepository.saveGestureTiming(tapWindow, minTapGap) }
                        },
                        valueRange = 50f..150f,
                        label = "Minimum Tap Gap (Debounce)",
                        valueDisplay = "${minTapGap}ms"
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Ignore phone touches toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Ignore touches on the phone itself",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "Ignores sensor spikes within 200ms of touching screen, so only desk taps count",
                                fontSize = 10.sp,
                                color = colors.textSecondary
                            )
                        }
                        AppToggle(
                            checked = ignoreTouches,
                            onCheckedChange = { chk ->
                                ignoreTouches = chk
                                app.deskTapSensor.ignorePhoneTouches = chk
                                scope.launch { app.preferencesRepository.setIgnorePhoneTouches(chk) }
                            }
                        )
                    }
                }
            }

            "practice" -> {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Interactive Practice Mode",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.accent
                    )
                    Text(
                        text = "Practice knocking on the desk to verify recognition of each gesture.",
                        fontSize = 11.sp,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(top = 2.dp, bottom = 14.dp)
                    )

                    Text("Target gesture to practice:", fontSize = 11.sp, color = colors.textSecondary)
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            DeskGesture.SINGLE_TAP,
                            DeskGesture.DOUBLE_TAP,
                            DeskGesture.TRIPLE_TAP,
                            DeskGesture.TAP_PAUSE_TAP
                        ).forEach { g ->
                            val isSel = practiceTargetGesture == g
                            AppButton(
                                onClick = {
                                    practiceTargetGesture = g
                                    practiceFeedback = "Knock on desk to test: ${g.label}"
                                    practiceSuccess = null
                                },
                                modifier = Modifier.weight(1f),
                                isAccent = isSel
                            ) {
                                Text(g.label, fontSize = 9.sp, color = if (isSel) colors.accent else colors.textPrimary)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.control)
                            .border(
                                1.dp,
                                when (practiceSuccess) {
                                    true -> colors.accent
                                    false -> colors.error
                                    else -> colors.border
                                },
                                RoundedCornerShape(10.dp)
                            )
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = practiceFeedback ?: "Ready",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = when (practiceSuccess) {
                                true -> colors.accent
                                false -> colors.error
                                else -> colors.textPrimary
                            }
                        )
                    }
                }
            }
        }

        // Dialog for picking gesture manually
        selectedActionForEdit?.let { action ->
            AlertDialog(
                onDismissRequest = { selectedActionForEdit = null },
                containerColor = colors.surface,
                titleContentColor = colors.textPrimary,
                textContentColor = colors.textSecondary,
                title = { Text("Assign Gesture to ${action.displayName}", fontSize = 14.sp) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        DeskGesture.values().forEach { g ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        scope.launch {
                                            app.preferencesRepository.saveGestureMapping(g.name, action.name)
                                            selectedActionForEdit = null
                                        }
                                    }
                                    .padding(vertical = 8.dp)
                            ) {
                                Text(g.label, fontSize = 12.sp, color = colors.textPrimary)
                            }
                        }
                    }
                },
                confirmButton = {
                    AppButton(onClick = { selectedActionForEdit = null }) {
                        Text("Close", fontSize = 11.sp, color = colors.accent)
                    }
                }
            )
        }
    }
}
