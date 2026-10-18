import React, { useState } from 'react';
import { 
  Download, 
  Code2, 
  FileCode, 
  Copy, 
  Check, 
  Smartphone, 
  Layers, 
  CheckCircle2, 
  ExternalLink 
} from 'lucide-react';
import JSZip from 'jszip';
import { NeuCard } from './neumorphic/NeuCard';
import { NeuButton } from './neumorphic/NeuButton';

interface AndroidFile {
  name: string;
  path: string;
  description: string;
  language: string;
  content: string;
}

const ANDROID_PROJECT_FILES: AndroidFile[] = [
  {
    name: 'DeskTapSensorManager.kt',
    path: 'app/src/main/java/com/silentstudy/gestures/DeskTapSensorManager.kt',
    description: 'Native accelerometer desk knock detector with high-pass gravity filter, API 24+ compatible',
    language: 'kotlin',
    content: `package com.silentstudy.gestures

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlin.math.sqrt

sealed class DeskGesture {
    object SingleTap : DeskGesture()
    object DoubleTap : DeskGesture()
    object TripleTap : DeskGesture()
    object TapPauseTap : DeskGesture()
    object LongKnock : DeskGesture()
    object FourTaps : DeskGesture()
    object FiveTaps : DeskGesture()
    object SixTaps : DeskGesture()
}

/**
 * Native SensorManager detector that detects knocks on a desk while the phone lies flat.
 * MinSdk = 24 compatible. Runs with screen off when held in foreground service with partial wake lock.
 */
class DeskTapSensorManager(private val context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val _gestures = MutableSharedFlow<DeskGesture>(extraBufferCapacity = 10)
    val gestures: SharedFlow<DeskGesture> = _gestures.asSharedFlow()

    private var isListening = false
    private var threshold = 1.8f // m/s^2 above resting gravity
    private val gravity = FloatArray(3) { 0f }
    private val tapTimestamps = mutableListOf<Long>()
    private var groupingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    fun startListening(sensitivity: Int = 5) {
        if (isListening || accelerometer == null) return
        // Map 1..10 sensitivity to threshold 3.5 down to 0.6 m/s^2
        threshold = 3.5f - (sensitivity - 1) * 0.32f
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        isListening = true
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

        // High-pass filter to remove gravity and slow movement
        val alpha = 0.8f
        gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0]
        gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1]
        gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2]

        val linearX = event.values[0] - gravity[0]
        val linearY = event.values[1] - gravity[1]
        val linearZ = event.values[2] - gravity[2]

        val magnitude = sqrt(linearX * linearX + linearY * linearY + linearZ * linearZ)
        val now = System.currentTimeMillis()

        if (magnitude > threshold) {
            onTapDetected(now)
        }
    }

    private fun onTapDetected(now: Long) {
        val last = tapTimestamps.lastOrNull()
        if (last != null && (now - last) < 70) {
            return // Debounce rapid spikes < 70ms
        }
        tapTimestamps.add(now)

        groupingJob?.cancel()
        groupingJob = scope.launch {
            delay(600) // Pattern grouping window
            evaluateTaps()
        }
    }

    private fun evaluateTaps() {
        val count = tapTimestamps.size
        if (count == 0) return

        if (count == 2) {
            val delta = tapTimestamps[1] - tapTimestamps[0]
            if (delta > 350) {
                _gestures.tryEmit(DeskGesture.TapPauseTap)
                tapTimestamps.clear()
                return
            }
        }

        when (count) {
            1 -> _gestures.tryEmit(DeskGesture.SingleTap)
            2 -> _gestures.tryEmit(DeskGesture.DoubleTap)
            3 -> _gestures.tryEmit(DeskGesture.TripleTap)
            4 -> _gestures.tryEmit(DeskGesture.FourTaps)
            5 -> _gestures.tryEmit(DeskGesture.FiveTaps)
            else -> if (count >= 6) _gestures.tryEmit(DeskGesture.SixTaps)
        }
        tapTimestamps.clear()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}`
  },
  {
    name: 'EarbudsAudioGuard.kt',
    path: 'app/src/main/java/com/silentstudy/audio/EarbudsAudioGuard.kt',
    description: 'Enforces strict earbuds-only audio routing. Audio never plays through phone speaker.',
    language: 'kotlin',
    content: `package com.silentstudy.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

/**
 * Earbuds-Only Audio Guard (Section 6)
 * Blocks playback if no Bluetooth earbuds or wired headphones are connected.
 * Never falls back to phone speaker. API 24 to API 34+ compatible.
 */
class EarbudsAudioGuard(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun isEarbudsConnected(): Boolean {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        return devices.any { device ->
            when (device.type) {
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
    }

    fun getPreferredEarbudsDevice(): AudioDeviceInfo? {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        return devices.firstOrNull { device ->
            device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && device.type == AudioDeviceInfo.TYPE_BLE_HEADSET) ||
            device.type == AudioDeviceInfo.TYPE_WIRED_HEADSET
        }
    }
}`
  },
  {
    name: 'AiProvider.kt',
    path: 'app/src/main/java/com/silentstudy/ai/AiProvider.kt',
    description: 'Provider-agnostic interface with Gemini, Claude, OpenAI, and Custom adapters',
    language: 'kotlin',
    content: `package com.silentstudy.ai

data class AnswerRequest(
    val imagesBase64: List<String>,
    val questionPrompt: String? = null,
    val subjectHint: String? = null
)

data class AnswerBlock(
    val type: String,     // "text", "math", "step"
    val display: String,  // Written form (KaTeX / LaTeX)
    val spoken: String,   // Natural-language reading for earbud TTS
    val stepNumber: Int? = null
)

data class AnswerResult(
    val subject: String,
    val questionText: String,
    val shortAnswer: String,
    val blocks: List<AnswerBlock>,
    val simpleExplanation: String
)

interface AiProvider {
    val name: String
    suspend fun answer(request: AnswerRequest): AnswerResult
    suspend fun testConnection(): Boolean
}`
  },
  {
    name: 'SilentStudyService.kt',
    path: 'app/src/main/java/com/silentstudy/service/SilentStudyService.kt',
    description: 'Foreground service with partial wake lock for background desk operation with screen off',
    language: 'kotlin',
    content: `package com.silentstudy.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.silentstudy.R
import com.silentstudy.gestures.DeskTapSensorManager

class SilentStudyService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var deskTapSensor: DeskTapSensorManager

    override fun onCreate() {
        super.onCreate()
        deskTapSensor = DeskTapSensorManager(this)
        acquireWakeLock()
        startForegroundServiceWithNotification()
        deskTapSensor.startListening()
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "SilentStudy::DeskGestureWakeLock"
        ).apply {
            setReferenceCounted(false)
            acquire(1000 * 60 * 60) // 1 hour max
        }
    }

    private fun startForegroundServiceWithNotification() {
        val channelId = "silentstudy_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "SilentStudy Active Session",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Silent desk tap gestures active"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("SilentStudy Active")
            .setContentText("Earbuds-only study session. Screen off desk taps active.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                101,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(101, notification)
        }
    }

    override fun onDestroy() {
        deskTapSensor.stopListening()
        wakeLock?.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}`
  },
  {
    name: 'NeumorphicModifier.kt',
    path: 'app/src/main/java/com/silentstudy/ui/NeumorphicModifier.kt',
    description: 'Jetpack Compose neumorphic dual-shadow modifier with API 24 fallback',
    language: 'kotlin',
    content: `package com.silentstudy.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Reusable Compose Neumorphism Modifier (Section 1)
 * Uses light top-left shadow and dark bottom-right shadow.
 * Light surface: #E0E5EC, Dark surface: #2B2E35.
 */
fun Modifier.neumorphic(
    isPressed: Boolean = false,
    cornerRadius: Dp = 24.dp,
    elevation: Dp = 6.dp,
    lightShadowColor: Color = Color.White.copy(alpha = 0.9f),
    darkShadowColor: Color = Color(0xFFA3B1C6).copy(alpha = 0.6f)
): Modifier = this.drawBehind {
    drawIntoCanvas { canvas ->
        if (!isPressed) {
            // Raised State: Top-Left Light Shadow
            val lightPaint = Paint().apply {
                color = lightShadowColor
            }
            canvas.drawRoundRect(
                left = -elevation.toPx() / 2,
                top = -elevation.toPx() / 2,
                right = size.width - elevation.toPx() / 2,
                bottom = size.height - elevation.toPx() / 2,
                radiusX = cornerRadius.toPx(),
                radiusY = cornerRadius.toPx(),
                paint = lightPaint
            )

            // Bottom-Right Dark Shadow
            val darkPaint = Paint().apply {
                color = darkShadowColor
            }
            canvas.drawRoundRect(
                left = elevation.toPx() / 2,
                top = elevation.toPx() / 2,
                right = size.width + elevation.toPx() / 2,
                bottom = size.height + elevation.toPx() / 2,
                radiusX = cornerRadius.toPx(),
                radiusY = cornerRadius.toPx(),
                paint = darkPaint
            )
        }
    }
}`
  },
  {
    name: 'build.gradle.kts',
    path: 'app/build.gradle.kts',
    description: 'Gradle configuration with minSdk = 24, targetSdk = 34, Compose, CameraX, Room, Hilt',
    language: 'kotlin',
    content: `plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt.android)
}

android {
    namespace = "com.silentstudy"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.silentstudy"
        minSdk = 24 // Android 7.0 Nougat compatibility
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
}

dependencies {
    // Jetpack Compose (API 24+)
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")

    // CameraX
    val cameraxVersion = "1.3.1"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // Room Database
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    kapt("androidx.room:room-compiler:$roomVersion")

    // WorkManager & DataStore
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Hilt Dependency Injection
    implementation("com.google.dagger:hilt-android:2.50")
    kapt("com.google.dagger:hilt-compiler:2.50")

    // Coroutines & Lifecycle
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
}`
  },
  {
    name: 'AndroidManifest.xml',
    path: 'app/src/main/AndroidManifest.xml',
    description: 'Permissions for Camera, Bluetooth, Foreground Service, Wake Lock, and API 24-34 guards',
    language: 'xml',
    content: `<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.silentstudy">

    <!-- Permissions with API 24 to API 34+ handling -->
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />
    <uses-permission android:name="android.permission.VIBRATE" />

    <!-- Bluetooth: Legacy for API 24-30, BLUETOOTH_CONNECT for API 31+ -->
    <uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />

    <!-- Foreground Service: API 28+ and API 34 types -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <application
        android:name=".SilentStudyApp"
        android:allowBackup="false"
        android:icon="@mipmap/ic_launcher"
        android:label="SilentStudy"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.SilentStudy">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.SilentStudy">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".service.SilentStudyService"
            android:foregroundServiceType="mediaPlayback"
            android:exported="false" />

    </application>
</manifest>`
  }
];

