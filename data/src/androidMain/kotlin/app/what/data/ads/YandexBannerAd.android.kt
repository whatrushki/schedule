package app.what.data.ads

import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
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

@Composable
actual fun YandexBannerAd(
    modifier: Modifier,
    adUnitId: String
) {
    val context = LocalContext.current
    var isAdLoaded by remember(adUnitId) { mutableStateOf(false) }

    val bannerView = remember(adUnitId) {
        BannerAdView(context).apply {
            setBannerAdEventListener(object : BannerAdEventListener {
                override fun onAdLoaded() {
                    isAdLoaded = true
                }

                override fun onAdFailedToLoad(error: AdRequestError) {
                    isAdLoaded = false
                }

                override fun onAdClicked() {}

                override fun onImpression(impressionData: ImpressionData?) {}
            })
            val displayMetrics = context.resources.displayMetrics
            val widthPixels = displayMetrics.widthPixels
            val density = displayMetrics.density
            val adWidth = (widthPixels / density).toInt().coerceAtLeast(320)
            setAdSize(BannerAdSize.sticky(context, adWidth))
            loadAd(AdRequest.Builder(adUnitId).build())
        }
    }

    AnimatedVisibility(
        visible = isAdLoaded,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        AndroidView(
            modifier = modifier
                .clip(MaterialTheme.shapes.large)
                .fillMaxWidth()
                .wrapContentHeight(),
            factory = {
                (bannerView.parent as? ViewGroup)?.removeView(bannerView)
                bannerView
            },
            update = {
                // view is retained across recompositions and pager swipes
            }
        )
    }
}
