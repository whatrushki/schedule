package app.what.schedule.features.schedule.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.shapes
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.what.foundation.ui.Gap
import app.what.foundation.ui.bclick
import app.what.foundation.ui.useStateList
import app.what.foundation.utils.ShareChannel
import app.what.foundation.utils.ShareChannelsRow
import app.what.foundation.utils.ShareData
import app.what.foundation.utils.executeShare
import app.what.foundation.utils.rememberPlatformContext
import app.what.domain.models.DaySchedule
import app.what.domain.models.LessonState
import app.what.domain.models.LessonType
import app.what.domain.models.ScheduleSearch
import app.what.foundation.ui.icons.WHATIcons
import app.what.foundation.ui.icons.filled.Telegram
import app.what.foundation.ui.icons.filled.VK
import app.what.foundation.ui.icons.filled.Whatsapp
import app.what.foundation.utils.DateTimeUtils


val ScheduleExportPane = @Composable { scheduleSearch: ScheduleSearch?,
                                       schedules: List<DaySchedule> ->
    val context = rememberPlatformContext()
    val selectedDays = useStateList(*schedules.take(1).toTypedArray())
    
    Column(
        Modifier.verticalScroll(rememberScrollState())
    ) {
        Text(
            "Поделиться расписанием",
            style = typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            fontSize = 42.sp,
            color = colorScheme.primary,
            modifier = Modifier.padding(12.dp)
        )
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            Gap(4)
            
            schedules.forEachIndexed { index, it ->
                val selected = it in selectedDays
                val contentColor = if (selected) colorScheme.onPrimary
                else colorScheme.onSecondaryContainer
                
                Box(
                    Modifier
                        .clip(shapes.medium)
                        .background(
                            if (selected) colorScheme.primary
                            else colorScheme.secondaryContainer
                        )
                        .bclick {
                            if (it in selectedDays) {
                                if (selectedDays.size > 1) selectedDays.remove(it)
                            } else selectedDays.add(it)
                        }
                ) {
                    val dayOfWeekStr = if (schedules.size > 2) {
                        DateTimeUtils.RUSSIAN_DAYS_SHORT.getOrElse(it.date.dayOfWeek.ordinal) { "" }
                    } else {
                        DateTimeUtils.RUSSIAN_DAYS_FULL.getOrElse(it.date.dayOfWeek.ordinal) { "" }
                    }
                    Text(
                        "${it.date.dayOfMonth}\n$dayOfWeekStr",
                        modifier = Modifier.padding(16.dp, 8.dp),
                        color = contentColor,
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp,
                        lineHeight = 20.sp
                    )
                }
            }
            
            Gap(4)
        }
        
        Gap(12)
        
        ShareChannelsRow(
            channels = ShareChannel.defaultChannels,
            onChannelClick = { channel ->
                val text = createShareTextFromDaySchedules(
                    scheduleSearch,
                    selectedDays.sortedBy { it.date }
                )
                executeShare(context, channel, ShareData.Text(text, "Расписание"))
            }
        )
        
        Gap(16)
    }
}

fun createShareTextFromDaySchedules(
    scheduleSearch: ScheduleSearch?,
    schedules: List<DaySchedule>
): String {
    val searchHeader = when (scheduleSearch) {
        is ScheduleSearch.Group -> "Расписание: ${scheduleSearch.name}"
        is ScheduleSearch.Teacher -> "Расписание: ${scheduleSearch.name}"
        null -> null
    }

    val daysText = schedules.sortedBy { it.date }.joinToString("\n\n---------------------------\n\n") { daySchedule ->
        val day = daySchedule.date.dayOfMonth
        val month = DateTimeUtils.RUSSIAN_MONTHS.getOrElse(daySchedule.date.monthNumber - 1) { "" }
        val dayOfWeek = DateTimeUtils.RUSSIAN_DAYS_SHORT.getOrElse(daySchedule.date.dayOfWeek.ordinal) { "" }

        val sortedLessons = daySchedule.lessons.sortedWith(
            compareBy({ if (it.number > 0) it.number else 99 }, { it.startTime })
        )

        val lessonsContent = if (sortedLessons.isEmpty()) {
            "🎉 Занятий нет — выходной!"
        } else {
            sortedLessons.joinToString("\n\n") { lesson ->
                val typeEmoji = when (lesson.type) {
                    LessonType.COMMON, LessonType.PRACTISE, LessonType.LECTURE -> "🌱"
                    LessonType.ADDITIONAL -> "🌾"
                    LessonType.CLASS_HOUR -> "🍁"
                    LessonType.LABORATORY -> "🪻"
                    LessonType.CREDIT -> "🍂"
                    LessonType.OBLIGATION -> "💸"
                }

                val stateBadge = when (lesson.state) {
                    LessonState.COMMON -> ""
                    LessonState.ADDED -> " ⚡ [Добавлена]"
                    LessonState.CHANGED -> " ♻️ [Замена]"
                    LessonState.REMOVED -> " ❌ [Отменена]"
                }

                val timeStr = "${lesson.startTime}–${lesson.endTime}"
                val pairStr = if (lesson.number > 0) "${lesson.number} пара" else "Занятие"
                val header = "$typeEmoji **$pairStr** ($timeStr)$stateBadge"

                val subjectName = lesson.subject.ifBlank { "Не заполнено" }
                val subjectText = if (lesson.state == LessonState.REMOVED) {
                    "~$subjectName~"
                } else {
                    "**$subjectName**"
                }

                val details = if (lesson.state == LessonState.REMOVED) {
                    null
                } else when {
                    lesson.otUnits.size == 1 -> {
                        val unit = lesson.otUnits.first()
                        val target = if (scheduleSearch is ScheduleSearch.Teacher) unit.group.name else unit.teacher.name
                        val room = buildString {
                            if (unit.auditory.isNotBlank()) {
                                append("Ауд. ${unit.auditory}")
                                if (unit.building.isNotBlank() && unit.building != "0") {
                                    append(" (корп. ${unit.building})")
                                }
                            }
                        }
                        listOfNotNull(target.takeIf { it.isNotBlank() }, room.takeIf { it.isNotBlank() })
                            .joinToString(" • ")
                    }
                    lesson.otUnits.isNotEmpty() -> {
                        lesson.otUnits.joinToString("\n") { unit ->
                            val target = if (scheduleSearch is ScheduleSearch.Teacher) unit.group.name else unit.teacher.name
                            val room = buildString {
                                if (unit.auditory.isNotBlank()) {
                                    append("Ауд. ${unit.auditory}")
                                    if (unit.building.isNotBlank() && unit.building != "0") {
                                        append(" (корп. ${unit.building})")
                                    }
                                }
                            }
                            "— " + listOfNotNull(target.takeIf { it.isNotBlank() }, room.takeIf { it.isNotBlank() })
                                .joinToString(" • ")
                        }
                    }
                    else -> null
                }

                listOfNotNull(header, subjectText, details.takeIf { !it.isNullOrBlank() }).joinToString("\n")
            }
        }

        "📅 **$day $month ($dayOfWeek)**\n\n$lessonsContent"
    }

    return if (searchHeader != null) "$searchHeader\n\n$daysText" else daysText
}