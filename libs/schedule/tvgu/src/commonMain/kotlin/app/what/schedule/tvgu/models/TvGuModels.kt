package app.what.schedule.tvgu.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TvGuGroupsResponse(
    val groups: List<TvGuGroupItem> = emptyList()
)

@Serializable
data class TvGuGroupItem(
    @SerialName("groupName") val groupName: String,
    @SerialName("facultyName") val facultyName: String? = null
)

@Serializable
data class TvGuTimetableResponse(
    val types: String? = null,
    val start: String? = null,
    val finish: String? = null,
    val withDates: Boolean = false,
    val lessonTimeData: List<TvGuLessonTime> = emptyList(),
    val lessonsContainers: List<TvGuLessonContainer> = emptyList()
)

@Serializable
data class TvGuLessonTime(
    val start: String,
    val end: String
)

@Serializable
data class TvGuLessonContainer(
    val lessonNumber: Int,
    val date: String? = null,
    val weekDay: Int,
    val weekMark: String,
    val texts: List<String?> = emptyList()
)
