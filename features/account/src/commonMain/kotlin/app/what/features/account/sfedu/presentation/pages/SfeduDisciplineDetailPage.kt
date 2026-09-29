package app.what.schedule.features.insts.sfedu.presentation.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.shapes
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.what.foundation.data.RemoteState
import app.what.foundation.ui.Gap
import app.what.schedule.features.insts.sfedu.domain.models.SfeduState
import app.what.schedule.sfedu.grade.SfeduGradeSubmodule
import app.what.schedule.ui.components.Fallback
import kotlinx.serialization.json.JsonPrimitive

@Composable
internal fun SfeduDisciplineDetailPage(
    state: State<SfeduState>,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.surface)
            .statusBarsPadding()
            .systemBarsPadding()
            .wrapContentWidth(Alignment.CenterHorizontally)
            .widthIn(max = 860.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Назад",
                    tint = colorScheme.onSurface
                )
            }
            Text(
                text = "Детали дисциплины",
                style = typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface
            )
        }
        when {
            state.value.disciplineDetailFetchState is RemoteState.Error -> {
                Fallback(
                    "Не удалось загрузить детали",
                    Modifier.fillMaxSize()
                )
            }

            state.value.disciplineDetailFetchState == RemoteState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = colorScheme.primary)
                }
            }

            state.value.disciplineDetail != null -> {
                val detail = state.value.disciplineDetail!!

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
                ) {
                    // Discipline title
                    item {
                        Text(
                            text = detail.Discipline.SubjectName,
                            style = typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface
                        )
                        Gap(4)
                        val controlType = when (detail.Discipline.Type) {
                            "exam" -> "Экзамен"
                            "credit" -> "Зачёт"
                            "credit_grade", "grading_credit" -> "Дифф. зачёт"
                            else -> detail.Discipline.Type
                        }
                        Text(
                            text = controlType,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = colorScheme.primary
                        )
                    }

                    // Semester info
                    item {
                        val seasonName = when (detail.Semester.Season) {
                            "autumn" -> "Осень"
                            "spring" -> "Весна"
                            else -> detail.Semester.Season
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(shapes.medium)
                                .background(colorScheme.surfaceContainer)
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "$seasonName ${detail.Semester.CalendarYear}",
                                fontSize = 14.sp,
                                color = colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Доп. баллы: ${detail.ExtraRate}",
                                fontSize = 14.sp,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Teachers
                    if (detail.Teachers.isNotEmpty()) {
                        item {
                            Text(
                                "Преподаватели",
                                style = typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onSurface
                            )
                        }
                        items(detail.Teachers) { teacher ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(shapes.small)
                                    .background(colorScheme.surfaceContainer)
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        teacher.Name,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (teacher.JobPositionName.isNotBlank()) {
                                        Text(
                                            teacher.JobPositionName,
                                            fontSize = 12.sp,
                                            color = colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (teacher.IsAuthor) {
                                    Box(
                                        modifier = Modifier
                                            .clip(shapes.extraSmall)
                                            .background(colorScheme.primaryContainer)
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            "Автор",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Modules & Submodules
                    if (detail.Submodules.isNotEmpty()) {
                        item {
                            Text(
                                "Оценки",
                                style = typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onSurface
                            )
                        }

                        // Render DisciplineMap modules
                        val modules = detail.DisciplineMap?.Modules ?: emptyMap()
                        if (modules.isNotEmpty()) {
                            modules.forEach { (moduleId, module) ->
                                item(key = "module_$moduleId") {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(shapes.medium)
                                            .background(colorScheme.surfaceContainer)
                                            .padding(12.dp)
                                    ) {
                                        Text(
                                            module.Title.ifBlank { "Модуль" },
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp,
                                            color = colorScheme.onSurface
                                        )
                                        Gap(8)
                                        val submoduleIds = module.submoduleIds
                                        submoduleIds.forEach { subId ->
                                            val sub = detail.Submodules[subId]
                                            if (sub != null) {
                                                SubmoduleRow(sub)
                                                Gap(4)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Exam & Bonus submodules
                        val examId = (detail.DisciplineMap?.Exam as? JsonPrimitive)?.content ?: detail.DisciplineMap?.Exam?.toString()
                        val bonusId = (detail.DisciplineMap?.Bonus as? JsonPrimitive)?.content ?: detail.DisciplineMap?.Bonus?.toString()

                        if (examId != null) {
                            val examSub = detail.Submodules[examId]
                            if (examSub != null) {
                                item(key = "exam") {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(shapes.medium)
                                            .background(colorScheme.surfaceContainer)
                                            .padding(12.dp)
                                    ) {
                                        Text(
                                            "Экзамен / Контроль",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp,
                                            color = colorScheme.onSurface
                                        )
                                        Gap(8)
                                        SubmoduleRow(examSub)
                                    }
                                }
                            }
                        }

                        if (bonusId != null) {
                            val bonusSub = detail.Submodules[bonusId]
                            if (bonusSub != null) {
                                item(key = "bonus") {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(shapes.medium)
                                            .background(colorScheme.surfaceContainer)
                                            .padding(12.dp)
                                    ) {
                                        Text(
                                            "Бонусные баллы",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp,
                                            color = colorScheme.onSurface
                                        )
                                        Gap(8)
                                        SubmoduleRow(bonusSub)
                                    }
                                }
                            }
                        }
                    }

                    item { Gap(16) }
                }
            }

            else -> Unit
        }
    }
}

@Composable
private fun SubmoduleRow(sub: SfeduGradeSubmodule) {
    val rate = sub.Rate
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                sub.Title.ifBlank { "Оценка" },
                fontSize = 13.sp,
                color = colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            val rateText = "${rate ?: "—"} / ${sub.MaxRate}"
            Text(
                rateText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (rate != null) colorScheme.primary else colorScheme.onSurfaceVariant
            )
        }
        Gap(4)
        val progress = if (sub.MaxRate > 0 && rate != null) {
            rate.toFloat() / sub.MaxRate.toFloat()
        } else 0f
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .clip(shapes.small),
            color = colorScheme.primary,
            trackColor = colorScheme.surfaceContainerHigh
        )
    }
}
