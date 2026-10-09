package app.what.schedule.features.settings.presentation

import app.what.foundation.ui.PlatformBackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.shapes
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.what.domain.repositories.ScheduleRepository
import app.what.foundation.core.Listener
import app.what.foundation.core.UIComponent
import app.what.foundation.data.settings.dependsOn
import app.what.foundation.data.settings.types.asColorPalette
import app.what.foundation.data.settings.types.asSheet
import app.what.foundation.data.settings.types.asSingleChoice
import app.what.foundation.data.settings.types.asSwitch
import app.what.domain.services.ReleaseHighlight
import app.what.domain.services.ReleaseNotes
import app.what.features.onboarding.presentation.UpdateOnboardingContent
import app.what.foundation.services.AppLogger.Companion.Auditor
import app.what.foundation.services.AppNotification
import app.what.foundation.services.Event
import app.what.foundation.services.LocalNotificationService
import app.what.foundation.ui.Gap
import app.what.foundation.ui.Show
import app.what.foundation.ui.animations.AnimatedEnter
import app.what.foundation.ui.bclick
import app.what.foundation.ui.components.PolicyView
import app.what.foundation.ui.controllers.rememberDialogController
import app.what.foundation.ui.keyboardAsState
import app.what.foundation.utils.Analytics
import app.what.foundation.utils.AppUtils
import app.what.schedule.data.local.settings.AppValues
import app.what.schedule.data.local.settings.NotificationPeriod
import app.what.schedule.data.local.settings.SubgroupPreference
import app.what.schedule.data.local.settings.ThemeStyle
import app.what.schedule.data.local.settings.ThemeType
import app.what.schedule.features.settings.domain.models.SettingsEvent
import app.what.schedule.features.settings.domain.models.SettingsState
import app.what.schedule.features.settings.presentation.components.AboutAppContent
import app.what.schedule.features.settings.presentation.components.asInstitutionChoice
import app.what.foundation.ui.icons.WHATIcons
import app.what.foundation.ui.icons.filled.Clear
import app.what.foundation.ui.icons.filled.Code
import app.what.foundation.ui.icons.filled.ImageRoller
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

// Внутреннее состояние для под-экрана
private data class SubScreenState(
    val title: String,
    val description: String,
    val content: List<UIComponent>
)

interface SettingsCategoryComponent : UIComponent {
    val title: String
    val description: String
    val icon: ImageVector
    val content: List<UIComponent>
}

