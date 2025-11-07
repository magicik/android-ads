package com.library.ads.max.native_ad

import android.content.Context
import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxError
import com.applovin.mediation.nativeAds.MaxNativeAdListener
import com.applovin.mediation.nativeAds.MaxNativeAdLoader
import com.applovin.mediation.nativeAds.MaxNativeAdView
import com.library.ads.provider.native_ad.BaseNativeAdLoader
import com.library.ads.provider.native_ad.INativeAdContainer

class MaxNativeAdLoaderWrapper(
    private val adUnitId: String, private val viewFactory: (Context) -> MaxNativeViewBinder
) : BaseNativeAdLoader {
    override fun loadAd(
        context: Context,
        onAdLoaded: (INativeAdContainer, Any) -> Unit,
        onFailed: ((Throwable?) -> Unit)?
    ) {
        val binder = viewFactory(context)
        val nativeAdView = binder.nativeAdView
        val loader = MaxNativeAdLoader(adUnitId, context)
        loader.setNativeAdListener(object : MaxNativeAdListener() {
            override fun onNativeAdLoaded(view: MaxNativeAdView?, ad: MaxAd) {
                val container = MaxNativeAdContainer(binder)
                onAdLoaded(container, ad)
            }

            override fun onNativeAdLoadFailed(adUnitId: String, error: MaxError) {
                onFailed?.invoke(Exception(error.message))
            }
        })
        loader.loadAd(nativeAdView)
    }
}
