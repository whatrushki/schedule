package app.what.schedule.features.insts.dgtu.presentation.pages

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.shapes
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import app.what.foundation.core.Listener
import app.what.foundation.data.RemoteState
import app.what.foundation.ui.Gap
import app.what.foundation.ui.Show
import app.what.foundation.ui.SystemBarsGap
import app.what.foundation.ui.VerticalGap
import app.what.foundation.ui.bclick
import app.what.foundation.ui.controllers.rememberDialogController
import app.what.foundation.ui.controllers.rememberSheetController
import app.what.foundation.ui.useState
import app.what.domain.models.NewListItem
import app.what.schedule.features.insts.dgtu.domain.models.DgtuEvent
import app.what.schedule.features.insts.dgtu.domain.models.DgtuState
import app.what.schedule.features.insts.dgtu.domain.models.EventListItem
import app.what.schedule.features.insts.dgtu.domain.models.Notification
import app.what.schedule.features.insts.dgtu.presentation.components.InfoBlock
import app.what.schedule.ui.components.AsyncImageWithFallback
import app.what.schedule.ui.theme.icons.WHATIcons
import app.what.schedule.ui.theme.icons.filled.Features
import app.what.schedule.ui.theme.icons.filled.Logs
import app.what.schedule.ui.theme.icons.filled.Room
import app.what.schedule.ui.theme.icons.filled.Run
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import app.what.foundation.utils.DateTimeUtils
import kotlinx.datetime.LocalDateTime

