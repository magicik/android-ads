package com.library.ads.provider.native

import android.view.View

interface INativeAdContainer {
    val view: View
    fun bindAd(ad: Any)
    fun destroy() {}
}
