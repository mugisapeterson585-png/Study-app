package com.silentstudy.audio

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Native TextToSpeech Manager (FIX 1 & Section 5 Speech Test Mode)
 * Enforces hardware EarbudsAudioGuard except during temporary 10-minute Test Mode.
 * Handles AudioFocus, pauses on calls, and provides spoken status prompts.
 */
class SilentStudyTtsManager(
    private val context: Context,
    val earbudsGuard: EarbudsAudioGuard
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val _hasError = MutableStateFlow<String?>(null)
    val hasError: StateFlow<String?> = _hasError.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    // Test mode 10-minute auto-expiry countdown in seconds
    private val _testModeSecondsRemaining = MutableStateFlow<Int?>(null)
    val testModeSecondsRemaining: StateFlow<Int?> = _testModeSecondsRemaining.asStateFlow()

    private var testModeJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    var onUtteranceFinished: ((utteranceId: String) -> Unit)? = null
    var onUtteranceStarted: ((utteranceId: String) -> Unit)? = null

    init {
        initTts()
    }

    private fun initTts() {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val engine = tts ?: return

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            engine.setAudioAttributes(audioAttributes)

            val locale = Locale.getDefault()
            val result = engine.setLanguage(locale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                val fallbackResult = engine.setLanguage(Locale.US)
                if (fallbackResult == TextToSpeech.LANG_MISSING_DATA || fallbackResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    _hasError.value = "TTS voice data missing on device. Tap to install voice in settings."
                    return
                }
            }

            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isSpeaking.value = true
                    utteranceId?.let { onUtteranceStarted?.invoke(it) }
                }

                override fun onDone(utteranceId: String?) {
                    _isSpeaking.value = false
                    utteranceId?.let { onUtteranceFinished?.invoke(it) }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _isSpeaking.value = false
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    _isSpeaking.value = false
                }
            })

            _isInitialized.value = true
            _hasError.value = null
        } else {
            _hasError.value = "Failed to initialise Android TextToSpeech engine."
        }
    }

    /**
     * Activates temporary Speech Test Mode with 10-minute auto-shutoff
     */
    fun enableTestMode() {
        earbudsGuard.setTestMode(true)
        testModeJob?.cancel()
        testModeJob = scope.launch {
            var seconds = 600 // 10 minutes
            _testModeSecondsRemaining.value = seconds
            while (seconds > 0) {
                delay(1000)
                seconds -= 1
                _testModeSecondsRemaining.value = seconds
            }
            disableTestMode()
        }
    }

    /**
     * Disables Speech Test Mode and enforces earbuds-only privacy
     */
    fun disableTestMode() {
        testModeJob?.cancel()
        testModeJob = null
        _testModeSecondsRemaining.value = null
        earbudsGuard.setTestMode(false)
        if (!earbudsGuard.evaluateAudioOutputs()) {
            stop() // Halt any ongoing speech if earbuds are not plugged in
        }
    }

    /**
     * Speaks text through audio channel.
     * Enforces EarbudsAudioGuard unless Test Mode is active.
     */
    fun speak(
        text: String,
        utteranceId: String,
        speechRate: Float = 1.0f,
        pitch: Float = 1.0f
    ): Boolean {
        if (!earbudsGuard.isPlaybackPermitted()) {
            _hasError.value = "Connect earbuds to listen. Audio blocked from speaker."
            return false
        }

        if (!_isInitialized.value || tts == null) {
            _hasError.value = "TTS not yet initialized. Please wait."
            return false
        }

        requestAudioFocus()

        tts?.let { engine ->
            engine.setSpeechRate(speechRate)
            engine.setPitch(pitch)

            val params = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            }
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
            return true
        }

        return false
    }

    fun stop() {
        tts?.stop()
        _isSpeaking.value = false
        abandonAudioFocus()
    }

    fun testVoice(rate: Float = 1.0f) {
        speak(
            "SilentStudy voice test active. Private earbud audio confirmed.",
            "test_voice_utterance",
            speechRate = rate
        )
    }

    fun playTestAnswer(rate: Float = 1.0f) {
        speak(
            "SilentStudy Sample Answer: The solution to the quadratic equation is x equals 1 half and x equals negative 3. Verified clear in audio channel.",
            "sample_test_answer",
            speechRate = rate
        )
    }

    fun spokenPrompt(text: String) {
        if (earbudsGuard.isPlaybackPermitted()) {
            speak(text, "prompt_${System.currentTimeMillis()}", speechRate = 1.1f)
        }
    }

    /**
     * Plays a private, subtle tick sound into earbuds when a gesture is recognized
     */
    fun playEarbudTick() {
        if (!earbudsGuard.isPlaybackPermitted()) return
        scope.launch(Dispatchers.IO) {
            try {
                val sampleRate = 16000
                val durationMs = 12
                val numSamples = (sampleRate * durationMs) / 1000
                val buffer = ShortArray(numSamples)
                val freq = 950.0
                for (i in 0 until numSamples) {
                    val angle = 2.0 * Math.PI * i * freq / sampleRate
                    val envelope = Math.exp(-4.0 * i / numSamples)
                    buffer[i] = (Math.sin(angle) * 12000 * envelope).toInt().toShort()
                }
                val audioTrack = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    android.media.AudioTrack.Builder()
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()
                        )
                        .setAudioFormat(
                            android.media.AudioFormat.Builder()
                                .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(sampleRate)
                                .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO)
                                .build()
                        )
                        .setBufferSizeInBytes(buffer.size * 2)
                        .setTransferMode(android.media.AudioTrack.MODE_STATIC)
                        .build()
                } else {
                    @Suppress("DEPRECATION")
                    android.media.AudioTrack(
                        AudioManager.STREAM_MUSIC,
                        sampleRate,
                        android.media.AudioFormat.CHANNEL_OUT_MONO,
                        android.media.AudioFormat.ENCODING_PCM_16BIT,
                        buffer.size * 2,
                        android.media.AudioTrack.MODE_STATIC
                    )
                }
                audioTrack.write(buffer, 0, buffer.size)
                audioTrack.play()
                delay(25)
                audioTrack.stop()
                audioTrack.release()
            } catch (_: Exception) {}
        }
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(audioAttributes)
                .setOnAudioFocusChangeListener { focusChange ->
                    when (focusChange) {
                        AudioManager.AUDIOFOCUS_LOSS -> stop()
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> stop()
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                            // Can duck volume if needed
                        }
                    }
                }
                .build()

            audioManager.requestAudioFocus(audioFocusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                { focusChange ->
                    if (focusChange == AudioManager.AUDIOFOCUS_LOSS || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                        stop()
                    }
                },
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
            audioManager.abandonAudioFocusRequest(audioFocusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
    }

    fun getOpenTtsSettingsIntent(): Intent {
        return Intent().apply {
            action = "com.android.settings.TTS_SETTINGS"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    fun shutdown() {
        disableTestMode()
        tts?.shutdown()
        tts = null
    }
}
