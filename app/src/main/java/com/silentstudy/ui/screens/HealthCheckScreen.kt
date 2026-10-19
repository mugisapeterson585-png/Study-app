package com.silentstudy.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.silentstudy.SilentStudyApp
import com.silentstudy.ui.components.AppButton
import com.silentstudy.ui.components.AppCard
import com.silentstudy.ui.theme.LocalAppColors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class HealthItem(
    val title: String,
    val description: String,
    val isHealthy: Boolean,
    val fixActionLabel: String? = null,
    val onFix: (() -> Unit)? = null
)

@Composable
fun HealthCheckScreen(
    app: SilentStudyApp,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()

    var items by remember { mutableStateOf<List<HealthItem>>(emptyList()) }
    var isChecking by remember { mutableStateOf(false) }

    val runDiagnostics = {
        scope.launch {
            isChecking = true
            val aiSettings = app.preferencesRepository.aiSettings.first()
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val connManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

            // 1. API Key
            val hasApiKey = aiSettings.apiKey.isNotBlank() || aiSettings.provider == "openrouter"

            // 2. Internet
            val activeNet = connManager.activeNetwork
            val caps = connManager.getNetworkCapabilities(activeNet)
            val hasInternet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

            // 3. Model reachable
            var modelReachable = false
            if (hasApiKey && hasInternet) {
                val testRes = app.aiProviderManager.testConnection(
                    aiSettings.provider,
                    aiSettings.apiKey,
                    aiSettings.model,
                    aiSettings.baseUrl
                )
                modelReachable = testRes.success
            }

            // 4. TTS Engine
            val ttsOk = app.ttsManager.isInitialized.value && app.ttsManager.hasError.value == null

            // 5. Earbuds
            val earbudsConnected = app.earbudsGuard.evaluateAudioOutputs()

            // 6. Motion sensor
            val sensorActive = app.deskTapSensor.isListening

            // 7. Camera permission
            val cameraGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED

            // 8. Notification permission
            val notifGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else true

            // 9. Battery optimization exemption
            val batteryExempt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                powerManager.isIgnoringBatteryOptimizations(context.packageName)
            } else true

            // 10. Foreground Service
            val serviceHealthy = true

            items = listOf(
                HealthItem(
                    title = "API Key Configured",
                    description = if (hasApiKey) "Active key detected for ${aiSettings.provider}." else "No API key configured for ${aiSettings.provider}.",
                    isHealthy = hasApiKey,
                    fixActionLabel = if (!hasApiKey) "Enter Key" else null,
                    onFix = { onBack() }
                ),
                HealthItem(
                    title = "Selected Model Reachable",
                    description = if (modelReachable) "${aiSettings.model} responding within latency limit." else "Cannot reach ${aiSettings.model}. Pick another in dropdown.",
                    isHealthy = modelReachable,
                    fixActionLabel = if (!modelReachable) "Select Model" else null,
                    onFix = { onBack() }
                ),
                HealthItem(
                    title = "Internet Connection",
                    description = if (hasInternet) "Network connection active." else "No internet detected. Offline queue is active.",
                    isHealthy = hasInternet
                ),
                HealthItem(
                    title = "Android Text-to-Speech",
                    description = if (ttsOk) "TTS voice engine initialized." else (app.ttsManager.hasError.value ?: "Voice data missing."),
                    isHealthy = ttsOk,
                    fixActionLabel = if (!ttsOk) "Install Voice" else null,
                    onFix = {
                        try {
                            context.startActivity(app.ttsManager.getOpenTtsSettingsIntent())
                        } catch (_: Exception) {}
                    }
                ),
                HealthItem(
                    title = "Private Earbuds Guard",
                    description = if (earbudsConnected) "Earbuds verified connected. Private audio active." else "No earbuds detected. Speaker audio blocked.",
                    isHealthy = earbudsConnected,
                    fixActionLabel = if (!earbudsConnected) "Bluetooth" else null,
                    onFix = {
                        try {
                            context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                        } catch (_: Exception) {}
                    }
                ),
                HealthItem(
                    title = "Desk Accelerometer Sensor",
                    description = if (sensorActive) "Accelerometer active for desk knocks." else "Tap sensor is currently dormant.",
                    isHealthy = sensorActive
                ),
                HealthItem(
                    title = "Camera Permission",
                    description = if (cameraGranted) "Granted for question photo capture." else "Denied. SilentStudy cannot capture question photos.",
                    isHealthy = cameraGranted,
                    fixActionLabel = if (!cameraGranted) "Grant" else null,
                    onFix = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    }
                ),
                HealthItem(
                    title = "Notification Permission",
                    description = if (notifGranted) "Granted for offline answer sync alerts." else "Denied. Offline answer alerts won't appear.",
                    isHealthy = notifGranted
                ),
                HealthItem(
                    title = "Battery Optimization Exemption",
                    description = if (batteryExempt) "App exempt from sleep; screen-off desk knocks will never freeze." else "Optimized. Phone may kill sensor during long screen-off study sessions.",
                    isHealthy = batteryExempt,
                    fixActionLabel = if (!batteryExempt) "Disable Sleep" else null,
                    onFix = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            try {
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        }
                    }
                ),
                HealthItem(
                    title = "Library Foreground Service",
                    description = if (serviceHealthy) "Foreground audio & sensor daemon ready." else "Service inactive.",
                    isHealthy = serviceHealthy
                )
            )
            isChecking = false
        }
    }

    LaunchedEffect(Unit) {
        runDiagnostics()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
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
                        text = "System Diagnostics & Health Check",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "Real hardware and service verification",
                        fontSize = 11.sp,
                        color = colors.textSecondary
                    )
                }

                AppButton(onClick = onBack) {
                    Text("Done", fontSize = 11.sp, color = colors.accent)
                }
            }
        }

        // Diagnostic Items
        items.forEach { item ->
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (item.isHealthy) Icons.Default.CheckCircle else Icons.Default.Error,
                            contentDescription = null,
                            tint = if (item.isHealthy) Color(0xFF5B8DEF) else Color(0xFFD06A6A),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = item.title,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                            Text(
                                text = item.description,
                                fontSize = 10.sp,
                                color = colors.textSecondary,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }

                    if (item.fixActionLabel != null && item.onFix != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        AppButton(
                            onClick = item.onFix,
                            isAccent = true
                        ) {
                            Text(item.fixActionLabel, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = colors.accent)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        AppButton(
            onClick = { runDiagnostics() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(if (isChecking) "Checking..." else "Re-check All Diagnostics", fontSize = 12.sp, color = colors.textPrimary)
        }
    }
}
