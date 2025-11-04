package com.library.ads.max.native

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import com.applovin.mediation.MaxAd
import com.applovin.mediation.nativeAds.MaxNativeAdView
import com.applovin.mediation.nativeAds.MaxNativeAdViewBinder
import com.magic.ads.R

class MaxTemplateView(private val ctx: Context) : MaxNativeViewBinder {
    private var _root: View =
        LayoutInflater.from(ctx).inflate(R.layout.max_template_native_ads, null)
    override val nativeView: View get() = _root

    override val nativeAdView: MaxNativeAdView by lazy {
        val binder = MaxNativeAdViewBinder.Builder(R.layout.max_template_native_ads)
            .setTitleTextViewId(R.id.title_text_view).setBodyTextViewId(R.id.body_text_view)
            .setIconImageViewId(R.id.icon_image_view).setCallToActionButtonId(R.id.cta_button)
            .setMediaContentViewGroupId(R.id.media_view_container)
            .setOptionsContentViewGroupId(R.id.ad_options_view).build()
        MaxNativeAdView(binder, ctx)
    }

    override fun bindAd(ad: MaxAd) {
        nativeView.post { /* optional tweaks */ }
    }

    override fun destroy() {
        try {
            nativeAdView.recycle()
        } catch (_: Throwable) {
        }
    }
}
