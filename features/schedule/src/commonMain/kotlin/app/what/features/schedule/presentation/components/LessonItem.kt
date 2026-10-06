package app.what.schedule.features.schedule.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.shapes
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import app.what.foundation.core.Listener
import app.what.schedule.data.local.settings.rememberAppValues
import app.what.foundation.ui.Gap
import app.what.foundation.ui.Show
import app.what.foundation.ui.applyIf
import app.what.foundation.ui.bclick
import app.what.foundation.ui.capplyIf
import app.what.foundation.ui.useState
import app.what.domain.models.Group
import app.what.domain.models.Lesson
import app.what.domain.models.LessonState
import app.what.domain.models.LessonType
import app.what.domain.models.OneTimeUnit
import app.what.domain.models.ScheduleSearch
import app.what.domain.models.Teacher
import app.what.schedule.data.remote.utils.formatTime
import app.what.schedule.features.schedule.domain.models.ScheduleEvent
import app.what.foundation.ui.icons.WHATIcons
import app.what.foundation.ui.icons.filled.Building
import app.what.foundation.ui.icons.filled.Group
import app.what.foundation.ui.icons.filled.Person
import app.what.foundation.ui.icons.filled.Room
import app.what.foundation.ui.icons.filled.Run
import com.materialkolor.ktx.DynamicScheme
import com.materialkolor.toColorScheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

enum class ViewType {
    TEACHER, STUDENT
}

@Composable
fun LessonUI(
    modifier: Modifier = Modifier,
    data: Lesson,
    viewType: ViewType,
    currentTime: LocalTime? = null,
    listener: Listener<ScheduleEvent>
) = when (data.type) {
    LessonType.CLASS_HOUR -> EventView(
        data,
        currentTime,
        viewType,
        listener,
        modifier
    )
    
    else -> CommonView(
        data,
        viewType,
        currentTime,
        listener,
        modifier
    )
}

@Composable
private fun getCommonViewAccentColor(state: LessonState, type: LessonType) = when (state) {
    LessonState.REMOVED -> colorScheme.secondary
    else -> if (type.isNonStandard) colorScheme.tertiary
    else colorScheme.primary
}

