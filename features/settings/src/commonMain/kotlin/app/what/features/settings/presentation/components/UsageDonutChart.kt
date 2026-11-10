package app.what.features.settings.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class DonutSegment(
    val bytes: Long,
    val color: Color
)

@Composable
fun UsageDonutChart(
    segments: List<DonutSegment>,
    totalBytes: Long,
    sizeValue: String,
    sizeUnit: String,
    modifier: Modifier = Modifier
) {
    val animationProgress = remember { Animatable(0f) }

    LaunchedEffect(totalBytes) {
        animationProgress.snapTo(0f)
        animationProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing)
        )
    }

    Box(
        modifier = modifier.size(190.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(180.dp)) {
            val strokeWidth = 26.dp.toPx()
            val diameter = size.minDimension - strokeWidth
            val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
            val arcSize = Size(diameter, diameter)

            val activeSegments = segments.filter { it.bytes > 0 }

            if (totalBytes <= 0L || activeSegments.isEmpty()) {
                // Пустое нейтральное кольцо
                drawArc(
                    color = Color.Gray.copy(alpha = 0.2f),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth)
                )
            } else if (activeSegments.size == 1) {
                // Один сегмент на всё кольцо
                drawArc(
                    color = activeSegments.first().color,
                    startAngle = -90f,
                    sweepAngle = 360f * animationProgress.value,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            } else {
                val n = activeSegments.size
                val gapAngle = 5f
                val totalGaps = n * gapAngle
                val availableAngle = 360f - totalGaps
                val minSweepAngle = 8f

                // 1) Рассчитываем пропорциональные углы
                val rawSweeps = activeSegments.map {
                    (it.bytes.toFloat() / totalBytes) * availableAngle
                }

                // 2) Для крошечных блоков гарантируем минимальный угол (minSweepAngle),
                // чтобы они красиво смотрелись как аккуратные скругленные точки/пилюли и не сминались
                var reservedForSmall = 0f
                var remainingRawTotal = 0f
                val smallIndices = mutableSetOf<Int>()

                rawSweeps.forEachIndexed { index, sweep ->
                    if (sweep < minSweepAngle) {
                        smallIndices.add(index)
                        reservedForSmall += minSweepAngle
                    } else {
                        remainingRawTotal += sweep
                    }
                }

                val angleForLarger = (availableAngle - reservedForSmall).coerceAtLeast(0f)
                val finalSweeps = rawSweeps.mapIndexed { index, sweep ->
                    if (index in smallIndices) {
                        minSweepAngle
                    } else if (remainingRawTotal > 0f) {
                        (sweep / remainingRawTotal) * angleForLarger
                    } else {
                        sweep
                    }
                }

                // 3) Отрисовываем дуги со скруглением (StrokeCap.Round)
                // Учитываем радиус скругления capAngle, чтобы концы секторов не наползали друг на друга
                val capAngle = ((strokeWidth / 2f) / (3.14159265f * diameter) * 360f)
                var currentAngle = -90f
                finalSweeps.forEachIndexed { index, sweep ->
                    val segment = activeSegments[index]
                    val visualSweep = (sweep - 2f * capAngle).coerceAtLeast(0.1f) * animationProgress.value
                    val start = currentAngle + capAngle

                    drawArc(
                        color = segment.color,
                        startAngle = start,
                        sweepAngle = visualSweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )

                    currentAngle += sweep + gapAngle
                }
            }
        }

        // Число и единица измерения в центре
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
