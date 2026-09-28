package app.what.schedule.features.insts.rksi.presentation.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.what.foundation.core.Listener
import app.what.foundation.data.RemoteState
import app.what.foundation.ui.Gap
import app.what.schedule.core.models.ReAttestationItemDto
import app.what.schedule.features.insts.rksi.domain.models.RksiEvent
import app.what.schedule.features.insts.rksi.domain.models.RksiState

@Composable
internal fun RksiReAttestationDialog(
    state: State<RksiState>,
    listener: Listener<RksiEvent>,
    onBack: () -> Unit
) {
    LaunchedEffect(Unit) {
        if (state.value.reAttestations.isEmpty()) {
            listener(RksiEvent.LoadReAttestations)
        }
    }

    val s = state.value

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.surface)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Назад",
                    tint = colorScheme.onSurface
                )
            }
            Text(
                "Переаттестация",
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        if (s.reAttestationsFetchState is RemoteState.Loading && s.reAttestations.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colorScheme.primary)
            }
        } else if (s.reAttestations.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(36.dp))
                            .background(colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    Gap(16)
                    Text(
                        "Задолженностей нет",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface
                    )
                    Gap(8)
                    Text(
                        "У вас отсутствуют направления на переаттестацию",
                        fontSize = 14.sp,
                        color = colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Gap(4)
                }
                items(s.reAttestations) { item ->
                    ReAttestationCard(item)
                }
                item {
                    Gap(24)
                }
            }
        }
    }
}

@Composable
private fun ReAttestationCard(item: ReAttestationItemDto) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colorScheme.surfaceContainer)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            item.discipline,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = colorScheme.onSurface
        )

        Gap(4)

        Text(
            "Преподаватель: ${item.teacher}",
            fontSize = 13.sp,
            color = colorScheme.onSurfaceVariant
        )

        Gap(6)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Выдано: ${item.issueDate}",
                fontSize = 12.sp,
                color = colorScheme.outline
            )
            Text(
                "Срок: ${item.deadlineDate}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = colorScheme.error
            )
        }
    }
}
