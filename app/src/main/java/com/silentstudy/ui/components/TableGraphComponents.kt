package com.silentstudy.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silentstudy.data.model.GraphData
import com.silentstudy.data.model.TableData
import com.silentstudy.ui.theme.LocalAppColors
import kotlin.math.max
import kotlin.math.min

/**
 * Scrollable Dark Grid Table Component (Section 5)
 * Displays structured extracted tables with highlighted active rows and reading controls.
 */
@Composable
fun TableView(
    table: TableData,
    activeRowIndex: Int = -1,
    onReadRow: ((Int) -> Unit)? = null,
    onReadTable: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = LocalAppColors.current

    AppCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.TableChart,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = table.title.ifEmpty { "Data Table" },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            }

            if (onReadTable != null) {
                AppButton(
                    onClick = onReadTable,
                    isAccent = true
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = colors.accent, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Read Table", fontSize = 10.sp, color = colors.accent, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Horizontal scroll container for table grid
        val horizontalScrollState = rememberScrollState()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, colors.border, RoundedCornerShape(8.dp))
                .horizontalScroll(horizontalScrollState)
        ) {
            Column {
                // Table Header Row
                Row(
                    modifier = Modifier
                        .background(colors.control)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    table.headers.forEach { header ->
                        Box(
                            modifier = Modifier
                                .widthIn(min = 90.dp)
                                .padding(end = 16.dp)
                        ) {
                            Text(
                                text = header,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.accent,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                // Table Data Rows
                table.rows.forEachIndexed { rIdx, row ->
                    val isActive = rIdx == activeRowIndex
                    val rowBg = if (isActive) {
                        colors.accent.copy(alpha = 0.20f)
                    } else if (rIdx % 2 == 0) {
                        colors.surface
                    } else {
                        colors.background
                    }

                    Row(
                        modifier = Modifier
                            .background(rowBg)
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        row.forEachIndexed { cIdx, cell ->
                            Box(
                                modifier = Modifier
                                    .widthIn(min = 90.dp)
                                    .padding(end = 16.dp)
                            ) {
                                Text(
                                    text = cell,
                                    fontSize = 11.sp,
                                    color = if (isActive) colors.textPrimary else colors.textSecondary,
                                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Pure Compose Canvas Chart & Data Table Renderer (Section 5)
 * Renders line charts, bar charts, and scatter graphs with dark theme styling,
 * axis labels, key points, trend summaries, and data table.
 */
@Composable
fun GraphChartView(
    graph: GraphData,
    modifier: Modifier = Modifier
) {
    val colors = LocalAppColors.current

    AppCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = graph.title.ifEmpty { "${graph.type.uppercase()} Chart" },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.accent
                )
                if (graph.trend.isNotBlank()) {
                    Text(
                        text = "Trend: ${graph.trend}",
                        fontSize = 10.sp,
                        color = colors.textSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Canvas Chart Drawing Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.control)
                .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height

                val paddingLeft = 35f
                val paddingBottom = 25f
                val chartWidth = width - paddingLeft
                val chartHeight = height - paddingBottom

                // 1. Draw X & Y Axes
                drawLine(
                    color = Color(0xFF2E333D),
                    start = Offset(paddingLeft, 0f),
                    end = Offset(paddingLeft, chartHeight),
                    strokeWidth = 2f
                )
                drawLine(
                    color = Color(0xFF2E333D),
                    start = Offset(paddingLeft, chartHeight),
                    end = Offset(width, chartHeight),
                    strokeWidth = 2f
                )

                // Gather all points across series
                val allPoints = graph.series.flatMap { it.points }
                if (allPoints.isNotEmpty()) {
                    val minX = allPoints.minOf { it.x }
                    val maxX = max(allPoints.maxOf { it.x }, minX + 0.001)
                    val minY = min(0.0, allPoints.minOf { it.y })
                    val maxY = max(allPoints.maxOf { it.y }, minY + 0.001)

                    val rangeX = maxX - minX
                    val rangeY = maxY - minY

                    val seriesColors = listOf(
                        Color(0xFF5B8DEF),
                        Color(0xFF81C784),
                        Color(0xFFFFB74D),
                        Color(0xFFBA68C8)
                    )

                    graph.series.forEachIndexed { sIdx, series ->
                        val strokeColor = seriesColors[sIdx % seriesColors.size]
                        val path = Path()

                        if (graph.type.lowercase() == "bar") {
                            val barWidth = (chartWidth / series.points.size.coerceAtLeast(1)) * 0.6f
                            series.points.forEachIndexed { pIdx, pt ->
                                val normX = ((pt.x - minX) / rangeX).toFloat()
                                val normY = ((pt.y - minY) / rangeY).toFloat()
                                val cx = paddingLeft + (pIdx + 0.5f) * (chartWidth / series.points.size)
                                val cy = chartHeight - (normY * chartHeight * 0.9f)
                                val barHeight = chartHeight - cy

                                drawRect(
                                    color = strokeColor.copy(alpha = 0.85f),
                                    topLeft = Offset(cx - barWidth / 2f, cy),
                                    size = Size(barWidth, barHeight)
                                )
                            }
                        } else {
                            // Line / Scatter chart
                            series.points.forEachIndexed { pIdx, pt ->
                                val normX = ((pt.x - minX) / rangeX).toFloat().coerceIn(0f, 1f)
                                val normY = ((pt.y - minY) / rangeY).toFloat().coerceIn(0f, 1f)
                                val px = paddingLeft + (normX * (chartWidth - 20f)) + 10f
                                val py = chartHeight - (normY * (chartHeight - 20f)) - 10f

                                if (pIdx == 0) {
                                    path.moveTo(px, py)
                                } else {
                                    path.lineTo(px, py)
                                }

                                // Dot marker
                                drawCircle(
                                    color = strokeColor,
                                    radius = 4.dp.toPx(),
                                    center = Offset(px, py)
                                )
                            }

                            if (graph.type.lowercase() != "scatter") {
                                drawPath(
                                    path = path,
                                    color = strokeColor,
                                    style = Stroke(width = 2.5f)
                                )
                            }
                        }
                    }
                }
            }

            // Axis labels overlay
            if (graph.xAxis != null || graph.yAxis != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomEnd),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${graph.yAxis?.label ?: ""} ${if (graph.yAxis?.unit?.isNotBlank() == true) "(${graph.yAxis?.unit})" else ""}",
                        fontSize = 9.sp,
                        color = colors.textSecondary
                    )
                    Text(
                        text = "${graph.xAxis?.label ?: ""} ${if (graph.xAxis?.unit?.isNotBlank() == true) "(${graph.xAxis?.unit})" else ""}",
                        fontSize = 9.sp,
                        color = colors.textSecondary
                    )
                }
            }
        }

        // Key Points list
        if (graph.keyPoints.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Key Data Points:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.accent)
                graph.keyPoints.forEach { pt ->
                    Text("• $pt", fontSize = 10.sp, color = colors.textSecondary)
                }
            }
        }

        // Data Table Underneath Chart
        val allPoints = graph.series.flatMap { it.points }
        if (allPoints.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text("Data Table:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
            Spacer(modifier = Modifier.height(4.dp))

            val scrollState = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(colors.control)
                    .horizontalScroll(scrollState)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                allPoints.take(8).forEach { pt ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = pt.label ?: String.format("%.1f", pt.x),
                            fontSize = 9.sp,
                            color = colors.accent,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = String.format("%.2f", pt.y),
                            fontSize = 10.sp,
                            color = colors.textPrimary
                        )
                    }
                }
            }
        }

        // Approximation warning
        if (graph.isEstimated) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(colors.control)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Values read from the image may be approximate",
                    fontSize = 9.sp,
                    color = colors.textSecondary
                )
            }
        }
    }
}
