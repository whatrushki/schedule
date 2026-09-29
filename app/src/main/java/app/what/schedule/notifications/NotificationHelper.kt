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
import app.what.schedule.MainActivity
import app.what.schedule.R

object NotificationHelper {
    const val CHANNEL_ID = "schedule_replacements"
    const val CHANNEL_UNIVERSITY_ID = "university_notifications"
    const val CHANNEL_FIRST_LESSON_ID = "first_lesson_notification"
    private const val NOTIFICATION_ID = 2001
    const val NOTIFICATION_FIRST_LESSON_ID = 4001

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

    fun showReplacementsNotification(
        context: Context,
        title: String,
        content: String,
        details: List<String> = emptyList(),
        notificationId: Int = NOTIFICATION_ID
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

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
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

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            1,
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

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            2,
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
