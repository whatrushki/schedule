package app.what.schedule.notifications.university

data class UniversityNotification(
    val id: Int,
    val title: String,
    val content: String,
    val bigText: String? = null
)