@Composable
internal fun DGTUMainScreen(
    state: State<DgtuState>,
    listener: Listener<DgtuEvent>
) = Column(
    modifier = Modifier
        .fillMaxSize()
        .wrapContentWidth(Alignment.CenterHorizontally)
        .widthIn(max = 860.dp)
        .verticalScroll(rememberScrollState())
        .background(colorScheme.surface),
    horizontalAlignment = Alignment.CenterHorizontally
) {
    val dialog = rememberDialogController()
    val sheet = rememberSheetController()
    
    LaunchedEffect(Unit) {
        listener(DgtuEvent.MainOpened)
    }
    
    if (state.value.studentInfo == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = 120.dp),
            contentAlignment = Alignment.Center
        ) {
            when (state.value.studentInfoFetchState) {
                is RemoteState.Error -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Не удалось загрузить профиль",
                            color = colorScheme.error,
                            fontSize = 16.sp
                        )
                        Gap(12)
                        Button(onClick = { listener(DgtuEvent.MainOpened) }) {
                            Text("Повторить")
                        }
                    }
                }
                else -> {
                    CircularProgressIndicator(color = colorScheme.primary)
                }
            }
        }
        return
    }
    
    with(state.value.studentInfo!!) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
                    .background(colorScheme.surfaceContainer)
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                ) {
                    Gap(12)
                    
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AsyncImageWithFallback(
                                state.value.studentInfo!!.photoLink,
                                headers = mapOf("authToken" to "Bearer ${state.value.token}"),
                                modifier = Modifier
                                    .size(62.dp)
                                    .clip(CircleShape)
                                    .border(3.dp, colorScheme.primary, CircleShape)
                            )
                            
                            Gap(18)
                            
                            Column {
                                Text(
                                    "$name $surname",
                                    color = colorScheme.onSurface,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "ID: ${state.value.studentId}",
                                    color = colorScheme.onSurfaceVariant,
                                    fontSize = 14.sp,
                                )
                                Gap(4)
                                Text(
                                    group.name,
                                    color = colorScheme.tertiary,
                                    fontSize = 12.sp,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(colorScheme.tertiary.copy(.3f))
                                        .padding(horizontal = 8.dp)
                                        .bclick {
                                            listener(DgtuEvent.OnGroupClicked)
                                        }
                                )
                            }
                        }
                        
                        IconButton(
                            onClick = {
                                dialog.open(true) { NotificationsPane(state.value.notifications) }
                            }
                        ) {
                            Icons.Default.Notifications.Show(
                                colorScheme.onSurfaceVariant, 24,
                                Modifier.padding(top = 12.dp, end = 8.dp)
                            )
                        }
                    }
                    
                    Gap(16)
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                    ) {
                        with(state.value.studentStatInfo) {
                            StatBox(
                                (this?.avgCourse ?: 0f).toString(),
                                "Ср. балл",
                                accentColor = colorScheme.tertiary,
                                modifier = Modifier.weight(1f)
                            )
                            
                            StatBox(
                                this?.avg4?.toInt()?.takeIf { it != 0 }?.toString() ?: "-",
                                "Хор.",
                                isPercent = true,
                                modifier = Modifier.weight(1f),
                            )
                            
                            StatBox(
                                this?.avg5?.toInt()?.takeIf { it != 0 }?.toString() ?: "-",
                                "Отл.",
                                isPercent = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    
                    Gap(16)
                    
                    Row(
                        Modifier
                            .height(IntrinsicSize.Min)
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .clip(shapes.small)
                                .background(colorScheme.primary)
                                .weight(1f)
                                .bclick {
                                    sheet.open(true) { StudentDetail(state.value) }
                                }
                        ) {
                            Text(
                                "Подробнее",
                                Modifier.padding(12.dp, 8.dp),
                                color = colorScheme.onPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        
                        Gap(8)
                        
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .width(48.dp)
                                .fillMaxHeight()
                                .clip(shapes.small)
                                .background(colorScheme.primary)
                                .bclick {
                                    listener(DgtuEvent.GenerateAccessQrCodeClicked)
                                    dialog.open { AccessQrPane(state) }
                                }
                        ) {
                            WHATIcons.Room.Show(colorScheme.onPrimary, 22)
                        }

                        Gap(8)

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .width(48.dp)
                                .fillMaxHeight()
                                .clip(shapes.small)
                                .background(colorScheme.error)
                                .bclick {
                                    listener(DgtuEvent.LogoutClicked)
                                }
                        ) {
                            Icons.AutoMirrored.Filled.Logout.Show(colorScheme.onError, 22)
                        }
                    }
                    
                    Gap(18)
                }
            }
            
            Gap(16)
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                InfoBlock(
                    accentColor = colorScheme.primary,
                    icon = WHATIcons.Features,
                    title = "Зачетка",
                    description = "Оценки и сессии",
                    modifier = Modifier.weight(1f)
                ) {
                    listener(DgtuEvent.ZachBookOpened)
                    dialog.open(true) {
                        DgtuZachBookPage(state, listener, onBack = { dialog.close() })
                    }
                }
                
                InfoBlock(
                    accentColor = colorScheme.primary,
                    icon = Icons.Default.Mail,
                    title = "Почта",
                    description = "stud.edu.ru",
                    modifier = Modifier.weight(1f)
                ) {
                    listener(DgtuEvent.MailsOpened)
                    dialog.open(true) {
                        DgtuMailsPage(state, listener, onBack = { dialog.close() })
                    }
                }
            }
            
            Column {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                ) {
                    Text(
                        "Мои новости",
                        color = colorScheme.onSurface,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                    
                    TextButton(onClick = {
                        listener(DgtuEvent.OnShowAllNewsClicked)
                    }) { Text("Все") }
                }
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    Gap(4)
                    state.value.news.forEach {
                        NewItemView(it) {
                            listener(DgtuEvent.OnNewClicked(it.id))
                        }
                    }
                    Gap(4)
                }
            }
            
            Column {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                ) {
                    Text(
                        "События",
                        color = colorScheme.onSurface,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                    
                    TextButton(onClick = {
                        dialog.open(true) {
                            EventsListPane(state) {
                                listener(DgtuEvent.OnEventClicked(it.id.toString()))
                                dialog.open(true) { EventDetailContent(state) }
                            }
                        }
                    }) { Text("Все") }
                }
                
                state.value.events.take(3).forEachIndexed { i, it ->
                    EventListItemView(it, i) {
                        listener(DgtuEvent.OnEventClicked(it.id.toString()))
                        dialog.open(true) { EventDetailContent(state) }
                    }
                }
            }
            
            Gap(60)
            SystemBarsGap()
        }
}


