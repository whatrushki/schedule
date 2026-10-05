package app.what.schedule.sfedu.grade

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

@Serializable
data class SfeduGradeStudentData(
    @SerialName("Marks")
    val Marks: Map<String, String> = emptyMap(),
    @SerialName("Disciplines")
    val Disciplines: List<SfeduGradeDiscipline> = emptyList(),
    @SerialName("Teachers")
    val Teachers: Map<String, JsonElement> = emptyMap(),
    @SerialName("EMailChanged")
    val EMailChanged: Boolean = false
)

@Serializable
data class SfeduGradeStudentResponse(
    @SerialName("response")
    val response: SfeduGradeStudentData = SfeduGradeStudentData()
)

@Serializable
data class SfeduGradeDiscipline(
    @SerialName("ID")
    val ID: Int,
    @SerialName("SubjectName")
    val SubjectName: String = "",
    @SerialName("Type")
    val Type: String = "",
    @SerialName("Rate")
    val Rate: Int = 0,
    @SerialName("MaxCurrentRate")
    val MaxCurrentRate: Int = 0,
    @SerialName("GradeNum")
    val GradeNum: Int = 0,
    @SerialName("SemesterID")
    val SemesterID: Int = 0,
    @SerialName("LastName")
    val LastName: String = "",
    @SerialName("FirstName")
    val FirstName: String = "",
    @SerialName("SecondName")
    val SecondName: String = "",
    @SerialName("FacultyName")
    val FacultyName: String = "",
    @SerialName("Degree")
    val Degree: String? = null,
    @SerialName("semesterNum")
    val semesterNum: Int? = null,
    @SerialName("semesterYear")
    val semesterYear: Int? = null,
    @SerialName("Hidden")
    val Hidden: Boolean = false,
    @SerialName("WasRemoved")
    val WasRemoved: Boolean = false,
    @SerialName("IsLocked")
    val IsLocked: Int = 0,
    @SerialName("IsBonus")
    val IsBonus: Boolean = false,
    @SerialName("Frozen")
    val Frozen: Boolean = false,
    @SerialName("HasControl")
    val HasControl: Boolean = false
)

@Serializable
data class SfeduGradeSemester(
    @SerialName("ID")
    val ID: Int,
    @SerialName("Year")
    val Year: Int = 0,
    @SerialName("Num")
    val Num: Int = 0,
    @SerialName("Type")
    val Type: String = "",
    @SerialName("Season")
    val Season: String = "",
    @SerialName("HalfYear")
    val HalfYear: Int = 0,
    @SerialName("CalendarYear")
    val CalendarYear: Int = 0
)

@Serializable
data class SfeduGradeSemesterListResponse(
    @SerialName("response")
    val response: JsonObject = JsonObject(emptyMap())
)

@Serializable
data class SfeduGradeTeacherDetail(
    @SerialName("ID")
    val ID: Int = 0,
    @SerialName("LastName")
    val LastName: String = "",
    @SerialName("FirstName")
    val FirstName: String = "",
    @SerialName("SecondName")
    val SecondName: String = "",
    @SerialName("JobPositionName")
    val JobPositionName: String = "",
    @SerialName("FacultyAbbr")
    val FacultyAbbr: String = "",
    @SerialName("IsAuthor")
    val IsAuthor: Boolean = false,
    @SerialName("Name")
    val Name: String = ""
)

@Serializable
data class SfeduGradeSubmodule(
    @SerialName("Title")
    val Title: String = "",
    @SerialName("MaxRate")
    val MaxRate: Int = 0,
    @SerialName("Rate")
    val Rate: Int? = null,
    @SerialName("Date")
    val Date: String? = null,
    @SerialName("Epo")
    val Epo: String? = null
)

@Serializable
data class SfeduGradeModule(
    @SerialName("Title")
    val Title: String = "",
    @SerialName("Submodules")
    val Submodules: JsonElement? = null
) {
    val submoduleIds: List<String>
        get() = when (val el = Submodules) {
            is JsonArray -> el.mapNotNull { (it as? JsonPrimitive)?.content }
            is JsonPrimitive -> el.content.split(" ", ",").filter { it.isNotBlank() }
            else -> emptyList()
        }
}

@Serializable
data class SfeduGradeDisciplineMap(
    @SerialName("Exam")
    val Exam: JsonElement? = null,
    @SerialName("Bonus")
    val Bonus: JsonElement? = null,
    @SerialName("Modules")
    private val rawModules: JsonElement? = null
) {
    val Modules: Map<String, SfeduGradeModule>
        get() {
            val obj = rawModules as? JsonObject ?: return emptyMap()
            val result = mutableMapOf<String, SfeduGradeModule>()
            for ((key, value) in obj) {
                if (value is JsonObject) {
                    val title = (value["Title"] as? JsonPrimitive)?.content ?: ""
                    val submodules = value["Submodules"]
                    result[key] = SfeduGradeModule(Title = title, Submodules = submodules)
                }
            }
            return result
        }
}

@Serializable
data class SfeduGradeDisciplineDetail(
    @SerialName("Discipline")
    val Discipline: SfeduGradeDiscipline,
    @SerialName("Teachers")
    val Teachers: List<SfeduGradeTeacherDetail> = emptyList(),
    @SerialName("DisciplineMap")
    val DisciplineMap: SfeduGradeDisciplineMap? = null,
    @SerialName("Submodules")
    private val rawSubmodules: JsonElement? = null,
    @SerialName("ExtraRate")
    val ExtraRate: Int = 0,
    @SerialName("Semester")
    val Semester: SfeduGradeSemester
) {
    val Submodules: Map<String, SfeduGradeSubmodule>
        get() {
            val obj = rawSubmodules as? JsonObject ?: return emptyMap()
            val result = mutableMapOf<String, SfeduGradeSubmodule>()
            for ((key, value) in obj) {
                if (value is JsonObject) {
                    try {
                        val title = (value["Title"] as? JsonPrimitive)?.content ?: ""
                        val maxRate = (value["MaxRate"] as? JsonPrimitive)?.intOrNull ?: 0
                        val rate = (value["Rate"] as? JsonPrimitive)?.intOrNull
                        val date = (value["Date"] as? JsonPrimitive)?.contentOrNull
                        val epo = (value["Epo"] as? JsonPrimitive)?.contentOrNull
                        result[key] = SfeduGradeSubmodule(
                            Title = title,
                            MaxRate = maxRate,
                            Rate = rate,
                            Date = date,
                            Epo = epo
                        )
                    } catch (_: Exception) {}
                }
            }
            return result
        }
}

@Serializable
data class SfeduGradeDisciplineDetailResponse(
    @SerialName("response")
    val response: SfeduGradeDisciplineDetail
)

class SfeduGradeTokenInvalidException(message: String = "Token is broken.") : Exception(message)
