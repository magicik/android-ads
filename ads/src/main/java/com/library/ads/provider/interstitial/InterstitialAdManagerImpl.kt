package com.library.ads.provider.interstitial

import android.app.Activity
import android.content.Context
import com.google.firebase.events.Subscriber
import com.library.ads.admob.AdmobInterstitialAdHelper
import com.library.ads.max.MaxInterstitialAdHelper
import com.library.ads.provider.config.AdRemoteConfigProvider
import com.library.ads.provider.config.ProviderAds

class InterstitialAdManagerImpl(
    context: Context,
    remoteConfigProvider: AdRemoteConfigProvider,
    admobAdUnitId: String?,
    maxAdUnitId: String?,
    private val subscriptionProvider: () -> Boolean,
) : InterstitialAdManager {

    private val impl: InterstitialAdManager = when (remoteConfigProvider.getAdProvider()) {
        ProviderAds.ADMOB.value -> {
            val admob =
                AdmobInterstitialAdHelper.getInstance(context, remoteConfigProvider, admobAdUnitId, subscriptionProvider)
            admob
        }

        ProviderAds.MAX.value -> {
            val max =
                MaxInterstitialAdHelper.getInstance(context, remoteConfigProvider, maxAdUnitId, subscriptionProvider)
            max
        }

        else -> {
            // fallback to max
            val fallback =
                MaxInterstitialAdHelper.getInstance(context, remoteConfigProvider, maxAdUnitId, subscriptionProvider)
            fallback
        }
    }

    override fun isAdReady(): Boolean = impl.isAdReady()
    override fun loadAd() {
        impl.loadAd()
    }

    override fun showAd(
        activity: Activity, listener: InterstitialAdManager.OnShowAdCompleteListener
    ) {
        impl.showAd(activity, listener)
    }

    override fun onSubscriptionChanged(subscribed: Boolean) {
        impl.onSubscriptionChanged(subscribed)
    }
}