package app.what.data.ads

import android.content.Context
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
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

        // Standard sticky/compact banner size (~50dp) designed for mobile banners (R-M-20151304-3)
        val adSize = remember(availableWidthDp) {
            BannerAdSize.sticky(context, availableWidthDp)
        }

        // Each pager page has its own key so swiping across days doesn't conflict
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

        // Only render when ad is actually loaded: zero space, no placeholders or gaps when waiting/empty
        AnimatedVisibility(
            visible = cachedBanner.isLoaded,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.large)
                        .fillMaxWidth()
                        .wrapContentHeight(),
                    factory = {
                        cachedBanner.bannerView.apply {
                            (parent as? ViewGroup)?.removeView(this)
                            clipToOutline = true
                        }
                    },
                    update = { view ->
                        view.clipToOutline = true
                    }
                )
            }
        }
    }
}
