package com.silentstudy.gestures

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

enum class DeskGesture(val label: String) {
    SINGLE_TAP("1 Tap"),
    DOUBLE_TAP("2 Taps"),
    TRIPLE_TAP("3 Taps"),
    TAP_PAUSE_TAP("Tap, Pause, Tap"),
    FOUR_TAPS("4 Taps"),
    FIVE_TAPS("5 Taps"),
    SIX_TAPS("6 Taps"),
    TAP_PAUSE_TAP_TAP("Tap, Pause, Tap, Tap")
}

enum class GestureAction(val displayName: String, val description: String) {
    PLAY_PAUSE("Play / Pause", "Toggle playback of current answer"),
    NEXT_ANSWER("Next Answer", "Advance to next saved answer"),
    PREV_ANSWER("Previous Answer", "Go back to previous answer"),
    REPEAT("Repeat Answer", "Replay from the very first part"),
    REPEAT_LAST_PART("Repeat Last Part", "Replay the specific part just spoken"),
    STOP_SILENT("Stop & Silent", "Immediately halt speech and clear queue"),
    JUMP_TO_N("Jump to Answer", "Enter answer number with consecutive taps"),
    CYCLE_SPEED("Cycle Speed", "Switch speech rate between normal, faster, and slower"),
    SHORT_SUMMARY("Short Summary", "Read the 1-2 sentence TL;DR direct answer"),
    EXPLAIN_SIMPLER("Explain Simpler", "Generate or read simple conceptual explanation"),
    SPEED_UP("Speed Up (+0.25x)", "Increase audio speech rate"),
    SPEED_DOWN("Speed Down (-0.25x)", "Decrease audio speech rate"),
    GESTURE_HELP("Gesture Help", "Read out current gesture map in earbuds")
}

data class TapMarker(
    val index: Int,
    val peakJerk: Float,
    val timestamp: Long
)

/**
 * Rebuilt High-Rate Jerk Desk Tap Sensor Manager (200Hz).
 * Registers accelerometer at 5000us (200Hz) and computes jerk = sqrt(dx^2 + dy^2 + dz^2).
 * Maintains rolling noise floor RMS, rejects sustained movement / rotation via Gyroscope,
 * and detects brief 60ms acoustic desk spikes.
 */
