package com.magic.ads.format

import android.app.Activity
import android.content.Context
import com.magic.ads.config.PlacementConfig
import com.magic.ads.core.AdPool
import com.magic.ads.listener.RewardCallback

/** One placement's rewarded ad, identified by [placementKey]. Same [AdPool]-backed shape as
 * [InterstitialAdManager]. */
class RewardedAdManager(private val placementKey: String) {

    fun isAdReady() = AdPool.isRewardedReady(placementKey)

    fun loadAd(context: Context, config: PlacementConfig, callback: RewardCallback? = null) {
        AdPool.loadRewarded(context, placementKey, config, callback)
    }

    fun showAd(activity: Activity, callback: RewardCallback): Boolean =
        AdPool.showRewarded(activity, placementKey, callback)
}
