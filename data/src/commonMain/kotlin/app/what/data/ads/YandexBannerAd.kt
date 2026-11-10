package app.what.data.ads

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun YandexBannerAd(
    modifier: Modifier = Modifier,
    adUnitId: String = "demo-banner-yandex"
)
