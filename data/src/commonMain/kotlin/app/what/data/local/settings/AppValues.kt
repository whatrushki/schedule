package app.what.schedule.data.local.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import app.what.foundation.data.settings.KeyValueStorage
import app.what.foundation.data.settings.Named
import app.what.foundation.data.settings.PreferenceEncryptor
import app.what.foundation.data.settings.PreferenceStorage
import app.what.domain.models.ScheduleSearch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

@Serializable
enum class ThemeType(override val displayName: String) : Named {
    Light("Светлая"),
    Dark("Тёмная"),
    System("Системная")
}

@Serializable
enum class ThemeStyle(override val displayName: String) : Named {
    Default("По умолчанию"),
    Material("Material"),
    CustomColor("Свой цвет"),
    Monochrome("Чёрно-белая")
}

@Serializable
enum class NotificationPeriod(val hours: Int, override val displayName: String) : Named {
    H1(1, "1 час"),
    H2(2, "2 часа"),
    H3(3, "3 часа"),
    H4(4, "4 часа"),
    H6(6, "6 часов"),
    H12(12, "12 часов")
}

@Serializable
enum class SubgroupPreference(val value: Int?, override val displayName: String) : Named {
    All(null, "Все"),
    First(1, "1 подгруппа"),
    Second(2, "2 подгруппа")
}


@Composable
fun ProvideGLobalAppValues(appValues: AppValues, content: @Composable () -> Unit) =
    CompositionLocalProvider(
        LocalAppValues provides appValues,
        content = content
    )


private val LocalAppValues = staticCompositionLocalOf<AppValues> {
    error("AppValues не предоставлен")
}

@Composable
fun rememberAppValues() = LocalAppValues.current

class AppValues(
    storage: KeyValueStorage,
    encryptor: PreferenceEncryptor? = null
) : PreferenceStorage(storage, encryptor) {
    val userId = createValue(
        "user_id", null, String.serializer(),
        "Идентификатор пользоваетля", "Уникальный ID установки"
    )
    
    val isFirstLaunch = createValue(
        "is_first_launch", true, Boolean.serializer(),
        "Первый запуск", "Отслеживание первого запуска приложения"
    )
    
    val lastSearch = createValue(
        "last_search", null, ScheduleSearch.serializer(),
        "Последний поиск", "Сохраненные параметры последнего поиска расписания"
    )
    
    val institution = createValue(
        "institution", null, String.serializer(),
        "Учебное заведение", "Выбранное учебное заведение для отображения расписания"
    )
    
    val themeType = createValue(
        "theme_type", ThemeType.Light, ThemeType.serializer(),
        "Тип темы", "Режим темы: светлая, темная или системная"
    )
    
    val themeStyle = createValue(
        "theme_style", ThemeStyle.Default, ThemeStyle.serializer(),
        "Стиль темы", "Визуальный стиль интерфейса"
    )
    
    val themeColor = createValue(
        "theme_color", Color(0xFF1F2137).value, ULong.serializer(),
        "Цвет темы", "Основной цвет оформления приложения"
    )
    
    val useAnimation = createValue(
        "use_animation", true, Boolean.serializer(),
        "Анимации", "Включение анимаций интерфейса"
    )
    
    val isAnalyticsEnabled = createValue(
        "is_analytics_enabled", true, Boolean.serializer(),
        "Анализ пользования", "Разрешите собирать анонимную статистику пользования"
    )

    val showCancelledLessons = createValue(
        "show_cancelled_lessons", true, Boolean.serializer(),
        "Отображать отмененные", "Показывать отмененные занятия в расписании"
    )

    val enableReplacementNotifications = createValue(
        "enable_replacement_notifications", false, Boolean.serializer(),
        "Уведомления о заменах", "Проверять изменения в расписании в фоне"
    )

    val notifyFavoritesReplacements = createValue(
        "notify_favorites_replacements", false, Boolean.serializer(),
        "Отслеживать избранное", "Проверять замены для групп и преподавателей из избранного"
    )

    val replacementNotificationsPeriod = createValue(
        "replacement_notifications_period", NotificationPeriod.H3, NotificationPeriod.serializer(),
        "Интервал проверки", "Периодичность проверки расписания"
    )

    val defaultSubgroup = createValue(
        "default_subgroup", SubgroupPreference.All, SubgroupPreference.serializer(),
        "Подгруппа по умолчанию", "Автоматический выбор вашей подгруппы в расписании"
    )

    val showNextDayInEvening = createValue(
        "show_next_day_in_evening", true, Boolean.serializer(),
        "Расписание на завтра вечером", "После 18:00 открывать следующий учебный день"
    )

    val enableProfileTab = createValue(
        "enable_profile_tab", true, Boolean.serializer(),
        "Вкладка профиля", "Отображать вкладку профиля в панели навигации"
    )

    val enableUniversityNotifications = createValue(
        "enable_university_notifications", false, Boolean.serializer(),
        "Уведомления из ЛК", "Периодически проверять сообщения и уведомления в личном кабинете"
    )

    val enableFirstLessonNotification = createValue(
        "enable_first_lesson_notification", false, Boolean.serializer(),
        "Первая пара", "Уведомление о месте проведения первой пары за 40 минут до начала"
    )

    val lastNotifiedUniversityNotificationId = createValue(
        "last_notified_univ_notif_id", "", String.serializer()
    )

    val lastNotifiedReplacementsHash = createValue(
        "last_notified_replacements_hash", "", String.serializer()
    )
    
    val thePolicy = createValue(
        "the_policy", null, String.serializer(),
        "Политика конфиденциальности", "Ознакомлены с политикой и условиями пользования"
    )
    
    val devSettingsUnlocked = createValue(
        "dev_settings_unlocked", false, Boolean.serializer(),
        "Настройки разработчика", "Доступ к настройкам разработчика"
    )
    
    val devPanelEnabled = createValue(
        "dev_panel_enabled", false, Boolean.serializer(),
        "Дебаг панель", "Панель для отслеживания действий в приложении"
    )
    
    val debugMode = createValue(
        "debug_mode", false, Boolean.serializer(),
        "Режим отладки", "Включение дополнительной информации для разработки"
    )
    
    // DGTU ---------------
    val dgtuToken = createValue(
        "dgtu_token",
        null,
        String.serializer(),
        isEncrypted = true,
    )
    
    val dgtuStudentId = createValue(
        "dgtu_userId",
        null,
        Int.serializer(),
        isEncrypted = true,
    )

    // RKSI ---------------
    val rksiCookies = createValue(
        "rksi_cookies",
        null,
        String.serializer(),
        isEncrypted = true,
    )

    val rksiLogin = createValue(
        "rksi_login",
        null,
        String.serializer(),
        isEncrypted = true,
    )

    val rksiPassword = createValue(
        "rksi_password",
        null,
        String.serializer(),
        isEncrypted = true,
    )

    // SFEDU Grade ---------------
    val sfeduGradeToken = createValue(
        "sfedu_grade_token",
        null,
        String.serializer(),
        isEncrypted = true,
    )
    val sfeduStudentName = createValue(
        "sfedu_student_name",
        null,
        String.serializer(),
    )
    val sfeduStudentGroup = createValue(
        "sfedu_student_group",
        null,
        String.serializer(),
    )
    val sfeduStudentDirection = createValue(
        "sfedu_student_direction",
        null,
        String.serializer(),
    )
    val sfeduStudentEmail = createValue(
        "sfedu_student_email",
        null,
        String.serializer(),
    )
    val sfeduLastKnownGrades = createValue(
        "sfedu_last_known_grades",
        "",
        String.serializer(),
    )
}


