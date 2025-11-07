package com.library.ads.admob.native_ad

import android.content.Context
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.library.ads.provider.native_ad.BaseNativeAdLoader
import com.library.ads.provider.native_ad.INativeAdContainer

class AdMobNativeAdLoader(
    private val adUnitId: String,
    private val viewFactory: (Context) -> AdMobNativeViewBinder
) : BaseNativeAdLoader {
    override fun loadAd(context: Context, onAdLoaded: (INativeAdContainer, Any) -> Unit, onFailed: ((Throwable?) -> Unit)?) {
        val binder = viewFactory(context)
        val adLoader = AdLoader.Builder(context, adUnitId)
            .forNativeAd { nativeAd ->
                val container = AdMobNativeAdContainer(binder)
                // add view first (caller typically does), but we notify caller via callback
                onAdLoaded(container, nativeAd)
                // then bind safely (container.bindAd will post)
//                container.bindAd(nativeAd)
            }.build()
        adLoader.loadAd(AdRequest.Builder().build())
    }
}
