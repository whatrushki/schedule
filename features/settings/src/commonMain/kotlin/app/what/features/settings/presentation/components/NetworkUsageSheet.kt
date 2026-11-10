package app.what.features.settings.presentation.components

import androidx.compose.runtime.Composable

@Composable
fun NetworkUsageSheet(
    onClose: () -> Unit = {}
) {
    UsageStatsSheet(
        initialPage = 1,
        onClose = onClose
    )
}
