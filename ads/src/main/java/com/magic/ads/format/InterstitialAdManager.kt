package com.magic.ads.format

import android.app.Activity
import android.content.Context
import com.magic.ads.config.PlacementConfig
import com.magic.ads.core.AdsManager
import com.magic.ads.listener.AdCallback

/**
 * One placement's interstitial ad. No waterfall: [loadAd] attempts exactly the ad unit
 * [PlacementConfig] resolves for the active provider; a failed load means no ad for this
 * cycle, not a fallback to another unit.
 */
class InterstitialAdManager {

    private var loadedAd: Any? = null
    private var isLoading = false
    private val pendingCallbacks = mutableListOf<AdCallback>()

    // Guards against a load() call that fires while a previous one is still in flight: only
    // the most recent request's terminal result (success or failure) gets to resolve pending
    // callbacks. A stale success is still cached for the next showAd() even if its callbacks
    // were already skipped.
    private var loadGeneration = 0
    private var resolvedGeneration = 0

    fun isAdReady() = loadedAd != null

    fun loadAd(context: Context, config: PlacementConfig, callback: AdCallback? = null) {
        val provider = AdsManager.activeProvider
        if (AdsManager.isPremium()) {
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
        if (isAdReady()) {
            callback?.onAdLoaded()
            return
        }
        if (isLoading) {
            callback?.let { pendingCallbacks.add(it) }
            return
        }
        val adUnitId = config.adUnitFor(provider.name)
        if (adUnitId.isBlank()) {
            callback?.onAdFailedToLoad(-1, "Ad unit not configured for ${provider.name}")
            return
        }

        isLoading = true
        val generation = ++loadGeneration
        provider.loadInterstitial(
            context, adUnitId, config.timeoutMs,
            onSuccess = { ad -> notifySuccess(ad, callback, generation) },
            onFail = { notifyAllFailed("Interstitial failed to load", callback, generation) }
        )
    }

    fun showAd(activity: Activity, callback: AdCallback? = null): Boolean {
        val provider = AdsManager.activeProvider ?: run {
            callback?.onAdFailedToLoad(-1, "No ad provider registered")
            return false
        }
        if (AdsManager.isPremium()) {
            loadedAd = null
            callback?.onAdFailedToLoad(-1, "Ads disabled for premium user")
            return false
        }
        if (!AdsManager.canShowInterstitial()) {
            callback?.onAdFailedToLoad(-1, "Interstitial cooldown active")
            return false
        }
        val ad = loadedAd ?: run {
            callback?.onAdFailedToLoad(-1, "Ad not ready")
            return false
        }
        AdsManager.notifyFullScreenAdShowing(true)
        provider.showInterstitial(activity, ad, object : AdCallback {
            override fun onAdShowed() {
                AdsManager.notifyInterstitialShown()
                callback?.onAdShowed()
            }
            override fun onAdDismissed() {
                loadedAd = null
                AdsManager.notifyFullScreenAdShowing(false)
                callback?.onAdDismissed()
            }
            override fun onAdFailedToLoad(errorCode: Int, errorMessage: String) {
                loadedAd = null
                AdsManager.notifyFullScreenAdShowing(false)
                callback?.onAdFailedToLoad(errorCode, errorMessage)
            }
            override fun onAdClicked() = callback?.onAdClicked() ?: Unit
            override fun onAdImpression() = callback?.onAdImpression() ?: Unit
        })
        return true
    }

    private fun notifySuccess(ad: Any, callback: AdCallback?, generation: Int) {
        loadedAd = ad
        if (generation <= resolvedGeneration) return
        resolvedGeneration = generation
        isLoading = false
        callback?.onAdLoaded()
        pendingCallbacks.forEach { it.onAdLoaded() }
        pendingCallbacks.clear()
    }

    private fun notifyAllFailed(message: String, callback: AdCallback?, generation: Int) {
        if (generation <= resolvedGeneration) return
        resolvedGeneration = generation
        isLoading = false
        callback?.onAdFailedToLoad(-1, message)
        pendingCallbacks.forEach { it.onAdFailedToLoad(-1, message) }
        pendingCallbacks.clear()
    }
}
