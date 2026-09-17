package app.what.schedule.features.insts.dgtu.presentation.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.shapes
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.what.foundation.core.Listener
import app.what.foundation.data.RemoteState
import app.what.foundation.ui.AppPullToRefresh
import app.what.foundation.ui.Gap
import app.what.schedule.dgtu.models.DGTUApi
import app.what.schedule.features.insts.dgtu.domain.models.DgtuEvent
import app.what.schedule.features.insts.dgtu.domain.models.DgtuState
import app.what.schedule.ui.components.Fallback

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DgtuZachBookPage(
    state: State<DgtuState>,
    listener: Listener<DgtuEvent>,
    onBack: () -> Unit
) {
    LaunchedEffect(Unit) {
        if (state.value.zachBook == null) {
            listener(DgtuEvent.ZachBookOpened)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Назад",
                    tint = colorScheme.onSurface
                )
            }
            Gap(8)
            Text(
                "Зачетная книжка",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface
            )
        }

        HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.5f))

        AppPullToRefresh(
            isRefreshing = state.value.zachBookFetchState == RemoteState.Loading,
            onRefresh = { listener(DgtuEvent.ZachBookOpened) },
            modifier = Modifier.fillMaxSize()
        ) {
            when {
                state.value.zachBookFetchState is RemoteState.Error && state.value.zachBook == null -> {
                    Fallback(
                        "Не удалось загрузить зачетную книжку",
                        Modifier.fillMaxSize(),
                        "Попробовать снова" to { listener(DgtuEvent.ZachBookOpened) }
                    )
                }

                state.value.zachBookFetchState == RemoteState.Loading && state.value.zachBook == null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = colorScheme.primary)
                    }
                }

                state.value.zachBook != null -> {
                    val data = state.value.zachBook!!

                    // Group items by semester (course + sem + year)
                    val semesterGroups = data.zachBook
                        .groupBy { "${it.course} курс • ${it.sem} семестр (${it.year})" }
                        .toList()

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
                    ) {
                        // Student Info Card
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(shapes.medium)
                                    .background(colorScheme.surfaceContainer)
                                    .padding(16.dp)
                            ) {
                                val name = data.studentName.ifBlank {
                                    data.studentInfo?.name ?: ""
                                }
                                if (name.isNotBlank()) {
                                    Text(
                                        text = name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = colorScheme.onSurface
                                    )
                                    Gap(4)
                                }

                                if (data.recordbook.isNotBlank()) {
                                    Text(
                                        text = "Зачетная книжка: №${data.recordbook}",
                                        fontSize = 13.sp,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                }

                                data.studentInfo?.let { info ->
                                    if (info.group.isNotBlank()) {
                                        Text(
                                            text = "Группа: ${info.group}",
                                            fontSize = 13.sp,
                                            color = colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (info.specialty.isNotBlank()) {
                                        Text(
                                            text = "Специальность: ${info.specialty}",
                                            fontSize = 13.sp,
                                            color = colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        // Performance statistics
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(shapes.medium)
                                    .background(colorScheme.surfaceContainerLow)
                                    .border(1.dp, colorScheme.outlineVariant, shapes.medium)
                                    .padding(16.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        "Успеваемость",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = colorScheme.onSurface
                                    )

                                    val avgValue = data.avgPoint.takeIf { it > 0f } ?: data.avg
                                    if (avgValue != null && avgValue > 0f) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(colorScheme.primaryContainer)
                                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = "Ср. балл: $avgValue",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                }

                                if (data.markCountStatistic.isNotEmpty()) {
                                    Gap(12)
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        for (stat in data.markCountStatistic) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(getMarkColor(stat.mark).copy(alpha = 0.15f))
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    text = "${stat.mark}: ${stat.count} (${stat.percent}%)",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = getMarkColor(stat.mark)
                                                )
                                            }
                                        }
                                    }
                                }

                                if (data.avgCourseStatistic.isNotEmpty()) {
                                    Gap(12)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        for (courseStat in data.avgCourseStatistic) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(colorScheme.surfaceContainerHigh)
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    text = "${courseStat.course} курс: ${courseStat.avg}",
                                                    fontSize = 12.sp,
                                                    color = colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Semesters & disciplines
                        for ((semesterTitle, items) in semesterGroups) {
                            item {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp)
                                ) {
                                    Text(
                                        text = semesterTitle,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = colorScheme.primary
                                    )
                                    Gap(8)
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        for (zachItem in items) {
                                            ZachDisciplineCard(zachItem, data.hideZET)
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Gap(16)
                        }
                    }
                }

                else -> Unit
            }
        }
    }
}

@Composable
private fun ZachDisciplineCard(item: DGTUApi.Models.ZachItem, hideZET: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colorScheme.surfaceContainer)
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = item.dis,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )

            Gap(8)

            val markColor = getMarkColor(item.mark)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(markColor.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = item.mark,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = markColor
                )
            }
        }

        Gap(8)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (item.controlForm.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(colorScheme.surfaceContainerHigh)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = item.controlForm,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }

                val hoursInfo = buildString {
                    if (item.hours > 0) append("${item.hours} ч.")
                    if (!hideZET && item.zet > 0f) {
                        if (isNotEmpty()) append(" • ")
                        append("${item.zet} ЗЕТ")
                    }
                }
                if (hoursInfo.isNotBlank()) {
                    Text(
                        text = hoursInfo,
                        fontSize = 11.sp,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            }

            if (item.date.isNotBlank()) {
                Text(
                    text = item.date,
                    fontSize = 11.sp,
                    color = colorScheme.onSurfaceVariant
                )
            }
        }

        if (item.teacherName.isNotBlank()) {
            Gap(4)
            Text(
                text = item.teacherName,
                fontSize = 12.sp,
                color = colorScheme.secondary
            )
        }
    }
}

private fun getMarkColor(mark: String): Color = when {
    mark.contains("Отл", ignoreCase = true) || mark == "5" -> Color(0xFF2E7D32)
    mark.contains("Хор", ignoreCase = true) || mark == "4" -> Color(0xFF1976D2)
    mark.contains("Удов", ignoreCase = true) || mark == "3" -> Color(0xFFE65100)
    mark.contains("Зачтено", ignoreCase = true) || mark.contains("Зачет", ignoreCase = true) -> Color(0xFF00796B)
    mark.contains("Не зачтено", ignoreCase = true) || mark.contains("Неуд", ignoreCase = true) || mark == "2" -> Color(0xFFD32F2F)
    else -> Color(0xFF616161)
}
