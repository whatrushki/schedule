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
import app.what.domain.models.Lesson
import app.what.domain.models.LessonState
import app.what.domain.models.LessonType
import app.what.domain.models.OneTimeUnit
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
) = schedules.sortedBy { it.date }.joinToString(
    "\n---------------------------\n\n"
) { daySchedule ->
    val day = daySchedule.date.dayOfMonth
    val month = DateTimeUtils.RUSSIAN_MONTHS.getOrElse(daySchedule.date.monthNumber - 1) { "" }
    val dayOfWeek = DateTimeUtils.RUSSIAN_DAYS_SHORT.getOrElse(daySchedule.date.dayOfWeek.ordinal) { "" }

    // Группируем пары по номеру с сохранением хронологического порядка
    val slots: Map<Int, List<Lesson>> = daySchedule.lessons
        .sortedWith(compareBy({ if (it.number > 0) it.number else 99 }, { it.startTime }))
        .groupBy { if (it.number > 0) it.number else 0 }

    val slotStrings = mutableListOf<String>()

    for ((pairNum, lessonsInSlot) in slots) {
        val activeLessons = lessonsInSlot.filter { it.state != LessonState.REMOVED }

        if (activeLessons.isEmpty()) {
            // Если все занятия слота отменены — аккуратно отмечаем одной строкой
            val pairLabel = if (pairNum == 0) "Классный час" else "$pairNum пара"
            slotStrings.add("$pairLabel — отмена")
        } else {
            // При замене показываем актуальные занятия без мусора
            for (lesson in activeLessons) {
                val typeEmoji = when (lesson.type) { //🌱🍂🪻🌼🌻☘️🌳🌴🌾🍁🍃
                    LessonType.COMMON, LessonType.PRACTISE, LessonType.LECTURE -> "🌱"
                    LessonType.ADDITIONAL -> "🌾"
                    LessonType.CLASS_HOUR -> "🍁"
                    LessonType.LABORATORY -> "🪻"
                    LessonType.CREDIT -> "🍂"
                    LessonType.OBLIGATION -> "💸"
                }
                val stateTag = when (lesson.state) {
                    LessonState.COMMON -> ""
                    LessonState.ADDED -> " (⚡)"
                    LessonState.CHANGED -> " (♻️)"
                    LessonState.REMOVED -> ""
                }

                val timeStr = " __${lesson.startTime}–${lesson.endTime}__"

                val pairTitle = if (lesson.number == 0) "Классный час" else "${lesson.number} пара"

                val unitsText = when {
                    lesson.otUnits.size == 1 -> {
                        val unit = lesson.otUnits.first()
                        val target: String = if (scheduleSearch is ScheduleSearch.Teacher) unit.group.name else unit.teacher.name
                        val room = if (unit.auditory.isNotBlank()) "Ауд. ${unit.auditory} (корп.${unit.building})\n" else ""
                        val targetStr = if (target.isNotBlank()) "$target\n" else ""
                        "$targetStr$room"
                    }
                    lesson.otUnits.isNotEmpty() -> {
                        lesson.otUnits.joinToString("\n") { unit: OneTimeUnit ->
                            val target: String = if (scheduleSearch is ScheduleSearch.Teacher) unit.group.name else unit.teacher.name
                            "— $target — Ауд. ${unit.auditory}/${unit.building}"
                        } + "\n"
                    }
                    else -> ""
                }

                val subjName = lesson.subject.ifBlank { "Не заполнено" }
                val lessonBlock = "$typeEmoji **$pairTitle**$timeStr$stateTag\n" +
                        "**$subjName**\n" +
                        unitsText

                slotStrings.add(lessonBlock.trimEnd())
            }
        }
    }

    "**$day $month ($dayOfWeek)**\n\n" + slotStrings.joinToString("\n\n")
}