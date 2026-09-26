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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.shapes
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import app.what.foundation.ui.Show
import app.what.foundation.ui.bclick
import app.what.schedule.ui.theme.icons.WHATIcons
import app.what.schedule.ui.theme.icons.filled.Features
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
            .wrapContentWidth(Alignment.CenterHorizontally)
            .widthIn(max = 860.dp)
    ) {
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

                    val semesterMap = remember(data.zachBook) {
                        data.zachBook
                            .groupBy { it.course to it.sem }
                            .toList()
                            .sortedWith(
                                compareByDescending<Pair<Pair<Int, Int>, *>> { it.first.first }
                                    .thenByDescending { it.first.second }
                            )
                    }

                    var selectedSemesterIndex by remember(semesterMap) {
                        val initialIdx = semesterMap.indexOfFirst { (key, _) ->
                            key.second == data.currentSem
                        }.takeIf { it >= 0 } ?: 0
                        mutableIntStateOf(initialIdx)
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
                    ) {
                        // Student Info Card
                        item {
                            val name = data.studentName.ifBlank {
                                data.studentInfo?.name ?: ""
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(shapes.large)
                                    .background(colorScheme.surfaceContainer)
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    if (name.isNotBlank()) {
                                        Text(
                                            text = name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Gap(3)
                                    }

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (data.recordbook.isNotBlank()) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(shapes.extraSmall)
                                                    .background(colorScheme.surfaceContainerHigh)
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "№ ${data.recordbook}",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        data.studentInfo?.let { info ->
                                            if (info.group.isNotBlank()) {
                                                Text(
                                                    text = info.group,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = colorScheme.primary
                                                )
                                            }
                                        }
                                    }

                                    data.studentInfo?.specialty?.takeIf { it.isNotBlank() }?.let { spec ->
                                        Gap(2)
                                        Text(
                                            text = spec,
                                            fontSize = 11.sp,
                                            color = colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
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
                                    .clip(shapes.large)
                                    .background(colorScheme.surfaceContainerLow)
                                    .border(1.dp, colorScheme.outlineVariant.copy(alpha = 0.35f), shapes.large)
                                    .padding(16.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column {
                                        Text(
                                            "Успеваемость",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = colorScheme.onSurface
                                        )
                                        Text(
                                            "Общая статистика оценок",
                                            fontSize = 12.sp,
                                            color = colorScheme.onSurfaceVariant
                                        )
                                    }

                                    val avgValue = data.avgPoint.takeIf { it > 0f } ?: data.avg
                                    if (avgValue != null && avgValue > 0f) {
                                        Box(
                                            modifier = Modifier
                                                .clip(shapes.medium)
                                                .background(colorScheme.primaryContainer)
                                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "$avgValue",
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 17.sp,
                                                    color = colorScheme.onPrimaryContainer
                                                )
                                                Gap(4)
                                                Text(
                                                    text = "ср. балл",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                                )
                                            }
                                        }
                                    }
                                }

                                if (data.markCountStatistic.isNotEmpty()) {
                                    Gap(14)
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        for (stat in data.markCountStatistic) {
                                            val markColor = getMarkColor(stat.mark)
                                            Row(
                                                modifier = Modifier
                                                    .clip(shapes.small)
                                                    .background(markColor.copy(alpha = 0.12f))
                                                    .border(1.dp, markColor.copy(alpha = 0.25f), shapes.small)
                                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .clip(CircleShape)
                                                        .background(markColor)
                                                )
                                                Text(
                                                    text = stat.mark,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = markColor
                                                )
                                                Text(
                                                    text = "${stat.count} (${stat.percent}%)",
                                                    fontSize = 11.sp,
                                                    color = colorScheme.onSurfaceVariant
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
                                        for (courseStat in data.avgCourseStatistic.sortedByDescending { it.course }) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(shapes.small)
                                                    .background(colorScheme.surfaceContainerHigh)
                                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                                            ) {
                                                Text(
                                                    text = "${courseStat.course} курс: ${courseStat.avg}",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Semester switcher
                        if (semesterMap.isNotEmpty()) {
                            item {
                                Column {
                                    Text(
                                        text = "Семестры",
                                        style = typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = colorScheme.onSurface
                                    )
                                    Gap(8)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        semesterMap.forEachIndexed { index, (key, _) ->
                                            val (c, s) = key
                                            val isSelected = index == selectedSemesterIndex
                                            Box(
                                                modifier = Modifier
                                                    .clip(shapes.medium)
                                                    .background(
                                                        if (isSelected) colorScheme.primary else colorScheme.surfaceContainerHigh
                                                    )
                                                    .bclick { selectedSemesterIndex = index }
                                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                                            ) {
                                                Text(
                                                    text = "$c курс • $s сем",
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    fontSize = 13.sp,
                                                    color = if (isSelected) colorScheme.onPrimary else colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Selected semester disciplines header
                        val selectedSemester = semesterMap.getOrNull(selectedSemesterIndex)
                        if (selectedSemester != null) {
                            val (course, sem) = selectedSemester.first
                            val disciplines = selectedSemester.second
                            val year = disciplines.firstOrNull()?.year.orEmpty()

                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "$course курс • $sem семестр" + if (year.isNotBlank()) " ($year)" else "",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = colorScheme.primary
                                    )
                                    Text(
                                        text = "${disciplines.size} предм.",
                                        style = typography.bodySmall,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            items(disciplines, key = { it.key }) { zachItem ->
                                ZachDisciplineCard(zachItem, data.hideZET)
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
            .clip(shapes.medium)
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
                lineHeight = 20.sp,
                color = colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )

            Gap(10)

            val markColor = getMarkColor(item.mark)
            Box(
                modifier = Modifier
                    .clip(shapes.small)
                    .background(markColor.copy(alpha = 0.15f))
                    .border(1.dp, markColor.copy(alpha = 0.3f), shapes.small)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = item.mark,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = markColor
                )
            }
        }

        Gap(10)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (item.controlForm.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(shapes.extraSmall)
                            .background(colorScheme.surfaceContainerHigh)
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = item.controlForm,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
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
                        fontSize = 12.sp,
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
            Gap(6)
            Text(
                text = item.teacherName,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = colorScheme.primary
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
