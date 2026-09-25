package app.what.schedule.features.schedule.presentation

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.shapes
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import app.what.foundation.ui.AppPullToRefresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.what.foundation.data.RemoteState
import app.what.foundation.ui.Gap
import app.what.foundation.ui.SegmentTab
import app.what.foundation.ui.Show
import app.what.foundation.ui.animations.AnimatedEnter
import app.what.foundation.ui.bclick
import app.what.foundation.ui.capplyIf
import app.what.foundation.ui.controllers.rememberDialogController
import app.what.foundation.ui.controllers.rememberSheetController
import app.what.foundation.ui.useChange
import app.what.foundation.ui.useSave
import app.what.foundation.ui.useState
import app.what.foundation.utils.freeze
import app.what.domain.models.DaySchedule
import app.what.domain.models.Lesson
import app.what.domain.models.LessonsScheduleType
import app.what.domain.models.ScheduleSearch
import app.what.schedule.features.schedule.domain.models.ScheduleEvent
import app.what.schedule.features.schedule.domain.models.ScheduleState
import app.what.schedule.features.schedule.presentation.components.BreakInfo
import app.what.schedule.features.schedule.presentation.components.LessonUI
import app.what.schedule.features.schedule.presentation.components.ScheduleExportPane
import app.what.schedule.features.schedule.presentation.components.ScheduleShimmer
import app.what.schedule.features.schedule.presentation.components.SearchButton
import app.what.schedule.features.schedule.presentation.components.ViewType
import app.what.schedule.ui.components.Fallback
import app.what.schedule.ui.components.ScheduleSearchPane
import app.what.schedule.ui.theme.icons.WHATIcons
import app.what.schedule.ui.theme.icons.filled.Network
import app.what.schedule.ui.theme.icons.filled.Run
import app.what.schedule.ui.theme.icons.filled.Warn
import app.what.foundation.utils.Analytics
import app.what.foundation.utils.DateTimeUtils
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toLocalDateTime

