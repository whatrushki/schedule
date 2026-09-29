package app.what.schedule.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import app.what.domain.models.LessonState
import app.what.domain.models.ScheduleResponse
import app.what.domain.models.toScheduleSearch
import app.what.domain.repositories.ScheduleRepository
import app.what.foundation.services.AppLogger.Companion.Auditor
import app.what.foundation.utils.LogCat
import app.what.foundation.utils.LogScope
import app.what.foundation.utils.buildTag
import app.what.foundation.utils.currentLocalDate
import app.what.schedule.data.local.settings.AppValues
import app.what.schedule.data.remote.utils.formatTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

object FirstLessonScheduler : KoinComponent {
    const val REQUEST_CODE = 4040

    fun scheduleNext(context: Context) {
        val appValues: AppValues by inject()
        val scheduleRepository: ScheduleRepository by inject()

        val tag = buildTag(LogScope.SCHEDULE, LogCat.UI)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val cancelIntent = Intent(context, FirstLessonAlarmReceiver::class.java)
        val pendingCancel = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            cancelIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )

        if (appValues.enableFirstLessonNotification.get() != true) {
            if (pendingCancel != null) {
                alarmManager?.cancel(pendingCancel)
                pendingCancel.cancel()
                Auditor.debug(tag, "FirstLessonScheduler: уведомления выключены, будильник снят")
            }
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val search = appValues.lastSearch.get() ?: run {
                    scheduleRepository.getAllFavoriteGroups().firstOrNull()?.toScheduleSearch()
                } ?: run {
                    Auditor.debug(tag, "FirstLessonScheduler: нет выбранной группы")
                    return@launch
                }

                val response = scheduleRepository.getSchedule(search, useCache = true, requiresData = false)
                val schedules = when (response) {
                    is ScheduleResponse.Available -> response.schedules
                    is ScheduleResponse.Error -> response.cachedSchedules ?: emptyList()
                    else -> emptyList()
                }

                if (schedules.isEmpty()) {
                    Auditor.debug(tag, "FirstLessonScheduler: расписание пустое для ${search.name}")
                    return@launch
                }

                val today = currentLocalDate()
                val nowMillis = System.currentTimeMillis()

                val upcomingDays = schedules
                    .filter { it.date >= today }
                    .sortedBy { it.date }

                for (day in upcomingDays) {
                    val activeLessons = day.lessons.filter { it.state != LessonState.REMOVED }
                    if (activeLessons.isEmpty()) continue

                    val firstLesson = activeLessons.minByOrNull { it.number } ?: continue
                    val lessonDateTime = LocalDateTime(day.date, firstLesson.startTime)
                    val triggerMillis = lessonDateTime.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds() - 40 * 60 * 1000

                    if (triggerMillis > nowMillis) {
                        val unit = firstLesson.otUnits.firstOrNull()
                        val alarmIntent = Intent(context, FirstLessonAlarmReceiver::class.java).apply {
                            action = "app.what.schedule.FIRST_LESSON_ALARM"
                            putExtra("date", day.date.toString())
                            putExtra("subject", firstLesson.subject)
                            putExtra("start_time", formatTime(firstLesson.startTime))
                            putExtra("auditory", unit?.auditory?.takeIf { it.isNotBlank() && it != "-" })
                            putExtra("building", unit?.building?.takeIf { it.isNotBlank() && it != "-" })
                            putExtra("teacher", unit?.teacher?.name?.takeIf { it.isNotBlank() && it != "-" })
                        }

                        val pendingIntent = PendingIntent.getBroadcast(
                            context,
                            REQUEST_CODE,
                            alarmIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )

                        alarmManager?.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerMillis,
                            pendingIntent
                        )

                        val diffMinutes = (triggerMillis - nowMillis) / 60000
                        Auditor.info(tag, "FirstLessonScheduler: запланировано напоминание на ${day.date} ${firstLesson.startTime} (через $diffMinutes мин) о ${firstLesson.subject}")
                        return@launch
                    }
                }
                Auditor.debug(tag, "FirstLessonScheduler: нет предстоящих первых пар")
            } catch (e: Exception) {
                Auditor.err(tag, "FirstLessonScheduler: ошибка планирования", e)
            }
        }
    }
}
