package com.magic.ads.format

import android.app.Activity
import android.content.Context
import com.magic.ads.config.PlacementConfig
import com.magic.ads.core.AdPool
import com.magic.ads.listener.AdCallback

/** One placement's app-open ad, identified by [placementKey]. [AdPool]'s own TTL (longer than
 * the other formats) handles reuse across the many [showAd] calls an app-open placement gets
 * over a session — this class no longer tracks expiry itself. */
class OpenAdManager(private val placementKey: String) {

    fun isAdReady() = AdPool.isAppOpenReady(placementKey)

    fun loadAd(context: Context, config: PlacementConfig, callback: AdCallback? = null) {
        AdPool.loadAppOpen(context, placementKey, config, callback)
    }

    fun showAd(activity: Activity, callback: AdCallback? = null): Boolean =
        AdPool.showAppOpen(activity, placementKey, callback)
}
