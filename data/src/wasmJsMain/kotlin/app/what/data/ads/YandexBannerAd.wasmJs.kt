package app.what.data.ads

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun YandexBannerAd(
    modifier: Modifier,
    adUnitId: String,
    pageKey: String
) {
    // No-op on Web/Wasm
}
