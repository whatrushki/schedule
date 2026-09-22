package app.what.foundation.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.materialkolor.PaletteStyle
import com.materialkolor.ktx.DynamicScheme
import com.materialkolor.toColorScheme

val BRAND_COLORS = listOf(
    Color(0xFF1F2137), // Navy
    Color(0xFF691616), // Crimson
    Color(0xFFD27731), // Terracotta
    Color(0xFF275325), // Forest Green
    Color(0xFF9B3782)  // Plum
)

// --- 1. Navy Theme (#1F2137) ---
val DarkNavyBrandScheme = darkColorScheme(
    primary = Color(0xFF8CA8FF),
    onPrimary = Color(0xFF0F1538),
    primaryContainer = Color(0xFF1F2137),
    onPrimaryContainer = Color(0xFFDCE2FF),
    secondary = Color(0xFFB5C4E8),
    onSecondary = Color(0xFF1B2A4A),
    secondaryContainer = Color(0xFF262A45),
    onSecondaryContainer = Color(0xFFE2E7F5),
    tertiary = Color(0xFF90A4CE),
    onTertiary = Color(0xFF10213E),
    tertiaryContainer = Color(0xFF253046),
    onTertiaryContainer = Color(0xFFD6E3FF),
    background = Color(0xFF0F111D),
    onBackground = Color(0xFFE4E7F4),
    surface = Color(0xFF141624),
    onSurface = Color(0xFFE4E7F4),
    surfaceVariant = Color(0xFF25283D),
    onSurfaceVariant = Color(0xFFC4C8DC),
    surfaceContainer = Color(0xFF1A1C2E),
    surfaceContainerLow = Color(0xFF141625),
    surfaceContainerHigh = Color(0xFF222438),
    surfaceContainerHighest = Color(0xFF2B2E46),
    surfaceBright = Color(0xFF333652),
    surfaceDim = Color(0xFF111320),
    outline = Color(0xFF6C718E),
    outlineVariant = Color(0xFF3B3F58),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

val LightNavyBrandScheme = lightColorScheme(
    primary = Color(0xFF1F2137),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDDE3F5),
    onPrimaryContainer = Color(0xFF101222),
    secondary = Color(0xFF383C5A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8ECF8),
    onSecondaryContainer = Color(0xFF1B1E36),
    tertiary = Color(0xFF4A5578),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE2E7F6),
    onTertiaryContainer = Color(0xFF1C2442),
    background = Color(0xFFF7F8FC),
    onBackground = Color(0xFF181A26),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF181A26),
    surfaceVariant = Color(0xFFEFF1F8),
    onSurfaceVariant = Color(0xFF43465C),
    surfaceContainer = Color(0xFFF1F3F9),
    surfaceContainerLow = Color(0xFFF6F8FC),
    surfaceContainerHigh = Color(0xFFE8EBF3),
    surfaceContainerHighest = Color(0xFFDFE3EC),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFD6DAE4),
    outline = Color(0xFF757890),
    outlineVariant = Color(0xFFC7CBD8),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

// --- 2. Crimson Theme (#691616) ---
val DarkCrimsonBrandScheme = darkColorScheme(
    primary = Color(0xFFFF6B6B),
    onPrimary = Color(0xFF4A0000),
    primaryContainer = Color(0xFF691616),
    onPrimaryContainer = Color(0xFFFFDAD9),
    secondary = Color(0xFFE5A8A8),
    onSecondary = Color(0xFF4A1A1A),
    secondaryContainer = Color(0xFF421515),
    onSecondaryContainer = Color(0xFFFFDADA),
    tertiary = Color(0xFFDFA69A),
    onTertiary = Color(0xFF451A12),
    tertiaryContainer = Color(0xFF3E1710),
    onTertiaryContainer = Color(0xFFFFDAD2),
    background = Color(0xFF140505),
    onBackground = Color(0xFFF5E3E3),
    surface = Color(0xFF1B0808),
    onSurface = Color(0xFFF5E3E3),
    surfaceVariant = Color(0xFF331515),
    onSurfaceVariant = Color(0xFFDBB5B5),
    surfaceContainer = Color(0xFF240C0C),
    surfaceContainerLow = Color(0xFF1C0808),
    surfaceContainerHigh = Color(0xFF2E1010),
    surfaceContainerHighest = Color(0xFF381515),
    surfaceBright = Color(0xFF441B1B),
    surfaceDim = Color(0xFF170606),
    outline = Color(0xFF8A5B5B),
    outlineVariant = Color(0xFF4D2828),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

val LightCrimsonBrandScheme = lightColorScheme(
    primary = Color(0xFF691616),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFCEBEB),
    onPrimaryContainer = Color(0xFF420505),
    secondary = Color(0xFF882B2B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF8DDDD),
    onSecondaryContainer = Color(0xFF450F0F),
    tertiary = Color(0xFF7F3426),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF9DED8),
    onTertiaryContainer = Color(0xFF421208),
    background = Color(0xFFFCF7F7),
    onBackground = Color(0xFF240E0E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF240E0E),
    surfaceVariant = Color(0xFFF7EDED),
    onSurfaceVariant = Color(0xFF5A3C3C),
    surfaceContainer = Color(0xFFF8EEEE),
    surfaceContainerLow = Color(0xFFFCF4F4),
    surfaceContainerHigh = Color(0xFFF2E3E3),
    surfaceContainerHighest = Color(0xFFE9D7D7),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFDEC5C5),
    outline = Color(0xFF885E5E),
    outlineVariant = Color(0xFFD6BCBC),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

