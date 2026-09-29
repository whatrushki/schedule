package app.what.schedule.notifications.university.checkers

import app.what.schedule.data.local.settings.AppValues
import app.what.schedule.dgtu.DGTUAccountClient
import app.what.schedule.notifications.university.UniversityNotification
import app.what.schedule.notifications.university.UniversityNotificationChecker

class DgtuNotificationChecker(
    private val appValues: AppValues,
    private val dgtuClient: DGTUAccountClient
) : UniversityNotificationChecker {

    override val institutionId: String = "dgtu"
    override val name: String = "ДГТУ Личный кабинет"

    override suspend fun isAvailable(): Boolean {
        return !appValues.dgtuToken.get().isNullOrBlank()
    }

    override suspend fun check(): List<UniversityNotification> {
        val token = appValues.dgtuToken.get() ?: return emptyList()
        val feedResponse = dgtuClient.getFeed(token)
        val feeds = feedResponse._data?.feed ?: return emptyList()
        val latestFeed = feeds.maxByOrNull { it.notificationID } ?: return emptyList()

        val lastNotified = appValues.lastNotifiedUniversityNotificationId.get()
        val latestIdStr = latestFeed.notificationID.toString()
        val shouldNotify = lastNotified.isNullOrBlank() || (latestFeed.notificationID > (lastNotified.toIntOrNull() ?: 0))

        if (!shouldNotify) return emptyList()

        val cleanText = (latestFeed.text ?: latestFeed.html ?: "").replace(Regex("<[^>]*>"), "").trim()
        val title = if (latestFeed.category.isNotBlank()) latestFeed.category else "Объявление ДГТУ"
        val notificationId = 3001 + (latestFeed.notificationID % 1000)

        appValues.lastNotifiedUniversityNotificationId.set(latestIdStr)

        return listOf(
            UniversityNotification(
                id = notificationId,
                title = title,
                content = cleanText,
                bigText = cleanText
            )
        )
    }
}
