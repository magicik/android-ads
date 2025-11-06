package com.library.ads.max.native_ad

import android.view.View
import com.library.ads.provider.native_ad.INativeAdContainer

class MaxNativeAdContainer(private val binder: MaxNativeViewBinder) : INativeAdContainer {
    override val view: View get() = binder.nativeView
    override fun bindAd(ad: Any) {
        view.post { }
    }

    override fun destroy() {
        binder.destroy()
    }
}