// --- 3. Terracotta Theme (#D27731) ---
val DarkTerracottaBrandScheme = darkColorScheme(
    primary = Color(0xFFD27731),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF5E2F09),
    onPrimaryContainer = Color(0xFFFFDCBE),
    secondary = Color(0xFFE89D65),
    onSecondary = Color(0xFF462100),
    secondaryContainer = Color(0xFF44240C),
    onSecondaryContainer = Color(0xFFFFDCC4),
    tertiary = Color(0xFFD9A06F),
    onTertiary = Color(0xFF3F2309),
    tertiaryContainer = Color(0xFF3E220B),
    onTertiaryContainer = Color(0xFFFFDDBC),
    background = Color(0xFF140B04),
    onBackground = Color(0xFFF5E7DF),
    surface = Color(0xFF1C1007),
    onSurface = Color(0xFFF5E7DF),
    surfaceVariant = Color(0xFF332012),
    onSurfaceVariant = Color(0xFFD6BAA6),
    surfaceContainer = Color(0xFF24150A),
    surfaceContainerLow = Color(0xFF1C1007),
    surfaceContainerHigh = Color(0xFF2E1C0F),
    surfaceContainerHighest = Color(0xFF382314),
    surfaceBright = Color(0xFF452C1A),
    surfaceDim = Color(0xFF160C05),
    outline = Color(0xFF8F6E55),
    outlineVariant = Color(0xFF4E3827),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

val LightTerracottaBrandScheme = lightColorScheme(
    primary = Color(0xFFD27731),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFCEEE2),
    onPrimaryContainer = Color(0xFF542803),
    secondary = Color(0xFF995119),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF9E4D4),
    onSecondaryContainer = Color(0xFF4A2000),
    tertiary = Color(0xFF8A5826),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF8E7D5),
    onTertiaryContainer = Color(0xFF412304),
    background = Color(0xFFFCF8F5),
    onBackground = Color(0xFF24170E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF24170E),
    surfaceVariant = Color(0xFFF7ECE4),
    onSurfaceVariant = Color(0xFF5E493B),
    surfaceContainer = Color(0xFFF7EDE4),
    surfaceContainerLow = Color(0xFFFCF4EE),
    surfaceContainerHigh = Color(0xFFF1E2D7),
    surfaceContainerHighest = Color(0xFFE9D5C8),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFDEC5B5),
    outline = Color(0xFF8E715F),
    outlineVariant = Color(0xFFDAC0B0),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

