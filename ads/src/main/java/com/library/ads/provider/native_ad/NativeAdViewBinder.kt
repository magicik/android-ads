package com.library.ads.provider.native_ad

import android.view.View

interface NativeAdViewBinder<T> {
    val nativeView: View
    fun bindAd(ad: T)
    fun destroy() {}
}
