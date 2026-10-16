package app.what.features.settings.presentation.components

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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.what.data.managers.CacheCategory
import app.what.data.managers.CacheCategoryType
import app.what.data.managers.CacheStorageManager
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlin.math.roundToInt

@Composable
fun StorageUsageSheet(
    onClose: () -> Unit
) {
    val cacheManager = koinInject<CacheStorageManager>()
    val scope = rememberCoroutineScope()

    var categories by remember { mutableStateOf<List<CacheCategory>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isClearing by remember { mutableStateOf(false) }

    // Карта выбранных категорий
    val selectedTypes = remember { mutableStateMapOf<CacheCategoryType, Boolean>() }

    LaunchedEffect(Unit) {
        val loaded = cacheManager.getCategories()
        categories = loaded
        loaded.forEach { cat ->
            selectedTypes[cat.type] = cat.sizeBytes > 0
        }
        isLoading = false
    }

    val selectedCategories = categories.filter { selectedTypes[it.type] == true && it.sizeBytes > 0 }
    val totalSelectedBytes = selectedCategories.sumOf { it.sizeBytes }
    val (sizeValueText, sizeUnitText) = formatSizeParts(totalSelectedBytes)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            // Круговой график (Donut Chart)
            StorageDonutChart(
                categories = selectedCategories,
                totalBytes = totalSelectedBytes,
                sizeValue = sizeValueText,
                sizeUnit = sizeUnitText
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Использование памяти",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Горизонтальный разделитель
        Box(
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .height(2.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Список категорий
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            categories.forEach { category ->
                val isSelected = selectedTypes[category.type] == true
                val percent = if (totalSelectedBytes > 0 && isSelected) {
                    ((category.sizeBytes.toFloat() / totalSelectedBytes) * 100f).roundToInt()
                } else if (category.sizeBytes == 0L) {
                    0
                } else {
                    0
                }

                CategoryStorageRow(
                    category = category,
                    isSelected = isSelected,
                    percent = percent,
                    onToggle = {
                        selectedTypes[category.type] = !isSelected
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Кнопка очистки
        Button(
            onClick = {
                if (totalSelectedBytes > 0 && !isClearing) {
                    isClearing = true
                    scope.launch {
                        val toClear = selectedTypes.filter { it.value }.keys.toSet()
                        val updated = cacheManager.clearCategories(toClear)
                        categories = updated
                        updated.forEach { cat ->
                            selectedTypes[cat.type] = cat.sizeBytes > 0
                        }
                        isClearing = false
                    }
                }
            },
            enabled = totalSelectedBytes > 0 && !isClearing,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            if (isClearing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            } else {
                Text(
                    text = if (totalSelectedBytes > 0) "Очистить кэш $sizeValueText $sizeUnitText" else "Кэш пуст",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun StorageDonutChart(
    categories: List<CacheCategory>,
    totalBytes: Long,
    sizeValue: String,
    sizeUnit: String
) {
    val animationProgress = remember { Animatable(0f) }

    LaunchedEffect(totalBytes) {
        animationProgress.snapTo(0f)
        animationProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
        )
    }

    Box(
        modifier = Modifier.size(190.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(180.dp)) {
            val strokeWidth = 28.dp.toPx()
            val diameter = size.minDimension - strokeWidth
            val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
            val arcSize = Size(diameter, diameter)

            if (totalBytes == 0L || categories.isEmpty()) {
                // Пустое кольцо
                drawArc(
                    color = Color.Gray.copy(alpha = 0.25f),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth)
                )
            } else {
                var currentAngle = -90f
                val gap = if (categories.size > 1) 2.5f else 0f

                categories.forEach { category ->
                    val sweep = (category.sizeBytes.toFloat() / totalBytes) * 360f * animationProgress.value
                    val effectiveSweep = (sweep - gap).coerceAtLeast(0.5f)

                    drawArc(
                        color = category.color,
                        startAngle = currentAngle + (gap / 2f),
                        sweepAngle = effectiveSweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                    )

                    currentAngle += sweep
                }
            }
        }

        // Число и единица в центре
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
private fun CategoryStorageRow(
    category: CacheCategory,
    isSelected: Boolean,
    percent: Int,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onToggle() }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Круглый чекбокс в стиле Telegram
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(
                    if (isSelected) category.color else Color.Transparent
                )
                .then(
                    if (!isSelected) Modifier.background(Color.Gray.copy(alpha = 0.2f), CircleShape)
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Название категории
        Text(
            text = category.title,
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

        // Размер
        Text(
            text = formatSize(category.sizeBytes),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
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