// --- 4. Forest Green Theme (#275325) ---
val DarkForestBrandScheme = darkColorScheme(
    primary = Color(0xFF5EDB56),
    onPrimary = Color(0xFF043806),
    primaryContainer = Color(0xFF275325),
    onPrimaryContainer = Color(0xFFC6F7C3),
    secondary = Color(0xFFA1D69D),
    onSecondary = Color(0xFF123912),
    secondaryContainer = Color(0xFF1A3819),
    onSecondaryContainer = Color(0xFFCCF0C9),
    tertiary = Color(0xFF8DCFB0),
    onTertiary = Color(0xFF0B3B26),
    tertiaryContainer = Color(0xFF143926),
    onTertiaryContainer = Color(0xFFBEF2D8),
    background = Color(0xFF071207),
    onBackground = Color(0xFFDEEADE),
    surface = Color(0xFF0E1A0E),
    onSurface = Color(0xFFDEEADE),
    surfaceVariant = Color(0xFF1B2B1B),
    onSurfaceVariant = Color(0xFFB4C8B3),
    surfaceContainer = Color(0xFF142413),
    surfaceContainerLow = Color(0xFF0E1A0E),
    surfaceContainerHigh = Color(0xFF1B2E1A),
    surfaceContainerHighest = Color(0xFF223821),
    surfaceBright = Color(0xFF2C462A),
    surfaceDim = Color(0xFF0A140A),
    outline = Color(0xFF658164),
    outlineVariant = Color(0xFF334A32),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

val LightForestBrandScheme = lightColorScheme(
    primary = Color(0xFF275325),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE2F3E1),
    onPrimaryContainer = Color(0xFF0B2B0A),
    secondary = Color(0xFF386B36),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDFF0DE),
    onSecondaryContainer = Color(0xFF133612),
    tertiary = Color(0xFF2B6B4C),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD6F3E3),
    onTertiaryContainer = Color(0xFF0C3320),
    background = Color(0xFFF7FAF7),
    onBackground = Color(0xFF111E11),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111E11),
    surfaceVariant = Color(0xFFEEF4EE),
    onSurfaceVariant = Color(0xFF455745),
    surfaceContainer = Color(0xFFEFF5EF),
    surfaceContainerLow = Color(0xFFF5FAF5),
    surfaceContainerHigh = Color(0xFFE5ECE5),
    surfaceContainerHighest = Color(0xFFDBE4DB),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFCDD8CD),
    outline = Color(0xFF6E816E),
    outlineVariant = Color(0xFFBDCEBD),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

// --- 5. Plum Theme (#9B3782) ---
val LightPlumBrandScheme = lightColorScheme(
    primary = Color(0xFF9B3782),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFCEBF6),
    onPrimaryContainer = Color(0xFF9B3782).copy(alpha = 0.85f),
    secondary = Color(0xFF813B70),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF7E2F0),
    onSecondaryContainer = Color(0xFF421537),
    tertiary = Color(0xFF8B475A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFCE2E9),
    onTertiaryContainer = Color(0xFF451523),
    background = Color(0xFFFCF7FA),
    onBackground = Color(0xFF22111E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF22111E),
    surfaceVariant = Color(0xFFF6ECF3),
    onSurfaceVariant = Color(0xFF553F4F),
    surfaceContainer = Color(0xFFF7ECF4),
    surfaceContainerLow = Color(0xFFFCF3F9),
    surfaceContainerHigh = Color(0xFFF1E1EC),
    surfaceContainerHighest = Color(0xFFE9D5E3),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFDEC5D7),
    outline = Color(0xFF8A6C82),
    outlineVariant = Color(0xFFD6C0D0),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

fun getBrandColorScheme(brandColor: Color, isDark: Boolean): ColorScheme {
    if (isDark) {
        return DynamicScheme(brandColor, isDark = true).toColorScheme(isAmoled = false)
    }

    if (brandColor == BRAND_COLORS[0]) return LightNavyBrandScheme
    if (brandColor == BRAND_COLORS[1]) return LightCrimsonBrandScheme
    if (brandColor == BRAND_COLORS[2]) return LightTerracottaBrandScheme
    if (brandColor == BRAND_COLORS[3]) return LightForestBrandScheme
    if (brandColor == BRAND_COLORS[4]) return LightPlumBrandScheme

    // In Compose Color, ARGB is stored in the high 32 bits (bits 32..63)
    val highRgb = (brandColor.value shr 32) and 0x00FFFFFFUL
    val lowRgb = brandColor.value and 0x00FFFFFFUL
    val rgb = if (highRgb != 0UL) highRgb else lowRgb

    return when (rgb) {
        0x1F2137UL -> LightNavyBrandScheme
        0x691616UL -> LightCrimsonBrandScheme
        0xD27731UL -> LightTerracottaBrandScheme
        0x275325UL -> LightForestBrandScheme
        0x9B3782UL -> LightPlumBrandScheme
        else -> DynamicScheme(brandColor, isDark = false).toColorScheme(isAmoled = false)
    }
}
