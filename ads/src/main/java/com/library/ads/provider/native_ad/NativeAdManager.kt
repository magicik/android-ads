package com.library.ads.provider.native_ad

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import com.library.ads.admob.native_ad.AdMobNativeAdLoader
import com.library.ads.admob.native_ad.AdMobNativeViewBinder
import com.library.ads.max.native_ad.MaxNativeAdLoaderWrapper
import com.library.ads.max.native_ad.MaxNativeViewBinder
import com.library.ads.provider.config.AdRemoteConfigProvider

class NativeAdManager(
    private val context: Context,
    private val remoteConfigProvider: AdRemoteConfigProvider,
    private val admobUnit: String?,
    private val maxUnit: String?,
    private val admobViewFactory: ((Context) -> AdMobNativeViewBinder)? = null,
    private val maxViewFactory: ((Context) -> MaxNativeViewBinder)? = null,
    private val subscriptionProvider: () -> Boolean
) {
    private var nativeContainerAd: INativeAdContainer? = null
    private var currentContainerView: ViewGroup? = null

    fun loadInto(container: ViewGroup, onFailed: ((Throwable?) -> Unit)? = null) {
        Handler(Looper.getMainLooper()).post {
            currentContainerView = container

            // If user subscribed -> hide + destroy
            if (subscriptionProvider()) {
                container.removeAllViews()
                container.visibility = View.GONE
                destroy()
                return@post
            } else {
                container.visibility = View.VISIBLE
            }
            when (remoteConfigProvider.getAdProvider().lowercase()) {
                "admob" -> {
                    if (admobUnit.isNullOrEmpty() || admobViewFactory == null) {
                        onFailed?.invoke(IllegalStateException("AdMob config missing"))
                        return@post
                    }
                    val loader = AdMobNativeAdLoader(admobUnit, admobViewFactory)
                    loader.loadAd(context, { nativeContainer, ad ->
                        this.nativeContainerAd = nativeContainer
                        container.removeAllViews()
                        container.addView(nativeContainer.view)
                        nativeContainer.bindAd(ad)
                    }, onFailed)
                }

                "max" -> {
                    if (maxUnit.isNullOrEmpty() || maxViewFactory == null) {
                        onFailed?.invoke(IllegalStateException("Max config missing"))
                        return@post
                    }
                    val loader = MaxNativeAdLoaderWrapper(maxUnit, maxViewFactory)
                    loader.loadAd(context, { nativeContainer, ad ->
                        this.nativeContainerAd = nativeContainer
                        container.removeAllViews()
                        container.addView(nativeContainer.view)
                        nativeContainer.bindAd(ad)
                    }, onFailed)
                }

                else -> onFailed?.invoke(IllegalArgumentException("Unknown provider"))
            }
        }
    }

    fun destroy() {
        Handler(Looper.getMainLooper()).post {
            try {
                nativeContainerAd?.destroy()
            } catch (_: Exception) { }
            nativeContainerAd = null
            currentContainerView?.removeAllViews()
            currentContainerView = null
        }
    }

    fun onSubscriptionChanged(subscribed: Boolean) {
        Handler(Looper.getMainLooper()).post {
            if (subscribed) {
                // hide and destroy current ad immediately
                currentContainerView?.removeAllViews()
                currentContainerView?.visibility = View.GONE
                destroy()
            } else {
                // optional: caller can call loadInto again when unsubscribed
            }
        }
    }
}