class DeskTapSensorManager(private val context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private val _gestures = MutableSharedFlow<DeskGesture>(extraBufferCapacity = 10)
    val gestures: SharedFlow<DeskGesture> = _gestures.asSharedFlow()

    // Real-time waveform stream (last 40 points, normalized 0f..100f)
    private val _liveWaveform = MutableStateFlow<List<Float>>(List(40) { 4f })
    val liveWaveform: StateFlow<List<Float>> = _liveWaveform.asStateFlow()

    // Visual tap markers inside the waveform box
    private val _tapMarkers = MutableStateFlow<List<TapMarker>>(emptyList())
    val tapMarkers: StateFlow<List<TapMarker>> = _tapMarkers.asStateFlow()

    // Building pattern progress counter
    private val _currentPatternLabel = MutableStateFlow("")
    val currentPatternLabel: StateFlow<String> = _currentPatternLabel.asStateFlow()

    // Last recognized gesture with timestamp
    private val _lastRecognized = MutableStateFlow<Pair<DeskGesture, Long>?>(null)
    val lastRecognized: StateFlow<Pair<DeskGesture, Long>?> = _lastRecognized.asStateFlow()

    // Live noise floor in m/s^2 jerk
    private val _noiseFloor = MutableStateFlow(0.04f)
    val noiseFloor: StateFlow<Float> = _noiseFloor.asStateFlow()

    // Current dynamic threshold line
    private val _currentThresholdDisplay = MutableStateFlow(0.40f)
    val currentThresholdDisplay: StateFlow<Float> = _currentThresholdDisplay.asStateFlow()

    // Auto-tune prompt trigger if user re-tries repeatedly
    private val _suggestAutoTune = MutableStateFlow(false)
    val suggestAutoTune: StateFlow<Boolean> = _suggestAutoTune.asStateFlow()

    // Interactive Calibration state
    data class CalibrationState(
        val inProgress: Boolean = false,
        val tapCount: Int = 0,
        val recordedPeaks: List<Float> = emptyList(),
        val recommendedSensitivity: Int = 6,
        val computedThreshold: Float = 0.5f,
        val isComplete: Boolean = false
    )
    private val _calibrationState = MutableStateFlow(CalibrationState())
    val calibrationState: StateFlow<CalibrationState> = _calibrationState.asStateFlow()

    var isListening = false
        private set

    // Base calibrated threshold (m/s^2 jerk). Level 10 = ~0.15, Level 1 = ~1.8
    var calibratedThreshold = 0.50f
        private set

    var hapticsEnabled = true
    var ignorePhoneTouches = false
    private var lastScreenTouchTime = 0L

    // Adjustable timing parameters
    var tapWindowMs: Long = 650L
    var minTapGapMs: Long = 80L

    // Sensor processing buffers
    private val prevAccel = FloatArray(3) { 0f }
    private var hasPrevAccel = false

    // Rolling noise floor window (2 seconds at 200Hz = ~400 samples)
    private val noiseBuffer = FloatArray(400) { 0.04f }
    private var noiseBufferIndex = 0
    private var noiseBufferFilled = 0

    // Spike detection state
    private var inSpike = false
    private var spikeStartTime = 0L
    private var spikePeakJerk = 0f
    private var lastTapTime = 0L

    // Shaking / motion rejection state
    private var highEnergyStartTime = 0L
    private var calmUntilTime = 0L
    private var lastGyroRotation = 0f

    private val tapTimestamps = mutableListOf<Long>()
    private var groupingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val recentAttempts = mutableListOf<Long>()

    fun startListening(sensitivity: Int = 6) {
        if (isListening) return
        setSensitivity(sensitivity)

        accelerometer?.let {
            // Register at 5000us (200Hz) high sampling rate
            sensorManager.registerListener(this, it, 5000)
        }
        gyroscope?.let {
            // Gyroscope to detect pick-up / rotations
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        isListening = true
    }

    fun setSensitivity(level: Int) {
        val clamped = level.coerceIn(1, 10)
        // Level 1 = 1.80 m/s^2, Level 10 = 0.15 m/s^2
        calibratedThreshold = 1.80f - (clamped - 1) * 0.183f
        _currentThresholdDisplay.value = max(calibratedThreshold, 4.0f * _noiseFloor.value)
    }

    fun setManualThreshold(thresholdVal: Float) {
        calibratedThreshold = thresholdVal.coerceIn(0.10f, 3.0f)
        _currentThresholdDisplay.value = max(calibratedThreshold, 4.0f * _noiseFloor.value)
    }

    fun updateTimingParams(windowMs: Long, gapMs: Long) {
        tapWindowMs = windowMs.coerceIn(400L, 1200L)
        minTapGapMs = gapMs.coerceIn(50L, 150L)
    }

    fun notifyScreenTouched() {
        lastScreenTouchTime = System.currentTimeMillis()
    }

    fun stopListening() {
        if (!isListening) return
        sensorManager.unregisterListener(this)
        groupingJob?.cancel()
        tapTimestamps.clear()
        _currentPatternLabel.value = ""
        isListening = false
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
            val gx = event.values[0]
            val gy = event.values[1]
            val gz = event.values[2]
            lastGyroRotation = sqrt(gx * gx + gy * gy + gz * gz)
            if (lastGyroRotation > 0.5f) {
                // Phone is being rotated or picked up: reject taps and require 300ms calm
                calmUntilTime = System.currentTimeMillis() + 300L
                inSpike = false
            }
            return
        }

        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val ax = event.values[0]
        val ay = event.values[1]
        val az = event.values[2]

        if (!hasPrevAccel) {
            prevAccel[0] = ax
            prevAccel[1] = ay
            prevAccel[2] = az
            hasPrevAccel = true
            return
        }

        val dx = ax - prevAccel[0]
        val dy = ay - prevAccel[1]
        val dz = az - prevAccel[2]
        prevAccel[0] = ax
        prevAccel[1] = ay
        prevAccel[2] = az

        // Jerk = instantaneous derivative magnitude across all axes
        val jerk = sqrt(dx * dx + dy * dy + dz * dz)
        val now = System.currentTimeMillis()

        // Screen touch rejection guard
        if (ignorePhoneTouches && (now - lastScreenTouchTime) < 200L) {
            return
        }

        // Check calm state after movement
        if (now < calmUntilTime) {
            inSpike = false
            return
        }

        val dynamicThreshold = max(calibratedThreshold, 4.0f * _noiseFloor.value)
        _currentThresholdDisplay.value = dynamicThreshold

        // Update noise floor only when signal is low and not during a spike
        if (jerk < dynamicThreshold * 0.8f && !inSpike) {
            noiseBuffer[noiseBufferIndex] = jerk
            noiseBufferIndex = (noiseBufferIndex + 1) % noiseBuffer.size
            if (noiseBufferFilled < noiseBuffer.size) noiseBufferFilled++

            // Rolling RMS computation over recent samples
            var sumSquares = 0.0
            val count = max(1, noiseBufferFilled)
            for (i in 0 until count) {
                sumSquares += noiseBuffer[i] * noiseBuffer[i]
            }
            val rms = max(0.015f, sqrt((sumSquares / count).toFloat()))
            _noiseFloor.value = rms
        }

        // Push normalized waveform sample (0..100)
        val normalized = min(100f, (jerk / 2.5f) * 100f)
        val currentList = _liveWaveform.value.toMutableList()
        if (currentList.size >= 40) currentList.removeAt(0)
        currentList.add(max(3f, normalized))
        _liveWaveform.value = currentList

        // Shake / sustained movement check (> 100ms high energy)
        if (jerk > dynamicThreshold) {
            if (highEnergyStartTime == 0L) {
                highEnergyStartTime = now
            } else if (now - highEnergyStartTime > 100L) {
                // High energy stayed longer than 100ms -> picking up/shaking phone
                calmUntilTime = now + 300L
                inSpike = false
                highEnergyStartTime = 0L
                return
            }
        } else {
            highEnergyStartTime = 0L
        }

        // Tap Spike Detection (settles back within ~60ms)
        if (jerk >= dynamicThreshold) {
            if (!inSpike) {
                inSpike = true
                spikeStartTime = now
                spikePeakJerk = jerk
            } else {
                if (jerk > spikePeakJerk) spikePeakJerk = jerk
                if (now - spikeStartTime > 70L) {
                    // Too long for a crisp desk tap spike
                    inSpike = false
                }
            }
        } else {
            if (inSpike) {
                val spikeDuration = now - spikeStartTime
                if (spikeDuration in 8L..65L && (now - lastTapTime) >= minTapGapMs) {
                    // Valid desk tap spike!
                    lastTapTime = now
                    recordTap(now, spikePeakJerk)
                }
                inSpike = false
            }
        }
    }

    private fun recordTap(now: Long, peak: Float) {
        vibrateTick()

        // If currently in calibration mode, record peak and increment
        if (_calibrationState.value.inProgress) {
            val state = _calibrationState.value
            val newPeaks = state.recordedPeaks + peak
            val newCount = state.tapCount + 1

            if (newCount >= 5) {
                val sorted = newPeaks.sorted()
                val medianPeak = sorted[sorted.size / 2]
                val computed = max(medianPeak * 0.50f, 3.0f * _noiseFloor.value)
                val recSensitivity = when {
                    computed < 0.35f -> 8
                    computed < 0.65f -> 6
                    computed < 1.00f -> 4
                    else -> 3
                }
                calibratedThreshold = computed
                _calibrationState.value = CalibrationState(
                    inProgress = false,
                    tapCount = 5,
                    recordedPeaks = newPeaks,
                    recommendedSensitivity = recSensitivity,
                    computedThreshold = computed,
                    isComplete = true
                )
                _currentPatternLabel.value = "Calibrated! Threshold: ${String.format("%.2f", computed)} m/s²"
            } else {
                _calibrationState.value = state.copy(tapCount = newCount, recordedPeaks = newPeaks)
                _currentPatternLabel.value = "Recorded tap $newCount of 5 (peak: ${String.format("%.2f", peak)})"
            }
            return
        }

        tapTimestamps.add(now)

        // Add visual tap marker
        val newMarkers = _tapMarkers.value.toMutableList()
        newMarkers.add(TapMarker(_liveWaveform.value.size - 1, peak, now))
        if (newMarkers.size > 8) newMarkers.removeAt(0)
        _tapMarkers.value = newMarkers

        val count = tapTimestamps.size
        _currentPatternLabel.value = if (count == 1) "1 tap..." else "$count taps..."

        groupingJob?.cancel()
        groupingJob = scope.launch {
            delay(tapWindowMs)
            evaluateTaps()
        }
    }

    private fun evaluateTaps() {
        val count = tapTimestamps.size
        if (count == 0) return

        val gesture = when {
            // Tap, Pause, Tap pattern (2 taps separated by > 280ms pause)
            count == 2 -> {
                val gap = tapTimestamps[1] - tapTimestamps[0]
                if (gap > 280L) DeskGesture.TAP_PAUSE_TAP else DeskGesture.DOUBLE_TAP
            }
            // Tap, Pause, Tap, Tap pattern (3 taps where first gap is > 260ms and second gap < 220ms)
            count == 3 -> {
                val gap1 = tapTimestamps[1] - tapTimestamps[0]
                val gap2 = tapTimestamps[2] - tapTimestamps[1]
                if (gap1 > 260L && gap2 < 240L) {
                    DeskGesture.TAP_PAUSE_TAP_TAP
                } else {
                    DeskGesture.TRIPLE_TAP
                }
            }
            count == 1 -> DeskGesture.SINGLE_TAP
            count == 4 -> DeskGesture.FOUR_TAPS
            count == 5 -> DeskGesture.FIVE_TAPS
            else -> DeskGesture.SIX_TAPS
        }

        emitGesture(gesture)
        _currentPatternLabel.value = "${gesture.label} recognized"
        tapTimestamps.clear()

        // Check if user is repeating gestures rapidly -> Offer auto-tune
        val now = System.currentTimeMillis()
        recentAttempts.add(now)
        recentAttempts.removeAll { now - it > 12_000 }
        if (recentAttempts.size >= 5) {
            _suggestAutoTune.value = true
        }
    }

    private fun emitGesture(gesture: DeskGesture) {
        _lastRecognized.value = Pair(gesture, System.currentTimeMillis())
        _gestures.tryEmit(gesture)
    }

    private fun vibrateTick() {
        if (!hapticsEnabled) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(20)
            }
        } catch (_: Exception) {}
    }

    /**
     * Soft Touch Pad Tap Trigger (allows user to test taps directly on the UI box)
     */
    fun triggerSoftTap() {
        val now = System.currentTimeMillis()
        val currentList = _liveWaveform.value.toMutableList()
        if (currentList.size >= 40) currentList.removeAt(0)
        currentList.add(85f)
        _liveWaveform.value = currentList
        recordTap(now, 1.2f)
    }

    fun simulateKnock(gesture: DeskGesture) {
        vibrateTick()
        emitGesture(gesture)
        _currentPatternLabel.value = "${gesture.label} simulated"
    }

    fun dismissAutoTune() {
        _suggestAutoTune.value = false
        recentAttempts.clear()
    }

    /**
     * Start the 5-tap desk calibration routine
     */
    fun start5TapCalibration() {
        _calibrationState.value = CalibrationState(
            inProgress = true,
            tapCount = 0,
            recordedPeaks = emptyList(),
            isComplete = false
        )
        _currentPatternLabel.value = "Tap the desk 5 times, softly, the way you will use it"
    }

    fun cancelCalibration() {
        _calibrationState.value = CalibrationState()
        _currentPatternLabel.value = ""
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