@Composable
private fun EventView(
    data: Lesson,
    currentTime: LocalTime?,
    viewType: ViewType,
    listener: Listener<ScheduleEvent>,
    modifier: Modifier = Modifier,
    expandable: Boolean = true
) {
    val commonViewAccentColor = getCommonViewAccentColor(data.state, data.type)
    
    val (expanded, setExpanded) = useState(currentTime != null && currentTime in data.startTime..data.endTime)
    val appValues = rememberAppValues()
    val defaultSubgroup = appValues.defaultSubgroup.get()
    val targetSubgroupNumber = defaultSubgroup?.value
    val initialUnitIndex = remember(data.otUnits, targetSubgroupNumber) {
        if (targetSubgroupNumber != null && data.otUnits.size > 1) {
            val matching = data.otUnits.indexOfFirst {
                it.group.name.contains("$targetSubgroupNumber")
            }
            if (matching != -1) matching
            else if (targetSubgroupNumber in 1..data.otUnits.size) targetSubgroupNumber - 1
            else 0
        } else 0
    }
    var currentUnitIndex by remember(data.otUnits, initialUnitIndex) { mutableIntStateOf(initialUnitIndex) }
    val currentUnit = data.otUnits.getOrNull(currentUnitIndex) ?: data.otUnits.firstOrNull()
    val currentSubject = currentUnit?.subject?.takeIf { it.isNotBlank() } ?: data.subject
    
    val expandedTitleBoxBackground by animateColorAsState(
        if (expanded) commonViewAccentColor.copy(alpha = .2f)
        else colorScheme.surfaceContainer
    )
    
    val backgroundColor by animateColorAsState(
        if (data.state.isRemoved) colorScheme.surfaceVariant
        else if (expanded) colorScheme.surfaceContainer
        else commonViewAccentColor
    )
    
    val titleColor by animateColorAsState(
        if (expanded) commonViewAccentColor
        else if (data.state.isRemoved) commonViewAccentColor
        else colorScheme.onTertiary
    )
    
    Box(
        modifier = modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .clip(shapes.medium)
            .background(backgroundColor)
            .animateContentSize()
            .bclick(enabled = expandable) {
                setExpanded(!expanded)
            }
    ) {
        AnimatedVisibility(expanded) {
            TimeLine(
                currentTime,
                data.startTime,
                data.endTime,
                commonViewAccentColor
            )
        }
        
        Tag(
            accentColor = commonViewAccentColor,
            state = data.state,
            modifier = Modifier
                .align(
                    if (expanded) Alignment.BottomEnd
                    else Alignment.CenterEnd
                )
                .padding(
                    end = if (expanded) 12.dp
                    else 20.dp,
                    bottom = if (expanded) 12.dp
                    else 0.dp
                )
        )
        
        Column(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                Modifier
                    .animateContentSize()
                    .applyIf(expanded) {
                        clip(CircleShape)
                            .background(expandedTitleBoxBackground)
                            .padding(8.dp, 4.dp)
                    },
                horizontalArrangement = if (expanded)
                    Arrangement.spacedBy(8.dp) else Arrangement.Center
            ) {
                AnimatedVisibility(expanded) {
                    Text(
                        text = "${formatTime(data.startTime)} - ${formatTime(data.endTime)}",
                        color = titleColor,
                        style = typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
                
                Text(
                    text = currentSubject,
                    color = titleColor,
                    textDecoration = if (data.state == LessonState.REMOVED) TextDecoration.LineThrough
                    else TextDecoration.None,
                    style = typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
            
            if (expanded) Gap(8)
            
            AnimatedVisibility(expanded) {
                OtUnitsView(
                    viewType = viewType,
                    otUnits = data.otUnits,
                    color = commonViewAccentColor,
                    horizontalAlignment = Alignment.CenterHorizontally,
                    expanded = true,
                    currentIndex = currentUnitIndex,
                    onIndexChanged = { idx, _ -> currentUnitIndex = idx },
                    onSearchClicked = {
                        listener(ScheduleEvent.OnSearchClicked(it))
                    }
                )
            }
        }
    }
}

@Composable
private fun CommonView(
    data: Lesson,
    viewType: ViewType,
    currentTime: LocalTime? = null,
    listener: Listener<ScheduleEvent>,
    modifier: Modifier = Modifier
) {
    val commonViewAccentColor = getCommonViewAccentColor(data.state, data.type)
    val (expanded, setExpanded) = useState(false)
    val (expandable, setExpandable) = useState(data.otUnits.size > 3)
    val appValues = rememberAppValues()
    val defaultSubgroup = appValues.defaultSubgroup.get()
    val targetSubgroupNumber = defaultSubgroup?.value
    val initialUnitIndex = remember(data.otUnits, targetSubgroupNumber) {
        if (targetSubgroupNumber != null && data.otUnits.size > 1) {
            val matching = data.otUnits.indexOfFirst {
                it.group.name.contains("$targetSubgroupNumber")
            }
            if (matching != -1) matching
            else if (targetSubgroupNumber in 1..data.otUnits.size) targetSubgroupNumber - 1
            else 0
        } else 0
    }
    var currentUnitIndex by remember(data.otUnits, initialUnitIndex) { mutableIntStateOf(initialUnitIndex) }
    var isSlideNext by remember(data.otUnits) { mutableStateOf(true) }
    val currentUnit = data.otUnits.getOrNull(currentUnitIndex) ?: data.otUnits.firstOrNull()
    val currentSubject = currentUnit?.subject?.takeIf { it.isNotBlank() } ?: data.subject
    
    Box(
        modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .applyIf(!expanded, elseBlock = {
                height(IntrinsicSize.Min)
            }) { height(150.dp) }
            .clip(shapes.medium)
            .background(
                if (data.state == LessonState.REMOVED) colorScheme.surfaceVariant
                else colorScheme.surfaceContainer
            )
            .bclick(expanded || expandable) {
                setExpanded(!expanded)
            }
    ) {
        Tag(
            state = data.state,
            accentColor = commonViewAccentColor,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 12.dp, end = 8.dp)
        )
        
        TimeLine(currentTime, data.startTime, data.endTime, commonViewAccentColor)
        
        Row(
            Modifier
                .padding(8.dp, 12.dp)
                .applyIf(!expanded) { fillMaxHeight() }
        ) {
            Gap(4)
            
            CommonViewLeftSegment(
                data.startTime,
                data.endTime,
                data.state,
                commonViewAccentColor,
                data.number
            )
            
            Gap(12)
            
            Column(
                modifier = Modifier
                    .weight(1f)
                    .applyIf(!expanded) { fillMaxHeight() }
            ) {
                CommonViewSubject(
                    subject = currentSubject,
                    type = data.type,
                    state = data.state,
                    accentColor = commonViewAccentColor,
                    expanded = expanded,
                    setExpandable = setExpandable,
                    isSlideNext = isSlideNext
                )
                
                Gap(8)
                
                OtUnitsView(
                    viewType = viewType,
                    otUnits = data.otUnits,
                    expanded = expanded,
                    setExpandable = setExpandable,
                    currentIndex = currentUnitIndex,
                    onIndexChanged = { idx, next ->
                        currentUnitIndex = idx
                        isSlideNext = next
                    },
                    onSearchClicked = {
                        listener(ScheduleEvent.OnSearchClicked(it))
                    },
                    modifier = if (!expanded) Modifier.weight(1f) else Modifier
                )
            }
        }
    }
}

@Composable
private fun BoxScope.TimeLine(
    currentTime: LocalTime?,
    startTime: LocalTime,
    endTime: LocalTime,
    accentColor: Color
) {
    val passingPercent by useState(0f).apply {
        if (currentTime != null) value = currentTime.percentOf(startTime, endTime)
    }
    
    if (currentTime != null) Box(
        modifier = Modifier
            .animateContentSize()
            .fillMaxHeight(passingPercent)
            .align(Alignment.TopStart)
            .width(4.dp)
            .background(accentColor)
    )
    
}

@Composable
private fun Tag(
    modifier: Modifier = Modifier,
    state: LessonState,
    accentColor: Color
) {
    if (state != LessonState.COMMON) Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(CircleShape)
            .height(24.dp)
            .background(accentColor)
    ) {
        Text(
            modifier = Modifier.padding(8.dp, 4.dp),
            text = when (state) {
                LessonState.ADDED -> "доб."
                LessonState.REMOVED -> "отм."
                LessonState.CHANGED -> "изм."
                else -> ""
            },
            color = colorScheme.onPrimary,
            style = typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold
            )
        )
    }
}