@Composable
fun SettingsView(
    state: SettingsState,
    listener: Listener<SettingsEvent>
) {
    val appValues: AppValues = koinInject()
    val appUtils: AppUtils = koinInject()
    val scheduleRepository: ScheduleRepository = koinInject()
    val notificationService = LocalNotificationService.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState { 2 }
    
    var activeCategoryTitle by rememberSaveable { mutableStateOf<String?>(null) }
    var subScreen by remember { mutableStateOf<SubScreenState?>(null) }
    var showClearCacheDialog by remember { mutableStateOf(false) }

    val rootComponents = getSettingsList(appValues, appUtils) {
        showClearCacheDialog = true
    }

    // Восстановление под-экрана после поворота или смены темы
    if (pagerState.currentPage != 0 && subScreen == null) {
        if (activeCategoryTitle != null) {
            val matched = rootComponents.filterIsInstance<SettingsCategoryComponent>()
                .firstOrNull { it.title == activeCategoryTitle }
            if (matched != null) {
                subScreen = SubScreenState(matched.title, matched.description, matched.content)
            } else {
                LaunchedEffect(Unit) {
                    pagerState.scrollToPage(0)
                    activeCategoryTitle = null
                }
            }
        } else {
            LaunchedEffect(Unit) {
                pagerState.scrollToPage(0)
            }
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage == 0) {
            activeCategoryTitle = null
            subScreen = null
        }
    }

    val navigateToSubScreen: (String, String, List<UIComponent>) -> Unit = { title, desc, list ->
        activeCategoryTitle = title
        subScreen = SubScreenState(title, desc, list)
        scope.launch { pagerState.animateScrollToPage(1) }
    }
    
    PlatformBackHandler(pagerState.currentPage != 0) {
        scope.launch {
            pagerState.animateScrollToPage(0)
            activeCategoryTitle = null
            subScreen = null
        }
    }

    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text("Очистить кэш расписания?") },
            text = { Text("Все сохранённые данные расписания будут удалены. При следующем переходе к группе они загрузятся заново.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearCacheDialog = false
                        scope.launch {
                            try {
                                scheduleRepository.clearScheduleCache()
                                notificationService?.notify(
                                    AppNotification(
                                        title = "Кэш очищен",
                                        message = "Кэш расписания успешно удалён",
                                        urgency = Event.Urgency.LOW
                                    )
                                )
                            } catch (e: Exception) {
                                Auditor.debug("Settings", "Ошибка очистки кэша: ${e.message}")
                            }
                        }
                    }
                ) {
                    Text("Очистить", color = colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
    
    CompositionLocalProvider(LocalSettingsNavigator provides navigateToSubScreen) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = 760.dp)
        ) {
            val isKeyboardOpen = keyboardAsState().value
            val headerHeight = if (isKeyboardOpen) 20.dp else (maxHeight * 0.28f).coerceIn(160.dp, 240.dp)

            Column(Modifier.fillMaxSize()) {
                val defaultDesc = remember {
                    listOf(
                        "( ˶°ㅁ°) !!",
                        "(๑ᵔ⤙ᵔ๑)",
                        "(˶ˆᗜˆ˵)",
                        "◝(ᵔᗜᵔ)◜",
                        "⸜(｡˃ ᵕ ˂ )⸝♡",
                        "(๑>◡<๑)",
                        "(˶˃⤙˶)"
                    ).random()
                }

                val headerTitle = if (pagerState.currentPage == 0) "Настройки"
                else subScreen?.title ?: ""
                
                val headerDesc = if (pagerState.currentPage == 0) defaultDesc
                else subScreen?.description ?: ""
                
                SettingsHeader(
                    title = headerTitle,
                    description = headerDesc,
                    headerHeight = headerHeight
                )
                
                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = pagerState.currentPage != 0,
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.Top
                ) { page ->
                    LazyColumn(Modifier.fillMaxSize()) {
                        if (page == 0) {
                            // Главная страница
                            item { SettingUpdateComponent.content(Modifier) }
                            
                            items(rootComponents.size) { index ->
                                rootComponents[index].content(Modifier)
                            }
                        } else {
                            // Вторая страница
                            val components = subScreen?.content ?: emptyList()
                            items(components.size) { index ->
                                components[index].content(Modifier)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsHeader(
    title: String,
    description: String,
    headerHeight: Dp
) = Box(
    Modifier
        .fillMaxWidth()
        .height(headerHeight)
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = (headerHeight - 90.dp).coerceAtLeast(16.dp))
    ) {
        AnimatedEnter {
            Text(
                text = title,
                style = typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                fontSize = 46.sp,
                color = colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 16.dp)
            )
        }
        
        AnimatedEnter(delay = 100) {
            Text(
                text = description,
                style = typography.titleLarge,
                fontStyle = FontStyle.Italic,
                fontFamily = FontFamily.Monospace,
                color = colorScheme.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 16.dp, top = 2.dp)
            )
        }
    }
}

@Composable
fun getSettingsList(
    app: AppValues,
    utils: AppUtils,
    onClearCacheClick: () -> Unit
): List<UIComponent> {
    val dialog = rememberDialogController()
    val isDevUnlocked by app.devSettingsUnlocked.collect()
    
    return listOf(
        category(
            "Расписание", "вуз, подгруппа, пары", Icons.Default.DateRange,
            content = listOf(
                app.institution.asInstitutionChoice {
                    Analytics.logUniversitySelect(it ?: "not selected")
                    app.lastSearch.set(null)
                },
                app.defaultSubgroup.asSingleChoice(
                    enumValues<SubgroupPreference>(),
                    { it.displayName }
                ) {
                    Analytics.logSettingChanged(app.defaultSubgroup.key, it.toString())
                },
                app.showNextDayInEvening.asSwitch {
                    Analytics.logSettingChanged(app.showNextDayInEvening.key, it.toString())
                },
                app.showCancelledLessons.asSwitch {
                    Analytics.logSettingChanged(app.showCancelledLessons.key, it.toString())
                }
            )
        ),
        
        category(
            "Внешний вид", "тема, цвета, вкладки", WHATIcons.ImageRoller,
            content = listOf(
                app.themeType.asSingleChoice(enumValues<ThemeType>(), { it.displayName }) {
                    Analytics.logSettingChanged(app.themeType.key, it.toString())
                },
                app.themeStyle.asSingleChoice(enumValues<ThemeStyle>(), { it.displayName }) {
                    Analytics.logSettingChanged(app.themeStyle.key, it.toString())
                },
                app.themeColor.asColorPalette {
                    Analytics.logSettingChanged(app.themeColor.key, it.toString())
                }.dependsOn(app.themeStyle) { it == ThemeStyle.CustomColor },
                app.enableProfileTab.asSwitch {
                    Analytics.logSettingChanged(app.enableProfileTab.key, it.toString())
                }
            )
        ),
        
        category(
            "Уведомления", "замены и изменения", Icons.Default.Notifications,
            content = listOf(
                app.enableReplacementNotifications.asSwitch {
                    Analytics.logSettingChanged(app.enableReplacementNotifications.key, it.toString())
                },
                app.notifyFavoritesReplacements.asSwitch {
                    Analytics.logSettingChanged(app.notifyFavoritesReplacements.key, it.toString())
                }.dependsOn(app.enableReplacementNotifications) { it == true },
                app.replacementNotificationsPeriod.asSingleChoice(
                    enumValues<NotificationPeriod>(),
                    { it.displayName }
                ) {
                    Analytics.logSettingChanged(app.replacementNotificationsPeriod.key, it.toString())
                }.dependsOn(app.enableReplacementNotifications) { it == true },
                app.enableUniversityNotifications.asSwitch {
                    Analytics.logSettingChanged(app.enableUniversityNotifications.key, it.toString())
                },
                app.enableFirstLessonNotification.asSwitch {
                    Analytics.logSettingChanged(app.enableFirstLessonNotification.key, it.toString())
                }
            )
        ),
        
        category(
            "Данные и память", "кэш, приватность", WHATIcons.Clear,
            content = listOf(
                settingAction(
                    title = "Очистить кэш расписания",
                    description = "Удалить сохранённые локально расписания",
                    isDestructive = true,
                    onClick = onClearCacheClick
                ),
                app.isAnalyticsEnabled.asSwitch {
                    Analytics.setAnalyticsCollectionEnabled(it)
                },
                app.thePolicy.asSheet { _, _ ->
                    PolicyView()
                }
            )
        ),

        developerCategory(
            "Для разработчиков", "отладка", WHATIcons.Code,
            isUnlocked = isDevUnlocked == true,
            content = listOf(
                settingAction(
                    title = "Показать демо Onboarding обновления",
                    description = "Открыть экран 'Что нового' с примерами настроек и фич",
                    onClick = {
                        dialog.open(full = true) {
                            UpdateOnboardingContent(
                                releaseNotes = ReleaseNotes(
                                    version = "3.2.0-demo",
                                    title = "Что нового в WHAT Schedule",
                                    shortDescription = "Демонстрация онбординга: наглядный обзор новых возможностей и моментальная настройка под себя.",
                                    highlights = listOf(
                                        ReleaseHighlight(
                                            title = "Вечернее расписание на завтра",
                                            description = "Теперь приложение автоматически переключает отображение на следующий день после окончания пар.",
                                            tag = "NEW",
                                            settingKey = "show_next_day_in_evening"
                                        ),
                                        ReleaseHighlight(
                                            title = "Уведомления о парах",
                                            description = "Получайте моментальные пуши об изменениях пар и напоминание о начале первой пары.",
                                            tag = "NEW",
                                            settings = listOf(
                                                "enable_replacement_notifications",
                                                "enable_first_lesson_notification"
                                            )
                                        ),
                                        ReleaseHighlight(
                                            title = "Оформление интерфейса",
                                            description = "Настройте тему приложения и анимации под свои предпочтения.",
                                            tag = "NEW",
                                            settings = listOf(
                                                "theme_type",
                                                "use_animation"
                                            )
                                        )
                                    ),
                                    changelog = listOf(
                                        "Оптимизирована скорость холодного запуска расписания",
                                        "Улучшена стабильность работы офлайн-режима при отсутствии сети",
                                        "Добавлена поддержка динамических цветов темы Material You",
                                        "Исправлено отображение аудиторий с нестандартными литерами",
                                        "Новые виджеты домашнего экрана с текущей парой и звонками",
                                        "Снижено потребление оперативной памяти и батареи в фоне",
                                        "Улучшен парсер расписания для редких типов занятий (факультативы)",
                                        "Добавлен быстрый выбор даты через компактный выпадающий календарь",
                                        "Исправлен сброс фильтра преподавателей при переключении недель",
                                        "Добавлена вибрация и звуковой отклик при отметке заданий",
                                        "Улучшена фоновая синхронизация заметок и избранных групп",
                                        "Исправлено растягивание интерфейса в горизонтальной ориентации",
                                        "Оптимизирована анимация горизонтального свайпа между днями",
                                        "Обновлены сетевые таймауты для работы при нестабильном 3G/LTE",
                                        "Исправлены вылеты при быстром переключении между профилями",
                                        "Добавлена поддержка экспорта расписания в системный календарь (.ics)",
                                        "Обновлены библиотеки зависимостей и Compose Runtime"
                                    )
                                ),
                                appValues = app,
                                onDismiss = { dialog.close() }
                            )
                        }
                    }
                ),
                app.devSettingsUnlocked.asSwitch(),
                app.isFirstLaunch.asSwitch(),
                app.devPanelEnabled.asSwitch(),
                app.debugMode.asSwitch()
            )
        ),
        
        actionCategory(
            "О приложении", "версия, авторы", Icons.Rounded.Info
        ) {
            dialog.open { AboutAppContent(app) }
        }
    )
}

val LocalSettingsNavigator = staticCompositionLocalOf<(String, String, List<UIComponent>) -> Unit> {
    error("Settings navigator not provided")
}

fun category(
    title: String,
    description: String,
    icon: ImageVector,
    content: List<UIComponent>
): SettingsCategoryComponent = object : SettingsCategoryComponent {
    override val title: String = title
    override val description: String = description
    override val icon: ImageVector = icon
    override val content: List<UIComponent> = content

    @Composable
    override fun content(modifier: Modifier) {
        val navigate = LocalSettingsNavigator.current
        CategoryItem(
            icon = icon,
            title = title,
            description = description,
            onClick = { navigate(title, description, content) }
        )
    }
}

fun developerCategory(
    title: String,
    description: String,
    icon: ImageVector,
    isUnlocked: Boolean,
    content: List<UIComponent>
): SettingsCategoryComponent = object : SettingsCategoryComponent {
    override val title: String = title
    override val description: String = description
    override val icon: ImageVector = icon
    override val content: List<UIComponent> = content

    @Composable
    override fun content(modifier: Modifier) {
        if (!isUnlocked) return
        val navigate = LocalSettingsNavigator.current
        CategoryItem(
            icon = icon,
            title = title,
            description = description,
            onClick = { navigate(title, description, content) }
        )
    }
}

fun actionCategory(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit
) = object : UIComponent {
    @Composable
    override fun content(modifier: Modifier) {
        CategoryItem(icon, title, description, onClick = onClick)
    }
}

fun settingAction(
    title: String,
    description: String? = null,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) = object : UIComponent {
    @Composable
    override fun content(modifier: Modifier) {
        Box(
            modifier
                .fillMaxWidth()
                .clip(shapes.medium)
                .bclick(block = onClick)
                .padding(horizontal = 28.dp, vertical = 14.dp)
        ) {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    text = title,
                    style = typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (isDestructive) colorScheme.error else colorScheme.onBackground
                )
                if (description != null) {
                    Gap(2)
                    Text(
                        text = description,
                        style = typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        color = colorScheme.secondary
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryItem(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) = Box(
    Modifier
        .clip(shapes.medium)
        .bclick(block = onClick)
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(28.dp, 12.dp)
    ) {
        icon.Show(colorScheme.primary, 28)
        
        Gap(18)
        
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = colorScheme.onBackground
            )
            Text(
                description,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = colorScheme.secondary
            )
        }
    }
}