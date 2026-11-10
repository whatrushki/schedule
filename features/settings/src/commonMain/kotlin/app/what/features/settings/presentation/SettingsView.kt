package app.what.schedule.features.settings.presentation

import app.what.foundation.ui.PlatformBackHandler
import app.what.features.settings.presentation.components.UsageStatsDialog
import app.what.features.settings.presentation.components.PolicyContent
import app.what.foundation.ui.controllers.rememberSheetController
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.shapes
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalUriHandler
import app.what.features.settings.presentation.components.FeedbackFormContent
import app.what.features.settings.presentation.components.FeedbackSuccessContent
import app.what.foundation.delivery.client.DeliveryAppContext
import app.what.foundation.delivery.client.DeliveryClient
import app.what.foundation.delivery.model.DeliveryNotification
import androidx.compose.foundation.layout.PaddingValues
import app.what.foundation.delivery.ui.DeliveryNotificationsContent
import app.what.foundation.delivery.ui.SurveyContent
import app.what.foundation.delivery.ui.SurveyDialog
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
import app.what.foundation.core.Listener
import app.what.foundation.core.UIComponent
import app.what.foundation.data.settings.dependsOn
import app.what.foundation.data.settings.types.asColorPalette
import app.what.foundation.data.settings.types.asSheet
import app.what.foundation.data.settings.types.asSingleChoice
import app.what.foundation.data.settings.types.ChoiceDialogContent
import app.what.foundation.data.settings.types.asSwitch
import app.what.foundation.ui.Gap
import app.what.foundation.ui.Show
import app.what.foundation.ui.animations.AnimatedEnter
import app.what.foundation.ui.bclick
import app.what.foundation.ui.controllers.rememberDialogController
import app.what.foundation.ui.keyboardAsState
import app.what.foundation.utils.Analytics
import app.what.foundation.utils.AppUtils
import app.what.schedule.data.local.settings.AppValues
import app.what.data.ads.YandexAdsConfig
import app.what.data.ads.YandexBannerAd
import app.what.schedule.data.local.settings.SupportAdsLevel
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.runtime.collectAsState
import app.what.foundation.auth.client.WhatAuthManager
import app.what.foundation.auth.model.WhatAccount
import app.what.foundation.auth.ui.WhatAccountChooserSheet

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
    val deliveryClient: DeliveryClient = koinInject()
    val whatAuthManager: WhatAuthManager = koinInject()
    val activeAccount by whatAuthManager.activeAccountFlow.collectAsState()
    val adsLevel by appValues.supportAdsLevel.collect()
    val uriHandler = LocalUriHandler.current
    val dialog = rememberDialogController()
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState { 2 }
    val sheet = rememberSheetController()
    
    var activeCategoryTitle by rememberSaveable { mutableStateOf<String?>(null) }
    var subScreen by remember { mutableStateOf<SubScreenState?>(null) }
    var activeNotifications by remember { mutableStateOf<List<DeliveryNotification>>(emptyList()) }
    var readNotificationIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(Unit) {
        try {
            readNotificationIds = deliveryClient.getReadNotificationIds()
            val appContext = DeliveryAppContext(
                appId = "app.what.schedule",
                versionCode = 200,
                institutionId = "",
                platform = "android",
                language = "ru"
            )
            activeNotifications = deliveryClient.fetchActiveNotifications(appContext)
        } catch (_: Exception) {}
    }

    val rootComponents = getSettingsList(
        app = appValues,
        utils = appUtils
    )

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
                    headerHeight = headerHeight,
                    actions = {
                        if (pagerState.currentPage == 0) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Кнопка уведомлений
                                val unreadCount = activeNotifications.count { !readNotificationIds.contains(it.id) }
                                Box {
                                    Surface(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .bclick {
                                                if (activeNotifications.isNotEmpty()) {
                                                    deliveryClient.markAllNotificationsAsRead(activeNotifications.map { it.id })
                                                    readNotificationIds = readNotificationIds + activeNotifications.map { it.id }.toSet()
                                                    dialog.open(true) {
                                                        DeliveryNotificationsContent(
                                                            notifications = activeNotifications,
                                                            readNotificationIds = readNotificationIds,
                                                            onNotificationDismiss = { id ->
                                                                deliveryClient.dismissNotification(id)
                                                                activeNotifications = activeNotifications.filter { it.id != id }
                                                            },
                                                            onClearAll = {
                                                                deliveryClient.dismissAllNotifications(activeNotifications.map { it.id })
                                                                activeNotifications = emptyList()
                                                                dialog.close()
                                                            },
                                                            onSurveyClick = { notif ->
                                                                notif.survey?.let { srv ->
                                                                    dialog.push(full = true) {
                                                                        SurveyContent(
                                                                            survey = srv,
                                                                            deliveryClient = deliveryClient,
                                                                            userId = appValues.userId.get()?.ifBlank { "anonymous_user" } ?: "anonymous_user",
                                                                            appId = "app.what.schedule",
                                                                            appVersion = "2.0.0",
                                                                            onDismiss = { dialog.pop() },
                                                                            onSubmitSuccess = {
                                                                                deliveryClient.dismissNotification(notif.id)
                                                                                activeNotifications = activeNotifications.filter { it.id != notif.id }
                                                                                dialog.pop()
                                                                            }
                                                                        )
                                                                    }
                                                                }
                                                            },
                                                            onActionClick = { url -> uriHandler.openUri(url) },
                                                            onClose = { dialog.close() }
                                                        )
                                                    }
                                                }
                                            },
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Notifications,
                                                contentDescription = "Уведомления",
                                                tint = if (unreadCount > 0) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

                                    if (unreadCount > 0) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(2.dp)
                                                .size(9.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.error)
                                        )
                                    }
                                }




                            }
                        }
                    }
                )
                
                if (adsLevel != SupportAdsLevel.OFF) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .clip(shapes.large),
                        contentAlignment = Alignment.Center
                    ) {
                        YandexBannerAd(adUnitId = YandexAdsConfig.SETTINGS_BANNER_ID)
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = pagerState.currentPage != 0,
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.Top
                ) { page ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 120.dp)
                    ) {
                        if (page == 0) {
                            // Главная страница: сразу обновления и категории настроек
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
    headerHeight: Dp,
    actions: @Composable () -> Unit = {}
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

    Box(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .statusBarsPadding()
            .padding(top = 10.dp, end = 16.dp)
    ) {
        actions()
    }
}

@Composable
fun getSettingsList(
    app: AppValues,
    utils: AppUtils
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
                },
                app.showBreaks.asSwitch {
                    Analytics.logSettingChanged(app.showBreaks.key, it.toString())
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
            "Данные и память", "память, сеть, приватность", WHATIcons.Clear,
            content = listOf(
                settingAction(
                    title = "Память и сеть",
                    description = "Статистика хранилища и интернет-трафика",
                    isDestructive = false,
                    onClick = {
                        dialog.open(true) {
                            UsageStatsDialog(onClose = { dialog.close() })
                        }
                    }
                ),
                app.isAnalyticsEnabled.asSwitch {
                    Analytics.setAnalyticsCollectionEnabled(it)
                },
                app.thePolicy.asSheet { _, _ ->
                    PolicyContent()
                }
            )
        ),

        category(
            "Поддержка автора", "Уровень рекламы и помощь разработке", Icons.Rounded.Favorite,
            content = listOf(
                app.supportAdsLevel.asSingleChoice(
                    enumValues<SupportAdsLevel>(),
                    { it.displayName }
                ) {
                    Analytics.logSettingChanged(app.supportAdsLevel.key, it.toString())
                }
            )
        ),
        
        developerCategory(
            "Для разработчиков", "отладка", WHATIcons.Code,
            isUnlocked = isDevUnlocked == true,
            content = listOf(
                app.devSettingsUnlocked.asSwitch(),
                app.isFirstLaunch.asSwitch(),
                app.devPanelEnabled.asSwitch(),
                app.debugMode.asSwitch()
            )
        ),
        
        actionCategory(
            "О приложении", "версия, авторы", Icons.Rounded.Info
        ) {
            dialog.open {
                AboutAppContent(
                    appValues = app,
                    onClose = { dialog.close() },
                    onFeedbackClick = {
                        dialog.push(full = true) {
                            FeedbackFormContent(
                                onBack = { dialog.pop() },
                                onClose = { dialog.close() },
                                onSuccess = { isBug ->
                                    dialog.replace(full = false) {
                                        FeedbackSuccessContent(
                                            isBug = isBug,
                                            onClose = { dialog.close() }
                                        )
                                    }
                                }
                            )
                        }
                    }
                )
            }
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

@Composable
private fun SettingsNotificationsBlock(
    notifications: List<DeliveryNotification>,
    unreadCount: Int,
    onClick: () -> Unit
) {
    val firstItem = notifications.firstOrNull() ?: return
    val count = notifications.size

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(shapes.medium)
            .background(colorScheme.secondaryContainer.copy(alpha = 0.45f))
            .bclick(block = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = null,
                tint = if (unreadCount > 0) colorScheme.primary else colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )

            Gap(14)

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (count == 1) firstItem.title else "Уведомления и опросы",
                    style = typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Gap(2)
                Text(
                    text = if (unreadCount > 0) "$unreadCount новых событий" else if (count == 1) firstItem.message else "$count событий",
                    style = typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Gap(8)

            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (unreadCount > 0) colorScheme.primary else colorScheme.surfaceContainerHighest)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = (if (unreadCount > 0) unreadCount else count).toString(),
                    style = typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (unreadCount > 0) colorScheme.onPrimary else colorScheme.onSurfaceVariant
                )
            }

            Gap(6)

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = colorScheme.secondary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}



