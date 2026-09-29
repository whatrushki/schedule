package app.what.schedule.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.what.foundation.services.AppLogger.Companion.Auditor
import app.what.foundation.utils.LogCat
import app.what.foundation.utils.LogScope
import app.what.foundation.utils.buildTag
import app.what.schedule.data.local.settings.AppValues
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class FirstLessonAlarmReceiver : BroadcastReceiver(), KoinComponent {
    private val appValues: AppValues by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val tag = buildTag(LogScope.SCHEDULE, LogCat.UI)
        Auditor.info(tag, "FirstLessonAlarmReceiver: получен интент ${intent.action}")

        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            FirstLessonScheduler.scheduleNext(context)
            return
        }

        if (appValues.enableFirstLessonNotification.get() != true) {
            return
        }

        val subject = intent.getStringExtra("subject") ?: return
        val startTime = intent.getStringExtra("start_time") ?: ""
        val auditory = intent.getStringExtra("auditory")
        val building = intent.getStringExtra("building")
        val teacher = intent.getStringExtra("teacher")

        val roomParts = mutableListOf<String>()
        if (!auditory.isNullOrBlank()) roomParts.add("Ауд. $auditory")
        if (!building.isNullOrBlank()) roomParts.add("корп. $building")
        val roomInfo = roomParts.joinToString(", ")

        val title = if (startTime.isNotBlank()) "Первая пара в $startTime" else "Первая пара"
        val summary = if (roomInfo.isNotEmpty()) "$roomInfo • $subject" else subject
        val bigText = buildString {
            if (roomInfo.isNotEmpty()) appendLine(roomInfo)
            appendLine(subject)
            if (!teacher.isNullOrBlank()) appendLine(teacher)
            if (startTime.isNotBlank()) append("Начало в $startTime")
        }

        NotificationHelper.showFirstLessonNotification(
            context = context,
            title = title,
            content = summary,
            bigText = bigText
        )

        // Планируем напоминание на следующий учебный день
        FirstLessonScheduler.scheduleNext(context)
    }
}
