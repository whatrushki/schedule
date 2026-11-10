package app.what.data.ads

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun YandexBannerAd(
    modifier: Modifier,
    adUnitId: String
) {
    // No-op on JVM/Desktop
}