@Composable
private fun SingleOtUnitView(
    viewType: ViewType,
    unit: OneTimeUnit,
    expanded: Boolean,
    setExpandable: (Boolean) -> Unit = {},
    onSearchClicked: (ScheduleSearch) -> Unit,
    color: Color = colorScheme.secondary,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    val cleanBuilding = unit.building
//        .replace("(?i)корпус\\s*|(?i)корп\\.?\\s*".toRegex(), "")
        .trim()
        .let { if (it.isEmpty() || it == "_") "-" else it }
    val isOnline = !unit.onlineUrl.isNullOrBlank() || unit.auditory.startsWith("http://") || unit.auditory.startsWith("https://")
    val meetingUrl = unit.onlineUrl ?: if (unit.auditory.startsWith("http")) unit.auditory else null

    Box(
        modifier = modifier,
        contentAlignment = if (horizontalAlignment == Alignment.CenterHorizontally) Alignment.Center else Alignment.TopStart
    ) {
        Column(horizontalAlignment = Alignment.Start) {
            val teacherOrGroup = if (viewType == ViewType.TEACHER) unit.group.name else unit.teacher.name
            if (teacherOrGroup.isNotBlank() && teacherOrGroup != "-") {
                AdditionalInfo(
                    color = color,
                    icon = if (viewType == ViewType.STUDENT) WHATIcons.Person else WHATIcons.Group,
                    texts = listOf(teacherOrGroup),
                    maxLines = if (viewType == ViewType.TEACHER && !expanded) 1 else Int.MAX_VALUE,
                    setExpandable = setExpandable,
                    onClick = {
                        onSearchClicked(
                            if (viewType == ViewType.TEACHER) ScheduleSearch.Group(it)
                            else ScheduleSearch.Teacher(it)
                        )
                    }
                )
            }

            if (isOnline && meetingUrl != null) {
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .bclick { uriHandler.openUri(meetingUrl) }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = "Онлайн",
                        modifier = Modifier.size(18.dp),
                        tint = colorScheme.primary
                    )
                    Gap(8)
                    Text(
                        text = "Подключиться",
                        color = colorScheme.primary,
                        style = typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            textDecoration = TextDecoration.Underline
                        )
                    )
                }
            } else {
                if (unit.auditory.isNotBlank() && unit.auditory != "-") {
                    AdditionalInfo(
                        color = color,
                        icon = WHATIcons.Room,
                        texts = listOf(unit.auditory)
                    )
                }

                if (cleanBuilding != "-") {
                    AdditionalInfo(
                        color = color,
                        icon = WHATIcons.Building,
                        texts = listOf(cleanBuilding)
                    )
                }
            }
        }
    }
}

