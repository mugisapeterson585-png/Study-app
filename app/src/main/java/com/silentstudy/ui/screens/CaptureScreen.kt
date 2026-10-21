package com.silentstudy.ui.screens

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Base64
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.silentstudy.SilentStudyApp
import com.silentstudy.ai.AnswerRequest
import com.silentstudy.data.model.Answer
import com.silentstudy.data.model.AnswerBlock
import com.silentstudy.service.OfflineQueueWorker
import com.silentstudy.ui.components.AppButton
import com.silentstudy.ui.components.AppCard
import com.silentstudy.ui.components.AppTextField
import com.silentstudy.ui.theme.LocalAppColors
import com.silentstudy.util.BudgetCheckResult
import com.silentstudy.util.ImageUtils
import com.silentstudy.util.TokenCostManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun CaptureScreen(
    app: SilentStudyApp,
    onAnswerGenerated: (Answer) -> Unit,
    onAnalyzeError: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val tokenManager = remember { TokenCostManager(app.database.usageDao()) }

    val cameraController = remember {
        LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            setEnabledUseCases(LifecycleCameraController.IMAGE_CAPTURE)
        }
    }

    var capturedPhotosBase64 by remember { mutableStateOf(listOf<String>()) }
    var promptNote by remember { mutableStateOf("") }
    var isTimerMode by remember { mutableStateOf(false) }
    var timerSecondsRemaining by remember { mutableStateOf<Int?>(null) }
    var isTorchOn by remember { mutableStateOf(false) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var analyzeStatusMessage by remember { mutableStateOf<String?>(null) }
    var budgetWarningMessage by remember { mutableStateOf<String?>(null) }

    // Batch capture queue
    var batchQueueCount by remember { mutableStateOf(0) }

    // Capture Mode: auto, direct, table, graph
    var captureMode by remember { mutableStateOf("auto") }

    val takePicture = {
        cameraController.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    val rawBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP)

                    // Downscale and compress to max 1280px, JPEG ~80
                    val compressedBase64 = ImageUtils.downscaleAndCompressBase64(rawBase64, 1280, 80)

                    scope.launch {
                        val aiSettings = app.preferencesRepository.aiSettings.first()
                        val maxAllowed = aiSettings.maxImagesPerQuestion
                        if (capturedPhotosBase64.size < maxAllowed) {
                            capturedPhotosBase64 = capturedPhotosBase64 + compressedBase64
                        }
                    }
                    image.close()
                }

                override fun onError(exception: ImageCaptureException) {
                    // Fallback to sample photo if camera is unavailable
                    capturedPhotosBase64 = capturedPhotosBase64 + "sample_exam_photo"
                }
            }
        )
    }

    val handleShutter = {
        if (isTimerMode) {
            scope.launch {
                timerSecondsRemaining = 3
                while (timerSecondsRemaining != null && timerSecondsRemaining!! > 0) {
                    delay(1000)
                    timerSecondsRemaining = timerSecondsRemaining!! - 1
                }
                timerSecondsRemaining = null
                takePicture()
            }
        } else {
            takePicture()
        }
    }

    val processAnswer = {
        scope.launch {
            if (capturedPhotosBase64.isEmpty()) return@launch

            isAnalyzing = true
            analyzeStatusMessage = "Checking cache & budget..."

            val aiSettings = app.preferencesRepository.aiSettings.first()
            val budgetSettings = app.preferencesRepository.budgetSettings.first()

            // 1. Photo deduplication cache check (SHA-256)
            val combinedHash = ImageUtils.computeSha256Hash(capturedPhotosBase64.joinToString(","))
            val cachedAnswer = app.database.answerDao().findByPhotoHash(combinedHash)

            if (cachedAnswer != null) {
                analyzeStatusMessage = "Reusing cached answer (0 tokens used)."
                delay(400)
                isAnalyzing = false
                onAnswerGenerated(cachedAnswer)
                return@launch
            }

            // 2. Budget cap protection
            val budgetCheck = tokenManager.checkBudget(budgetSettings, aiSettings.maxTokensAnswer)
            when (budgetCheck) {
                is BudgetCheckResult.Blocked -> {
                    isAnalyzing = false
                    analyzeStatusMessage = budgetCheck.message
                    onAnalyzeError(budgetCheck.message)
                    return@launch
                }
                is BudgetCheckResult.Warning -> {
                    budgetWarningMessage = budgetCheck.message
                }
                is BudgetCheckResult.Allowed -> {
                    budgetWarningMessage = null
                }
            }

            // 3. Connectivity check: If offline, save to pending queue and enqueue WorkManager
            val connManager = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNet = connManager.activeNetwork
            val caps = connManager.getNetworkCapabilities(activeNet)
            val isOnline = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

            if (!isOnline) {
                val pendingAnswer = Answer(
                    subject = "Queued Question",
                    questionText = promptNote.ifEmpty { "Offline Captured Photo" },
                    imagePaths = capturedPhotosBase64,
                    shortAnswer = "Question saved offline. Will auto-process when connection returns.",
                    simpleExplanation = "",
                    blocks = listOf(AnswerBlock("text", "Offline: queued for network sync", "Question queued for offline network sync")),
                    photoHash = combinedHash,
                    status = "pending_offline"
                )
                val newId = app.database.answerDao().insertAnswer(pendingAnswer)
                OfflineQueueWorker.enqueueWork(context)

                isAnalyzing = false
                analyzeStatusMessage = "No internet. Answer #$newId saved to offline queue."
                capturedPhotosBase64 = emptyList()
                promptNote = ""
                return@launch
            }

            // 4. Online AI Generation with fallback
            analyzeStatusMessage = "Sending to ${aiSettings.provider} (${aiSettings.model})..."

            try {
                val result = app.aiProviderManager.generateAnswerWithFallback(
                    provider = aiSettings.provider,
                    apiKey = aiSettings.apiKey,
                    model = aiSettings.model,
                    baseUrl = aiSettings.baseUrl,
                    fallbackProvider = aiSettings.fallbackProvider,
                    fallbackApiKey = aiSettings.fallbackApiKey,
                    fallbackModel = aiSettings.fallbackModel,
                    request = AnswerRequest(
                        imagesBase64 = capturedPhotosBase64,
                        promptNote = promptNote.ifEmpty { null },
                        answerStyle = aiSettings.answerStyle,
                        captureMode = captureMode,
                        conciseMode = aiSettings.conciseMode,
                        maxOutputTokens = if (aiSettings.answerStyle == "direct") 400 else aiSettings.maxTokensAnswer,
                        lazyExplanation = aiSettings.lazyExplanation
                    )
                )

                // 5. Record real token usage
                tokenManager.recordUsage(
                    provider = aiSettings.provider,
                    model = result.rawModelUsed.ifEmpty { aiSettings.model },
                    promptTokens = result.usage.promptTokens,
                    completionTokens = result.usage.completionTokens,
                    pricePerMInput = budgetSettings.pricePerMInputUsd,
                    pricePerMOutput = budgetSettings.pricePerMOutputUsd
                )

                val answerToSave = Answer(
                    subject = result.subject,
                    questionText = result.questionText,
                    imagePaths = capturedPhotosBase64,
                    shortAnswer = result.shortAnswer,
                    blocks = result.blocks,
                    answerParts = result.answerParts,
                    table = result.table,
                    graph = result.graph,
                    answerStyle = aiSettings.answerStyle,
                    simpleExplanation = result.simpleExplanation,
                    photoHash = combinedHash,
                    status = "completed"
                )

                isAnalyzing = false
                capturedPhotosBase64 = emptyList()
                promptNote = ""
                onAnswerGenerated(answerToSave)
            } catch (e: Exception) {
                isAnalyzing = false
                analyzeStatusMessage = "Error: ${e.message}"
                onAnalyzeError(e.message ?: "Analysis failed")
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Dark Viewfinder Frame (Flat low-glare, zero white)
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.background)
                    .border(1.dp, colors.border, RoundedCornerShape(16.dp))
            ) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            controller = cameraController
                            cameraController.bindToLifecycle(lifecycleOwner)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Framing reticle
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(18.dp)
                        .border(1.dp, colors.accent.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                ) {
                    Text(
                        text = "ALIGN QUESTION / EQUATIONS",
                        color = colors.textSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp)
                    )
                }

                // Shutter timer countdown overlay
                timerSecondsRemaining?.let { sec ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(colors.background.copy(alpha = 0.7f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$sec",
                            fontSize = 64.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.accent
                        )
                    }
                }

                // Camera Controls Overlay
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Flashlight toggle
                    IconButton(
                        onClick = {
                            isTorchOn = !isTorchOn
                            cameraController.enableTorch(isTorchOn)
                        },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(colors.surface.copy(alpha = 0.8f))
                    ) {
                        Icon(
                            imageVector = if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = "Flashlight",
                            tint = if (isTorchOn) colors.accent else colors.textSecondary
                        )
                    }

                    // 3-second timer toggle
                    IconButton(
                        onClick = { isTimerMode = !isTimerMode },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(colors.surface.copy(alpha = 0.8f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = "Timer",
                            tint = if (isTimerMode) colors.accent else colors.textSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Capture Mode Selector Chips (Auto, Direct, Table, Graph)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(
                    "auto" to "Auto",
                    "direct" to "Direct",
                    "table" to "Table",
                    "graph" to "Graph"
                ).forEach { (mKey, mLabel) ->
                    val isSel = captureMode == mKey
                    AppButton(
                        onClick = { captureMode = mKey },
                        modifier = Modifier.weight(1f),
                        isAccent = isSel
                    ) {
                        Text(
                            text = mLabel,
                            fontSize = 11.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSel) colors.accent else colors.textSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Shutter Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppButton(
                    onClick = { handleShutter() },
                    cornerRadius = 32.dp,
                    isAccent = true,
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Shutter",
                        tint = colors.accent,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }

        // Question Pages Tray (Batch & Multi-Photo)
        if (capturedPhotosBase64.isNotEmpty()) {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Question Photos (${capturedPhotosBase64.size} captured)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                    AppButton(onClick = { capturedPhotosBase64 = emptyList() }) {
                        Text("Clear", fontSize = 10.sp, color = colors.error)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(capturedPhotosBase64) { index, _ ->
                        Box(
                            modifier = Modifier
                                .size(width = 75.dp, height = 95.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.control)
                                .border(1.dp, colors.accent.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Image, contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Page ${index + 1}", fontSize = 11.sp, color = colors.textPrimary)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                AppTextField(
                    value = promptNote,
                    onValueChange = { promptNote = it },
                    label = "Optional Student Note / Specific Step",
                    placeholder = "e.g. Solve part b, or show derivative"
                )

                if (budgetWarningMessage != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = budgetWarningMessage!!,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.error
                    )
                }

                if (analyzeStatusMessage != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = analyzeStatusMessage!!,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.accent
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                AppButton(
                    onClick = { processAnswer() },
                    modifier = Modifier.fillMaxWidth(),
                    isAccent = true,
                    enabled = !isAnalyzing
                ) {
                    if (isAnalyzing) {
                        CircularProgressIndicator(color = colors.accent, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Analyzing Question...", color = colors.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Text("Analyze & Solve with AI", color = colors.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
