package app.what.features.settings.presentation.utils

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Генерирует гармоничную палитру цветов на основе основного цвета выбранной темы.
 * Создаёт красивую градацию тонов (от глубокого насыщенного к мягкому пастельному).
 */
@Composable
fun rememberThemePalette(count: Int = 5): List<Color> {
    val primary = MaterialTheme.colorScheme.primary
    return remember(primary, count) {
        generateThemePalette(primary, count)
    }
}

fun generateThemePalette(baseColor: Color, count: Int = 5): List<Color> {
    val (baseHue, baseSat, baseVal) = colorToHsv(baseColor)
    return List(count) { i ->
        val t = if (count <= 1) 0f else i.toFloat() / (count - 1)
        val hue = (baseHue + (t - 0.5f) * 18f + 360f) % 360f
        val sat = (baseSat * (1.1f - t * 0.65f)).coerceIn(0.2f, 1f)
        val value = (baseVal * 0.78f + t * (0.96f - baseVal * 0.78f)).coerceIn(0.48f, 0.98f)
        hsvToColor(hue, sat, value)
    }
}

private fun colorToHsv(color: Color): FloatArray {
    val r = color.red
    val g = color.green
    val b = color.blue
    val max = max(r, max(g, b))
    val min = min(r, min(g, b))
    val delta = max - min
    val hue = when {
        delta == 0f -> 0f
        max == r -> ((g - b) / delta * 60f + 360f) % 360f
        max == g -> ((b - r) / delta * 60f + 120f) % 360f
        else -> ((r - g) / delta * 60f + 240f) % 360f
    }
    val sat = if (max == 0f) 0f else delta / max
    val value = max
    return floatArrayOf(hue, sat, value)
}

private fun hsvToColor(hue: Float, saturation: Float, value: Float): Color {
    val c = value * saturation
    val x = c * (1f - abs((hue / 60f) % 2f - 1f))
    val m = value - c
    val (r1, g1, b1) = when {
        hue < 60f -> Triple(c, x, 0f)
        hue < 120f -> Triple(x, c, 0f)
        hue < 180f -> Triple(0f, c, x)
        hue < 240f -> Triple(0f, x, c)
        hue < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(
        red = (r1 + m).coerceIn(0f, 1f),
        green = (g1 + m).coerceIn(0f, 1f),
        blue = (b1 + m).coerceIn(0f, 1f),
        alpha = 1f
    )
}
