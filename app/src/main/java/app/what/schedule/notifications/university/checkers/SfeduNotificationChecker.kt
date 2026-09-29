package app.what.schedule.notifications.university.checkers

import app.what.schedule.data.local.settings.AppValues
import app.what.schedule.notifications.university.UniversityNotification
import app.what.schedule.notifications.university.UniversityNotificationChecker
import app.what.schedule.sfedu.grade.SfeduGradeClient
import app.what.schedule.sfedu.grade.SfeduGradeTokenInvalidException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SfeduNotificationChecker(
    private val appValues: AppValues,
    private val sfeduGradeClient: SfeduGradeClient
) : UniversityNotificationChecker {

    override val institutionId: String = "sfedu"
    override val name: String = "БРС ЮФУ"

    override suspend fun isAvailable(): Boolean {
        return !appValues.sfeduGradeToken.get().isNullOrBlank()
    }

    override suspend fun check(): List<UniversityNotification> {
        val token = appValues.sfeduGradeToken.get() ?: return emptyList()

        val disciplines = try {
            val studentData = sfeduGradeClient.getStudentData(token)
            studentData.Disciplines.filter { !it.Hidden && !it.WasRemoved }
        } catch (_: SfeduGradeTokenInvalidException) {
            return emptyList()
        } catch (e: Exception) {
            throw e
        }

        if (disciplines.isEmpty()) return emptyList()

        val currentMap = disciplines.associate { it.ID.toString() to it.Rate }
        val rawSnapshot = appValues.sfeduLastKnownGrades.get()

        // Первый запуск после входа: сохраняем базовый снимок баллов без отправки уведомления
        if (rawSnapshot.isNullOrBlank()) {
            appValues.sfeduLastKnownGrades.set(Json.encodeToString(currentMap))
            return emptyList()
        }

        val oldMap: Map<String, Int> = try {
            Json.decodeFromString(rawSnapshot)
        } catch (_: Exception) {
            emptyMap()
        }

        data class DisciplineChange(
            val id: Int,
            val name: String,
            val oldRate: Int,
            val newRate: Int
        ) {
            val diff: Int get() = newRate - oldRate
        }

        val changes = mutableListOf<DisciplineChange>()
        for (disc in disciplines) {
            val key = disc.ID.toString()
            val oldRate = oldMap[key]
            if (oldRate != null) {
                if (disc.Rate != oldRate) {
                    changes.add(DisciplineChange(disc.ID, disc.SubjectName, oldRate, disc.Rate))
                }
            } else if (disc.Rate > 0) {
                changes.add(DisciplineChange(disc.ID, disc.SubjectName, 0, disc.Rate))
            }
        }

        // Сохраняем актуальный снимок баллов
        appValues.sfeduLastKnownGrades.set(Json.encodeToString(currentMap))

        if (changes.isEmpty()) return emptyList()

        return if (changes.size == 1) {
            val change = changes.first()
            val diffStr = if (change.diff > 0) "+${change.diff}" else "${change.diff}"
            val title = if (change.diff > 0) "БРС ЮФУ: Новые баллы" else "БРС ЮФУ: Изменение баллов"
            val text = "${change.name}: ${change.oldRate} → ${change.newRate} ($diffStr)"
            val notifId = 3501 + (change.id % 400)
            listOf(
                UniversityNotification(
                    id = notifId,
                    title = title,
                    content = text,
                    bigText = text
                )
            )
        } else {
            val title = "БРС ЮФУ: Новые баллы (${changes.size})"
            val summary = "Обновлены баллы по ${changes.size} предметам"
            val details = changes.joinToString("\n") { ch ->
                val diffStr = if (ch.diff > 0) "+${ch.diff}" else "${ch.diff}"
                "• ${ch.name}: ${ch.oldRate} → ${ch.newRate} ($diffStr)"
            }
            val notifId = 3501 + (changes.first().id % 400)
            listOf(
                UniversityNotification(
                    id = notifId,
                    title = title,
                    content = summary,
                    bigText = "$summary\n$details"
                )
            )
        }
    }
}
