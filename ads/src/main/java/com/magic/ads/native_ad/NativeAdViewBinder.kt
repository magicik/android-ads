package com.magic.ads.native_ad

import android.content.Context
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView

/**
 * Tier 2 of native ad rendering: the app supplies its own layout entirely. The library only
 * loads the ad and owns its lifecycle (destroy on premium/replace) — it never assumes
 * anything about the view hierarchy inside [NativeAdView].
 *
 * [createView] must inflate/build a [NativeAdView] (AdMob requires the real wrapper type for
 * click/impression attribution — a plain ViewGroup won't work). [bind] then wires the ad's
 * fields into whatever child views that layout has, in whatever order/positions the app wants.
 */
interface NativeAdViewBinder {
    fun createView(context: Context): NativeAdView
    fun bind(view: NativeAdView, nativeAd: NativeAd)
}
