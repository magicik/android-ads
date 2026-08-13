package com.magic.ads.format

import android.app.Activity
import android.content.Context
import com.magic.ads.config.PlacementConfig
import com.magic.ads.core.AdsManager
import com.magic.ads.listener.RewardCallback

/** One placement's rewarded ad. Same no-waterfall, generation-guarded shape as [InterstitialAdManager]. */
class RewardedAdManager {

    private var loadedAd: Any? = null
    private var isLoading = false
    private val pendingCallbacks = mutableListOf<RewardCallback>()
    private var loadGeneration = 0
    private var resolvedGeneration = 0

    fun isAdReady() = loadedAd != null

    fun loadAd(context: Context, config: PlacementConfig, callback: RewardCallback? = null) {
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
        provider.loadRewarded(
            context, adUnitId, config.timeoutMs,
            onSuccess = { ad -> notifySuccess(ad, callback, generation) },
            onFail = { notifyAllFailed("Rewarded failed to load", callback, generation) }
        )
    }

    fun showAd(activity: Activity, callback: RewardCallback): Boolean {
        val provider = AdsManager.activeProvider ?: run {
            callback.onAdFailedToLoad(-1, "No ad provider registered")
            return false
        }
        if (AdsManager.isPremium()) {
            loadedAd = null
            callback.onAdFailedToLoad(-1, "Ads disabled for premium user")
            return false
        }
        val ad = loadedAd ?: run {
            callback.onAdFailedToLoad(-1, "Ad not ready")
            return false
        }
        AdsManager.notifyFullScreenAdShowing(true)
        provider.showRewarded(activity, ad, object : RewardCallback {
            override fun onUserEarnedReward(rewardType: String, rewardAmount: Int) =
                callback.onUserEarnedReward(rewardType, rewardAmount)
            override fun onAdShowed() = callback.onAdShowed()
            override fun onAdDismissed() {
                loadedAd = null
                AdsManager.notifyFullScreenAdShowing(false)
                callback.onAdDismissed()
            }
            override fun onAdFailedToLoad(errorCode: Int, errorMessage: String) {
                loadedAd = null
                AdsManager.notifyFullScreenAdShowing(false)
                callback.onAdFailedToLoad(errorCode, errorMessage)
            }
            override fun onAdClicked() = callback.onAdClicked()
            override fun onAdImpression() = callback.onAdImpression()
        })
        return true
    }

    private fun notifySuccess(ad: Any, callback: RewardCallback?, generation: Int) {
        loadedAd = ad
        if (generation <= resolvedGeneration) return
        resolvedGeneration = generation
        isLoading = false
        callback?.onAdLoaded()
        pendingCallbacks.forEach { it.onAdLoaded() }
        pendingCallbacks.clear()
    }

    private fun notifyAllFailed(message: String, callback: RewardCallback?, generation: Int) {
        if (generation <= resolvedGeneration) return
        resolvedGeneration = generation
        isLoading = false
        callback?.onAdFailedToLoad(-1, message)
        pendingCallbacks.forEach { it.onAdFailedToLoad(-1, message) }
        pendingCallbacks.clear()
    }
}
