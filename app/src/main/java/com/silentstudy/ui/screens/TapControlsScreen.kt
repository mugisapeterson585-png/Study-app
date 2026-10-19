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
import com.silentstudy.ui.components.AppButton
import com.silentstudy.ui.components.AppCard
import com.silentstudy.ui.components.AppSlider
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

    var activeSubTab by remember { mutableStateOf("actions") } // actions, timing, practice
    var selectedActionForEdit by remember { mutableStateOf<GestureAction?>(null) }
    var isRecordingGestureFor by remember { mutableStateOf<GestureAction?>(null) }

    // Timing state
    var tapWindow by remember { mutableStateOf(gestureSettings.tapWindowMs) }
    var longKnockDuration by remember { mutableStateOf(gestureSettings.longKnockDurationMs) }
    var minTapGap by remember { mutableStateOf(gestureSettings.minTapGapMs) }

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
                        text = "Customize physical desk knock mappings",
                        fontSize = 11.sp,
                        color = colors.textSecondary
                    )
                }
                AppButton(onClick = onBack) {
                    Text("Done", fontSize = 11.sp, color = colors.accent)
                }
            }
        }

        // Subtabs: Actions, Timing, Practice
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                "actions" to "Actions & Map",
                "timing" to "Timing",
                "practice" to "Practice Mode"
            ).forEach { (id, label) ->
                val isSel = activeSubTab == id
                AppButton(
                    onClick = { activeSubTab = id },
                    modifier = Modifier.weight(1f),
                    isAccent = isSel
                ) {
                    Text(label, fontSize = 11.sp, color = if (isSel) colors.accent else colors.textPrimary)
                }
            }
        }

        when (activeSubTab) {
            "actions" -> {
                // Presets & Gesture Help Audio Bar
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Presets & Audio Guide", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.accent)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                            Text("Minimal", fontSize = 10.sp, color = colors.textPrimary)
                        }
                        AppButton(
                            onClick = { scope.launch { app.preferencesRepository.applyPreset("one_handed") } },
                            modifier = Modifier.weight(1f),
                            isAccent = gestureSettings.preset == "one_handed"
                        ) {
                            Text("One-Hand", fontSize = 10.sp, color = colors.textPrimary)
                        }
                        AppButton(
                            onClick = { scope.launch { app.preferencesRepository.resetGestureDefaults() } },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Reset", fontSize = 10.sp, color = colors.textSecondary)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    AppButton(
                        onClick = {
                            val mapSummary = gestureSettings.customMappings.entries.joinToString(". ") {
                                "${it.key.replace('_', ' ')} triggers ${it.value.replace('_', ' ')}"
                            }
                            app.ttsManager.speak("Current gesture controls: $mapSummary", "gesture_help_audio")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.VolumeUp, contentDescription = null, tint = colors.accent, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Read Gesture Map Through Earbuds", fontSize = 11.sp, color = colors.textPrimary)
                    }
                }

                // Recording Modal / Banner
                if (isRecordingGestureFor != null) {
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "RECORDING GESTURE FOR: ${isRecordingGestureFor!!.displayName}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.accent
                                )
                                Text(
                                    text = "Knock your desired pattern on the desk now (taps, pauses, long knock)...",
                                    fontSize = 10.sp,
                                    color = colors.textSecondary
                                )
                            }
                            AppButton(onClick = { isRecordingGestureFor = null }) {
                                Text("Cancel", fontSize = 10.sp, color = colors.error)
                            }
                        }
                    }
                }

                // Actions List
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(GestureAction.values(), key = { it.name }) { action ->
                        // Find assigned gesture(s) for this action
                        val assignedGestures = gestureSettings.customMappings.filter { it.value == action.name }.keys

                        AppCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = action.displayName,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.textPrimary
                                    )
                                    Text(
                                        text = action.description,
                                        fontSize = 10.sp,
                                        color = colors.textSecondary
                                    )

                                    if (assignedGestures.isNotEmpty()) {
                                        Row(
                                            modifier = Modifier.padding(top = 4.dp),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            assignedGestures.forEach { gName ->
                                                val hasConflict = conflicts.containsKey(gName)
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(if (hasConflict) colors.error.copy(alpha = 0.2f) else colors.accent.copy(alpha = 0.2f))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = if (hasConflict) "$gName (Conflict!)" else gName.replace('_', ' '),
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (hasConflict) colors.error else colors.accent
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        Text(
                                            text = "Unassigned",
                                            fontSize = 9.sp,
                                            color = colors.textSecondary,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    AppButton(
                                        onClick = { isRecordingGestureFor = action },
                                        isAccent = true
                                    ) {
                                        Icon(Icons.Default.Mic, contentDescription = null, tint = colors.accent, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("Record", fontSize = 10.sp, color = colors.accent)
                                    }
                                    AppButton(
                                        onClick = { selectedActionForEdit = action }
                                    ) {
                                        Text("Pick", fontSize = 10.sp, color = colors.textPrimary)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            "timing" -> {
                // Timing Sliders
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Adjustable Sensor Timing",
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
                            app.deskTapSensor.updateTimingParams(tapWindow, longKnockDuration, minTapGap)
                            scope.launch { app.preferencesRepository.saveGestureTiming(tapWindow, longKnockDuration, minTapGap) }
                        },
                        valueRange = 400f..1200f,
                        label = "Tap Window (Grouping Interval)",
                        valueDisplay = "${tapWindow}ms"
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    AppSlider(
                        value = longKnockDuration.toFloat(),
                        onValueChange = {
                            longKnockDuration = it.toLong()
                            app.deskTapSensor.updateTimingParams(tapWindow, longKnockDuration, minTapGap)
                            scope.launch { app.preferencesRepository.saveGestureTiming(tapWindow, longKnockDuration, minTapGap) }
                        },
                        valueRange = 500f..1500f,
                        label = "Long Knock Duration",
                        valueDisplay = "${longKnockDuration}ms"
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    AppSlider(
                        value = minTapGap.toFloat(),
                        onValueChange = {
                            minTapGap = it.toLong()
                            app.deskTapSensor.updateTimingParams(tapWindow, longKnockDuration, minTapGap)
                            scope.launch { app.preferencesRepository.saveGestureTiming(tapWindow, longKnockDuration, minTapGap) }
                        },
                        valueRange = 40f..150f,
                        label = "Minimum Tap Gap (Debounce)",
                        valueDisplay = "${minTapGap}ms"
                    )
                }
            }

            "practice" -> {
                // Practice Mode
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Interactive Practice Mode",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.accent
                    )
                    Text(
                        text = "The app tells you what gesture to knock, then validates whether the desk sensor recognized it.",
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
                            DeskGesture.LONG_KNOCK
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
                                Text(g.label, fontSize = 10.sp, color = if (isSel) colors.accent else colors.textPrimary)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                when (practiceSuccess) {
                                    true -> colors.accent.copy(alpha = 0.2f)
                                    false -> colors.error.copy(alpha = 0.2f)
                                    else -> colors.control
                                }
                            )
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
        }

        // Dialog for picking gesture manually
        selectedActionForEdit?.let { action ->
            AlertDialog(
                onDismissRequest = { selectedActionForEdit = null },
                containerColor = colors.surface,
                titleColor = colors.textPrimary,
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