@Composable
private fun OtUnitsView(
    viewType: ViewType,
    otUnits: List<OneTimeUnit>,
    expanded: Boolean,
    setExpandable: (Boolean) -> Unit = {},
    currentIndex: Int = 0,
    onIndexChanged: (Int, Boolean) -> Unit = { _, _ -> },
    onSearchClicked: (ScheduleSearch) -> Unit,
    color: Color = colorScheme.secondary,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    horizontalArrangement: Arrangement.Horizontal =
        Arrangement.spacedBy(if (viewType == ViewType.TEACHER) 16.dp else 8.dp),
    modifier: Modifier = Modifier
) {
    if (otUnits.isEmpty()) return

    if (otUnits.size == 1) {
        SingleOtUnitView(
            viewType = viewType,
            unit = otUnits.first(),
            expanded = expanded,
            setExpandable = setExpandable,
            onSearchClicked = onSearchClicked,
            color = color,
            horizontalAlignment = horizontalAlignment,
            modifier = modifier.fillMaxWidth()
        )
    } else {
        val currentIdx by rememberUpdatedState(currentIndex)
        val onIndexChangedState by rememberUpdatedState(onIndexChanged)
        var dragAccumulator by remember { mutableFloatStateOf(0f) }
        var isSlideNext by remember { mutableStateOf(true) }

        Column(
            modifier = modifier
                .fillMaxWidth()
                .applyIf(!expanded) { fillMaxHeight() },
            verticalArrangement = if (!expanded) Arrangement.SpaceBetween else Arrangement.Top,
            horizontalAlignment = horizontalAlignment
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (!expanded) Modifier.weight(1f) else Modifier)
                    .pointerInput(otUnits.size) {
                        detectHorizontalDragGestures(
                            onDragCancel = {
                                dragAccumulator = 0f
                            },
                            onDragEnd = {
                                if (dragAccumulator < -40f) {
                                    isSlideNext = true
                                    val nextIndex = (currentIdx + 1) % otUnits.size
                                    onIndexChangedState(nextIndex, true)
                                } else if (dragAccumulator > 40f) {
                                    isSlideNext = false
                                    val prevIndex = (currentIdx - 1 + otUnits.size) % otUnits.size
                                    onIndexChangedState(prevIndex, false)
                                }
                                dragAccumulator = 0f
                            },
                            onHorizontalDrag = { _, dragAmount ->
                                dragAccumulator += dragAmount
                            }
                        )
                    }
            ) {
                AnimatedContent(
                    targetState = currentIndex,
                    transitionSpec = {
                        if (isSlideNext) {
                            (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                                slideOutHorizontally { width -> -width } + fadeOut()
                            )
                        } else {
                            (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                                slideOutHorizontally { width -> width } + fadeOut()
                            )
                        }
                    },
                    label = "OtUnitsCarousel",
                    modifier = Modifier.fillMaxWidth()
                ) { page ->
                    SingleOtUnitView(
                        viewType = viewType,
                        unit = otUnits[page % otUnits.size],
                        expanded = expanded,
                        setExpandable = setExpandable,
                        onSearchClicked = onSearchClicked,
                        color = color,
                        horizontalAlignment = horizontalAlignment,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (expanded) {
                Gap(4)
            }

            // Centered dots indicator
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(otUnits.size) { index ->
                    val isSelected = currentIndex == index
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (isSelected) 6.dp else 4.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) colorScheme.primary
                                else colorScheme.outlineVariant
                            )
                            .bclick {
                                val next = index >= currentIdx
                                isSlideNext = next
                                onIndexChangedState(index, next)
                            }
                    )
                }
            }
        }
    }
}

