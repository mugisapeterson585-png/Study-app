package com.silentstudy.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Real hardware Earbuds Audio Guard (FIX 1 & Section 5 Speech Test Mode)
 * Uses real AudioManager.getDevices(GET_DEVICES_OUTPUTS) and an AudioDeviceCallback.
 * Only allows Bluetooth A2DP, SCO, BLE Headset, and wired headsets/headphones.
 * Never plays through the phone speaker unless temporary Speech Test Mode is explicitly activated.
 */
class EarbudsAudioGuard(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _isEarbudsConnected = MutableStateFlow(false)
    val isEarbudsConnected: StateFlow<Boolean> = _isEarbudsConnected.asStateFlow()

    private val _connectedDeviceName = MutableStateFlow<String?>(null)
    val connectedDeviceName: StateFlow<String?> = _connectedDeviceName.asStateFlow()

    // Speech Test Mode state (OFF by default, allows temporary speaker playback for testing)
    private val _isTestModeActive = MutableStateFlow(false)
    val isTestModeActive: StateFlow<Boolean> = _isTestModeActive.asStateFlow()

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            evaluateAudioOutputs()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            evaluateAudioOutputs()
        }
    }

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioManager.registerAudioDeviceCallback(audioDeviceCallback, Handler(Looper.getMainLooper()))
        }
        evaluateAudioOutputs()
    }

    fun setTestMode(active: Boolean) {
        _isTestModeActive.value = active
    }

    /**
     * Inspects active output audio devices.
     * Evaluates strictly for private earbuds/headphones.
     */
    fun evaluateAudioOutputs(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            @Suppress("DEPRECATION")
            val isWired = audioManager.isWiredHeadsetOn
            @Suppress("DEPRECATION")
            val isBluetooth = audioManager.isBluetoothA2dpOn || audioManager.isBluetoothScoOn
            val connected = isWired || isBluetooth
            _isEarbudsConnected.value = connected
            _connectedDeviceName.value = if (connected) "Earbuds Connected" else null
            return connected
        }

        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val earbudDevice = devices.firstOrNull { isEarbudDevice(it) }

        val connected = earbudDevice != null
        _isEarbudsConnected.value = connected
        _connectedDeviceName.value = earbudDevice?.productName?.toString() ?: if (connected) "Earbuds Connected" else null

        // Explicitly route to preferred device on Android 12+ (API 31+) if communication device is used
        if (connected && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && earbudDevice != null) {
            try {
                audioManager.setCommunicationDevice(earbudDevice)
            } catch (_: Exception) {}
        }

        return connected
    }

    /**
     * Checks whether playback is permitted:
     * Either physical earbuds are connected, or Test Mode is temporarily active.
     */
    fun isPlaybackPermitted(): Boolean {
        return _isTestModeActive.value || evaluateAudioOutputs()
    }

    private fun isEarbudDevice(device: AudioDeviceInfo): Boolean {
        return when (device.type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_USB_HEADSET -> true
            else -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    device.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                    device.type == AudioDeviceInfo.TYPE_BLE_SPEAKER
                } else false
            }
        }
    }

    fun unregister() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
        }
    }
}
