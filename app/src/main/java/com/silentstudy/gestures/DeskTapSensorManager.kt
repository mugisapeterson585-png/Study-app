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
    LONG_KNOCK("Long Knock"),
    FOUR_TAPS("4 Taps"),
    FIVE_TAPS("5 Taps"),
    SIX_TAPS("6 Taps")
}

enum class GestureAction(val displayName: String, val description: String) {
    PLAY_PAUSE("Play / Pause", "Toggle playback of current answer block"),
    NEXT_ANSWER("Next Answer", "Advance to next saved answer"),
    PREV_ANSWER("Previous Answer", "Go back to previous answer"),
    REPEAT("Repeat Answer", "Replay from the very first block"),
    STOP_SILENT("Stop & Silent", "Immediately halt speech and clear queue"),
    SHORT_SUMMARY("Short Summary", "Read the 1-2 sentence TL;DR direct answer"),
    EXPLAIN_SIMPLER("Explain Simpler", "Generate or read simple conceptual explanation"),
    JUMP_TO_N("Jump Ahead (+5)", "Skip ahead in the answer library"),
    SPEED_UP("Speed Up (+0.25x)", "Increase audio speech rate"),
    SPEED_DOWN("Speed Down (-0.25x)", "Decrease audio speech rate"),
    SWITCH_MODE("Switch Short/Detailed", "Toggle concise summary mode"),
    TOGGLE_TORCH("Toggle Flashlight", "Switch camera flashlight on or off"),
    START_END_SESSION("Start/End Session", "Toggle active library study session"),
    QUIZ_KNEW("Quiz: Knew It", "Mark current flashcard as remembered"),
    QUIZ_MISSED("Quiz: Missed", "Mark current flashcard as missed"),
    GESTURE_HELP("Gesture Help", "Read out current gesture map in earbuds")
}

/**
 * Native SensorManager Desk Tap Detector (Section 8 & Section 3 Live Waves)
 * Detects knocks on a table surface while the phone lies flat.
 * High-pass filter removes gravity. Grouping window groups taps into patterns.
 * Emits real-time waveform magnitudes for the live visual Desk Knock Pad.
 */
