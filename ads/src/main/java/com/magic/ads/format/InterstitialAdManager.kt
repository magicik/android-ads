package com.magic.ads.format

import android.app.Activity
import android.content.Context
import com.magic.ads.config.PlacementConfig
import com.magic.ads.core.AdPool
import com.magic.ads.listener.AdCallback

/**
 * One placement's interstitial ad, identified by [placementKey]. Load/show/cache are all
 * delegated to [AdPool], which waterfalls across [PlacementConfig.listAds] and pools successful
 * loads — this class is just a thin, placement-scoped facade over it.
 */
class InterstitialAdManager(private val placementKey: String) {

    fun isAdReady() = AdPool.isInterstitialReady(placementKey)

    fun loadAd(context: Context, config: PlacementConfig, callback: AdCallback? = null) {
        AdPool.loadInterstitial(context, placementKey, config, callback)
    }

    fun showAd(activity: Activity, callback: AdCallback? = null): Boolean =
        AdPool.showInterstitial(activity, placementKey, callback)
}
