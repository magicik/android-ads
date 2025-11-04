package com.library.ads.admob.native_ads

import android.view.View
import com.google.android.gms.ads.nativead.NativeAd
import com.library.ads.provider.native.INativeAdContainer

class AdMobNativeAdContainer(
    private val binder: AdMobNativeViewBinder // TemplateView implements this
) : INativeAdContainer {
    override val view: View get() = binder.nativeView
    override fun bindAd(ad: Any) {
        if (ad is NativeAd) view.post { binder.bindAd(ad) }
    }
    override fun destroy() { binder.destroy() }
}
