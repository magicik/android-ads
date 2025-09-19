package com.library.ads.provider.open

import android.app.Activity
import android.content.Context
import com.library.ads.admob.AdmobOpenAdHelper
import com.library.ads.max.MaxOpenAdHelper
import com.library.ads.provider.config.AdRemoteConfigProvider
import com.library.ads.provider.config.ProviderAds

class OpenAdManagerImpl(
    context: Context,
    admobAdUnitId: String,
    maxAdUnitId: String,
    remoteConfigProvider: AdRemoteConfigProvider
) : OpenAdManager {

    private val impl: OpenAdManager = when (remoteConfigProvider.getAdProvider()) {
        ProviderAds.ADMOB.value -> {
            val admob = AdmobOpenAdHelper(admobAdUnitId, remoteConfigProvider)
            AdMobOpenAdAdapter(admob)
        }

        ProviderAds.MAX.value -> {
            val max =
                MaxOpenAdHelper(maxAdUnitId, context, remoteConfigProvider = remoteConfigProvider)
            MaxOpenAdAdapter(max)
        }

        else -> {
            val fallback = MaxOpenAdHelper(maxAdUnitId, context, remoteConfigProvider)
            MaxOpenAdAdapter(fallback)
        }
    }

    override fun isAdAvailable(): Boolean = impl.isAdAvailable()

    override fun showAdIfAvailable(
        activity: Activity,
        listener: OpenAdManager.OnShowAdCompleteListener?
    ) {
        impl.showAdIfAvailable(activity, listener)
    }

    override fun loadAd(activity: Activity?, onComplete: (() -> Unit)?) {
        impl.loadAd(activity, onComplete)
    }
}