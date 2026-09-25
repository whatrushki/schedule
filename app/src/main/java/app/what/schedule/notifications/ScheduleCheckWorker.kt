package app.what.schedule.notifications

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.what.domain.models.DaySchedule
import app.what.domain.models.Lesson
import app.what.domain.models.LessonState
import app.what.domain.models.ScheduleResponse
import app.what.domain.repositories.ScheduleRepository
import app.what.foundation.services.AppLogger.Companion.Auditor
import app.what.foundation.utils.LogCat
import app.what.foundation.utils.LogScope
import app.what.foundation.utils.buildTag
import app.what.foundation.utils.currentLocalDate
import app.what.schedule.data.local.settings.AppValues
import app.what.schedule.data.remote.utils.formatTime
import app.what.schedule.features.widget.ScheduleWidget
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ScheduleCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    private val appValues: AppValues by inject()
    private val scheduleRepository: ScheduleRepository by inject()

    override suspend fun doWork(): Result {
        val tag = buildTag(LogScope.CORE, LogCat.NET)
        Auditor.info(tag, "ScheduleCheckWorker: запуск фоновой проверки замен")

        if (appValues.enableReplacementNotifications.get() != true) {
            Auditor.debug(tag, "ScheduleCheckWorker: уведомления отключены в настройках")
            return Result.success()
        }

        val search = appValues.lastSearch.get() ?: run {
            Auditor.debug(tag, "ScheduleCheckWorker: группа не выбрана")
            return Result.success()
        }

        return try {
            val response = scheduleRepository.getSchedule(
                search = search,
                useCache = false,
                requiresData = true,
                forceLive = false
            )

            val schedules: List<DaySchedule> = when (response) {
                is ScheduleResponse.Available -> response.schedules
                else -> {
                    Auditor.debug(tag, "ScheduleCheckWorker: расписание не получено ($response)")
                    return Result.success()
                }
            }

            // Автоматически обновляем виджеты свежими данными
            try {
                ScheduleWidget.instance.updateAll(applicationContext)
            } catch (_: Exception) {
            }

            // Находим замены на сегодня и ближайшие 2 дня
            val today = currentLocalDate()
            val nextDays = (0..2).map { today.plus(it, DateTimeUnit.DAY) }

            val relevantDays = schedules.filter { it.date in nextDays }
            val replacementLessons = mutableListOf<Pair<DaySchedule, Lesson>>()

            for (day in relevantDays) {
                for (lesson in day.lessons) {
                    if (lesson.state != LessonState.COMMON) {
                        replacementLessons.add(day to lesson)
                    }
                }
            }

            if (replacementLessons.isEmpty()) {
                Auditor.debug(tag, "ScheduleCheckWorker: замен на ближайшие дни не найдено")
                return Result.success()
            }

            // Вычисляем уникальную сигнатуру замен, чтобы не спамить повторно
            val signature = replacementLessons.joinToString(";") { (day, lesson) ->
                "${day.date}_${lesson.number}_${lesson.subject}_${lesson.state}"
            }

            val lastSignature = appValues.lastNotifiedReplacementsHash.get()
            if (signature == lastSignature) {
                Auditor.debug(tag, "ScheduleCheckWorker: замены уже были отправлены ранее, пропускаем")
                return Result.success()
            }

            // Запоминаем отправленную сигнатуру
            appValues.lastNotifiedReplacementsHash.set(signature)

            // Формируем текст уведомления
            val title = "Замены в расписании (${search.name})"
            val details = replacementLessons.map { (day, lesson) ->
                val dayLabel = when (day.date) {
                    today -> "Сегодня"
                    today.plus(1, DateTimeUnit.DAY) -> "Завтра"
                    today.plus(2, DateTimeUnit.DAY) -> "Послезавтра"
                    else -> "${day.date.dayOfMonth}.${day.date.monthNumber}"
                }
                val stateLabel = when (lesson.state) {
                    LessonState.ADDED -> "добавлена"
                    LessonState.REMOVED -> "отменена"
                    LessonState.CHANGED -> "изменена"
                    LessonState.COMMON -> ""
                }
                val subject = lesson.subject.ifBlank { "Пара ${lesson.number}" }
                val timeStr = formatTime(lesson.startTime)
                "$dayLabel: $subject ($stateLabel в $timeStr)"
            }

            val summary = details.take(2).joinToString(", ")

            NotificationHelper.showReplacementsNotification(
                context = applicationContext,
                title = title,
                content = summary,
                details = details
            )

            Auditor.info(tag, "ScheduleCheckWorker: успешно отправлено уведомление о ${replacementLessons.size} заменах")
            Result.success()
        } catch (e: Exception) {
            Auditor.debug(tag, "ScheduleCheckWorker ошибка проверки: ${e.message}")
            Result.retry()
        }
    }
}
