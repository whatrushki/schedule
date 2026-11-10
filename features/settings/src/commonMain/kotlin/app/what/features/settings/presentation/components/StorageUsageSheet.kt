package app.what.features.settings.presentation.components

import androidx.compose.runtime.Composable

@Composable
fun StorageUsageSheet(
    onClose: () -> Unit = {}
) {
    UsageStatsSheet(
        initialPage = 0,
        onClose = onClose
    )
}
