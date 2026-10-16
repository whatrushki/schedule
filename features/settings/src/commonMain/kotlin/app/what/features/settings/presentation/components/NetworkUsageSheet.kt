package app.what.features.settings.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.what.data.network.traffic.NetworkTrafficManager
import app.what.data.network.traffic.NetworkTransportType
import app.what.data.network.traffic.TrafficBucket
import app.what.data.network.traffic.TrafficCategory
import app.what.foundation.ui.icons.WHATIcons
import app.what.foundation.ui.icons.filled.ImageRoller
import app.what.foundation.ui.icons.filled.News
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.koinInject
import kotlin.math.roundToInt

@Composable
fun NetworkUsageSheet(
    onClose: () -> Unit = {}
) {
    val trafficManager = koinInject<NetworkTrafficManager>()
    val stats by trafficManager.stats.collectAsState()

    // null = Весь, иначе конкретный тип транспорта
    var selectedTransport by remember { mutableStateOf<NetworkTransportType?>(null) }
    val expandedCategories = remember { mutableStateMapOf<TrafficCategory, Boolean>() }

    val totalBucket = stats.getTotalBucket(selectedTransport)
    val totalBytes = totalBucket.totalBytes
    val (sizeValueText, sizeUnitText) = formatSizeParts(totalBytes)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Вкладки: Весь | Мобильный | Wi-Fi | Роуминг
        TransportFilterTabs(
            selected = selectedTransport,
            onSelect = { selectedTransport = it }
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Donut Chart по категориям
        val activeCategories = TrafficCategory.entries.mapNotNull { category ->
            val bucket = stats.getBucketFor(category, selectedTransport)
            if (bucket.totalBytes > 0) category to bucket else null
        }

        TrafficDonutChart(
            categories = activeCategories,
            totalBytes = totalBytes,
            sizeValue = sizeValueText,
            sizeUnit = sizeUnitText
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Дата начала отсчета
        val dateText = remember(stats.trackingStartedAt) {
            formatTrackingDate(stats.trackingStartedAt)
        }
        Text(
            text = dateText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Список категорий с раскрывающимися подробностями
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TrafficCategory.entries.forEach { category ->
                val bucket = stats.getBucketFor(category, selectedTransport)
                val isExpanded = expandedCategories[category] == true
                val percent = if (totalBytes > 0) {
                    ((bucket.totalBytes.toFloat() / totalBytes) * 100f).roundToInt()
                } else 0

                CategoryTrafficCard(
                    category = category,
                    bucket = bucket,
                    percent = percent,
                    isExpanded = isExpanded,
                    onToggleExpand = {
                        expandedCategories[category] = !isExpanded
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Кнопка «Сбросить статистику»
        Button(
            onClick = {
                trafficManager.resetStats()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            )
        ) {
            Text(
                text = "Сбросить статистику",
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun TransportFilterTabs(
    selected: NetworkTransportType?,
    onSelect: (NetworkTransportType?) -> Unit
) {
    val tabs: List<Pair<String, NetworkTransportType?>> = listOf(
        "Весь" to null,
        "Мобильный" to NetworkTransportType.CELLULAR,
        "Wi-Fi" to NetworkTransportType.WIFI,
        "Роуминг" to NetworkTransportType.ROAMING
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        tabs.forEach { (title, type) ->
            val isSelected = selected == type
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else Color.Transparent
                    )
                    .clickable { onSelect(type) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TrafficDonutChart(
    categories: List<Pair<TrafficCategory, TrafficBucket>>,
    totalBytes: Long,
    sizeValue: String,
    sizeUnit: String
) {
    val animProgress = remember { Animatable(0f) }

    LaunchedEffect(categories, totalBytes) {
        animProgress.snapTo(0f)
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing)
        )
    }

    Box(
        modifier = Modifier.size(200.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(190.dp)) {
            val strokeWidth = 26.dp.toPx()
            val diameter = size.minDimension - strokeWidth
            val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
            val arcSize = Size(diameter, diameter)

            // Фон кольца
            drawArc(
                color = Color.LightGray.copy(alpha = 0.2f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            if (totalBytes > 0 && categories.isNotEmpty()) {
                var currentAngle = -90f
                val progress = animProgress.value

                categories.forEach { (cat, bucket) ->
                    val proportion = bucket.totalBytes.toFloat() / totalBytes.toFloat()
                    val sweep = proportion * 360f * progress

                    drawArc(
                        color = cat.color,
                        startAngle = currentAngle,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                    )

                    currentAngle += sweep
                }
            }
        }

        // Общая сумма в центре кольца
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = if (totalBytes == 0L) "0" else sizeValue,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (totalBytes == 0L) "Б" else sizeUnit,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CategoryTrafficCard(
    category: TrafficCategory,
    bucket: TrafficBucket,
    percent: Int,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    val icon = when (category) {
        TrafficCategory.SCHEDULE -> Icons.Default.DateRange
        TrafficCategory.NEWS -> WHATIcons.News
        TrafficCategory.ACCOUNT -> Icons.Default.AccountCircle
        TrafficCategory.MEDIA -> WHATIcons.ImageRoller
        TrafficCategory.SYSTEM -> Icons.Default.Build
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .clickable { onToggleExpand() }
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Круглая цветная плашка с иконкой категории
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(category.color.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = category.color
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Название категории и процент
            Text(
                text = category.displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (percent > 0) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "$percent%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Суммарный объем
            Text(
                text = formatSize(bucket.totalBytes),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.width(6.dp))

            // Стрелочка раскрытия
            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Раскрывающиеся подробности (как в Telegram)
        AnimatedVisibility(visible = isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, start = 48.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                DetailRow(
                    label = "↑ Отправлено байт",
                    value = formatSize(bucket.sentBytes)
                )
                DetailRow(
                    label = "↓ Получено байт",
                    value = formatSize(bucket.receivedBytes)
                )
                DetailRow(
                    label = "Выполнено запросов",
                    value = "${bucket.requestsCount}"
                )
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun formatTrackingDate(timestamp: Long): String {
    if (timestamp <= 0L) return "Статистика использования сети"
    return try {
        val dt = Instant.fromEpochMilliseconds(timestamp).toLocalDateTime(TimeZone.currentSystemDefault())
        val months = listOf(
            "янв.", "февр.", "марта", "апр.", "мая", "июня",
            "июля", "авг.", "сент.", "окт.", "нояб.", "дек."
        )
        val monthStr = months.getOrElse(dt.monthNumber - 1) { "" }
        val hour = dt.hour.toString().padStart(2, '0')
        val minute = dt.minute.toString().padStart(2, '0')
        "Статистика использования сети с ${dt.dayOfMonth} $monthStr ${dt.year}, $hour:$minute"
    } catch (_: Exception) {
        "Статистика использования сети"
    }
}

private fun formatSizeParts(bytes: Long): Pair<String, String> {
    if (bytes <= 0) return "0" to "Б"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0

    return when {
        gb >= 1.0 -> {
            val rounded = ((gb * 10).roundToInt() / 10.0).toString().replace('.', ',')
            rounded to "GB"
        }
        mb >= 1.0 -> {
            val rounded = ((mb * 10).roundToInt() / 10.0).toString().replace('.', ',')
            rounded to "MB"
        }
        kb >= 1.0 -> {
            val rounded = ((kb * 10).roundToInt() / 10.0).toString().replace('.', ',')
            rounded to "KB"
        }
        else -> bytes.toString() to "Б"
    }
}

private fun formatSize(bytes: Long): String {
    val (value, unit) = formatSizeParts(bytes)
    return "$value $unit"
}
