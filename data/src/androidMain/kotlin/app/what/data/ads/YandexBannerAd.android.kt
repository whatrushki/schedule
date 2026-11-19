package app.what.data.ads

import android.content.Context
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
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

class CachedBanner(
    val adUnitId: String,
    val bannerView: BannerAdView
) {
    var isLoaded by mutableStateOf(false)
    var isFailed by mutableStateOf(false)
    var currentAdSize: BannerAdSize? = null
}

object YandexAdManager {
    private val banners = mutableMapOf<String, CachedBanner>()

    fun getOrCreateBanner(
        context: Context,
        cacheKey: String,
        adUnitId: String,
        adSize: BannerAdSize
    ): CachedBanner {
        val existing = banners[cacheKey]
        if (existing != null) {
            if (existing.currentAdSize != adSize) {
                existing.currentAdSize = adSize
                existing.bannerView.setAdSize(adSize)
            }
            if (!existing.isLoaded && !existing.isFailed) {
                return existing
            }
            if (existing.isLoaded) {
                return existing
            }
            // Retry on failure
            existing.isFailed = false
            existing.bannerView.loadAd(AdRequest.Builder(adUnitId).build())
            return existing
        }

        val bannerView = BannerAdView(context)
        val cached = CachedBanner(adUnitId, bannerView)
        cached.currentAdSize = adSize

        bannerView.setBannerAdEventListener(object : BannerAdEventListener {
            override fun onAdLoaded() {
                cached.isLoaded = true
                cached.isFailed = false
            }

            override fun onAdFailedToLoad(error: AdRequestError) {
                cached.isLoaded = false
                cached.isFailed = true
            }

            override fun onAdClicked() {}

            override fun onImpression(impressionData: ImpressionData?) {}
        })

        bannerView.setAdSize(adSize)
        bannerView.loadAd(AdRequest.Builder(adUnitId).build())

        banners[cacheKey] = cached
        return cached
    }
}

@Composable
actual fun YandexBannerAd(
    modifier: Modifier,
    adUnitId: String,
    pageKey: String
) {
    val context = LocalContext.current

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        val availableWidthDp = maxWidth.value.toInt().coerceAtLeast(320)

        // Use adaptive inline banner size suitable for feed/schedule lists (max 180dp height)
        val adSize = remember(availableWidthDp) {
            BannerAdSize.inline(context, availableWidthDp, 180)
        }
        // getHeight(context) in Yandex Mobile Ads SDK returns height in DP directly
        val bannerHeightDp = remember(adSize) {
            val h = adSize.getHeight(context)
            if (h > 0) h.dp else 160.dp
        }

        // Each pager page has its own key so swiping across days doesn't tear down or conflict with other days
        val cacheKey = remember(adUnitId, pageKey) {
            if (pageKey.isNotBlank()) "$adUnitId#$pageKey" else "$adUnitId#default"
        }

        val cachedBanner = remember(cacheKey, adSize) {
            YandexAdManager.getOrCreateBanner(
                context = context,
                cacheKey = cacheKey,
                adUnitId = adUnitId,
                adSize = adSize
            )
        }

        if (!cachedBanner.isFailed) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = bannerHeightDp)
                    .clip(MaterialTheme.shapes.large),
                contentAlignment = Alignment.Center
            ) {
                if (!cachedBanner.isLoaded) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = bannerHeightDp)
                            .background(colorScheme.surfaceContainerLow.copy(alpha = 0.5f))
                    )
                }

                AnimatedVisibility(
                    visible = cachedBanner.isLoaded,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    AndroidView(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight(),
                        factory = {
                            (cachedBanner.bannerView.parent as? ViewGroup)?.removeView(cachedBanner.bannerView)
                            cachedBanner.bannerView
                        },
                        update = { _ ->
                            // Ensure bannerView stays attached
                        }
                    )
                }
            }
        }
    }
}
