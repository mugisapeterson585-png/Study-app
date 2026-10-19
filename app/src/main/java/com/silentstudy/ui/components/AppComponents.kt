package com.silentstudy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silentstudy.ui.theme.LocalAppColors

/**
 * Flat Dark Low-Glare UI Components (Zero Glare, Zero White)
 * Solid dark surfaces with subtle 1dp borders (#2E333D) and clean Material design.
 */

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    borderColor: Color? = null,
    backgroundColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LocalAppColors.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(backgroundColor ?: colors.surface)
            .border(1.dp, borderColor ?: colors.border, RoundedCornerShape(cornerRadius))
            .padding(16.dp)
    ) {
        Column(content = content)
    }
}

@Composable
fun AppButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isAccent: Boolean = false,
    cornerRadius: Dp = 14.dp,
    content: @Composable RowScope.() -> Unit
) {
    val colors = LocalAppColors.current
    val interactionSource = remember { MutableInteractionSource() }

    val bg = if (isAccent) colors.accent.copy(alpha = 0.25f) else colors.control
    val borderCol = if (isAccent) colors.accent.copy(alpha = 0.6f) else colors.border

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(cornerRadius))
            .background(bg)
            .border(1.dp, borderCol, RoundedCornerShape(cornerRadius))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
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
fun AppIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isAccent: Boolean = false,
    content: @Composable () -> Unit
) {
    val colors = LocalAppColors.current
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (isAccent) colors.accent.copy(alpha = 0.25f) else colors.control)
            .border(1.dp, if (isAccent) colors.accent.copy(alpha = 0.6f) else colors.border, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
fun AppToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    sublabel: String? = null
) {
    val colors = LocalAppColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 13.sp,
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
                .width(48.dp)
                .height(26.dp)
                .clip(CircleShape)
                .background(if (checked) colors.accent.copy(alpha = 0.35f) else colors.control)
                .border(1.dp, if (checked) colors.accent.copy(alpha = 0.6f) else colors.border, CircleShape)
                .padding(3.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(if (checked) colors.accent else colors.textSecondary)
            )
        }
    }
}

@Composable
fun AppSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    label: String,
    valueDisplay: String,
    modifier: Modifier = Modifier
) {
    val colors = LocalAppColors.current
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 6.dp)) {
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
                inactiveTrackColor = colors.border
            )
        )
    }
}

@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = ""
) {
    val colors = LocalAppColors.current
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        if (label.isNotEmpty()) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, color = colors.textSecondary, fontSize = 13.sp) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = colors.control,
                unfocusedContainerColor = colors.control,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary,
                focusedBorderColor = colors.accent,
                unfocusedBorderColor = colors.border,
                cursorColor = colors.accent
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
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Earbuds real status badge
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surface)
                .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = if (isEarbudsConnected) Icons.Default.Headphones else Icons.Default.VolumeOff,
                contentDescription = "Earbuds Status",
                tint = if (isEarbudsConnected) colors.accent else Color(0xFFD06A6A),
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (isEarbudsConnected) (deviceName ?: "Earbuds Connected") else "Speaker Guard (Silent)",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary
            )
        }

        // Desk mode button
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surface)
                .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                .clickable(onClick = onDeskModeClick)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Nightlight,
                    contentDescription = "Desk Mode",
                    tint = colors.accent,
                    modifier = Modifier.size(13.dp)
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

@Composable
fun AfterAnswerMenu(
    countdownSeconds: Int,
    onRepeat: () -> Unit,
    onNext: () -> Unit,
    onSimpler: () -> Unit
) {
    val colors = LocalAppColors.current
    AppCard(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "After-Answer Menu (${countdownSeconds}s)",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = colors.accent
            )
            Text(
                text = "Knock desk or tap",
                fontSize = 10.sp,
                color = colors.textSecondary
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AppButton(onClick = onRepeat, modifier = Modifier.weight(1f)) {
                Text("1 Tap: Repeat", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
            }
            AppButton(onClick = onNext, modifier = Modifier.weight(1f), isAccent = true) {
                Text("2 Taps: Next", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.accent)
            }
            AppButton(onClick = onSimpler, modifier = Modifier.weight(1f)) {
                Text("3 Taps: Simpler", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.textSecondary)
            }
        }
    }
}
