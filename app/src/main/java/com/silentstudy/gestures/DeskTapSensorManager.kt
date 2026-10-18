package com.silentstudy.gestures

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Vibrator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlin.math.sqrt

enum class DeskGesture {
    SINGLE_TAP,
    DOUBLE_TAP,
    TRIPLE_TAP,
    TAP_PAUSE_TAP,
    LONG_KNOCK,
    FOUR_TAPS,
    FIVE_TAPS,
    SIX_TAPS
}

/**
 * Native SensorManager Desk Tap Detector (Section 8)
 * Detects knocks on a table surface while the phone lies flat.
 * High-pass filter removes gravity. Grouping window groups taps into patterns.
 * Runs in a Foreground Service with WakeLock when screen is off.
 */
class DeskTapSensorManager(private val context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    private val _gestures = MutableSharedFlow<DeskGesture>(extraBufferCapacity = 10)
    val gestures: SharedFlow<DeskGesture> = _gestures.asSharedFlow()

    private var isListening = false
    private var threshold = 1.8f
    private val gravity = FloatArray(3) { 0f }
    private val tapTimestamps = mutableListOf<Long>()
    private var groupingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    private var longKnockStartTime: Long? = null
    private var isLongKnockFired = false

    fun startListening(sensitivity: Int = 6) {
        if (isListening || accelerometer == null) return
        setSensitivity(sensitivity)
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        isListening = true
    }

    fun setSensitivity(level: Int) {
        val clamped = level.coerceIn(1, 10)
        // 1 -> 3.5 m/s^2, 10 -> 0.6 m/s^2
        threshold = 3.5f - (clamped - 1) * 0.32f
    }

    fun stopListening() {
        if (!isListening) return
        sensorManager.unregisterListener(this)
        groupingJob?.cancel()
        tapTimestamps.clear()
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

        // Long knock check (~800ms sustained vibration)
        if (magnitude > threshold * 0.85f) {
            if (longKnockStartTime == null) {
                longKnockStartTime = now
            } else if (now - longKnockStartTime!! > 750 && !isLongKnockFired) {
                isLongKnockFired = true
                vibrateTick()
                _gestures.tryEmit(DeskGesture.LONG_KNOCK)
            }
        } else {
            if (longKnockStartTime != null && (now - longKnockStartTime!!) < 750) {
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
        if (last != null && (now - last) < 70) return // Debounce < 70ms

        tapTimestamps.add(now)
        vibrateTick()

        groupingJob?.cancel()
        groupingJob = scope.launch {
            delay(600) // Pattern grouping window
            evaluateTaps()
        }
    }

    private fun evaluateTaps() {
        val count = tapTimestamps.size
        if (count == 0) return

        // Tap-pause-tap check
        if (count == 2) {
            val delta = tapTimestamps[1] - tapTimestamps[0]
            if (delta > 350) {
                _gestures.tryEmit(DeskGesture.TAP_PAUSE_TAP)
                tapTimestamps.clear()
                return
            }
        }

        when (count) {
            1 -> _gestures.tryEmit(DeskGesture.SINGLE_TAP)
            2 -> _gestures.tryEmit(DeskGesture.DOUBLE_TAP)
            3 -> _gestures.tryEmit(DeskGesture.TRIPLE_TAP)
            4 -> _gestures.tryEmit(DeskGesture.FOUR_TAPS)
            5 -> _gestures.tryEmit(DeskGesture.FIVE_TAPS)
            else -> if (count >= 6) _gestures.tryEmit(DeskGesture.SIX_TAPS)
        }
        tapTimestamps.clear()
    }

    private fun vibrateTick() {
        try {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(20)
        } catch (_: Exception) {}
    }

    fun simulateKnock(gesture: DeskGesture) {
        vibrateTick()
        _gestures.tryEmit(gesture)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
