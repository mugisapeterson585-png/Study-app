package com.silentstudy.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silentstudy.ui.theme.LocalNeuColors

/**
 * Reusable Compose Neumorphism Modifier (Section 1)
 * Compatible with minSdk 24 (API 24+).
 * Dual shadow approach: light top-left, dark bottom-right.
 */
fun Modifier.neumorphic(
    isPressed: Boolean = false,
    cornerRadius: Dp = 20.dp,
    elevation: Dp = 6.dp,
    lightShadowColor: Color? = null,
    darkShadowColor: Color? = null
): Modifier = this.then(
    Modifier.drawBehind {
        val lightColor = lightShadowColor ?: Color(0xFF2A2E36)
        val darkColor = darkShadowColor ?: Color(0xFF14161A)

        drawIntoCanvas { canvas ->
            if (!isPressed) {
                // Top-Left Light Shadow
                val lightPaint = Paint().apply {
                    color = lightColor
                    isAntiAlias = true
                }
                canvas.drawRoundRect(
                    left = -elevation.toPx() * 0.7f,
                    top = -elevation.toPx() * 0.7f,
                    right = size.width - elevation.toPx() * 0.3f,
                    bottom = size.height - elevation.toPx() * 0.3f,
                    radiusX = cornerRadius.toPx(),
                    radiusY = cornerRadius.toPx(),
                    paint = lightPaint
                )

                // Bottom-Right Dark Shadow
                val darkPaint = Paint().apply {
                    color = darkColor
                    isAntiAlias = true
                }
                canvas.drawRoundRect(
                    left = elevation.toPx() * 0.3f,
                    top = elevation.toPx() * 0.3f,
                    right = size.width + elevation.toPx() * 0.7f,
                    bottom = size.height + elevation.toPx() * 0.7f,
                    radiusX = cornerRadius.toPx(),
                    radiusY = cornerRadius.toPx(),
                    paint = darkPaint
                )
            }
        }
    }
)

@Composable
fun NeuCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    elevation: Dp = 5.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LocalNeuColors.current
    Box(
        modifier = modifier
            .neumorphic(
                isPressed = false,
                cornerRadius = cornerRadius,
                elevation = elevation,
                lightShadowColor = colors.lightShadow,
                darkShadowColor = colors.darkShadow
            )
            .background(colors.surface, RoundedCornerShape(cornerRadius))
            .padding(16.dp)
    ) {
        Column(content = content)
    }
}

@Composable
fun NeuButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isAccent: Boolean = false,
    cornerRadius: Dp = 20.dp,
    content: @Composable RowScope.() -> Unit
) {
    val colors = LocalNeuColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val currentElevation by animateDpAsState(
        targetValue = if (isPressed) 1.dp else 5.dp,
        animationSpec = tween(durationMillis = 150),
        label = "button_elevation"
    )

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 52.dp)
            .neumorphic(
                isPressed = isPressed,
                cornerRadius = cornerRadius,
                elevation = currentElevation,
                lightShadowColor = colors.lightShadow,
                darkShadowColor = colors.darkShadow
            )
            .background(
                color = if (isAccent) colors.accent.copy(alpha = 0.25f) else colors.surface,
                shape = RoundedCornerShape(cornerRadius)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

@Composable
fun NeuToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    sublabel: String? = null
) {
    val colors = LocalNeuColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary
            )
            if (sublabel != null) {
                Text(
                    text = sublabel,
                    fontSize = 11.sp,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .width(52.dp)
                .height(30.dp)
                .background(
                    if (checked) colors.accent.copy(alpha = 0.35f) else colors.darkShadow.copy(alpha = 0.5f),
                    CircleShape
                )
                .padding(4.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .background(if (checked) colors.accent else colors.textSecondary, CircleShape)
            )
        }
    }
}

@Composable
fun NeuSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    label: String,
    valueDisplay: String,
    modifier: Modifier = Modifier
) {
    val colors = LocalNeuColors.current
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
            Text(text = valueDisplay, fontSize = 12.sp, color = colors.accent, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = colors.accent,
                activeTrackColor = colors.accent,
                inactiveTrackColor = colors.darkShadow
            )
        )
    }
}

@Composable
fun NeuTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = ""
) {
    val colors = LocalNeuColors.current
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.textPrimary,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, color = colors.textSecondary, fontSize = 13.sp) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = colors.surface,
                unfocusedContainerColor = colors.surface,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary,
                focusedBorderColor = colors.accent,
                unfocusedBorderColor = colors.darkShadow
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun StatusPill(
    isEarbudsConnected: Boolean,
    deviceName: String?,
    isSensorActive: Boolean,
    sessionDurationSeconds: Long,
    onDeskModeClick: () -> Unit
) {
    val colors = LocalNeuColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Earbuds real status badge
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(colors.surface, RoundedCornerShape(16.dp))
                .neumorphic(cornerRadius = 16.dp, elevation = 3.dp, lightShadowColor = colors.lightShadow, darkShadowColor = colors.darkShadow)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = if (isEarbudsConnected) Icons.Default.Headphones else Icons.Default.VolumeOff,
                contentDescription = "Earbuds Status",
                tint = if (isEarbudsConnected) colors.accent else Color(0xFFF59E0B),
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (isEarbudsConnected) (deviceName ?: "Earbuds Active") else "Speaker Guard (No Audio)",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary
            )
        }

        // Desk mode button & credits
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onDeskModeClick)
                    .background(colors.surface)
                    .neumorphic(cornerRadius = 16.dp, elevation = 3.dp, lightShadowColor = colors.lightShadow, darkShadowColor = colors.darkShadow)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Nightlight,
                        contentDescription = "Desk Mode",
                        tint = colors.accent,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Desk Mode",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                }
            }
        }
    }
}

@Composable
fun AfterAnswerMenu(
    countdownSeconds: Int,
    onRepeat: () -> Unit,
    onNext: () -> Unit,
    onSimpler: () -> Unit
) {
    val colors = LocalNeuColors.current
    NeuCard(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "After-Answer Menu ($countdownSeconds s)",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = colors.accent
            )
            Text(
                text = "Knock desk or tap button",
                fontSize = 10.sp,
                color = colors.textSecondary
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NeuButton(onClick = onRepeat, modifier = Modifier.weight(1f)) {
                Text("1 Tap: Repeat", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
            }
            NeuButton(onClick = onNext, modifier = Modifier.weight(1f)) {
                Text("2 Taps: Next", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.accent)
            }
            NeuButton(onClick = onSimpler, modifier = Modifier.weight(1f)) {
                Text("3 Taps: Simpler", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.textSecondary)
            }
        }
    }
}
