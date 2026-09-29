package app.what.schedule.notifications.university

import android.content.Context
import app.what.foundation.services.AppLogger.Companion.Auditor
import app.what.schedule.notifications.NotificationHelper

class UniversityNotificationManager(
    private val checkers: List<UniversityNotificationChecker>
) {
    suspend fun checkAll(context: Context, tag: String) {
        for (checker in checkers) {
            try {
                if (!checker.isAvailable()) continue

                Auditor.debug(tag, "UniversityNotificationManager: проверка уведомлений для ${checker.name} (${checker.institutionId})...")
                val notifications = checker.check()
                for (notification in notifications) {
                    NotificationHelper.showUniversityNotification(
                        context = context,
                        title = notification.title,
                        content = notification.content,
                        bigText = notification.bigText,
                        notificationId = notification.id
                    )
                    Auditor.info(tag, "UniversityNotificationManager: отправлено уведомление [${checker.institutionId}]: ${notification.title}")
                }
            } catch (e: Exception) {
                Auditor.debug(tag, "UniversityNotificationManager: ошибка проверки для ${checker.institutionId}: ${e.message}")
            }
        }
    }
}
