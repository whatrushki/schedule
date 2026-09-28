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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.shapes
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.what.features.account.components.SelectField
import app.what.foundation.core.Listener
import app.what.foundation.data.RemoteState
import app.what.foundation.ui.Gap
import app.what.schedule.features.insts.rksi.domain.models.RksiEvent
import app.what.schedule.features.insts.rksi.domain.models.RksiState
import app.what.schedule.ui.components.AsyncImageWithFallback

@Composable
internal fun RksiPaymentQrDialog(
    state: State<RksiState>,
    listener: Listener<RksiEvent>,
    onBack: () -> Unit
) {
    LaunchedEffect(Unit) {
        if (state.value.qrConfig == null) {
            listener(RksiEvent.LoadPaymentQr)
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
                "QR для оплаты",
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        
        if (s.qrConfigFetchState is RemoteState.Loading && s.qrConfig == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colorScheme.primary)
            }
        } else if (s.qrConfig == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Не удалось загрузить параметры оплаты", color = colorScheme.error)
            }
        } else {
            val config = s.qrConfig
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Gap(12)
                
                val currentTarget = config.targets.firstOrNull { it.id == s.selectedQrTarget }
                SelectField(
                    label = "Назначение платежа",
                    selectedValueText = currentTarget?.title ?: "",
                    options = config.targets.map { it.id to it.title },
                    selectedId = s.selectedQrTarget,
                    onSelect = { listener(RksiEvent.SelectQrTarget(it)) }
                )
                
                Gap(12)
                
                val currentYear = config.years.firstOrNull { it.id == s.selectedQrYear }
                SelectField(
                    label = "Учебный год",
                    selectedValueText = currentYear?.title ?: "",
                    options = config.years.map { it.id to it.title },
                    selectedId = s.selectedQrYear,
                    onSelect = { listener(RksiEvent.SelectQrYear(it)) }
                )
                
                Gap(12)
                
                val currentPeriod = config.periods.firstOrNull { it.id == s.selectedQrPeriod }
                SelectField(
                    label = "Период",
                    selectedValueText = currentPeriod?.title ?: "",
                    options = config.periods.map { it.id to it.title },
                    selectedId = s.selectedQrPeriod,
                    onSelect = { listener(RksiEvent.SelectQrPeriod(it)) }
                )
                
                Gap(24)
                
                // QR Image container
                Box(
                    modifier = Modifier
                        .size(260.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(colorScheme.surfaceContainerHigh)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (s.isQrLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(36.dp),
                            color = colorScheme.primary
                        )
                    } else if (s.qrImageBytes != null) {
                        coil3.compose.AsyncImage(
                            model = s.qrImageBytes,
                            contentDescription = "QR для оплаты",
                            modifier = Modifier.fillMaxSize().clip(shapes.medium)
                        )
                    } else if (s.qrImageUrl != null) {
                        AsyncImageWithFallback(
                            s.qrImageUrl,
                            modifier = Modifier.fillMaxSize().clip(shapes.medium)
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCode,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = colorScheme.outline
                            )
                            Gap(8)
                            Text(
                                "Выберите параметры для формирования QR",
                                color = colorScheme.outline,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                
                Gap(16)
                
                Text(
                    "Отсканируйте QR-код в мобильном приложении вашего банка для быстрой оплаты образовательных услуг",
                    fontSize = 13.sp,
                    color = colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                
                Gap(24)
            }
        }
    }
}