@Composable
private fun CommonViewSubject(
    subject: String,
    type: LessonType,
    state: LessonState,
    accentColor: Color,
    expanded: Boolean,
    setExpandable: (Boolean) -> Unit,
    isSlideNext: Boolean = true
) = Box(
    Modifier
        .clip(if (expanded) shapes.medium else CircleShape)
        .fillMaxWidth()
        .background(accentColor.copy(alpha = .2f))
) {
    AnimatedContent(
        targetState = subject,
        transitionSpec = {
            if (isSlideNext) {
                (slideInHorizontally { width -> width / 4 } + fadeIn()).togetherWith(
                    slideOutHorizontally { width -> -width / 4 } + fadeOut()
                )
            } else {
                (slideInHorizontally { width -> -width / 4 } + fadeIn()).togetherWith(
                    slideOutHorizontally { width -> width / 4 } + fadeOut()
                )
            }
        },
        label = "SubjectAnimation"
    ) { currentText ->
        val isLongTitle = currentText.split(" ").size > 3
        val (subjectFontSize, setSubjectFontSize) = useState(if (isLongTitle) 12 else 16)
        
        Text(
            modifier = Modifier.padding(16.dp, 8.dp),
            text = currentText,
            color = when (state) {
                LessonState.REMOVED -> colorScheme.secondary
                else -> if (type.isNonStandard) colorScheme.tertiary
                else colorScheme.onPrimaryContainer
            },
            fontSize = subjectFontSize.sp,
            overflow = TextOverflow.Ellipsis,
            maxLines = if (expanded) Int.MAX_VALUE else 2,
            style = typography.titleSmall.copy(
                fontWeight = FontWeight.ExtraBold,
                lineHeight = (subjectFontSize + 4).sp,
                textDecoration = if (state == LessonState.REMOVED) TextDecoration.LineThrough
                else TextDecoration.None
            ),
            onTextLayout = {
                if (it.hasVisualOverflow) setExpandable(true)
                if (it.lineCount > 1 && subjectFontSize != 12) setSubjectFontSize(12)
            }
        )
    }
}


@Composable
private fun CommonViewLeftSegment(
    startTime: LocalTime,
    endTime: LocalTime,
    state: LessonState,
    accentColor: Color,
    number: Int
) = Column(
    modifier = Modifier
        .fillMaxHeight()
        .width(76.dp),
    verticalArrangement = Arrangement.SpaceBetween
) {
    Column {
        Gap(4)
        
        Text(
            text = formatTime(startTime),
            fontSize = 24.sp,
            maxLines = 1,
            softWrap = false,
            color = accentColor,
            style = typography.headlineSmall.copy(
                fontWeight = FontWeight.ExtraBold
            )
        )
        
        Text(
            text = formatTime(endTime),
            fontSize = 20.sp,
            maxLines = 1,
            softWrap = false,
            color = if (state == LessonState.REMOVED) colorScheme.secondary
            else colorScheme.onPrimaryContainer,
            style = typography.headlineSmall.copy(
                fontWeight = FontWeight.Medium
            )
        )
    }
    
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(CircleShape)
            .size(24.dp)
            .background(accentColor)
    ) {
        Text(
            text = number.toString(),
            color = colorScheme.onPrimary,
            style = typography.bodyLarge.copy(
                fontWeight = FontWeight.SemiBold
            )
        )
    }
}

