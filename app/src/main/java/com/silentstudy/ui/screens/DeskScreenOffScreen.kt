package com.silentstudy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DeskScreenOffScreen(
    currentAnswerSummary: String?,
    onWake: () -> Unit
) {
    var currentTime by remember { mutableStateOf("") }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = timeFormat.format(Date())
            delay(1000)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0C0E))
            .clickable(onClick = onWake)
            .padding(24.dp)
    ) {
        // Minimal Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Headphones,
                    contentDescription = null,
                    tint = Color(0xFF6EA8FE).copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "DESK SCREEN-OFF MODE",
                    color = Color(0xFF6A7280),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            IconButton(onClick = onWake) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Wake Screen",
                    tint = Color(0xFF6A7280)
                )
            }
        }

        // Center Minimal OLED Clock
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = currentTime,
                color = Color(0xFF4A5260),
                fontSize = 72.sp,
                fontWeight = FontWeight.ExtraLight,
                fontFamily = FontFamily.Monospace
            )

            if (currentAnswerSummary != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = currentAnswerSummary,
                    color = Color(0xFF5A6270),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Knock on desk: 1 = Play · 2 = Next · 3 = Prev · 4 = Wake",
                color = Color(0xFF3A4250),
                fontSize = 10.sp
            )
        }
    }
}
