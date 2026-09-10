package app.what.foundation.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf

val LocalThemeIsDark = compositionLocalOf<Boolean> { error("LocalThemeIsDark is not provided") }

@Composable
fun WHATTheme(
    theme: ColorScheme,
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val dynamic = if (dynamicColor) getDynamicColorScheme(isDarkTheme) else null
    val colorScheme = dynamic ?: theme

    CompositionLocalProvider(
        LocalThemeIsDark provides isDarkTheme
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}