@Composable
private fun AdditionalInfo(
    icon: ImageVector,
    texts: List<String>,
    maxLines: Int = Int.MAX_VALUE,
    color: Color = colorScheme.secondary,
    setExpandable: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
    onClick: ((String) -> Unit)? = null
) = Row(modifier = modifier, verticalAlignment = Alignment.Top) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(18.dp),
        tint = color
    )
    
    Gap(8)
    
    FlowRow(
        maxLines = maxLines
    ) {
        texts.forEachIndexed { i, it ->
            Text(
                text = it + if (i != texts.lastIndex) ", " else "",
                color = color,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
                style = typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                modifier = Modifier.bclick(onClick != null) { onClick?.invoke(it) }
            
            )
        }
    }
}

@Composable
fun BreakInfo(minutes: Int, active: Boolean = false) = Row(
    Modifier.padding(horizontal = 32.dp),
    verticalAlignment = Alignment.CenterVertically
) {
    WHATIcons.Run.Show(
        modifier = Modifier.size(20.dp),
        color = if (active) colorScheme.primary
        else colorScheme.outline
    )
    
    Gap(10)
    
    val hours = minutes / 60
    val mins = minutes % 60
    
    Text(
        buildString {
            append("Перерыв")
            if (hours != 0) append(" $hours ч.")
            if (mins != 0) append(" $mins мин.")
        },
        color = if (active) colorScheme.primary
        else colorScheme.outline,
        fontSize = 14.sp
    )
}

