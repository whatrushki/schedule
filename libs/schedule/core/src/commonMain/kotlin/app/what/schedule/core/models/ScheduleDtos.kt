package app.what.schedule.core.models

import kotlinx.serialization.Serializable
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

@Serializable
data class InstitutionMetaDto(
    val id: String,
    val name: String,
    val fullName: String,
    val description: String,
    val sourceTypes: Set<SourceTypeDto>,
    val sourceUrl: String,
    val hasAccountService: Boolean = false
)

enum class SourceTypeDto { API, PARSER, EXCEL, PDF }

@Serializable
data class DayScheduleDto(
    val date: LocalDate,
    val scheduleType: LessonsScheduleTypeDto,
    val lessons: List<LessonDto>
)

@Serializable
data class LessonDto(
    val date: LocalDate,
    val number: Int,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val subject: String,
    val otUnits: List<OneTimeUnitDto>,
    val type: LessonTypeDto,
    val state: LessonStateDto = LessonStateDto.COMMON
) {
    infix operator fun plus(other: LessonDto) = copy(otUnits = otUnits + other.otUnits)
    infix operator fun plus(other: List<LessonDto>) = copy(otUnits = otUnits + other.flatMap(LessonDto::otUnits))

    fun equalsWithReplacement(other: LessonDto): Boolean {
        if (otUnits.isEmpty() && other.otUnits.isEmpty()) return true
        if (otUnits.isEmpty() || other.otUnits.isEmpty()) return false

        fun clean(s: String) = s.replace(" ", "")
            .replace(".", "")
            .replace("—", "-")
            .replace("–", "-")
            .replace("с", "c", ignoreCase = true)
            .replace("а", "a", ignoreCase = true)
            .replace("о", "o", ignoreCase = true)
            .replace("р", "p", ignoreCase = true)
            .replace("х", "x", ignoreCase = true)
            .replace("е", "e", ignoreCase = true)
            .trim()
            .lowercase()

        fun cleanRoom(r: String): String {
            val trimmed = r.trim().removeSuffix(".0")
            return clean(trimmed)
        }

        return otUnits.all { unit ->
            other.otUnits.any { otherUnit ->
                clean(unit.teacher) == clean(otherUnit.teacher) &&
                cleanRoom(unit.room) == cleanRoom(otherUnit.room)
            }
        }
    }
}

@Serializable
enum class LessonsScheduleTypeDto {
    COMMON,
    SHORTENED,
    WITH_CLASS_HOUR
}

@Serializable
enum class LessonStateDto {
    COMMON, ADDED, REMOVED, CHANGED
}

@Serializable
data class OneTimeUnitDto(
    val teacher: String,
    val group: String,
    val room: String,
    val additional: String = "",
    val onlineUrl: String? = null,
    val subject: String? = null
)

@Serializable
enum class LessonTypeDto {
    COMMON,
    PRACTICE,
    LECTURE,
    LABORATORY,
    EXAM,
    CREDIT,
    CONSULTATION,
    CLASS_HOUR,
    OTHER,
    UNKNOWN;

    companion object {
        fun fromString(value: String): LessonTypeDto = when {
            value.contains("класс", ignoreCase = true) -> CLASS_HOUR
            value.contains("пр", ignoreCase = true) -> PRACTICE
            value.contains("лек", ignoreCase = true) -> LECTURE
            value.contains("лаб", ignoreCase = true) -> LABORATORY
            value.contains("экз", ignoreCase = true) -> EXAM
            value.contains("зач", ignoreCase = true) -> CREDIT
            value.contains("конс", ignoreCase = true) -> CONSULTATION
            else -> OTHER
        }
    }
}

@Serializable
data class LessonTimeDto(
    val number: Int,
    val start: LocalTime,
    val end: LocalTime
)

@Serializable
data class GroupDto(
    val id: String,
    val name: String,
    val course: Int = 0
)

@Serializable
data class TeacherDto(
    val id: String,
    val name: String
)

@Serializable
sealed interface SearchTargetDto {
    val id: String
    val name: String

    @Serializable
    data class Group(override val id: String, override val name: String) : SearchTargetDto
    @Serializable
    data class Teacher(override val id: String, override val name: String) : SearchTargetDto
}

@Serializable
enum class AccountCapability {
    PROFILE,
    EDIT_PROFILE,
    ENQUIRIES,
    PAYMENT_QR,
    RE_ATTESTATION,
    ELECTRONIC_PASS,
    GRADES_BOOK,
    INTERNAL_MAIL,
    EVENTS
}

@Serializable
data class AccountProfileDto(
    val fullName: String,
    val name: String,
    val surname: String,
    val middleName: String? = null,
    val group: String,
    val specialty: String? = null,
    val curator: String? = null,
    val curatorPhone: String? = null,
    val photoUrl: String? = null,
    val educationForm: String? = null,
    val healthGroup: String? = null,
    val socialStatus: String? = null,
    val extraDetails: Map<String, String> = emptyMap()
)

@Serializable
data class EnquiryItemDto(
    val title: String,
    val status: String,
    val orderDate: String,
    val pickupLocation: String,
    val count: Int
)

@Serializable
data class EnquiryTypeDto(
    val id: String,
    val title: String,
    val description: String? = null
)

@Serializable
data class ReAttestationItemDto(
    val discipline: String,
    val teacher: String,
    val issueDate: String,
    val deadlineDate: String
)

@Serializable
data class PaymentTargetOptionDto(
    val id: String,
    val title: String
)

@Serializable
data class PaymentYearOptionDto(
    val id: String,
    val title: String
)

@Serializable
data class PaymentPeriodOptionDto(
    val id: String,
    val title: String
)

@Serializable
data class PaymentQrConfigDto(
    val targets: List<PaymentTargetOptionDto>,
    val years: List<PaymentYearOptionDto>,
    val periods: List<PaymentPeriodOptionDto>
)

@Serializable
data class ProfileInputFieldDto(
    val id: String,
    val name: String,
    val label: String,
    val value: String,
    val type: String = "text",
    val options: List<Pair<String, String>> = emptyList(),
    val isRequired: Boolean = false,
    val isReadOnly: Boolean = false
)

@Serializable
data class ProfileSectionDto(
    val title: String,
    val fields: List<ProfileInputFieldDto>
)

@Serializable
data class SocialStatusOptionDto(
    val id: String,
    val title: String,
    val isChecked: Boolean
)

