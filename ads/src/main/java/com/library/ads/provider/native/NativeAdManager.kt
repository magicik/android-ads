package com.library.ads.provider.native

import android.content.Context
import android.view.ViewGroup
import com.library.ads.admob.native_ads.AdMobNativeAdLoader
import com.library.ads.admob.native_ads.AdMobNativeViewBinder
import com.library.ads.max.native.MaxNativeAdLoaderWrapper
import com.library.ads.max.native.MaxNativeViewBinder
import com.library.ads.provider.config.AdRemoteConfigProvider

class NativeAdManager(
    private val context: Context,
    private val remoteConfigProvider: AdRemoteConfigProvider,
    private val admobUnit: String?,
    private val maxUnit: String?,
    private val admobViewFactory: ((Context) -> AdMobNativeViewBinder)? = null,
    private val maxViewFactory: ((Context) -> MaxNativeViewBinder)? = null
) {
    fun loadInto(container: ViewGroup, onFailed: ((Throwable?) -> Unit)? = null) {
        when (remoteConfigProvider.getAdProvider().lowercase()) {
            "admob" -> {
                if (admobUnit.isNullOrEmpty() || admobViewFactory == null) { onFailed?.invoke(IllegalStateException("AdMob config missing")); return }
                val loader = AdMobNativeAdLoader(admobUnit, admobViewFactory)
                loader.loadAd(context, { nativeContainer ->
                    container.removeAllViews()
                    container.addView(nativeContainer.view)
                }, onFailed)
            }
            "max" -> {
                if (maxUnit.isNullOrEmpty() || maxViewFactory == null) { onFailed?.invoke(IllegalStateException("Max config missing")); return }
                val loader = MaxNativeAdLoaderWrapper(maxUnit, maxViewFactory)
                loader.loadAd(context, { nativeContainer ->
                    container.removeAllViews()
                    container.addView(nativeContainer.view)
                }, onFailed)
            }
            else -> onFailed?.invoke(IllegalArgumentException("Unknown provider"))
        }
    }
}
