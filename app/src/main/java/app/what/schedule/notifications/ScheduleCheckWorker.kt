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
import app.what.domain.services.ReplacementDetector
import app.what.domain.services.ReplacementNotificationFormatter
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

            // Запускаем детектор замен
            val today = currentLocalDate()
            val rawKnownSignatures = appValues.lastNotifiedReplacementsHash.get()
            val knownSignatures = ReplacementDetector.deserializeSignatures(rawKnownSignatures)

            val detection = ReplacementDetector.detect(
                searchId = search.id,
                schedules = schedules,
                today = today,
                knownSignatures = knownSignatures,
                daysAhead = 2
            )

            // Всегда сохраняем обновленные сигнатуры (с очисткой устаревших дат)
            val updatedRaw = ReplacementDetector.serializeSignatures(detection.updatedSignatures)
            appValues.lastNotifiedReplacementsHash.set(updatedRaw)

            if (!detection.hasNewReplacements) {
                Auditor.debug(tag, "ScheduleCheckWorker: новых замен не обнаружено")
                return Result.success()
            }

            // Формируем чистое, информативное уведомление без дублирования дат
            val formatted = ReplacementNotificationFormatter.format(
                searchName = search.name,
                newReplacements = detection.newReplacements,
                today = today
            )

            NotificationHelper.showReplacementsNotification(
                context = applicationContext,
                title = formatted.title,
                content = formatted.summary,
                details = formatted.details
            )

            Auditor.info(tag, "ScheduleCheckWorker: успешно отправлено уведомление о ${detection.newReplacements.size} новых заменах")
            Result.success()
        } catch (e: Exception) {
            Auditor.debug(tag, "ScheduleCheckWorker ошибка проверки: ${e.message}")
            Result.retry()
        }
    }
}
