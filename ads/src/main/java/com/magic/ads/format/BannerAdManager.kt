package com.magic.ads.format

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import com.magic.ads.config.PlacementConfig
import com.magic.ads.core.AdRemovalRegistry
import com.magic.ads.core.AdsManager
import com.magic.ads.core.RemovableAd
import com.magic.ads.listener.AdCallback

/**
 * One placement's banner. Registers with [AdRemovalRegistry] on construction so
 * [AdsManager.setPremium] tears it down automatically — the app never has to remember to call
 * anything here when the user goes premium.
 *
 * The ad view itself is fully SDK-rendered (unlike native), so there's no style/layout
 * injection here — only [container] placement and adaptive-vs-fixed sizing are configurable.
 */
class BannerAdManager : RemovableAd {

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
        val provider = AdsManager.activeProvider
        if (AdsManager.isPremium()) {
            container.removeAllViews()
            container.visibility = View.GONE
            callback?.onAdFailedToLoad(-1, "Ads disabled for premium user")
            return
        }
        if (!config.enable) {
            callback?.onAdFailedToLoad(-1, "Ads disabled by config")
            return
        }
        if (provider == null) {
            callback?.onAdFailedToLoad(-1, "No ad provider registered")
            return
        }
        val adUnitId = config.adUnitFor(provider.name)
        if (adUnitId.isBlank()) {
            callback?.onAdFailedToLoad(-1, "Ad unit not configured for ${provider.name}")
            return
        }

        provider.loadBanner(
            activity, container, adUnitId, adaptive, callback,
            onSuccess = { adView ->
                // The provider has already swapped [container]'s content for the new adView;
                // only the orphaned old ad object itself still needs releasing.
                provider.destroyBannerAd(currentAdView)
                currentAdView = adView
                currentContainer = container
                callback?.onAdLoaded()
            },
            onFail = { callback?.onAdFailedToLoad(-1, "Banner failed to load") }
        )
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
