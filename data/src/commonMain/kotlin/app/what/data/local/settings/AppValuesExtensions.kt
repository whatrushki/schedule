package app.what.data.local.settings

import androidx.compose.ui.Modifier
import app.what.foundation.core.UIComponent
import app.what.foundation.data.settings.types.asSingleChoice
import app.what.foundation.data.settings.types.asSwitch
import app.what.schedule.data.local.settings.AppValues
import app.what.schedule.data.local.settings.NotificationPeriod
import app.what.schedule.data.local.settings.SubgroupPreference
import app.what.schedule.data.local.settings.ThemeStyle
import app.what.schedule.data.local.settings.ThemeType

/**
 * Ищет UIComponent настройки по её id (ключу) из AppValues.
 * Автоматически подбирает нужный тип компонента (Switch, SingleChoice и т.д.)
 * на основе типа поля в AppValues.
 */
fun AppValues.findSettingComponent(key: String, modifier: Modifier = Modifier): UIComponent? {
    return when (key) {
        // Boolean переключатели
        showNextDayInEvening.key -> showNextDayInEvening.asSwitch()
        showCancelledLessons.key -> showCancelledLessons.asSwitch()
        enableProfileTab.key -> enableProfileTab.asSwitch()
        enableReplacementNotifications.key -> enableReplacementNotifications.asSwitch()
        notifyFavoritesReplacements.key -> notifyFavoritesReplacements.asSwitch()
        enableUniversityNotifications.key -> enableUniversityNotifications.asSwitch()
        enableFirstLessonNotification.key -> enableFirstLessonNotification.asSwitch()
        useAnimation.key -> useAnimation.asSwitch()
        isAnalyticsEnabled.key -> isAnalyticsEnabled.asSwitch()
        devSettingsUnlocked.key -> devSettingsUnlocked.asSwitch()
        devPanelEnabled.key -> devPanelEnabled.asSwitch()
        debugMode.key -> debugMode.asSwitch()

        // Enum / Выбор значений
        themeType.key -> themeType.asSingleChoice(ThemeType.entries.toTypedArray(), onDisplay = { it.displayName })
        themeStyle.key -> themeStyle.asSingleChoice(ThemeStyle.entries.toTypedArray(), onDisplay = { it.displayName })
        defaultSubgroup.key -> defaultSubgroup.asSingleChoice(SubgroupPreference.entries.toTypedArray(), onDisplay = { it.displayName })
        replacementNotificationsPeriod.key -> replacementNotificationsPeriod.asSingleChoice(NotificationPeriod.entries.toTypedArray(), onDisplay = { it.displayName })

        else -> null
    }
}

fun AppValues.findSwitchSettingComponent(key: String, modifier: Modifier = Modifier): UIComponent? =
    findSettingComponent(key, modifier)
