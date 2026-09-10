package app.what.foundation.utils

import app.what.foundation.services.AppLogger.Companion.Auditor

interface AnalyticsTracker {
    fun setAnalyticsCollectionEnabled(enabled: Boolean) {}
    fun logUniversitySelect(uniName: String) {}
    fun logScheduleRequest(targetName: String, type: String) {}
    fun logNewsOpen(newsId: String, link: String, title: String) {}
    fun logSettingChanged(settingKey: String, value: String) {}
    fun logShare(contentType: String, url: String) {}
    fun logEasterEggFound(method: String) {}
    fun logDevPanelOpen() {}
    fun logScreenView(screenName: String) {}
}

class ConsoleAnalyticsTracker : AnalyticsTracker {
    override fun logUniversitySelect(uniName: String) {
        Auditor.debug("Analytics", "select_university: $uniName")
    }
    override fun logScheduleRequest(targetName: String, type: String) {
        Auditor.debug("Analytics", "request_schedule: $targetName ($type)")
    }
    override fun logNewsOpen(newsId: String, link: String, title: String) {
        Auditor.debug("Analytics", "news_view_detail: $newsId - $title")
    }
    override fun logSettingChanged(settingKey: String, value: String) {
        Auditor.debug("Analytics", "settings_update: $settingKey = $value")
    }
    override fun logShare(contentType: String, url: String) {
        Auditor.debug("Analytics", "share: $contentType -> $url")
    }
    override fun logEasterEggFound(method: String) {
        Auditor.info("Analytics", "easter_egg_unlocked: $method")
    }
    override fun logDevPanelOpen() {
        Auditor.debug("Analytics", "dev_panel_open")
    }
    override fun logScreenView(screenName: String) {
        Auditor.debug("Analytics", "screen_view: $screenName")
    }
}

object Analytics : AnalyticsTracker {
    var tracker: AnalyticsTracker = ConsoleAnalyticsTracker()

    override fun setAnalyticsCollectionEnabled(enabled: Boolean) = tracker.setAnalyticsCollectionEnabled(enabled)
    override fun logUniversitySelect(uniName: String) = tracker.logUniversitySelect(uniName)
    override fun logScheduleRequest(targetName: String, type: String) = tracker.logScheduleRequest(targetName, type)
    override fun logNewsOpen(newsId: String, link: String, title: String) = tracker.logNewsOpen(newsId, link, title)
    override fun logSettingChanged(settingKey: String, value: String) = tracker.logSettingChanged(settingKey, value)
    override fun logShare(contentType: String, url: String) = tracker.logShare(contentType, url)
    override fun logEasterEggFound(method: String) = tracker.logEasterEggFound(method)
    override fun logDevPanelOpen() = tracker.logDevPanelOpen()
    override fun logScreenView(screenName: String) = tracker.logScreenView(screenName)
}