// MaxNativeViewBinder.kt
package com.library.ads.max.native_ad

import com.applovin.mediation.MaxAd
import com.applovin.mediation.nativeAds.MaxNativeAdView
import com.library.ads.provider.native_ad.NativeAdViewBinder

/**
 * Với MAX, object ad SDK trả về là MaxAd; nhưng binding thực tế được SDK xử lý khi bạn tạo MaxNativeAdView bằng MaxNativeAdViewBinder.
 * Chúng ta giữ interface đơn giản: rootView là view gốc (inflated layout), và bind sẽ được thực hiện khi onNativeAdLoaded gọi.
 */
interface MaxNativeViewBinder : NativeAdViewBinder<MaxAd> {
    val nativeAdView: MaxNativeAdView
}