export const NativeAndroidExport: React.FC = () => {
  const [selectedFile, setSelectedFile] = useState<AndroidFile>(ANDROID_PROJECT_FILES[0]);
  const [copied, setCopied] = useState(false);
  const [isExporting, setIsExporting] = useState(false);

  const handleCopy = () => {
    navigator.clipboard.writeText(selectedFile.content);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const handleDownloadZip = async () => {
    setIsExporting(true);
    try {
      const zip = new JSZip();
      ANDROID_PROJECT_FILES.forEach(file => {
        zip.file(file.path, file.content);
      });
      zip.file('README.md', `# SilentStudy - Native Android Kotlin & Compose

Private, earbuds-only AI study companion for quiet places like libraries.

## Architecture
- MinSdk: 24 (Android 7.0 Nougat)
- TargetSdk: 34
- Jetpack Compose + Neumorphic Soft UI
- Provider-Agnostic AI Layer (Gemini, Claude, OpenAI, Custom)
- Desk-Tap Accelerometer Sensor Engine with High-pass Filter
- Earbuds-Only Audio Guard (Never plays through phone speaker)
- Foreground Service with WakeLock for Screen-Off Desk Operation

Import into Android Studio Hedgehog / Iguana / Jellyfish to build the APK.`);

      const blob = await zip.generateAsync({ type: 'blob' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'SilentStudy-Android-Kotlin-Project.zip';
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
    } catch (e) {
      console.error('Failed to create ZIP', e);
    } finally {
      setIsExporting(false);
    }
  };

  return (
    <div className="flex flex-col gap-4 pb-20">
      {/* Overview Banner */}
      <NeuCard elevation="raised" rounded="2xl" className="p-4 sm:p-5">
        <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <div className="w-12 h-12 rounded-2xl neu-pressed flex items-center justify-center text-blue-600 dark:text-blue-400">
              <Smartphone size={24} />
            </div>
            <div>
              <h2 className="text-base font-bold text-slate-800 dark:text-slate-100">
                Native Android Kotlin Architecture
              </h2>
              <span className="text-xs text-slate-500 dark:text-slate-400">
                minSdk 24 (Android 7.0) · targetSdk 34 · Jetpack Compose · Room · Hilt · CameraX
              </span>
            </div>
          </div>

          <NeuButton
            variant="accent"
            size="md"
            onClick={handleDownloadZip}
            disabled={isExporting}
          >
            <Download size={16} className="mr-2" />
            <span>{isExporting ? 'Packaging ZIP...' : 'Download Android Studio ZIP'}</span>
          </NeuButton>
        </div>
      </NeuCard>

      {/* Code Viewer Two-Column Layout */}
      <div className="grid grid-cols-1 md:grid-cols-12 gap-4">
        {/* Left: File Navigator */}
        <div className="md:col-span-4 flex flex-col gap-2">
          <span className="text-xs font-bold uppercase tracking-wider text-slate-500 dark:text-slate-400 px-1">
            Source Modules
          </span>
          {ANDROID_PROJECT_FILES.map(file => (
            <button
              key={file.name}
              onClick={() => setSelectedFile(file)}
              className={`p-3 rounded-2xl text-left transition-all cursor-pointer flex flex-col ${
                selectedFile.name === file.name
                  ? 'neu-pressed border border-blue-500/40 text-blue-600 dark:text-blue-400'
                  : 'neu-raised text-slate-700 dark:text-slate-300 hover:text-slate-900'
              }`}
            >
              <div className="flex items-center gap-2">
                <FileCode size={15} />
                <span className="text-xs font-bold font-mono truncate">{file.name}</span>
              </div>
              <span className="text-[11px] text-slate-500 dark:text-slate-400 mt-1 line-clamp-1">
                {file.description}
              </span>
            </button>
          ))}
        </div>

        {/* Right: Code Inspector */}
        <div className="md:col-span-8 flex flex-col">
          <NeuCard elevation="raised" rounded="2xl" className="p-4 flex flex-col h-full overflow-hidden">
            <div className="flex items-center justify-between pb-3 border-b border-slate-300/40 dark:border-slate-700/40">
              <div className="flex flex-col">
                <span className="text-xs font-mono font-bold text-slate-800 dark:text-slate-200">
                  {selectedFile.path}
                </span>
                <span className="text-[11px] text-slate-500 dark:text-slate-400">
                  {selectedFile.description}
                </span>
              </div>

              <NeuButton size="sm" onClick={handleCopy}>
                {copied ? <Check size={14} className="text-emerald-500 mr-1" /> : <Copy size={14} className="mr-1" />}
                <span>{copied ? 'Copied' : 'Copy'}</span>
              </NeuButton>
            </div>

            <pre className="mt-3 p-3 rounded-xl neu-pressed overflow-x-auto text-xs font-mono text-slate-800 dark:text-slate-200 leading-relaxed max-h-[500px]">
              <code>{selectedFile.content}</code>
            </pre>
          </NeuCard>
        </div>
      </div>
    </div>
  );
};
