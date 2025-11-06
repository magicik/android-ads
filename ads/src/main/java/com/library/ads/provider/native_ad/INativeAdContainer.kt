package com.library.ads.provider.native_ad

import android.view.View

interface INativeAdContainer {
    val view: View
    fun bindAd(ad: Any)
    fun destroy() {}
}