@Composable
fun StudentDetail(
    state: DgtuState
) = Column(
    Modifier
        .fillMaxSize()
        .wrapContentWidth(Alignment.CenterHorizontally)
        .widthIn(max = 860.dp)
        .padding(12.dp)
        .systemBarsPadding()
) {
    val student = state.studentInfo!!
    
    KeyValueList(
        items = listOf(
            "ФИО" to student.fullName,
            "Группа" to student.group.name,
            "Курс" to student.course,
            "Кафедра" to student.kafName,
            "Факультет" to student.faculty,
            "Дата рождения" to student.birthday.toString(),
            "Гражданство" to student.nationality,
            "Email" to student.email,
            "Телефон" to (student.numberMobile ?: "—"),
            "Студенческий" to student.numRecordBook,
            "Идентификатор" to state.studentId.toString(),
            "Год поступления" to student.admissionYear,
        )
    )
}

@Composable
private fun KeyValueList(
    items: List<Pair<String, String>>
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items.forEach { (key, value) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = key,
                    style = typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant
                )
                
                Text(
                    text = value,
                    style = typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.onSurface,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
            
            if (items.last().first != key) {
                HorizontalDivider(
                    color = colorScheme.outlineVariant.copy(alpha = 0.3f),
                    thickness = 1.dp
                )
            }
        }
    }
}

fun getCategoryIcon(category: String): ImageVector = when (category) {
    "Журналы" -> WHATIcons.Run // Книга, как на фото
    "Уведомление" -> WHATIcons.Run // Колокольчик
    "Справки" -> WHATIcons.Run // Документ/справка
    "Зачетная книжка" -> WHATIcons.Run// Звезда для оценок
    "Перемещения" -> WHATIcons.Run  // Стрелки для перемещений
    else -> WHATIcons.Run  // Дефолт
}

fun getCategoryColor(category: String, default: Color): Color = when (category) {
    "Журналы" -> Color(0xFF2196F3)  // Синий, как на фото
    "Уведомление" -> Color(0xFF4CAF50)  // Зелёный
    "Справки" -> Color(0xFFFF9800)  // Оранжевый
    "Зачетная книжка" -> Color(0xFF9C27B0)  // Фиолетовый
    "Перемещения" -> Color(0xFF673AB7)  // Тёмно-фиолетовый (или подставьте)
    else -> default
}


private val EventColors = listOf(
    Color(0xFF1976D2),  // синий
    Color(0xFF388E3C),  // зелёный
    Color(0xFFF57C00),  // оранжевый
    Color(0xFF8E24AA),  // фиолетовый
    Color(0xFFD81B60),  // розово-красный
)

@Composable
fun NotificationsPane(data: List<Notification>) {
    LazyColumn(
        Modifier.fillMaxSize()
    ) {
        item { VerticalGap(20) }
        items(data, key = { it.id }) {
            NotificationItem(it)
        }
    }
}


@Composable
fun NotificationItem(data: Notification) = Box(
    Modifier.height(IntrinsicSize.Min)
) {
    val uriHandler = LocalUriHandler.current
    val primary = colorScheme.primary
    val icon = remember { getCategoryIcon(data.category) }
    val accentColor = remember { getCategoryColor(data.category, primary) }
    val (expanded, setExpanded) = useState(false)
    
    Row(
        Modifier.padding(horizontal = 16.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(accentColor)
            ) {
                icon.Show(Color.White, 16, Modifier.padding(4.dp))
            }
            
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(1.dp)
                    .padding(vertical = 4.dp)
                    .background(colorScheme.secondary)
            )
        }
        
        Gap(16)
        
        Column {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    data.category.uppercase(),
                    color = accentColor,
                    fontSize = 14.sp,
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.Bold,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                
                Text(
                    DateTimeUtils.formatDateTime(data.date),
                    color = colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                )
            }
            
            Gap(12)
            
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(shapes.medium)
                    .background(colorScheme.surfaceContainer)
                    .bclick { setExpanded(!expanded) }
            ) {
                Column(
                    Modifier.padding(12.dp)
                ) {
                    Text(
                        data.content,
                        modifier = Modifier.animateContentSize(),
                        color = colorScheme.onSurface,
                        fontSize = 16.sp,
                        lineHeight = 20.sp,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = if (expanded) Int.MAX_VALUE else 3
                    )
                    
                    if (data.link != null) {
                        Gap(8)
                        
                        TextButton(onClick = { uriHandler.openUri(data.link.second) }) {
                            Text(data.link.first)
                        }
                    }
                }
            }
            
            Gap(32.dp)
        }
    }
}