@Composable
fun LessonPreview() = MaterialTheme(DynamicScheme(Color(0xFF682C78), true).toColorScheme(isAmoled = false)) {
    Column(
        Modifier
            .background(colorScheme.background)
            .verticalScroll(rememberScrollState())
    ) {
        val currentTime = LocalTime(10, 45) // During second lesson
        Gap(12)
        
        // Morning lessons with realistic times
        LessonUI(
            data = Lesson(
                date = LocalDate(2026, 9, 1),
                number = 1,
                subject = "Математический анализ",
                type = LessonType.COMMON,
                startTime = LocalTime(8, 30),
                endTime = LocalTime(10, 0),
                state = LessonState.COMMON,
                otUnits = listOf(
                    OneTimeUnit(
                        group = Group("ПМИ-21"),
                        teacher = Teacher("Петрова Е.В."),
                        building = "Главный корпус",
                        auditory = "301"
                    )
                )
            ),
            viewType = ViewType.STUDENT,
            currentTime = currentTime
        ) {}
        Gap(12)
        
        // Break between lessons
        BreakInfo(30) // 30 min break
        Gap(12)
        
        // Current lesson (in progress at 10:45)
        LessonUI(
            data = Lesson(
                date = LocalDate(2026, 9, 1),
                number = 2,
                subject = "Базы данных",
                type = LessonType.COMMON,
                startTime = LocalTime(10, 30),
                endTime = LocalTime(12, 0),
                state = LessonState.COMMON,
                otUnits = listOf(
                    OneTimeUnit(
                        group = Group("ИС-22"),
                        teacher = Teacher("Соколов Д.М."),
                        building = "2",
                        auditory = "215"
                    ),
                    OneTimeUnit(
                        group = Group("ИС-23"),
                        teacher = Teacher("Соколов Д.М."),
                        building = "2",
                        auditory = "215"
                    )
                )
            ),
            viewType = ViewType.STUDENT,
            currentTime = currentTime
        ) {}
        Gap(12)
        
        BreakInfo(15) // 15 min break
        Gap(12)
        
        // Changed lesson (room changed)
        LessonUI(
            data = Lesson(
                date = LocalDate(2026, 9, 1),
                number = 3,
                subject = "Программирование на Kotlin",
                type = LessonType.COMMON,
                startTime = LocalTime(12, 15),
                endTime = LocalTime(13, 45),
                state = LessonState.CHANGED,
                otUnits = listOf(
                    OneTimeUnit(
                        group = Group("ИС-22"),
                        teacher = Teacher("Иванов А.А."),
                        building = "3",
                        auditory = "405 (бывш. 302)"
                    )
                )
            ),
            viewType = ViewType.STUDENT,
            currentTime = currentTime
        ) {}
        Gap(12)
        
        // Lunch break
        BreakInfo(60) // Lunch hour
        Gap(12)
        
        // Additional lesson
        LessonUI(
            data = Lesson(
                date = LocalDate(2026, 9, 1),
                number = 4,
                subject = "Факультатив: Машинное обучение",
                type = LessonType.ADDITIONAL,
                startTime = LocalTime(14, 45),
                endTime = LocalTime(16, 15),
                state = LessonState.COMMON,
                otUnits = listOf(
                    OneTimeUnit(
                        group = Group("ИС-21, ИС-22"),
                        teacher = Teacher("Смирнов П.Р."),
                        building = "Лабораторный корпус",
                        auditory = "101"
                    )
                )
            ),
            viewType = ViewType.STUDENT,
            currentTime = currentTime
        ) {}
        Gap(12)
        
        // Cancelled lesson (REMOVED state)
        LessonUI(
            data = Lesson(
                date = LocalDate(2026, 9, 1),
                number = 5,
                subject = "Философия",
                type = LessonType.COMMON,
                startTime = LocalTime(16, 30),
                endTime = LocalTime(18, 0),
                state = LessonState.REMOVED,
                otUnits = listOf(
                    OneTimeUnit(
                        group = Group("ИС-22"),
                        teacher = Teacher("Козлова М.И."),
                        building = "1",
                        auditory = "202"
                    )
                )
            ),
            viewType = ViewType.STUDENT,
            currentTime = currentTime
        ) {}
        Gap(12)
        
        // Teacher view example with multiple groups
        LessonUI(
            data = Lesson(
                date = LocalDate(2026, 9, 1),
                number = 1,
                subject = "Физика",
                type = LessonType.COMMON,
                startTime = LocalTime(8, 30),
                endTime = LocalTime(10, 0),
                state = LessonState.COMMON,
                otUnits = listOf(
                    OneTimeUnit(
                        group = Group("Физ-21"),
                        teacher = Teacher("Николаев В.П."),
                        building = "Главный корпус",
                        auditory = "115"
                    ),
                    OneTimeUnit(
                        group = Group("Физ-22"),
                        teacher = Teacher("Николаев В.П."),
                        building = "Главный корпус",
                        auditory = "115"
                    ),
                    OneTimeUnit(
                        group = Group("Физ-23"),
                        teacher = Teacher("Николаев В.П."),
                        building = "Главный корпус",
                        auditory = "115"
                    )
                )
            ),
            viewType = ViewType.TEACHER,
            currentTime = currentTime
        ) {}
        Gap(12)
        
        // Class hour
        LessonUI(
            data = Lesson(
                date = LocalDate(2026, 9, 1),
                number = 2,
                subject = "Классный час",
                type = LessonType.CLASS_HOUR,
                startTime = LocalTime(10, 30),
                endTime = LocalTime(11, 15),
                state = LessonState.COMMON,
                otUnits = listOf(
                    OneTimeUnit(
                        group = Group("ИС-22"),
                        teacher = Teacher("Куратор: Соколов Д.М."),
                        building = "1",
                        auditory = "актовый зал"
                    )
                )
            ),
            viewType = ViewType.STUDENT,
            currentTime = currentTime
        ) {}
        Gap(12)
    }
}

private fun LocalTime.percentOf(start: LocalTime, end: LocalTime): Float {
    val timeLeft = (this.hour - start.hour) * 60 + this.minute - start.minute
    val timeAll = (end.hour - start.hour) * 60 + end.minute - start.minute
    return timeLeft.toFloat() / timeAll.toFloat()
}