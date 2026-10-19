package com.silentstudy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silentstudy.data.model.Answer
import com.silentstudy.ui.components.AppButton
import com.silentstudy.ui.components.AppCard
import com.silentstudy.ui.components.AppTextField
import com.silentstudy.ui.theme.LocalAppColors
import kotlinx.coroutines.launch

@Composable
fun AnswersScreen(
    answers: List<Answer>,
    onPlayAnswer: (Answer) -> Unit,
    onToggleStar: (Long) -> Unit,
    onDeleteAnswer: (Answer) -> Unit,
    onRestoreAnswer: (Answer) -> Unit,
    onRetryAnswer: (Answer) -> Unit,
    onGenerateSimpleExplanation: (Answer) -> Unit
) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var selectedSubject by remember { mutableStateOf("All") }
    var starredOnly by remember { mutableStateOf(false) }

    // Deletion confirmation & undo state
    var answerPendingDelete by remember { mutableStateOf<Answer?>(null) }
    var recentlyDeletedAnswer by remember { mutableStateOf<Answer?>(null) }
    var showUndoSnackbar by remember { mutableStateOf(false) }

    // Expanded card for lazy generation details
    var expandedAnswerId by remember { mutableStateOf<Long?>(null) }

    val subjects = listOf("All", "Mathematics", "Physics", "Chemistry", "Biology", "General")

    val filtered = answers.filter { item ->
        val matchesSubject = selectedSubject == "All" || item.subject.equals(selectedSubject, ignoreCase = true)
        val matchesStarred = !starredOnly || item.starred
        val matchesQuery = searchQuery.isEmpty() ||
                item.questionText.contains(searchQuery, ignoreCase = true) ||
                item.shortAnswer.contains(searchQuery, ignoreCase = true) ||
                item.id.toString() == searchQuery.removePrefix("#")
        matchesSubject && matchesStarred && matchesQuery
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Search & Star filter
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    AppTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = "",
                        placeholder = "Search #number, equations, concepts..."
                    )
                }
                IconButton(onClick = { starredOnly = !starredOnly }) {
                    Icon(
                        imageVector = if (starredOnly) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Starred filter",
                        tint = if (starredOnly) colors.accent else colors.textSecondary
                    )
                }
            }

            // Subject Horizontal Filter
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(subjects) { subject ->
                    val isSelected = selectedSubject == subject
                    Box(
                        modifier = Modifier
                            .clickable { selectedSubject = subject }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = subject,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) colors.accent else colors.textSecondary
                        )
                    }
                }
            }

            // Answers List
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (filtered.isEmpty()) {
                    item {
                        AppCard(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "No answers found. Capture a question with the camera to solve.",
                                fontSize = 12.sp,
                                color = colors.textSecondary
                            )
                        }
                    }
                }

                items(filtered, key = { it.id }) { answer ->
                    val isExpanded = expandedAnswerId == answer.id

                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "#${answer.id}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.accent
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = answer.subject,
                                    fontSize = 12.sp,
                                    color = colors.textSecondary
                                )

                                // Offline or Failed Badge
                                if (answer.status == "pending_offline") {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(colors.accent.copy(alpha = 0.2f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("Offline Sync Queued", fontSize = 9.sp, color = colors.accent, fontWeight = FontWeight.Bold)
                                    }
                                } else if (answer.status == "failed") {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(colors.error.copy(alpha = 0.2f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("Failed", fontSize = 9.sp, color = colors.error, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { onToggleStar(answer.id) }) {
                                    Icon(
                                        imageVector = if (answer.starred) Icons.Default.Star else Icons.Default.StarBorder,
                                        contentDescription = "Star",
                                        tint = if (answer.starred) colors.accent else colors.textSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(onClick = { answerPendingDelete = answer }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = colors.textSecondary.copy(alpha = 0.6f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = answer.questionText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary,
                            maxLines = if (isExpanded) 10 else 2
                        )

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = answer.shortAnswer,
                            fontSize = 12.sp,
                            color = colors.textSecondary,
                            maxLines = if (isExpanded) 15 else 3
                        )

                        // Failure retry row
                        if (answer.status == "failed") {
                            Spacer(modifier = Modifier.height(8.dp))
                            answer.errorMessage?.let { err ->
                                Text(
                                    text = "Error: $err",
                                    fontSize = 11.sp,
                                    color = colors.error
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            AppButton(
                                onClick = { onRetryAnswer(answer) },
                                isAccent = true
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = colors.accent, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Retry Question Analysis", fontSize = 11.sp, color = colors.accent)
                            }
                        }

                        // Lazy simple explanation expansion
                        if (isExpanded) {
                            Spacer(modifier = Modifier.height(10.dp))
                            if (answer.simpleExplanation.isNotEmpty() && !answer.simpleExplanation.contains("(Generated on demand)")) {
                                Text(
                                    text = "Simple Explanation:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.accent
                                )
                                Text(
                                    text = answer.simpleExplanation,
                                    fontSize = 11.sp,
                                    color = colors.textPrimary,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            } else {
                                AppButton(
                                    onClick = { onGenerateSimpleExplanation(answer) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Lightbulb, contentDescription = null, tint = colors.accent, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Generate Simple Explanation (On Demand)", fontSize = 11.sp, color = colors.textPrimary)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppButton(
                                onClick = { expandedAnswerId = if (isExpanded) null else answer.id }
                            ) {
                                Text(if (isExpanded) "Show Less" else "${answer.blocks.size} steps · Details", fontSize = 10.sp, color = colors.textSecondary)
                            }

                            AppButton(
                                onClick = { onPlayAnswer(answer) },
                                isAccent = true,
                                modifier = Modifier.height(36.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = colors.accent, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Listen in Earbuds", fontSize = 11.sp, color = colors.accent, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Delete Confirmation Dialog
        answerPendingDelete?.let { ans ->
            AlertDialog(
                onDismissRequest = { answerPendingDelete = null },
                containerColor = colors.surface,
                titleColor = colors.textPrimary,
                textContentColor = colors.textSecondary,
                title = { Text("Delete Answer #${ans.id}?", fontSize = 14.sp, fontWeight = FontWeight.Bold) },
                text = { Text("Are you sure you want to delete this answer? You can undo immediately after.", fontSize = 12.sp) },
                confirmButton = {
                    AppButton(
                        onClick = {
                            recentlyDeletedAnswer = ans
                            onDeleteAnswer(ans)
                            answerPendingDelete = null
                            showUndoSnackbar = true
                        },
                        isAccent = true
                    ) {
                        Text("Delete", fontSize = 11.sp, color = colors.error)
                    }
                },
                dismissButton = {
                    AppButton(onClick = { answerPendingDelete = null }) {
                        Text("Cancel", fontSize = 11.sp, color = colors.textSecondary)
                    }
                }
            )
        }

        // Undo Snackbar
        if (showUndoSnackbar && recentlyDeletedAnswer != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.control)
                    .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Answer #${recentlyDeletedAnswer!!.id} deleted.",
                        fontSize = 12.sp,
                        color = colors.textPrimary
                    )
                    Row {
                        AppButton(
                            onClick = {
                                recentlyDeletedAnswer?.let { onRestoreAnswer(it) }
                                showUndoSnackbar = false
                                recentlyDeletedAnswer = null
                            },
                            isAccent = true
                        ) {
                            Text("Undo", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.accent)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(onClick = { showUndoSnackbar = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = colors.textSecondary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}