@Composable
fun ScheduleView(
    state: State<ScheduleState>,
    listener: (ScheduleEvent) -> Unit
) = AppPullToRefresh(
    isRefreshing = state.value.scheduleState == RemoteState.Loading,
    onRefresh = { listener(ScheduleEvent.OnRefresh) },
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .wrapContentWidth(Alignment.CenterHorizontally)
            .widthIn(max = 860.dp)
            .capplyIf(state.value.scheduleState != RemoteState.Idle && !state.value.schedules.isEmpty()) {
                verticalScroll(rememberScrollState())
            }
    ) {
        val sheet = rememberSheetController()
        val dialog = rememberDialogController()
        val scheduleExportSheet = remember(state.value.schedules) {
            @Composable { ScheduleExportPane(state.value.selectedSearch, state.value.schedules) }
        }
        
        val scheduleSearchSheet: @Composable () -> Unit = {
            ScheduleSearchPane(
                state,
                {
                    listener(ScheduleEvent.OnSearchClicked(it))
                    sheet.animateClose()
                },
                { listener(ScheduleEvent.OnSearchLongPressed(it)) },
                { listener(ScheduleEvent.OnRefreshSearches) }
            )
        }
        
        val (scheduleType, setScheduleType) = useState<LessonsScheduleType?>(null)
        val currentDate = app.what.foundation.utils.currentLocalDate().freeze()
        val currentTime = useChange(app.what.foundation.utils.currentLocalTime(), 60) {
            app.what.foundation.utils.currentLocalTime()
        }
        var showBreaks by useSave(false)
        val scope = rememberCoroutineScope()
        val weeks = remember(state.value.schedules) {
            state.value.schedules.groupBy { it.date.getWeekNumber() }
        }
        val daysPagerState = rememberPagerState { state.value.schedules.size }
        val weeksPagerState = rememberPagerState { weeks.size }
        
        @Composable
        fun Lesson.Show(date: LocalDate) = LessonUI(
            data = this,
            listener = listener,
            currentTime = if (date == currentDate)
                currentTime.value else null,
            viewType = when (state.value.selectedSearch) {
                is ScheduleSearch.Teacher -> ViewType.TEACHER
                else -> ViewType.STUDENT
            }
        )
        
        LaunchedEffect(daysPagerState.currentPage, state.value.scheduleState) {
            if (state.value.schedules.isEmpty()) return@LaunchedEffect
            
            scope.launch {
                weeks.values.forEachIndexed { index, it ->
                    if (state.value.schedules.getOrNull(daysPagerState.currentPage) in it) {
                        weeksPagerState.animateScrollToPage(index)
                        return@forEachIndexed
                    }
                }
            }
            
            val currentDaySchedule =
                state.value.schedules.getOrNull(daysPagerState.currentPage) ?: return@LaunchedEffect
            setScheduleType(currentDaySchedule.scheduleType)
        }

        var hasNavigatedToTodayInitially by useSave(false)
        var lastNavigatedSearchId by useSave<String?>(null)

        LaunchedEffect(state.value.schedules, state.value.selectedSearch?.id) {
            val schedules = state.value.schedules
            if (schedules.isEmpty()) return@LaunchedEffect

            val currentSearchId = state.value.selectedSearch?.id
            val isInitialForSearch = !hasNavigatedToTodayInitially || lastNavigatedSearchId != currentSearchId

            if (isInitialForSearch) {
                hasNavigatedToTodayInitially = true
                lastNavigatedSearchId = currentSearchId
                val today = app.what.foundation.utils.currentLocalDate()
                val targetIndex = schedules.indexOfFirst { it.date == today }
                    .takeIf { it != -1 }
                    ?: schedules.indexOfFirst { it.date >= today }.takeIf { it != -1 }
                    ?: 0
                daysPagerState.scrollToPage(targetIndex)
            } else {
                val previousDate = schedules.getOrNull(daysPagerState.currentPage)?.date
                if (previousDate != null) {
                    val matchingIndex = schedules.indexOfFirst { it.date == previousDate }
                    if (matchingIndex != -1 && matchingIndex != daysPagerState.currentPage) {
                        daysPagerState.scrollToPage(matchingIndex)
                    }
                }
            }
        }
        
        Gap(16)
        
        Column(
            Modifier.statusBarsPadding()
        ) {
            Row(
                Modifier
                    .height(IntrinsicSize.Min)
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SearchButton(
                    state.value.selectedSearch,
                    scheduleType,
                    Modifier
                        .animateContentSize()
                        .weight(1f)
                ) {
                    sheet.open(content = scheduleSearchSheet, full = true)
                }
                
                AnimatedEnter(state.value.schedules.isNotEmpty()) {
                    StyledIconButton(
                        WHATIcons.Run,
                        active = if (showBreaks) ActiveState.ACTIVE else ActiveState.DISABLED
                    ) { showBreaks = !showBreaks }
                }
                
                AnimatedEnter(state.value.schedules.isNotEmpty()) {
                    StyledIconButton(
                        Icons.Default.Share,
                        state.value.schedules.isNotEmpty()
                    ) {
                        Analytics.logShare("schedule", "")
                        sheet.open(content = scheduleExportSheet)
                    }
                }

                if (app.what.foundation.utils.isDesktop) {
                    StyledIconButton(
                        Icons.Default.Refresh,
                        state.value.scheduleState != RemoteState.Loading
                    ) {
                        listener(ScheduleEvent.OnRefresh)
                    }
                }
            }
            
            
            AnimatedEnter(state.value.schedules.isNotEmpty()) {
                Column {
                    Gap(8)
                    ScheduleCalendar(weeks, weeksPagerState, daysPagerState) {
                        scope.launch { daysPagerState.animateScrollToPage(it) }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                contentAlignment = Alignment.Center
            ) {
                if (state.value.scheduleState == RemoteState.Loading) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .height(2.dp)
                            .clip(CircleShape),
                        color = colorScheme.primary,
                        trackColor = colorScheme.surfaceVariant
                    )
                }
            }

            if (state.value.isOffline && state.value.schedules.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    onClick = { listener(ScheduleEvent.OnRefresh) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = WHATIcons.Network,
                            contentDescription = "Оффлайн",
                            modifier = Modifier.size(16.dp),
                            tint = colorScheme.primary
                        )
                        Text(
                            text = "Оффлайн • кэш от ${formatLastModified(state.value.lastModified)}",
                            style = typography.labelMedium,
                            color = colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "Обновить",
                            style = typography.labelSmall,
                            color = colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            
            when (state.value.scheduleState) {
                RemoteState.Loading if state.value.schedules.isEmpty() -> ScheduleShimmer()
                is RemoteState.Error if state.value.schedules.isEmpty() -> Fallback(
                    text = "Не удалось загрузить расписание.\nЕсли у вас включен VPN, попробуйте отключить его или проверьте подключение к сети.",
                    modifier = Modifier.fillMaxSize(),
                    action = "Повторить" to {
                        listener(ScheduleEvent.OnRefresh)
                    }
                )
                RemoteState.Success if state.value.schedules.isEmpty() -> Fallback(
                    text = "Тут ничего нет, попробуйте другую группу :3",
                    modifier = Modifier.fillMaxSize(),
                    action = "Выбрать" to {
                        sheet.open(content = scheduleSearchSheet, full = true)
                    }
                )
                
                RemoteState.Idle if state.value.schedules.isEmpty() -> Fallback(
                    text = "Для того чтобы появилось расписание нужно выбрать группу",
                    modifier = Modifier.fillMaxSize(),
                    action = "Выбрать" to {
                        sheet.open(content = scheduleSearchSheet, full = true)
                    }
                )
                
                else -> AnimatedEnter {
                    HorizontalPager(
                        state = daysPagerState,
                        verticalAlignment = Alignment.Top,
                        key = { state.value.schedules[it].date.toString() },
                        modifier = Modifier
                            .fillMaxHeight()
                    ) {
                        val date = state.value.schedules[it].date
                        val lessons = state.value.schedules[it].lessons
                        
                        Column(
                            verticalArrangement = Arrangement.spacedBy(if (showBreaks) 4.dp else 12.dp),
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            if (lessons.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "В этот день занятий нет 🎉",
                                        style = typography.bodyLarge,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                lessons.zipWithNext()
                                    .forEach { (first, second) ->
                                        first.Show(date)
                                        
                                        AnimatedEnter(showBreaks) {
                                            BreakInfo(
                                                (second.startTime.toSecondOfDay() - first.endTime.toSecondOfDay()) / 60,
                                                currentTime.value in first.startTime..second.startTime && currentDate == first.date
                                            )
                                        }
                                    }
                                
                                lessons.lastOrNull()?.Show(date)
                            }
                            
                            Gap(132)
                        }
                    }
                }
            }
        }
    }
}

enum class ActiveState {
    DISABLED, NEUTRAL, ACTIVE
}

@Composable
fun StyledIconButton(
    icon: ImageVector,
    enabled: Boolean = true,
    active: ActiveState = ActiveState.NEUTRAL,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxHeight()
            .aspectRatio(1f, true)
            .clip(CircleShape)
            .background(colorScheme.surfaceContainer)
            .bclick(enabled, onClick)
    ) {
        icon.Show(
            when (active) {
                ActiveState.ACTIVE -> colorScheme.primary
                ActiveState.NEUTRAL -> colorScheme.secondary
                ActiveState.DISABLED -> colorScheme.secondary
            }
        )
    }
}

