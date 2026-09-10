package app.what.foundation.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

@Composable
expect fun getDynamicColorScheme(isDarkTheme: Boolean): ColorScheme?
