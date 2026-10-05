package app.what.schedule.core.models

import app.what.foundation.scraper.model.*

fun ScrapedUnit.toOneTimeUnitDto(): OneTimeUnitDto = OneTimeUnitDto(
    teacher = teacher,
    group = group,
    room = room,
    additional = building,
    onlineUrl = onlineUrl,
    subject = subject
)

fun OneTimeUnitDto.toScrapedUnit(): ScrapedUnit = ScrapedUnit(
    teacher = teacher,
    group = group,
    room = room,
    building = additional,
    onlineUrl = onlineUrl,
    subject = subject
)

fun ScrapedLessonType.toLessonTypeDto(): LessonTypeDto = when (this) {
    ScrapedLessonType.COMMON -> LessonTypeDto.COMMON
    ScrapedLessonType.PRACTICE -> LessonTypeDto.PRACTICE
    ScrapedLessonType.LECTURE -> LessonTypeDto.LECTURE
    ScrapedLessonType.LABORATORY -> LessonTypeDto.LABORATORY
    ScrapedLessonType.EXAM -> LessonTypeDto.EXAM
    ScrapedLessonType.CREDIT -> LessonTypeDto.CREDIT
    ScrapedLessonType.CONSULTATION -> LessonTypeDto.CONSULTATION
    ScrapedLessonType.CLASS_HOUR -> LessonTypeDto.CLASS_HOUR
    ScrapedLessonType.OTHER -> LessonTypeDto.OTHER
    ScrapedLessonType.UNKNOWN -> LessonTypeDto.UNKNOWN
}

fun LessonTypeDto.toScrapedLessonType(): ScrapedLessonType = when (this) {
    LessonTypeDto.COMMON -> ScrapedLessonType.COMMON
    LessonTypeDto.PRACTICE -> ScrapedLessonType.PRACTICE
    LessonTypeDto.LECTURE -> ScrapedLessonType.LECTURE
    LessonTypeDto.LABORATORY -> ScrapedLessonType.LABORATORY
    LessonTypeDto.EXAM -> ScrapedLessonType.EXAM
    LessonTypeDto.CREDIT -> ScrapedLessonType.CREDIT
    LessonTypeDto.CONSULTATION -> ScrapedLessonType.CONSULTATION
    LessonTypeDto.CLASS_HOUR -> ScrapedLessonType.CLASS_HOUR
    LessonTypeDto.OTHER -> ScrapedLessonType.OTHER
    LessonTypeDto.UNKNOWN -> ScrapedLessonType.UNKNOWN
}

fun ScrapedLessonState.toLessonStateDto(): LessonStateDto = when (this) {
    ScrapedLessonState.COMMON -> LessonStateDto.COMMON
    ScrapedLessonState.ADDED -> LessonStateDto.ADDED
    ScrapedLessonState.REMOVED -> LessonStateDto.REMOVED
    ScrapedLessonState.CHANGED -> LessonStateDto.CHANGED
}

fun LessonStateDto.toScrapedLessonState(): ScrapedLessonState = when (this) {
    LessonStateDto.COMMON -> ScrapedLessonState.COMMON
    LessonStateDto.ADDED -> ScrapedLessonState.ADDED
    LessonStateDto.REMOVED -> ScrapedLessonState.REMOVED
    LessonStateDto.CHANGED -> ScrapedLessonState.CHANGED
}

fun ScrapedScheduleType.toScheduleTypeDto(): LessonsScheduleTypeDto = when (this) {
    ScrapedScheduleType.COMMON -> LessonsScheduleTypeDto.COMMON
    ScrapedScheduleType.SHORTENED -> LessonsScheduleTypeDto.SHORTENED
    ScrapedScheduleType.WITH_CLASS_HOUR -> LessonsScheduleTypeDto.WITH_CLASS_HOUR
}

fun LessonsScheduleTypeDto.toScrapedScheduleType(): ScrapedScheduleType = when (this) {
    LessonsScheduleTypeDto.COMMON -> ScrapedScheduleType.COMMON
    LessonsScheduleTypeDto.SHORTENED -> ScrapedScheduleType.SHORTENED
    LessonsScheduleTypeDto.WITH_CLASS_HOUR -> ScrapedScheduleType.WITH_CLASS_HOUR
}

fun ScrapedLesson.toLessonDto(): LessonDto = LessonDto(
    date = date,
    number = number,
    startTime = startTime,
    endTime = endTime,
    subject = subject,
    otUnits = units.map { it.toOneTimeUnitDto() },
    type = type.toLessonTypeDto(),
    state = state.toLessonStateDto()
)

fun LessonDto.toScrapedLesson(): ScrapedLesson = ScrapedLesson(
    date = date,
    number = number,
    startTime = startTime,
    endTime = endTime,
    subject = subject,
    units = otUnits.map { it.toScrapedUnit() },
    type = type.toScrapedLessonType(),
    state = state.toScrapedLessonState()
)

fun ScrapedDay.toDayScheduleDto(): DayScheduleDto = DayScheduleDto(
    date = date,
    scheduleType = scheduleType.toScheduleTypeDto(),
    lessons = lessons.map { it.toLessonDto() }
)

fun LessonTimeDto.toTimeSlot(): LessonTimeSlot = LessonTimeSlot(
    number = number,
    start = start,
    end = end
)

fun ScrapedNewsItem.toNewListItemDto(): NewListItemDto = NewListItemDto(
    id = id,
    title = title,
    description = description,
    date = date,
    imageUrl = imageUrl,
    sourceUrl = sourceUrl,
    tags = tags
)
