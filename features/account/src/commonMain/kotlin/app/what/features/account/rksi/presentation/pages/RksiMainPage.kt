package app.what.schedule.features.insts.rksi.presentation.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.Button
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.what.features.account.components.InfoBlock
import app.what.features.account.components.KeyValueList
import app.what.features.account.components.NewItemView
import app.what.foundation.core.Listener
import app.what.foundation.data.RemoteState
import app.what.foundation.ui.Gap
import app.what.foundation.ui.Show
import app.what.foundation.ui.SystemBarsGap
import app.what.foundation.ui.animations.rememberShimmer
import app.what.foundation.ui.bclick
import app.what.foundation.ui.controllers.rememberDialogController
import app.what.foundation.ui.controllers.rememberSheetController
import app.what.schedule.features.insts.rksi.domain.models.RksiEvent
import app.what.schedule.features.insts.rksi.domain.models.RksiState

@Composable
internal fun RksiMainScreen(
    state: State<RksiState>,
    listener: Listener<RksiEvent>
) {
    val dialog = rememberDialogController()
    val sheet = rememberSheetController()
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(Unit) {
        listener(RksiEvent.MainOpened)
    }

    val profile = state.value.profile
    val shimmer = rememberShimmer()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .wrapContentWidth(Alignment.CenterHorizontally)
            .widthIn(max = 860.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (state.value.profileFetchState is RemoteState.Error && profile == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(top = 120.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Не удалось загрузить профиль",
                        color = colorScheme.error,
                        fontSize = 16.sp
                    )
                    Gap(12)
                    Button(onClick = { listener(RksiEvent.MainOpened) }) {
                        Text("Повторить")
                    }
                }
            }
        } else if (profile == null) {
            // Loading Shimmer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
                    .background(colorScheme.surfaceContainer)
                    .statusBarsPadding()
                    .padding(18.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(62.dp)
                                .clip(CircleShape)
                                .background(shimmer)
                        )
                        Gap(16)
                        Column {
                            Box(
                                modifier = Modifier
                                    .width(180.dp)
                                    .height(20.dp)
                                    .clip(shapes.small)
                                    .background(shimmer)
                            )
                            Gap(8)
                            Box(
                                modifier = Modifier
                                    .width(90.dp)
                                    .height(16.dp)
                                    .clip(shapes.small)
                                    .background(shimmer)
                            )
                        }
                    }
                    Gap(16)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(shapes.medium)
                            .background(shimmer)
                    )
                }
            }
        } else {
            // Profile Card Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
                    .background(colorScheme.surfaceContainer)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                ) {
                    Gap(12)

                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(62.dp)
                                    .clip(CircleShape)
                                    .background(colorScheme.primary.copy(alpha = 0.15f))
                                    .border(2.dp, colorScheme.primary, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                )
                            }

                            Gap(16)

                            val shortName = profile.fullName.trim().split(Regex("\\s+")).take(2).joinToString(" ")

                            Column {
                                Text(
                                    shortName.ifBlank { profile.fullName },
                                    color = colorScheme.onSurface,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                if (profile.group.isNotBlank()) {
                                    Gap(4)
                                    Text(
                                        profile.group,
                                        color = colorScheme.tertiary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(colorScheme.tertiary.copy(alpha = 0.3f))
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                            .bclick {
                                                listener(RksiEvent.OnGroupClicked)
                                            }
                                    )
                                }
                            }
                        }
                    }

                    if (!profile.curator.isNullOrBlank()) {
                        Gap(12)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Классный руководитель:",
                                    fontSize = 12.sp,
                                    color = colorScheme.outline
                                )
                                Text(
                                    profile.curator!!,
                                    fontSize = 13.sp,
                                    color = colorScheme.onSurface,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            if (!profile.curatorPhone.isNullOrBlank()) {
                                IconButton(
                                    onClick = {
                                        try {
                                            uriHandler.openUri("tel:${profile.curatorPhone}")
                                        } catch (_: Exception) {}
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Call,
                                        contentDescription = "Позвонить",
                                        tint = colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    Gap(16)

                    // Action buttons row (Подробнее, QR, Выйти)
                    Row(
                        modifier = Modifier
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
                                    sheet.open(true) {
                                        RksiStudentDetail(
                                            state = state.value,
                                            onEditClicked = {
                                                sheet.close()
                                                dialog.open(true) {
                                                    RksiProfileEditorDialog(state, listener, onBack = { dialog.close() })
                                                }
                                            }
                                        )
                                    }
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
                                    dialog.open(true) {
                                        RksiPaymentQrDialog(state, listener, onBack = { dialog.close() })
                                    }
                                }
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCode,
                                contentDescription = "QR-код",
                                tint = colorScheme.onPrimary,
                                modifier = Modifier.size(22.dp)
                            )
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
                                    listener(RksiEvent.LogoutClicked)
                                }
                        ) {
                            Icons.AutoMirrored.Filled.Logout.Show(colorScheme.onError, 22)
                        }
                    }

                    Gap(18)
                }
            }
        }

        Gap(16)

        // 2 InfoBlocks in a Row (equal height)
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(horizontal = 12.dp)
        ) {
            InfoBlock(
                accentColor = colorScheme.primary,
                icon = Icons.Default.Description,
                title = "Справки",
                description = "Заказ",
                modifier = Modifier.weight(1f).fillMaxHeight()
            ) {
                dialog.open(true) {
                    RksiEnquiriesDialog(state, listener, onBack = { dialog.close() })
                }
            }

            InfoBlock(
                accentColor = colorScheme.primary,
                icon = Icons.AutoMirrored.Filled.Assignment,
                title = "Долги",
                description = "Пересдачи",
                modifier = Modifier.weight(1f).fillMaxHeight()
            ) {
                dialog.open(true) {
                    RksiReAttestationDialog(state, listener, onBack = { dialog.close() })
                }
            }
        }

        Gap(12)

        // "Мои новости" section
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
                    listener(RksiEvent.OnShowAllNewsClicked)
                }) { Text("Все") }
            }

            if (state.value.news.isEmpty() && state.value.newsFetchState == RemoteState.Loading) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    Gap(4)
                    repeat(3) {
                        Box(
                            modifier = Modifier
                                .width(220.dp)
                                .height(130.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(shimmer)
                        )
                    }
                    Gap(4)
                }
            } else if (state.value.news.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    Gap(4)
                    state.value.news.forEach { item ->
                        NewItemView(item) {
                            listener(RksiEvent.OnNewClicked(item.id))
                        }
                    }
                    Gap(4)
                }
            }
        }

        Gap(60)
        SystemBarsGap()
    }
}