class DeskTapSensorManager(private val context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

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

    // Real-time waveform stream (last 30 magnitude points 0f..100f)
    private val _liveWaveform = MutableStateFlow<List<Float>>(List(30) { 5f })
    val liveWaveform: StateFlow<List<Float>> = _liveWaveform.asStateFlow()

    // Building pattern progress counter (e.g. "1 tap...", "2 taps...")
    private val _currentPatternLabel = MutableStateFlow("")
    val currentPatternLabel: StateFlow<String> = _currentPatternLabel.asStateFlow()

    // Last recognized gesture with timestamp
    private val _lastRecognized = MutableStateFlow<Pair<DeskGesture, Long>?>(null)
    val lastRecognized: StateFlow<Pair<DeskGesture, Long>?> = _lastRecognized.asStateFlow()

    // Auto-tune prompt trigger if user re-tries repeatedly
    private val _suggestAutoTune = MutableStateFlow(false)
    val suggestAutoTune: StateFlow<Boolean> = _suggestAutoTune.asStateFlow()

    var isListening = false
        private set

    var threshold = 1.8f
        private set

    var hapticsEnabled = true

    // Adjustable timing parameters
    var tapWindowMs: Long = 600L
    var longKnockDurationMs: Long = 750L
    var minTapGapMs: Long = 70L

    private val gravity = FloatArray(3) { 0f }
    private val tapTimestamps = mutableListOf<Long>()
    private var groupingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    private var longKnockStartTime: Long? = null
    private var isLongKnockFired = false
    private val recentAttempts = mutableListOf<Long>()

    fun startListening(sensitivity: Int = 6) {
        if (isListening || accelerometer == null) return
        setSensitivity(sensitivity)
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        isListening = true
    }

    fun setSensitivity(level: Int) {
        val clamped = level.coerceIn(1, 10)
        // Level 1 = 3.5 m/s^2 (less sensitive), Level 10 = 0.6 m/s^2 (most sensitive)
        threshold = 3.5f - (clamped - 1) * 0.32f
    }

    fun updateTimingParams(windowMs: Long, longKnockMs: Long, gapMs: Long) {
        tapWindowMs = windowMs.coerceIn(400L, 1200L)
        longKnockDurationMs = longKnockMs.coerceIn(500L, 1500L)
        minTapGapMs = gapMs.coerceIn(40L, 150L)
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
        if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        // High-pass filter to remove 1G gravity and slow tilt
        val alpha = 0.8f
        gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0]
        gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1]
        gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2]

        val linearX = event.values[0] - gravity[0]
        val linearY = event.values[1] - gravity[1]
        val linearZ = event.values[2] - gravity[2]

        val magnitude = sqrt(linearX * linearX + linearY * linearY + linearZ * linearZ)
        val now = System.currentTimeMillis()

        // Push normalized magnitude (0..100) to live waveform stream
        val normalized = min(100f, (magnitude / 5f) * 100f)
        val currentList = _liveWaveform.value.toMutableList()
        if (currentList.size >= 30) currentList.removeAt(0)
        currentList.add(max(6f, normalized))
        _liveWaveform.value = currentList

        // Long knock check (~750ms sustained vibration)
        if (magnitude > threshold * 0.85f) {
            if (longKnockStartTime == null) {
                longKnockStartTime = now
            } else if (now - longKnockStartTime!! > longKnockDurationMs && !isLongKnockFired) {
                isLongKnockFired = true
                vibrateTick()
                emitGesture(DeskGesture.LONG_KNOCK)
                _currentPatternLabel.value = "Long knock recognized!"
            }
        } else {
            if (longKnockStartTime != null && (now - longKnockStartTime!!) < longKnockDurationMs) {
                if (magnitude > threshold) {
                    recordTap(now)
                }
            }
            longKnockStartTime = null
            isLongKnockFired = false
        }
    }

    private fun recordTap(now: Long) {
        val last = tapTimestamps.lastOrNull()
        if (last != null && (now - last) < minTapGapMs) return // Debounce minimum gap

        tapTimestamps.add(now)
        vibrateTick()

        val count = tapTimestamps.size
        _currentPatternLabel.value = if (count == 1) "1 tap..." else "$count taps..."

        groupingJob?.cancel()
        groupingJob = scope.launch {
            delay(tapWindowMs) // Pattern grouping window
            evaluateTaps()
        }
    }

    private fun evaluateTaps() {
        val count = tapTimestamps.size
        if (count == 0) return

        // Tap-pause-tap check (2 taps separated by > 320ms gap)
        if (count == 2) {
            val delta = tapTimestamps[1] - tapTimestamps[0]
            if (delta > 320) {
                emitGesture(DeskGesture.TAP_PAUSE_TAP)
                _currentPatternLabel.value = "Tap, pause, tap recognized"
                tapTimestamps.clear()
                return
            }
        }

        val gesture = when (count) {
            1 -> DeskGesture.SINGLE_TAP
            2 -> DeskGesture.DOUBLE_TAP
            3 -> DeskGesture.TRIPLE_TAP
            4 -> DeskGesture.FOUR_TAPS
            5 -> DeskGesture.FIVE_TAPS
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
                vibrator?.vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(25)
            }
        } catch (_: Exception) {}
    }

    /**
     * Soft Touch Pad Tap Trigger (allows user to test taps directly on the UI box)
     */
    fun triggerSoftTap() {
        val now = System.currentTimeMillis()
        // Inject spike into waveform
        val currentList = _liveWaveform.value.toMutableList()
        if (currentList.size >= 30) currentList.removeAt(0)
        currentList.add(85f)
        _liveWaveform.value = currentList

        recordTap(now)
    }

    /**
     * Long Knock Pad Trigger
     */
    fun triggerLongKnock() {
        vibrateTick()
        val currentList = _liveWaveform.value.toMutableList()
        if (currentList.size >= 30) currentList.removeAt(0)
        currentList.add(95f)
        _liveWaveform.value = currentList
        emitGesture(DeskGesture.LONG_KNOCK)
        _currentPatternLabel.value = "Long knock recognized"
    }

    /**
     * Direct gesture simulation for test buttons
     */
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
     * Desk Calibration Routine:
     * Measures ambient desk noise floor for 1.5 seconds, then recommends optimal sensitivity.
     */
    fun calibrateDeskBaseline(
        scope: CoroutineScope,
        onProgress: (Float) -> Unit,
        onComplete: (recommendedSensitivity: Int) -> Unit
    ) {
        scope.launch {
            val measurements = mutableListOf<Float>()
            val steps = 15
            for (i in 0 until steps) {
                delay(100)
                val currentMax = _liveWaveform.value.takeLast(5).maxOrNull() ?: 10f
                measurements.add(currentMax)
                onProgress((i + 1).toFloat() / steps.toFloat())
            }

            val avgNoise = measurements.average().toFloat()
            // Ambient noise ~ 10-20 -> Sensitivity 7
            // Quiet desk ~ 5-10 -> Sensitivity 6
            // Noisy / vibrating table ~ 25+ -> Sensitivity 4
            val recommended = when {
                avgNoise < 12f -> 7
                avgNoise < 22f -> 6
                avgNoise < 35f -> 4
                else -> 3
            }
            setSensitivity(recommended)
            onComplete(recommended)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
