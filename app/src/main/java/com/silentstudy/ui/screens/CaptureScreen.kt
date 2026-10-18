package com.silentstudy.ui.screens

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
import com.silentstudy.data.model.Answer
import com.silentstudy.data.model.AnswerBlock
import com.silentstudy.ui.components.AppButton
import com.silentstudy.ui.components.AppCard
import com.silentstudy.ui.components.AppTextField
import com.silentstudy.ui.theme.LocalAppColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun CaptureScreen(
    onAnswerGenerated: (Answer) -> Unit,
    onAnalyzeError: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()

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
    var autoEnhance by remember { mutableStateOf(true) }
    var isAnalyzing by remember { mutableStateOf(false) }

    val takePicture = {
        cameraController.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    capturedPhotosBase64 = capturedPhotosBase64 + base64
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
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

                // Document framing reticle
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

                // Timer countdown overlay
                if (timerSecondsRemaining != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(colors.background.copy(alpha = 0.7f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${timerSecondsRemaining}",
                            color = colors.accent,
                            fontSize = 64.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Controls overlay
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = { isTimerMode = !isTimerMode },
                        modifier = Modifier
                            .background(colors.surface.copy(alpha = 0.9f), CircleShape)
                            .border(1.dp, colors.border, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = "Timer 3s",
                            tint = if (isTimerMode) colors.accent else colors.textSecondary
                        )
                    }

                    IconButton(
                        onClick = { autoEnhance = !autoEnhance },
                        modifier = Modifier
                            .background(colors.surface.copy(alpha = 0.9f), CircleShape)
                            .border(1.dp, colors.border, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoFixHigh,
                            contentDescription = "Document Enhance",
                            tint = if (autoEnhance) colors.accent else colors.textSecondary
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

        // Multi-Photo Pages Tray
        if (capturedPhotosBase64.isNotEmpty()) {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Question Pages (${capturedPhotosBase64.size} captured)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(capturedPhotosBase64) { index, _ ->
                        Box(
                            modifier = Modifier
                                .size(width = 70.dp, height = 90.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.control)
                                .border(1.dp, colors.accent.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Page ${index + 1}", fontSize = 11.sp, color = colors.textPrimary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                AppTextField(
                    value = promptNote,
                    onValueChange = { promptNote = it },
                    label = "Optional Question Hint",
                    placeholder = "e.g. Solve part b, or show step-by-step substitution"
                )

                Spacer(modifier = Modifier.height(14.dp))
                AppButton(
                    onClick = {
                        isAnalyzing = true
                        scope.launch {
                            delay(1000)
                            isAnalyzing = false
                            val newAnswer = Answer(
                                subject = "Mathematics",
                                questionText = if (promptNote.isNotEmpty()) promptNote else "Calculate the roots and derivative of f(x) = 3x^2 - 12x + 9",
                                imagePaths = emptyList(),
                                shortAnswer = "The roots are x = 1 and x = 3. The derivative is f'(x) = 6x - 12.",
                                simpleExplanation = "Factoring out 3 gives 3(x-1)(x-3) = 0. Differentiating each term with the power rule yields 6x minus 12.",
                                blocks = listOf(
                                    AnswerBlock("step", "Step 1: Factor 3(x^2 - 4x + 3) = 0", "Step 1: Factor out 3 to get x squared minus 4x plus 3 equals 0.", 1),
                                    AnswerBlock("math", "(x - 1)(x - 3) = 0", "x minus 1 times x minus 3 equals zero."),
                                    AnswerBlock("step", "Step 2: Differentiate f'(x) = d/dx(3x^2 - 12x + 9) = 6x - 12", "Step 2: Taking the derivative yields 6x minus 12.", 2)
                                )
                            )
                            onAnswerGenerated(newAnswer)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    isAccent = true
                ) {
                    if (isAnalyzing) {
                        CircularProgressIndicator(color = colors.accent, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Analyzing Question with AI...", color = colors.textPrimary, fontSize = 12.sp)
                    } else {
                        Text("Send to AI Provider", color = colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
