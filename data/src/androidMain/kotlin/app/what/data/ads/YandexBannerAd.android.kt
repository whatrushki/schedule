package app.what.data.ads

import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.yandex.mobile.ads.banner.BannerAdEventListener
import com.yandex.mobile.ads.banner.BannerAdSize
import com.yandex.mobile.ads.banner.BannerAdView
import com.yandex.mobile.ads.common.AdRequest
import com.yandex.mobile.ads.common.AdRequestError
import com.yandex.mobile.ads.common.ImpressionData

@Composable
actual fun YandexBannerAd(
    modifier: Modifier,
    adUnitId: String
) {
    val context = LocalContext.current

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        val density = context.resources.displayMetrics.density
        val availableWidthDp = maxWidth.value.toInt().coerceAtLeast(320)

        val adSize = remember(availableWidthDp) {
            BannerAdSize.sticky(context, availableWidthDp)
        }
        val bannerHeightDp = remember(adSize, density) {
            val h = adSize.getHeight(context)
            if (h > 0) (h / density).dp else 60.dp
        }

        var isAdLoaded by remember(adUnitId) { mutableStateOf(false) }
        var isAdFailed by remember(adUnitId) { mutableStateOf(false) }

        val bannerView = remember(adUnitId, adSize) {
            BannerAdView(context).apply {
                setBannerAdEventListener(object : BannerAdEventListener {
                    override fun onAdLoaded() {
                        isAdLoaded = true
                        isAdFailed = false
                    }

                    override fun onAdFailedToLoad(error: AdRequestError) {
                        isAdLoaded = false
                        isAdFailed = true
                    }

                    override fun onAdClicked() {}

                    override fun onImpression(impressionData: ImpressionData?) {}
                })
                setAdSize(adSize)
                loadAd(AdRequest.Builder(adUnitId).build())
            }
        }

        DisposableEffect(bannerView) {
            onDispose {
                bannerView.destroy()
            }
        }

        if (!isAdFailed) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(bannerHeightDp)
                    .clip(MaterialTheme.shapes.large),
                contentAlignment = Alignment.Center
            ) {
                if (!isAdLoaded) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(colorScheme.surfaceContainerLow.copy(alpha = 0.5f))
                    )
                }

                AnimatedVisibility(
                    visible = isAdLoaded,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = {
                            (bannerView.parent as? ViewGroup)?.removeView(bannerView)
                            bannerView
                        },
                        update = {}
                    )
                }
            }
        }
    }
}
