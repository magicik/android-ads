package com.library.ads.provider.interstitial

import android.app.Activity
import android.content.Context
import com.library.ads.admob.AdmobInterstitialAdHelper
import com.library.ads.max.MaxInterstitialAdHelper
import com.library.ads.provider.config.AdRemoteConfigProvider
import com.library.ads.provider.config.ProviderAds

class InterstitialAdManagerImpl(
    context: Context,
    remoteConfigProvider: AdRemoteConfigProvider,
    admobAdUnitId: String?,
    maxAdUnitId: String?
) : InterstitialAdManager {

    private val impl: InterstitialAdManager = when (remoteConfigProvider.getAdProvider()) {
        ProviderAds.ADMOB.value -> {
            val admob =
                AdmobInterstitialAdHelper.getInstance(context, remoteConfigProvider, admobAdUnitId)
            AdMobInterstitialAdapter(admob)
        }

        ProviderAds.MAX.value -> {
            val max =
                MaxInterstitialAdHelper.getInstance(context, remoteConfigProvider, maxAdUnitId)
            MaxInterstitialAdapter(max)
        }

        else -> {
            // fallback to max
            val fallback =
                MaxInterstitialAdHelper.getInstance(context, remoteConfigProvider, maxAdUnitId)
            MaxInterstitialAdapter(fallback)
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
}