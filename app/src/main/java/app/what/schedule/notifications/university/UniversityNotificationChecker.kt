package app.what.schedule.notifications.university

interface UniversityNotificationChecker {
    /**
     * University or institution identifier (e.g. "dgtu", "sfedu", "rksi").
     */
    val institutionId: String

    /**
     * Human-readable display name for logging and auditing.
     */
    val name: String

    /**
     * Returns true if this checker is eligible to run (e.g., user is logged in with valid token).
     */
    suspend fun isAvailable(): Boolean

    /**
     * Checks for new notifications and updates internal state (e.g. last seen id or grades snapshot).
     * Returns a list of new notifications that should be shown to the user.
     */
    suspend fun check(): List<UniversityNotification>
}
