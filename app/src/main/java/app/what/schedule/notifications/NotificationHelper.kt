package app.what.schedule.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.what.domain.models.ScheduleSearch
import app.what.schedule.MainActivity
import app.what.schedule.ScheduleActivity
import app.what.schedule.R

object NotificationHelper {
    const val CHANNEL_ID = "schedule_replacements"
    const val CHANNEL_UNIVERSITY_ID = "university_notifications"
    const val CHANNEL_FIRST_LESSON_ID = "first_lesson_notifications"
    const val NOTIFICATION_FIRST_LESSON_ID = 4001

    const val EXTRA_TARGET_SCREEN = "extra_target_screen"
    const val EXTRA_SEARCH_ID = "extra_search_id"
    const val EXTRA_SEARCH_NAME = "extra_search_name"
    const val EXTRA_SEARCH_TYPE = "extra_search_type"
    const val EXTRA_INSTITUTION_ID = "extra_institution_id"

    const val SCREEN_SCHEDULE = "screen_schedule"
    const val SCREEN_NOTIFICATIONS = "screen_notifications"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val repName = "Замены в расписании"
            val repDesc = "Уведомления об изменениях, отменах и добавлениях занятий"
            val repChannel = NotificationChannel(CHANNEL_ID, repName, NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = repDesc
                enableLights(true)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(repChannel)

            val uniName = "Уведомления университета"
            val uniDesc = "Важные сообщения и объявления из личного кабинета"
            val uniChannel = NotificationChannel(CHANNEL_UNIVERSITY_ID, uniName, NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = uniDesc
                enableLights(true)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(uniChannel)

            val flName = "Первая пара"
            val flDesc = "Напоминание о месте и предмете за 40 минут до начала первой пары"
            val flChannel = NotificationChannel(CHANNEL_FIRST_LESSON_ID, flName, NotificationManager.IMPORTANCE_HIGH).apply {
                description = flDesc
                enableLights(true)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(flChannel)
        }
    }

    private fun getLaunchIntent(context: Context): Intent {
        val pmIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        return (pmIntent ?: Intent(context, ScheduleActivity::class.java)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
    }

    fun showReplacementsNotification(
        context: Context,
        title: String,
        content: String,
        details: List<String> = emptyList(),
        search: ScheduleSearch? = null,
        notificationId: Int = NOTIFICATION_FIRST_LESSON_ID - 1000
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        val intent = getLaunchIntent(context).apply {
            putExtra(EXTRA_TARGET_SCREEN, SCREEN_SCHEDULE)
            if (search != null) {
                putExtra(EXTRA_SEARCH_ID, search.id)
                putExtra(EXTRA_SEARCH_NAME, search.name)
                putExtra(EXTRA_SEARCH_TYPE, if (search is ScheduleSearch.Teacher) "teacher" else "group")
                putExtra(EXTRA_INSTITUTION_ID, search.institutionId)
            }
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(content)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        if (details.isNotEmpty()) {
            val inboxStyle = NotificationCompat.InboxStyle()
                .setBigContentTitle(title)
                .setSummaryText("${details.size} ${getReplacementWord(details.size)}")
            details.forEach { inboxStyle.addLine(it) }
            builder.setStyle(inboxStyle)
        }

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (_: SecurityException) {
            // Permission might have been revoked in runtime
        }
    }

    fun showUniversityNotification(
        context: Context,
        title: String,
        content: String,
        bigText: String? = null,
        notificationId: Int = 3001
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        val intent = getLaunchIntent(context).apply {
            putExtra(EXTRA_TARGET_SCREEN, SCREEN_NOTIFICATIONS)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_UNIVERSITY_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText ?: content))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (_: SecurityException) {
        }
    }

    fun showFirstLessonNotification(
        context: Context,
        title: String,
        content: String,
        bigText: String? = null,
        notificationId: Int = NOTIFICATION_FIRST_LESSON_ID
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        val intent = getLaunchIntent(context).apply {
            putExtra(EXTRA_TARGET_SCREEN, SCREEN_SCHEDULE)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_FIRST_LESSON_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(content)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        if (!bigText.isNullOrBlank()) {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
        }

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (_: SecurityException) {
        }
    }

    private fun getReplacementWord(count: Int): String {
        val rem100 = count % 100
        val rem10 = count % 10
        return when {
            rem100 in 11..19 -> "замен"
            rem10 == 1 -> "замена"
            rem10 in 2..4 -> "замены"
            else -> "замен"
        }
    }
}
