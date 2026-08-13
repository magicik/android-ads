package com.magic.ads.format

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.magic.ads.config.PlacementConfig
import com.magic.ads.core.AdsManager
import com.magic.ads.listener.AdCallback

/**
 * One placement's app-open ad. Unlike the other formats, a loaded ad is kept and reused across
 * multiple [showAd] calls until it expires ([AD_EXPIRY_MS]) — app-open ads are shown on every
 * app resume, not consumed once like an interstitial.
 */
class OpenAdManager {

    private var appOpenAd: Any? = null
    private var loadedAt = 0L
    private var isLoading = false
    private val pendingCallbacks = mutableListOf<AdCallback>()
    private val handler = Handler(Looper.getMainLooper())
    private var callerTimeout: Runnable? = null

    fun isAdReady(): Boolean {
        val elapsed = System.currentTimeMillis() - loadedAt
        return appOpenAd != null && elapsed < AD_EXPIRY_MS
    }

    fun destroyCurrentAd() {
        appOpenAd = null
    }

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

        // The caller-facing timeout only resolves callbacks; it does not end the underlying
        // load. isLoading stays true until the SDK request terminally resolves, so a loadAd()
        // call that lands mid-request queues up instead of firing a duplicate request.
        if (callback != null) {
            pendingCallbacks.add(callback)
            scheduleCallerTimeout(config.timeoutMs)
        }
        if (isLoading) return
        isLoading = true

        val adUnitId = config.adUnitFor(provider.name)
        if (adUnitId.isBlank()) {
            onLoadTerminated(null, "Ad unit not configured for ${provider.name}")
            return
        }
        provider.loadAppOpen(
            context, adUnitId, config.timeoutMs,
            onSuccess = { ad -> onLoadTerminated(ad, "") },
            onFail = { onLoadTerminated(null, "App open ad failed to load") }
        )
    }

    fun showAd(activity: Activity, callback: AdCallback? = null): Boolean {
        val provider = AdsManager.activeProvider ?: run {
            callback?.onAdFailedToLoad(-1, "No ad provider registered")
            return false
        }
        if (AdsManager.isPremium()) {
            appOpenAd = null
            callback?.onAdFailedToLoad(-1, "Ads disabled for premium user")
            return false
        }
        if (!isAdReady()) {
            callback?.onAdFailedToLoad(-1, "Ad not ready")
            return false
        }
        val ad = appOpenAd ?: return false
        AdsManager.notifyFullScreenAdShowing(true)
        provider.showAppOpen(activity, ad, object : AdCallback {
            override fun onAdShowed() = callback?.onAdShowed() ?: Unit
            override fun onAdDismissed() {
                appOpenAd = null
                AdsManager.notifyFullScreenAdShowing(false)
                callback?.onAdDismissed()
            }
            override fun onAdFailedToLoad(errorCode: Int, errorMessage: String) {
                appOpenAd = null
                AdsManager.notifyFullScreenAdShowing(false)
                callback?.onAdFailedToLoad(errorCode, errorMessage)
            }
            override fun onAdClicked() = callback?.onAdClicked() ?: Unit
            override fun onAdImpression() = callback?.onAdImpression() ?: Unit
        })
        return true
    }

    // Terminal result of the load request — the moment it's no longer in flight. A fill
    // arriving after the caller timeout is still cached here, so the next resume can show it
    // without a fresh request.
    private fun onLoadTerminated(ad: Any?, errorMessage: String) {
        isLoading = false
        callerTimeout?.let { handler.removeCallbacks(it) }
        callerTimeout = null
        if (ad != null) {
            appOpenAd = ad
            loadedAt = System.currentTimeMillis()
        }
        val callbacks = pendingCallbacks.toList()
        pendingCallbacks.clear()
        if (ad != null) {
            callbacks.forEach { it.onAdLoaded() }
        } else {
            callbacks.forEach { it.onAdFailedToLoad(-1, errorMessage) }
        }
    }

    private fun scheduleCallerTimeout(timeoutMs: Long) {
        callerTimeout?.let { handler.removeCallbacks(it) }
        val timeout = Runnable {
            callerTimeout = null
            val callbacks = pendingCallbacks.toList()
            pendingCallbacks.clear()
            callbacks.forEach { it.onAdFailedToLoad(-1, "Ad load timed out") }
        }
        callerTimeout = timeout
        handler.postDelayed(timeout, timeoutMs)
    }

    companion object {
        private const val AD_EXPIRY_MS = 4 * 60 * 60 * 1000L
    }
}
