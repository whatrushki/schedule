package app.what.schedule.features.insts.dgtu.presentation.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import app.what.foundation.ui.AppPullToRefresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.what.foundation.core.Listener
import app.what.foundation.data.RemoteState
import app.what.foundation.ui.Gap
import app.what.foundation.ui.bclick
import app.what.schedule.features.insts.dgtu.domain.models.DgtuEvent
import app.what.schedule.features.insts.dgtu.domain.models.DgtuState
import app.what.schedule.features.insts.dgtu.domain.models.Mail
import app.what.schedule.ui.components.AsyncImageWithFallback
import app.what.schedule.ui.components.Fallback
import app.what.foundation.utils.DateTimeUtils
import kotlinx.datetime.LocalDateTime

import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import app.what.foundation.ui.PlatformBackHandler

@Composable
fun DgtuMailsPage(
    state: State<DgtuState>,
    listener: Listener<DgtuEvent>,
    onBack: (() -> Unit)? = null
) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()
    var selectedMail by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    PlatformBackHandler(enabled = pagerState.currentPage == 1) {
        coroutineScope.launch {
            pagerState.animateScrollToPage(0)
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage == 0 && selectedMail != null) {
            selectedMail = null
            listener(DgtuEvent.CloseMailDetail)
        }
    }

    HorizontalPager(
        state = pagerState,
        userScrollEnabled = pagerState.currentPage == 1,
        modifier = Modifier.fillMaxSize()
    ) { page ->
        when (page) {
            0 -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .wrapContentWidth(Alignment.CenterHorizontally)
                        .widthIn(max = 860.dp)
                ) {
                    AppPullToRefresh(
                        isRefreshing = state.value.mailsFetchState == RemoteState.Loading,
                        onRefresh = { listener(DgtuEvent.MailsOpened) },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val lazyListState = rememberLazyListState()

                        LaunchedEffect(lazyListState.canScrollForward) {
                            if (!lazyListState.canScrollForward && state.value.mailsFetchState != RemoteState.Loading)
                                listener(DgtuEvent.OnMailsListEndingScrolled)
                        }

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            state = lazyListState
                        ) {
                            when (state.value.mailsFetchState) {
                                is RemoteState.Error -> item {
                                    Fallback(
                                        "Произошла непредвиденная ошибка",
                                        Modifier.fillMaxSize(),
                                        "Попробовать снова" to { listener(DgtuEvent.MailsOpened) }
                                    )
                                }

                                RemoteState.Success, RemoteState.Loading -> items(state.value.mails, key = { it.id }) { mail ->
                                    MailListItem(
                                        data = mail,
                                        modifier = Modifier.animateItem(),
                                        onClick = {
                                            selectedMail = mail.id to mail.messageId
                                            listener(DgtuEvent.MailOpened(mail.id, mail.messageId))
                                            coroutineScope.launch {
                                                pagerState.animateScrollToPage(1)
                                            }
                                        }
                                    )
                                }

                                else -> Unit
                            }
                        }
                    }
                }
            }

            1 -> {
                val currentSelected = selectedMail
                if (currentSelected != null) {
                    DgtuMailDetailPage(
                        threadId = currentSelected.first,
                        messageId = currentSelected.second,
                        state = state,
                        listener = listener,
                        onBack = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(0)
                            }
                        }
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
fun MailListItem(
    data: Mail,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) = Box(
    modifier
        .fillMaxWidth()
        .bclick { onClick() }
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(12.dp)
    ) {
        AsyncImageWithFallback(
            data.photoLink,
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
        )

        Gap(12)

        Column(modifier = Modifier.weight(1f)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    formatName(data.sender),
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                Gap(8)

                Text(
                    formatDateTime(data.sendDateTime),
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariant
                )
            }

            Text(
                data.title,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = colorScheme.secondary
            )

            Text(
                data.description,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 12.sp,
                color = colorScheme.onSurfaceVariant
            )
        }
    }
}

fun formatName(value: String): String {
    val parts = value.trim().split("\\s+".toRegex())
    return if (parts.size >= 3) {
        val initials = listOfNotNull(
            parts.getOrNull(1)?.firstOrNull()?.let { "$it." },
            parts.getOrNull(2)?.firstOrNull()?.let { "$it." }
        ).joinToString(" ")
        "${parts[0]} $initials".trim()
    } else value
}

fun formatDateTime(value: LocalDateTime): String {
    val today = app.what.foundation.utils.currentLocalDate()
    val date = value.date

    val timeStr = DateTimeUtils.formatTime(value.time)
    val shortMonthStr = "${date.dayOfMonth} ${DateTimeUtils.RUSSIAN_MONTHS.getOrElse(date.monthNumber - 1) { "" }.take(3)}"

    return when {
        date == today -> timeStr
        date.toEpochDays() == today.toEpochDays() - 1 -> "вчера"
        date.toEpochDays() == today.toEpochDays() - 2 -> "позавчера"
        else -> shortMonthStr.replaceFirstChar { it.uppercaseChar() }
    }
}