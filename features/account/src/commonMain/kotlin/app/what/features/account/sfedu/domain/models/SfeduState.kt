package app.what.schedule.features.insts.sfedu.domain.models

import app.what.domain.models.NewListItem
import app.what.foundation.data.RemoteState
import app.what.schedule.sfedu.grade.SfeduGradeDiscipline
import app.what.schedule.sfedu.grade.SfeduGradeDisciplineDetail
import app.what.schedule.sfedu.grade.SfeduGradeSemester

data class SfeduState(
    val token: String? = null,
    val isValidating: Boolean = false,
    val tokenError: String? = null,

    // Student profile info
    val studentName: String? = null,
    val studentGroup: String? = null,
    val studentDirection: String? = null,
    val facultyName: String? = null,
    val courseNum: Int? = null,
    val degree: String? = null,
    val email: String? = null,
    val recordBook: String? = null,

    // BRS Grades & Disciplines
    val disciplinesFetchState: RemoteState = RemoteState.Idle,
    val disciplines: List<SfeduGradeDiscipline> = emptyList(),
    val marks: Map<String, String> = emptyMap(),

    val semesters: List<SfeduGradeSemester> = emptyList(),
    val selectedSemesterId: Int? = null,

    val disciplineDetailFetchState: RemoteState = RemoteState.Idle,
    val disciplineDetail: SfeduGradeDisciplineDetail? = null,

    // News
    val newsFetchState: RemoteState = RemoteState.Idle,
    val news: List<NewListItem> = emptyList()
) {
    val isAuthorized: Boolean get() = !token.isNullOrBlank()

    val currentSemesterDisciplines: List<SfeduGradeDiscipline>
        get() {
            val semId = selectedSemesterId ?: return disciplines
            return disciplines.filter { it.SemesterID == semId }
        }

    val displayStudentName: String
        get() = studentName?.takeIf { it.isNotBlank() } ?: "Владислав Сергеевич Паршин"

    val displayShortName: String
        get() {
            val full = displayStudentName.trim()
            val parts = full.split("\\s+".toRegex()).filter { it.isNotBlank() }
            if (parts.size >= 3) {
                val p1 = parts[1].lowercase()
                val p2 = parts[2].lowercase()
                return when {
                    p1.endsWith("ич") || p1.endsWith("на") -> "${parts[0]} ${parts[2]}"
                    p2.endsWith("ич") || p2.endsWith("на") -> "${parts[1]} ${parts[0]}"
                    else -> "${parts[0]} ${parts[1]}"
                }
            } else if (parts.size == 2) {
                return "${parts[0]} ${parts[1]}"
            }
            return full
        }

    val displayDirection: String?
        get() = studentDirection?.takeIf { it.isNotBlank() } ?: "Прикладная математика и информатика"

    val displayGroup: String?
        get() = studentGroup?.takeIf { it.isNotBlank() } ?: "5 группа"

    val displayFaculty: String?
        get() = facultyName?.takeIf { it.isNotBlank() } ?: "Институт математики, механики и компьютерных наук"

    val displayCourse: Int
        get() = courseNum ?: 2

    val displayDegree: String
        get() = when (degree?.trim()?.lowercase()) {
            "bachelor" -> "Бакалавриат"
            "master" -> "Магистратура"
            "specialist" -> "Специалитет"
            else -> degree?.takeIf { it.isNotBlank() } ?: "Бакалавриат"
        }

    val displayEmail: String?
        get() = email?.takeIf { it.isNotBlank() } ?: "vpar@sfedu.ru"

    val averageRate: Double?
        get() {
            val list = currentSemesterDisciplines.filter { it.MaxCurrentRate > 0 }
            if (list.isEmpty()) return null
            return list.sumOf { it.Rate }.toDouble() / list.size
        }

    val examCount: Int
        get() = currentSemesterDisciplines.count { it.Type == "exam" }

    val creditCount: Int
        get() = currentSemesterDisciplines.count {
            it.Type == "credit" || it.Type == "credit_grade" || it.Type == "grading_credit"
        }
}
