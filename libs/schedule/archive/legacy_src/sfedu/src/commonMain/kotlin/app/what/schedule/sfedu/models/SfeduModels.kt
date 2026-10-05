package app.what.schedule.sfedu.models

import kotlinx.serialization.Serializable

@Serializable
data class SfeduWeekInfo(
    val week: Int = 0
)

@Serializable
data class SfeduGrade(
    val id: Int = 0,
    val num: Int = 0,
    val degree: String = ""
)

@Serializable
data class SfeduGroup(
    val id: Int = 0,
    val name: String = "",
    val num: Int = 0,
    val gradeid: Int = 0,
    val grorder: Int? = null
)

@Serializable
data class SfeduTeacher(
    val id: Int = 0,
    val name: String = "",
    val degree: String? = null
)

@Serializable
data class SfeduScheduleResponse(
    val lessons: List<SfeduLesson> = emptyList(),
    val curricula: List<SfeduCurriculum> = emptyList(),
    val groups: List<SfeduGroupInfo> = emptyList()
)

@Serializable
data class SfeduLesson(
    val id: Int? = null,
    val timeslot: String = "",
    val ctype: Boolean? = null,
    val info: String? = null,
    val uberid: Int? = null,
    val subcount: Int? = null
)

@Serializable
data class SfeduCurriculum(
    val id: Int? = null,
    val lessonid: Int = 0,
    val subnum: Int? = null,
    val subjectid: Int? = null,
    val subjectname: String = "",
    val subjectabbr: String? = null,
    val teacherid: Int? = null,
    val teachername: String? = null,
    val teacherdegree: String? = null,
    val roomid: Int? = null,
    val roomname: String? = null
)

@Serializable
data class SfeduGroupInfo(
    val id: Int? = null,
    val uberid: Int? = null,
    val name: String = "",
    val num: Int? = null,
    val groupnum: Int? = null,
    val gradenum: Int? = null,
    val degree: String? = null
)
