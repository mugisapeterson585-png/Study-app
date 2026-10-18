package com.silentstudy.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silentstudy.data.model.Answer
import com.silentstudy.ui.components.AppButton
import com.silentstudy.ui.components.AppCard
import com.silentstudy.ui.components.AppTextField
import com.silentstudy.ui.theme.LocalAppColors

@Composable
fun AnswersScreen(
    answers: List<Answer>,
    onPlayAnswer: (Answer) -> Unit,
    onToggleStar: (Long) -> Unit,
    onDeleteAnswer: (Answer) -> Unit
) {
    val colors = LocalAppColors.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedSubject by remember { mutableStateOf("All") }
    var starredOnly by remember { mutableStateOf(false) }

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
            items(filtered, key = { it.id }) { answer ->
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
                            IconButton(onClick = { onDeleteAnswer(answer) }) {
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
                        maxLines = 2
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = answer.shortAnswer,
                        fontSize = 12.sp,
                        color = colors.textSecondary,
                        maxLines = 3
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${answer.blocks.size} proof blocks",
                            fontSize = 10.sp,
                            color = colors.textSecondary
                        )

                        AppButton(
                            onClick = { onPlayAnswer(answer) },
                            isAccent = true,
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = colors.textPrimary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Listen in Earbuds", fontSize = 11.sp, color = colors.textPrimary)
                        }
                    }
                }
            }
        }
    }
}
