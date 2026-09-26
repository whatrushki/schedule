package app.what.data.mappers

import androidx.compose.ui.text.AnnotatedString
import app.what.domain.models.*
import app.what.foundation.utils.currentTimeMillis
import app.what.schedule.core.models.*
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import app.what.data.remote.utils.fromHtml

fun GroupDto.toDomain(): Group = Group(name = name, id = id)
fun TeacherDto.toDomain(): Teacher = Teacher(name = name, id = id)

fun OneTimeUnitDto.toDomain(): OneTimeUnit = OneTimeUnit(
    group = Group(name = group, id = group),
    teacher = Teacher(name = teacher, id = teacher),
    auditory = room,
    building = additional.replace("(?i)корпус\\s*|(?i)корп\\.?\\s*".toRegex(), "").trim()
)

fun LessonTypeDto.toDomain(): LessonType = when (this) {
    LessonTypeDto.LECTURE -> LessonType.LECTURE
    LessonTypeDto.PRACTICE -> LessonType.PRACTISE
    LessonTypeDto.LABORATORY -> LessonType.LABORATORY
    LessonTypeDto.CREDIT, LessonTypeDto.EXAM -> LessonType.CREDIT
    LessonTypeDto.CLASS_HOUR -> LessonType.CLASS_HOUR
    else -> LessonType.COMMON
}

fun LessonStateDto.toDomain(): LessonState = when (this) {
    LessonStateDto.ADDED -> LessonState.ADDED
    LessonStateDto.REMOVED -> LessonState.REMOVED
    LessonStateDto.CHANGED -> LessonState.CHANGED
    LessonStateDto.COMMON -> LessonState.COMMON
}

fun LessonsScheduleTypeDto.toDomain(): LessonsScheduleType = when (this) {
    LessonsScheduleTypeDto.SHORTENED -> LessonsScheduleType.SHORTENED
    LessonsScheduleTypeDto.WITH_CLASS_HOUR -> LessonsScheduleType.WITH_CLASS_HOUR
    LessonsScheduleTypeDto.COMMON -> LessonsScheduleType.COMMON
}

fun LessonDto.toDomain(): Lesson = Lesson(
    date = date,
    number = number,
    startTime = startTime,
    endTime = endTime,
    subject = subject,
    otUnits = otUnits.map { it.toDomain() },
    type = type.toDomain(),
    state = state.toDomain()
)

fun DayScheduleDto.toDomain(): DaySchedule = DaySchedule(
    date = date,
    scheduleType = scheduleType.toDomain(),
    lessons = lessons.map { it.toDomain() }
)

fun List<DayScheduleDto>.toDomainResponse(): ScheduleResponse {
    if (isEmpty()) return ScheduleResponse.Empty
    return ScheduleResponse.Available.FromSource(
        schedules = map { it.toDomain() },
        lastModified = Instant.fromEpochMilliseconds(currentTimeMillis()).toLocalDateTime(TimeZone.currentSystemDefault())
    )
}

fun NewListItemDto.toDomain(): NewListItem = NewListItem(
    id = id,
    url = sourceUrl ?: urlOrFallback(id),
    bannerUrl = imageUrl ?: "",
    title = title,
    description = description,
    timestamp = date,
    tags = tags.map { NewTag(it, it) }
)

fun NewDetailDto.toDomain(): NewItem {
    val domainDescription = descriptionHtml?.let { AnnotatedString.Companion.fromHtml(it) }
        ?.takeIf { it.isNotBlank() }

    val domainContent: NewContent = if (contentBlocks.isNotEmpty()) {
        val domainBlocks = contentBlocks.map { block ->
            when (block) {
                is NewContentBlockDto.Text -> NewContent.Item.Text(AnnotatedString.Companion.fromHtml(block.html))
                is NewContentBlockDto.Subtitle -> NewContent.Item.Subtitle(block.text)
                is NewContentBlockDto.Image -> NewContent.Item.Image(block.url)
                is NewContentBlockDto.ImageCarousel -> NewContent.Item.ImageCarousel(block.urls)
                is NewContentBlockDto.UnsortedList -> NewContent.Item.UnsortedList(block.items)
                is NewContentBlockDto.SortedList -> NewContent.Item.SortedList(block.items)
                is NewContentBlockDto.Quote -> NewContent.Item.Quote(
                    AuthorInfo(block.author.avatarUrl, block.author.name, block.author.role),
                    block.text
                )
                is NewContentBlockDto.Info -> NewContent.Item.Info(block.text)
                is NewContentBlockDto.VideoVK -> NewContent.Item.Video.VK(block.url)
            }
        }
        NewContent.Container.Column(domainBlocks)
    } else if (fullText.isNotBlank()) {
        NewContent.Item.Text(AnnotatedString.Companion.fromHtml(fullText))
    } else {
        NewContent.Container.Column(emptyList())
    }

    return NewItem(
        id = id,
        url = sourceUrl ?: id,
        bannerUrl = bannerUrl ?: images.firstOrNull(),
        title = title,
        description = domainDescription,
        tags = tags.map { NewTag(it, it) },
        timestamp = date,
        content = domainContent
    )
}

private fun urlOrFallback(id: String): String = id
