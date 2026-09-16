package com.magic.ads.format

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import com.magic.ads.config.PlacementConfig
import com.magic.ads.core.AdPool
import com.magic.ads.core.AdRemovalRegistry
import com.magic.ads.core.AdsManager
import com.magic.ads.core.RemovableAd
import com.magic.ads.listener.AdCallback

/**
 * One placement's banner, identified by [placementKey]. Registers with [AdRemovalRegistry] on
 * construction so [AdsManager.setPremium] tears it down automatically — the app never has to
 * remember to call anything here when the user goes premium.
 *
 * Banner loads always go through [AdPool] as `alwaysReload = true`: unlike the other formats a
 * banner view can't sit unshown in a pool, so every [loadAd] call is a fresh fetch — [AdPool]
 * still applies waterfall/throttling/frequency bookkeeping for it, just no caching-for-later.
 */
class BannerAdManager(private val placementKey: String) : RemovableAd {

    private var currentAdView: Any? = null
    private var currentContainer: ViewGroup? = null

    init {
        AdRemovalRegistry.register(this)
    }

    fun loadAd(
        activity: Activity,
        container: ViewGroup,
        config: PlacementConfig,
        adaptive: Boolean = true,
        callback: AdCallback? = null
    ) {
        if (AdsManager.isPremium()) {
            container.removeAllViews()
            container.visibility = View.GONE
            callback?.onAdFailedToLoad(-1, "Ads disabled for premium user")
            return
        }
        AdPool.loadBanner(activity, placementKey, container, config, adaptive, object : AdCallback {
            override fun onAdLoaded() {
                // The provider has already swapped [container]'s content for the new adView;
                // only the orphaned old ad object itself still needs releasing.
                AdsManager.activeProvider?.destroyBannerAd(currentAdView)
                currentAdView = AdPool.getHeldBannerAd(placementKey)
                currentContainer = container
                callback?.onAdLoaded()
            }
            override fun onAdFailedToLoad(errorCode: Int, errorMessage: String) {
                callback?.onAdFailedToLoad(errorCode, errorMessage)
            }
            override fun onAdShowed() = callback?.onAdShowed() ?: Unit
            override fun onAdDismissed() = callback?.onAdDismissed() ?: Unit
            override fun onAdClicked() = callback?.onAdClicked() ?: Unit
            override fun onAdImpression() = callback?.onAdImpression() ?: Unit
        })
    }

    fun destroyCurrentAd() {
        AdsManager.activeProvider?.destroyBannerAd(currentAdView)
        currentAdView = null
    }

    /** Called by [AdRemovalRegistry] when ads are removed by purchase. */
    override fun forceRemoveAd() {
        AdsManager.activeProvider?.destroyBannerAd(currentAdView)
        currentAdView = null
        currentContainer?.removeAllViews()
        currentContainer?.visibility = View.GONE
        currentContainer = null
    }
}
