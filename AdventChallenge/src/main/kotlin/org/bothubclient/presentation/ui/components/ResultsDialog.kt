package org.bothubclient.presentation.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.bothubclient.domain.entity.BackgroundJob
import org.bothubclient.domain.entity.BoredReportItem
import org.bothubclient.domain.entity.JobStatus
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ResultsDialog(
    jobs: List<BackgroundJob>,
    reports: List<BoredReportItem>,
    onToggleJob: (String, Boolean) -> Unit,
    onRunJobNow: (String) -> Unit,
    onUpdateInterval: (String, Int) -> Unit,
    onClose: () -> Unit
) {
    Dialog(onDismissRequest = onClose) {
        Surface(
            modifier = Modifier.width(700.dp).height(550.dp),
            shape = RoundedCornerShape(12.dp),
            elevation = 8.dp
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Результаты", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Закрыть")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                var selectedTab by remember { mutableStateOf(0) }
                val tabs = listOf("Отчеты", "Фоновые процессы")

                TabRow(selectedTabIndex = selectedTab) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                when (selectedTab) {
                    0 -> ReportsTab(reports)
                    1 -> ProcessesTab(jobs, onToggleJob, onRunJobNow, onUpdateInterval)
                }
            }
        }
    }
}

@Composable
private fun ReportsTab(reports: List<BoredReportItem>) {
    if (reports.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Нет отчетов", color = Color.Gray, fontSize = 14.sp)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(reports) { report ->
            ReportCard(report)
        }
    }
}

@Composable
private fun ReportCard(report: BoredReportItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = 2.dp,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = report.activity,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = report.llmSummary,
                fontSize = 13.sp,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatTimestamp(report.createdAtEpochMs),
                fontSize = 11.sp,
                color = Color.Gray
            )
        }
    }
}

@Composable
private fun ProcessesTab(
    jobs: List<BackgroundJob>,
    onToggleJob: (String, Boolean) -> Unit,
    onRunJobNow: (String) -> Unit,
    onUpdateInterval: (String, Int) -> Unit
) {
    if (jobs.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Нет фоновых задач", color = Color.Gray, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Напишите в чат, например: \"присылай раз в 5 минут чем заняться\"",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(jobs) { job ->
            JobCard(job, onToggleJob, onRunJobNow, onUpdateInterval)
        }
    }
}

@Composable
private fun JobCard(
    job: BackgroundJob,
    onToggleJob: (String, Boolean) -> Unit,
    onRunJobNow: (String) -> Unit,
    onUpdateInterval: (String, Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = 2.dp,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Bored Report",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    StatusChip(job.status)
                }
                Switch(
                    checked = job.enabled,
                    onCheckedChange = { onToggleJob(job.id, it) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val presets = listOf(1, 5, 15, 60)
                presets.forEach { minutes ->
                    val label = if (minutes == 60) "1ч" else "${minutes}м"
                    OutlinedButton(
                        onClick = { onUpdateInterval(job.id, minutes) },
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        colors = if (job.intervalMinutes == minutes)
                            ButtonDefaults.outlinedButtonColors(
                                backgroundColor = MaterialTheme.colors.primary.copy(
                                    alpha = 0.1f
                                )
                            )
                        else ButtonDefaults.outlinedButtonColors()
                    ) {
                        Text(label, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Button(
                    onClick = { onRunJobNow(job.id) },
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Run Now",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Запустить", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Интервал: ${job.intervalMinutes} мин", fontSize = 11.sp, color = Color.Gray)
                    job.lastRunEpochMs?.let {
                        Text("Последний запуск: ${formatTimestamp(it)}", fontSize = 11.sp, color = Color.Gray)
                    }
                    Text(
                        "Следующий запуск: ${formatTimestamp(job.nextRunEpochMs)}",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            }

            if (job.lastError != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Ошибка: ${job.lastError}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colors.error
                )
            }
        }
    }
}

@Composable
private fun StatusChip(status: JobStatus) {
    val (text, color) = when (status) {
        JobStatus.ACTIVE -> "Активна" to Color(0xFF4CAF50)
        JobStatus.PAUSED -> "Приостановлена" to Color.Gray
        JobStatus.RUNNING -> "Выполняется..." to Color(0xFF2196F3)
        JobStatus.ERROR -> "Ошибка" to Color(0xFFF44336)
    }
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.15f),
        modifier = Modifier.padding(top = 2.dp)
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

private fun formatTimestamp(epochMs: Long): String {
    val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(epochMs))
}
