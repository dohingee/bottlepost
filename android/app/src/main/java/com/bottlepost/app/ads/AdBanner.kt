package com.bottlepost.app.ads

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bottlepost.app.BuildConfig
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

/**
 * Anchored adaptive banner for the bottom of the main screen. Keeps the banner's height while
 * loading (the SDK does not render into a zero-height view) and collapses if no ad is available.
 */
@Composable
fun AdBanner(modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val context = LocalContext.current
        val width = maxWidth.value.toInt()
        var failed by remember { mutableStateOf(false) }
        val size = remember(width) { AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, width) }
        val view = remember(width) {
            AdView(context).apply {
                adUnitId = BuildConfig.ADMOB_BANNER
                setAdSize(size)
                adListener = object : AdListener() {
                    override fun onAdLoaded() {
                        failed = false
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        failed = true
                    }
                }
                loadAd(AdRequest.Builder().build())
            }
        }
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        DisposableEffect(view, lifecycle) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> view.resume()
                    Lifecycle.Event.ON_PAUSE -> view.pause()
                    else -> {}
                }
            }
            lifecycle.addObserver(observer)
            onDispose {
                lifecycle.removeObserver(observer)
                view.destroy()
            }
        }
        AndroidView(
            factory = { view },
            modifier = Modifier.fillMaxWidth().height(if (failed) 0.dp else size.height.dp),
        )
    }
}