@Composable
fun AccessQrPane(state: State<DgtuState>) = Box(
    Modifier.background(Color.White)
) {
    val painter = rememberQrCodePainter(state.value.accessQr.toString())
    
    Image(
        painter = painter,
        contentDescription = "Access QR Code",
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clip(shapes.medium)
            .aspectRatio(1f)
    )
}

@Composable
fun StatBox(
    data: String,
    text: String,
    modifier: Modifier = Modifier,
    accentColor: Color = colorScheme.primary,
    isPercent: Boolean = false,
) = Box(
    contentAlignment = Alignment.Center,
    modifier = modifier
        .clip(shapes.medium)
        .background(colorScheme.surfaceContainerHigh)
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(vertical = 16.dp)
    ) {
        Text(
            data.plus(if (isPercent) " %" else ""),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = accentColor
        )
        
        Text(
            text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            color = colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun EventsListPane(
    state: State<DgtuState>,
    onClick: (EventListItem) -> Unit
) = Box(
    Modifier.fillMaxSize(),
    contentAlignment = Alignment.TopCenter
) {
    LazyColumn(
        Modifier
            .fillMaxSize()
            .wrapContentWidth(Alignment.CenterHorizontally)
            .widthIn(max = 760.dp)
    ) {
        itemsIndexed(state.value.events, key = { i, it -> it.id }) { i, it ->
            EventListItemView(it, i) { onClick(it) }
        }
    }
}

@Composable
fun EventListItemView(data: EventListItem, index: Int, onClick: () -> Unit) =
    Box(Modifier.bclick(block = onClick)) {
        val accentColor = EventColors.getOrElse(index % EventColors.size) { EventColors.first() }
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp, 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clip(shapes.medium)
                    .background(accentColor.copy(alpha = .2f))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp, 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        DateTimeUtils.RUSSIAN_MONTHS_NOMINATIVE.getOrElse(data.date.monthNumber - 1) { "" }.take(3).uppercase(),
                        color = accentColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        lineHeight = 16.sp
                    )
                    
                    Text(
                        data.date.dayOfMonth.toString().padStart(2, '0'),
                        color = accentColor,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 24.sp,
                        lineHeight = 24.sp
                    )
                }
            }
            
            Gap(16)
            
            Column(Modifier.weight(1f)) {
                Text(
                    data.title,
                    fontSize = 16.sp,
                    color = colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                val timeText = "${data.date.hour.toString().padStart(2, '0')}:${data.date.minute.toString().padStart(2, '0')}"
                val placeText = data.place?.trim()?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
                val subtitleText = if (placeText != null) "$timeText • $placeText" else timeText

                Text(
                    subtitleText,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = colorScheme.onSurfaceVariant
                )
            }
        }
    }

@Composable
fun NewItemView(data: NewListItem, modifier: Modifier = Modifier, onClick: () -> Unit) = Box(
    modifier
        .width(230.dp)
        .clip(shapes.large)
        .background(colorScheme.surfaceContainer)
        .bclick(block = onClick)
) {
    data.tags.firstOrNull()?.let { tag ->
        FilterChip(
            true, {},
            label = { Text(tag.name) },
            modifier = Modifier
                .zIndex(2f)
                .padding(top = 8.dp, start = 8.dp)
        )
    }
    
    Column {
        AsyncImageWithFallback(
            data.bannerUrl,
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
        )
        
        Column(
            Modifier.padding(12.dp)
        ) {
            Text(
                DateTimeUtils.formatDate(data.timestamp),
                fontSize = 14.sp,
                color = colorScheme.onSurfaceVariant
            )
            
            Text(
                data.title,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                color = colorScheme.onSurface
            )
        }
    }
}

