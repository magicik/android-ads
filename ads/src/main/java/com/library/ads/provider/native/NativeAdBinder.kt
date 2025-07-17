package com.library.ads.provider.native

import android.content.Context
import android.view.View
import com.applovin.mediation.nativeAds.MaxNativeAdView
import com.google.android.gms.ads.nativead.NativeAd

interface NativeAdBinder {
    fun bindAdMob(context: Context, nativeAd: NativeAd): View?
    fun bindMax(context: Context): MaxNativeAdView?
}
