package com.library.ads.provider.native_ad

import android.content.Context

interface BaseNativeAdLoader {
    fun loadAd(
        context: Context,
        onAdLoaded: (INativeAdContainer) -> Unit,
        onFailed: ((Throwable?) -> Unit)? = null
    )
}