private fun formatEventDate(start: LocalDateTime, end: LocalDateTime?): String {
    val dayMonth = DateTimeUtils.formatShortDate(start.date).replaceFirstChar { it.uppercase() }
    val timeStart = DateTimeUtils.formatTime(start.time)
    
    return if (end != null && end.date == start.date) {
        val timeEnd = DateTimeUtils.formatTime(end.time)
        "$dayMonth, $timeStart – $timeEnd"
    } else {
        "$dayMonth, $timeStart"
    }
}

@Composable
fun EventDetailContent(
    state: State<DgtuState>,
    modifier: Modifier = Modifier,
    onOpenClicked: () -> Unit = {},
) {
    val uriHandler = LocalUriHandler.current
    val event = state.value.eventDetail

    if (event == null) {
        Box(
            modifier = modifier.fillMaxSize().padding(40.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = colorScheme.primary)
        }
        return
    }

    val rawUrl = event.linkOrganizer?.takeIf { it.isNotBlank() } ?: "https://lk.donstu.ru"
    val targetUrl = if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) rawUrl else "https://$rawUrl"

    val handleOpen = {
        if (onOpenClicked != {}) {
            onOpenClicked()
        } else {
            try {
                uriHandler.openUri(targetUrl)
            } catch (_: Exception) {}
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = 760.dp)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 76.dp)
        ) {
            Gap(24)
            
            if (event.categoryName.isNotBlank()) {
                FilterChip(
                    selected = true,
                    onClick = {},
                    label = { Text(event.categoryName) }
                )
                
                Gap(16)
            }
            
            Text(
                text = event.name,
                style = typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface
            )
            
            Gap(20)
            
            InfoItem(
                formatEventDate(event.dateStart, event.dateEnd),
                "Время проведения",
            ) {
                Icons.Outlined.DateRange.Show(colorScheme.primary)
            }
            
            Gap(12)
            
            InfoItem(
                event.place,
                "Место проведения"
            ) {
                Icons.Outlined.LocationOn.Show(colorScheme.primary)
            }
            
            Gap(12)
            
            if (event.initiator != null) {
                InfoItem(
                    event.initiator.name,
                    "Инициатор",
                ) {
                    Icons.Outlined.Person.Show(colorScheme.primary)
                }
                
                Gap(20)
            }
            
            Column {
                Text(
                    text = "Описание мероприятия",
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = event.description,
                    color = colorScheme.onSurfaceVariant,
                    lineHeight = 24.sp
                )
            }
            
            Gap(20)
            
            if (event.target != null || event.levelName != null) Column {
                Text(
                    text = "Дополнительно",
                    style = typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.onSurface
                )
                
                Gap(8.dp)
                
                if (event.levelName != null) AdditionalInfoItem("Уровень: ${event.levelName}")
                if (event.target != null) AdditionalInfoItem("Цель: ${event.target}")
                
                Gap(20)
            }
        }
        
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(colorScheme.surface)
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Button(
                onClick = handleOpen,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = shapes.medium
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Открыть", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun AdditionalInfoItem(
    content: String
) = Row {
    WHATIcons.Run.Show(colorScheme.primary, 18, Modifier.padding(top = 4.dp))
    Gap(8)
    Text(content, style = typography.bodyMedium)
}

@Composable
fun InfoItem(
    title: String,
    description: String,
    leadingContent: @Composable () -> Unit
) = Row(Modifier.fillMaxWidth()) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        leadingContent()
    }
    
    Gap(16)
    
    Column {
        Text(
            text = title,
            style = typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = colorScheme.onSurface
        )
        
        Text(
            description,
            style = typography.bodyMedium,
            color = colorScheme.onSurfaceVariant
        )
    }
}