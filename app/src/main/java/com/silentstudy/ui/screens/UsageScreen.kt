package com.silentstudy.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silentstudy.SilentStudyApp
import com.silentstudy.data.model.ApiUsageLog
import com.silentstudy.data.repository.BudgetSettings
import com.silentstudy.ui.components.AppButton
import com.silentstudy.ui.components.AppCard
import com.silentstudy.ui.components.AppTextField
import com.silentstudy.ui.components.AppToggle
import com.silentstudy.ui.theme.LocalAppColors
import com.silentstudy.util.TokenCostManager
import com.silentstudy.util.UsageStats
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun UsageScreen(
    app: SilentStudyApp,
    budgetSettings: BudgetSettings,
    cheapMode: Boolean,
    onToggleCheapMode: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val tokenManager = remember { TokenCostManager(app.database.usageDao()) }

    var stats by remember { mutableStateOf(UsageStats(0L, 0.0, 0L, 0.0, 0L, 0.0)) }
    val logs by app.database.usageDao().getAllLogs().collectAsState(initial = emptyList())

    // Editable form state for budget caps
    var dailyTokensInput by remember { mutableStateOf(budgetSettings.dailyTokenCap.toString()) }
    var monthlyTokensInput by remember { mutableStateOf(budgetSettings.monthlyTokenCap.toString()) }
    var dailyCostInput by remember { mutableStateOf(budgetSettings.dailyCostCapUsd.toString()) }
    var monthlyCostInput by remember { mutableStateOf(budgetSettings.monthlyCostCapUsd.toString()) }
    var priceInput by remember { mutableStateOf(budgetSettings.pricePerMInputUsd.toString()) }
    var priceOutput by remember { mutableStateOf(budgetSettings.pricePerMOutputUsd.toString()) }

    var saveNotice by remember { mutableStateOf<String?>(null) }
    val dateFormat = remember { SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()) }

    LaunchedEffect(Unit) {
        stats = tokenManager.getUsageStats()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        item {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Token & Cost Protection",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "Real API response token telemetry & budget caps",
                            fontSize = 11.sp,
                            color = colors.textSecondary
                        )
                    }
                    AppButton(onClick = onBack) {
                        Text("Back", fontSize = 11.sp, color = colors.accent)
                    }
                }
            }
        }

        // Cheap Mode Banner
        item {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                AppToggle(
                    checked = cheapMode,
                    onCheckedChange = { onToggleCheapMode(it) },
                    label = "Cheap Mode (Cost Optimizer)",
                    sublabel = "Automatically picks the cheapest vision model and lowers max output limits."
                )
            }
        }

        // Totals Card: Today, This Week, This Month
        item {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Usage Totals",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.accent
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Text("TODAY", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = colors.textSecondary)
                        Text("${stats.todayTokens}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                        Text("$${String.format("%.4f", stats.todayCost)}", fontSize = 11.sp, color = colors.accent)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Text("THIS WEEK", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = colors.textSecondary)
                        Text("${stats.weekTokens}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                        Text("$${String.format("%.4f", stats.weekCost)}", fontSize = 11.sp, color = colors.accent)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Text("THIS MONTH", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = colors.textSecondary)
                        Text("${stats.monthTokens}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                        Text("$${String.format("%.4f", stats.monthCost)}", fontSize = 11.sp, color = colors.accent)
                    }
                }
            }
        }

        // Budget Protection Caps Form
        item {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Budget Protection Caps",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.accent
                )
                Text(
                    text = "Warns at 80% and strictly blocks new AI requests at 100%. Set to 0 to disable cap.",
                    fontSize = 11.sp,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        AppTextField(
                            value = dailyTokensInput,
                            onValueChange = { dailyTokensInput = it },
                            label = "Daily Token Cap",
                            placeholder = "0 (unlimited)"
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        AppTextField(
                            value = monthlyTokensInput,
                            onValueChange = { monthlyTokensInput = it },
                            label = "Monthly Token Cap",
                            placeholder = "0 (unlimited)"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        AppTextField(
                            value = dailyCostInput,
                            onValueChange = { dailyCostInput = it },
                            label = "Daily Cost Cap ($)",
                            placeholder = "0.0"
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        AppTextField(
                            value = monthlyCostInput,
                            onValueChange = { monthlyCostInput = it },
                            label = "Monthly Cost Cap ($)",
                            placeholder = "0.0"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        AppTextField(
                            value = priceInput,
                            onValueChange = { priceInput = it },
                            label = "Price / 1M Input ($)",
                            placeholder = "0.15"
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        AppTextField(
                            value = priceOutput,
                            onValueChange = { priceOutput = it },
                            label = "Price / 1M Output ($)",
                            placeholder = "0.60"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                AppButton(
                    onClick = {
                        scope.launch {
                            app.preferencesRepository.saveBudgetCaps(
                                dailyTokens = dailyTokensInput.toLongOrNull() ?: 0L,
                                monthlyTokens = monthlyTokensInput.toLongOrNull() ?: 0L,
                                dailyCost = dailyCostInput.toDoubleOrNull() ?: 0.0,
                                monthlyCost = monthlyCostInput.toDoubleOrNull() ?: 0.0,
                                priceInput = priceInput.toDoubleOrNull() ?: 0.15,
                                priceOutput = priceOutput.toDoubleOrNull() ?: 0.60
                            )
                            saveNotice = "Budget caps saved successfully."
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    isAccent = true
                ) {
                    Text("Save Budget Caps", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.accent)
                }

                if (saveNotice != null) {
                    Text(
                        text = saveNotice!!,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.accent,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }

        // Request Telemetry Log
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Request Log (${logs.size})",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                if (logs.isNotEmpty()) {
                    AppButton(
                        onClick = {
                            scope.launch {
                                app.database.usageDao().clearLogs()
                                stats = tokenManager.getUsageStats()
                            }
                        }
                    ) {
                        Text("Clear Log", fontSize = 10.sp, color = colors.textSecondary)
                    }
                }
            }
        }

        if (logs.isEmpty()) {
            item {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "No API requests logged yet. As you capture questions, real token counts and costs will appear here.",
                        fontSize = 11.sp,
                        color = colors.textSecondary
                    )
                }
            }
        } else {
            items(logs.take(30), key = { it.id }) { log ->
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "${log.provider.uppercase()} · ${log.model}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                            Text(
                                text = dateFormat.format(Date(log.timestamp)),
                                fontSize = 10.sp,
                                color = colors.textSecondary
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${log.totalTokens} tokens",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = colors.accent
                            )
                            Text(
                                text = "In: ${log.promptTokens} · Out: ${log.completionTokens} · $${String.format("%.4f", log.estimatedCostUsd)}",
                                fontSize = 10.sp,
                                color = colors.textSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}