@Composable
fun ScheduleCalendar(
    weeks: Map<Int, List<DaySchedule>>,
    weeksPagerState: PagerState,
    daysPagerState: PagerState,
    onClick: (day: Int) -> Unit
) {
    val schedules = weeks.values.flatten()
    
    HorizontalPager(
        weeksPagerState,
        key = { weeks.entries.elementAt(it).key },
        modifier = Modifier.fillMaxWidth()
    ) {
        val thisWeek = weeks.entries.elementAt(it)
        
        SingleChoiceSegmentedButtonRow(
            space = (-4).dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
        ) {
            thisWeek.value.forEach { day ->
                val realIndex = schedules.indexOfFirst { it.date == day.date }
                val selected = daysPagerState.currentPage == realIndex
                
                    val dayOfWeekStr = if (thisWeek.value.size > 2) {
                        DateTimeUtils.RUSSIAN_DAYS_SHORT.getOrElse(day.date.dayOfWeek.ordinal) { "" }
                    } else {
                        DateTimeUtils.RUSSIAN_DAYS_FULL.getOrElse(day.date.dayOfWeek.ordinal) { "" }
                    }
                    SegmentTab(
                        selected = selected,
                        index = realIndex,
                        count = schedules.size,
                        icon = null,
                        label = "${day.date.dayOfMonth}" + (if (thisWeek.value.size > 5) "\n" else " ") + dayOfWeekStr
                    ) {
                        onClick(realIndex)
                    }
                }
            }
        }
    }

fun LocalDate.getWeekNumber(): Int {
    val jan1 = LocalDate(this.year, 1, 1)
    val jan1DayOfWeek = jan1.dayOfWeek.isoDayNumber
    return (this.dayOfYear + jan1DayOfWeek - 2) / 7 + 1
}

@Composable
fun ErrorContent(
    error: Throwable,
    onCopyClick: () -> Unit
) {
    val stackTrace = remember(error) { error.stackTraceToString() }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            WHATIcons.Warn.Show(colorScheme.error, 28)
            Gap(12)
            Text(
                text = "Ошибка загрузки",
                style = typography.headlineSmall,
                color = colorScheme.onSurface
            )
        }
        
        Gap(20)
        
        Text(
            text = error.message ?: "Произошла неизвестная ошибка",
            style = typography.bodyLarge,
            color = colorScheme.onSurfaceVariant
        )
        
        Gap(24)
        
        Text(
            text = "Полный стек-трейс",
            style = typography.titleMedium,
            color = colorScheme.primary
        )
        
        Gap(8)
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 380.dp)
                .background(
                    color = colorScheme.surfaceVariant,
                    shape = shapes.medium
                )
                .border(
                    width = 1.dp,
                    color = colorScheme.outlineVariant,
                    shape = shapes.medium
                )
        ) {
            Text(
                text = stackTrace,
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                style = typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 18.sp
                ),
                color = colorScheme.onSurfaceVariant
            )
        }
        
        Gap(24)
        Button(
            onClick = onCopyClick,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 14.dp)
        ) {
            Icons.Default.Share.Show(colorScheme.onPrimary, 20)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Копировать стек-трейс",
                style = typography.labelLarge
            )
        }
    }
}

private fun formatLastModified(dt: kotlinx.datetime.LocalDateTime?): String {
    if (dt == null) return "ранее"
    val day = dt.dayOfMonth
    val monthName = when (dt.monthNumber) {
        1 -> "янв"
        2 -> "фев"
        3 -> "мар"
        4 -> "апр"
        5 -> "мая"
        6 -> "июн"
        7 -> "июл"
        8 -> "авг"
        9 -> "сен"
        10 -> "окт"
        11 -> "ноя"
        12 -> "дек"
        else -> ""
    }
    val time = "${dt.hour.toString().padStart(2, '0')}:${dt.minute.toString().padStart(2, '0')}"
    return "$day $monthName, $time"
}