@Composable
fun RksiStudentDetail(
    state: RksiState,
    onEditClicked: () -> Unit
) = Column(
    Modifier
        .fillMaxWidth()
        .wrapContentWidth(Alignment.CenterHorizontally)
        .widthIn(max = 860.dp)
        .padding(16.dp)
        .systemBarsPadding()
        .verticalScroll(rememberScrollState())
) {
    val profile = state.profile ?: return@Column

    Text(
        "Анкета студента",
        style = typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = colorScheme.onSurface,
        modifier = Modifier.padding(bottom = 16.dp)
    )

    val items = buildList {
        add("ФИО" to profile.fullName)
        if (profile.group.isNotBlank()) add("Группа" to profile.group)
        if (!profile.specialty.isNullOrBlank()) add("Специальность" to profile.specialty!!)
        if (!profile.curator.isNullOrBlank()) add("Классный руководитель" to profile.curator!!)
        if (!profile.curatorPhone.isNullOrBlank()) add("Телефон руководителя" to profile.curatorPhone!!)
        if (!profile.educationForm.isNullOrBlank()) add("Форма обучения" to profile.educationForm!!)
        if (!profile.healthGroup.isNullOrBlank()) add("Группа здоровья" to profile.healthGroup!!)
        if (!profile.socialStatus.isNullOrBlank()) add("Социальный статус" to profile.socialStatus!!)
    }

    KeyValueList(items = items)

    Gap(24)

    Button(
        onClick = onEditClicked,
        modifier = Modifier.fillMaxWidth(),
        shape = shapes.medium
    ) {
        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
        Gap(8)
        Text("Редактировать анкету")
    }
}

