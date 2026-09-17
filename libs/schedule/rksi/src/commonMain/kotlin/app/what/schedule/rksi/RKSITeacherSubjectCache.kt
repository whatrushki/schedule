package app.what.schedule.rksi

import app.what.schedule.core.models.LessonDto

object RKSITeacherSubjectCache {
    private val groupTeacherToSubject = mutableMapOf<String, String>()
    private val teacherToSubject = mutableMapOf<String, String>()

    private fun normalize(s: String): String = s
        .replace(" ", "")
        .replace("-", "")
        .replace("—", "")
        .replace("–", "")
        .replace(".", "")
        .replace("c", "с", ignoreCase = true)
        .replace("a", "а", ignoreCase = true)
        .replace("e", "е", ignoreCase = true)
        .replace("o", "о", ignoreCase = true)
        .replace("p", "р", ignoreCase = true)
        .replace("x", "х", ignoreCase = true)
        .trim()
        .lowercase()

    fun record(group: String, teacher: String, subject: String) {
        val trimmedSubject = subject.trim()
        if (trimmedSubject.isBlank() || trimmedSubject.contains("Классный", ignoreCase = true) || trimmedSubject == "Предмет не указан") return

        val cleanG = normalize(group)
        val cleanT = normalize(teacher)
        if (cleanT.isNotEmpty()) {
            if (cleanG.isNotEmpty()) {
                groupTeacherToSubject["${cleanG}_$cleanT"] = trimmedSubject
            }
            teacherToSubject[cleanT] = trimmedSubject
        }
    }

    fun recordLessons(lessons: Iterable<LessonDto>) {
        lessons.forEach { lesson ->
            lesson.otUnits.forEach { unit ->
                record(unit.group, unit.teacher, lesson.subject)
            }
        }
    }

    fun resolve(teacher: String, group: String = ""): String? {
        val cleanT = normalize(teacher)
        if (cleanT.isEmpty()) return null

        val cleanG = normalize(group)
        if (cleanG.isNotEmpty()) {
            val directKey = "${cleanG}_$cleanT"
            groupTeacherToSubject[directKey]?.let { return it }

            val match = groupTeacherToSubject.entries.firstOrNull { (k, _) ->
                if (k.startsWith("${cleanG}_")) {
                    val entryT = k.removePrefix("${cleanG}_")
                    entryT == cleanT || entryT.startsWith(cleanT) || cleanT.startsWith(entryT)
                } else false
            }
            if (match != null) return match.value
        }

        teacherToSubject[cleanT]?.let { return it }

        val teacherMatch = teacherToSubject.entries.firstOrNull { (k, _) ->
            k == cleanT || k.startsWith(cleanT) || cleanT.startsWith(k)
        }
        return teacherMatch?.value
    }

    fun clear() {
        groupTeacherToSubject.clear()
        teacherToSubject.clear()
    }
}
