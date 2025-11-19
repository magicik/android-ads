package com.library.ads.provider.open

import android.app.Activity
import android.content.Context
import com.library.ads.admob.AdmobOpenAdHelper
import com.library.ads.max.MaxOpenAdHelper
import com.library.ads.provider.config.AdRemoteConfigProvider
import com.library.ads.provider.config.ProviderAds

class OpenAdManagerImpl(
    private val context: Context,
    private val admobAdUnitId: String,
    private val maxAdUnitId: String,
    private val remoteConfigProvider: AdRemoteConfigProvider,
    private val subscriptionProvider: () -> Boolean
) : OpenAdManager {
    private var impl: OpenAdManager? = null

//    private val impl: OpenAdManager = when (remoteConfigProvider.getAdProvider()) {
//        ProviderAds.ADMOB.value -> {
//            val admob = AdmobOpenAdHelper(admobAdUnitId, remoteConfigProvider, subscriptionProvider)
//            admob
//        }
//
//        ProviderAds.MAX.value -> {
//            val max = MaxOpenAdHelper(
//                maxAdUnitId,
//                context,
//                remoteConfigProvider = remoteConfigProvider,
//                subscriptionProvider
//            )
//            max
//        }
//
//        else -> {
//            val fallback =
//                MaxOpenAdHelper(maxAdUnitId, context, remoteConfigProvider, subscriptionProvider)
//            fallback
//        }
//    }

    fun createImplWhenReady(provider: String, sdkReady: Boolean) {
        if (impl != null) return
        impl = when (provider) {
            ProviderAds.ADMOB.value -> AdmobOpenAdHelper(
                admobAdUnitId,
                remoteConfigProvider,
                subscriptionProvider
            )

            ProviderAds.MAX.value -> {
                val max = MaxOpenAdHelper(
                    maxAdUnitId,
                    context,
                    remoteConfigProvider,
                    subscriptionProvider
                )
                if (sdkReady) {
                    // If SDK ready already, initialize helper to load
                    max.initializeAfterSdkReady()
                }
                max
            }

            else -> {
                val fallback = MaxOpenAdHelper(
                    maxAdUnitId,
                    context,
                    remoteConfigProvider,
                    subscriptionProvider
                )
                if (sdkReady) fallback.initializeAfterSdkReady()
                fallback
            }
        }
    }

    override fun isAdAvailable(): Boolean = impl?.isAdAvailable() ?: false

    override fun showAdIfAvailable(
        activity: Activity, listener: OpenAdManager.OnShowAdCompleteListener?
    ) {
        impl?.showAdIfAvailable(activity, listener)
    }

    override fun loadAd(activity: Activity?, onComplete: (() -> Unit)?) {
        impl?.loadAd(activity, onComplete)
    }

    override fun onSubscriptionChanged(subscribed: Boolean) {
        impl?.onSubscriptionChanged(subscribed)
    }
}