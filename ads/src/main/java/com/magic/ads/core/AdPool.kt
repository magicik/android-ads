package com.magic.ads.core

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.magic.ads.config.PlacementConfig
import com.magic.ads.listener.AdCallback
import com.magic.ads.listener.NativeCallback
import com.magic.ads.listener.RewardCallback
import com.magic.ads.testguard.TestAdGuard
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * Waterfall + cache for every ad format: each placement (keyed by an app-supplied
 * [placementKey]) tries [PlacementConfig.enabledAds] in order (an optional [PlacementConfig
 * .highFloor] unit first, no timeout of its own), and successful loads are cached — up to
 * [maxAdsPerPlacement] ads per placement, evicting the lowest-tier/oldest first, with a
 * per-[AdType] cap on how many distinct placements may hold cached ads at once.
 *
 * This sits *underneath* the `format/` managers: they call [AdPool] for the actual load/show,
 * keeping their own public API (placementKey supplied at construction, not per call).
 */
object AdPool {

    private sealed class LoadDecision {
        data class Ready(val wrapper: AdWrapper) : LoadDecision()
        object Queued : LoadDecision()
        object Start : LoadDecision()
    }

    private class Slot {
        val ads = mutableListOf<AdWrapper>()
        var isLoading = false
        val pendingCallbacks = mutableListOf<(AdWrapper?, String) -> Unit>()
    }

    private val slots = ConcurrentHashMap<String, Slot>()
    private val activeRequestsByType = ConcurrentHashMap<AdType, AtomicInteger>()
    private val pendingQueue = ConcurrentHashMap<AdType, ConcurrentLinkedQueue<Runnable>>()

    @Volatile
    private var maxAdsPerPlacement: Int = 2

    @Volatile
    private var maxNativePlacements: Int = 6

    @Volatile
    private var maxPlacementsPerType: Int = 2

    @Volatile
    private var maxConcurrentPerType: Int = 3

    @JvmStatic
    fun setMaxAdsPerPlacement(n: Int) { maxAdsPerPlacement = n.coerceAtLeast(1) }

    @JvmStatic
    fun setMaxNativePlacements(n: Int) { maxNativePlacements = n.coerceAtLeast(1) }

    @JvmStatic
    fun setMaxPlacementsPerType(n: Int) { maxPlacementsPerType = n.coerceAtLeast(1) }

    @JvmStatic
    fun setMaxConcurrentPerType(n: Int) { maxConcurrentPerType = n.coerceAtLeast(1) }

    @JvmStatic
    fun setFullScreenMinIntervalSeconds(seconds: Int) = GlobalFullScreenAdCap.setMinIntervalSeconds(seconds)

    @JvmStatic
    fun setFullScreenMaxShowsPerDay(n: Int) = GlobalFullScreenAdCap.setMaxShowsPerDay(n)

    @JvmStatic
    fun setFullScreenOneInEveryN(n: Int) = GlobalFullScreenAdCap.setOneInEveryN(n)


    // ── Interstitial ──────────────────────────────────────────────────────────

    @JvmStatic
    @JvmOverloads
    fun loadInterstitial(context: Context, placementKey: String, config: PlacementConfig, callback: AdCallback? = null) {
        engineLoad(placementKey, AdType.INTERSTITIAL, config, { adUnitId, timeoutMs, onSuccess, onFail ->
            val provider = AdsManager.activeProvider
            if (provider == null) onFail() else provider.loadInterstitial(context, adUnitId, timeoutMs, onSuccess, onFail)
        }) { wrapper, error ->
            if (wrapper != null) callback?.onAdLoaded() else callback?.onAdFailedToLoad(-1, error)
        }
    }

    @JvmStatic
    @JvmOverloads
    fun showInterstitial(activity: Activity, placementKey: String, callback: AdCallback? = null): Boolean {
        if (AdsManager.isPremium()) {
            callback?.onAdFailedToLoad(-1, "Ads disabled for premium user")
            return false
        }
        val provider = AdsManager.activeProvider ?: run {
            callback?.onAdFailedToLoad(-1, "No ad provider registered")
            return false
        }
        val k = key(placementKey, AdType.INTERSTITIAL)
        val wrapper = peekReady(k, AdType.INTERSTITIAL) ?: run {
            callback?.onAdFailedToLoad(-1, "Ad not ready")
            return false
        }
        if (!GlobalFullScreenAdCap.tryConsume(activity)) {
            callback?.onAdFailedToLoad(-1, "Full-screen ad cooldown active")
            return false
        }
        if (!claimFullScreenSlot()) {
            callback?.onAdFailedToLoad(-1, "Another full-screen ad is already showing")
            return false
        }
        consumeReady(k, AdType.INTERSTITIAL)
        provider.showInterstitial(activity, wrapper.adObject, object : AdCallback {
            override fun onAdShowed() {
                GlobalFullScreenAdCap.recordShown(activity)
                callback?.onAdShowed()
            }
            override fun onAdDismissed() { releaseFullScreenSlot(); callback?.onAdDismissed() }
            override fun onAdFailedToLoad(errorCode: Int, errorMessage: String) {
                releaseFullScreenSlot()
                callback?.onAdFailedToLoad(errorCode, errorMessage)
            }
            override fun onAdClicked() = callback?.onAdClicked() ?: Unit
            override fun onAdImpression() = callback?.onAdImpression() ?: Unit
        })
        return true
    }

    @JvmStatic
    fun isInterstitialReady(placementKey: String): Boolean =
        peekReady(key(placementKey, AdType.INTERSTITIAL), AdType.INTERSTITIAL) != null


    // ── Rewarded ──────────────────────────────────────────────────────────────

    @JvmStatic
    @JvmOverloads
    fun loadRewarded(context: Context, placementKey: String, config: PlacementConfig, callback: RewardCallback? = null) {
        engineLoad(placementKey, AdType.REWARDED, config, { adUnitId, timeoutMs, onSuccess, onFail ->
            val provider = AdsManager.activeProvider
            if (provider == null) onFail() else provider.loadRewarded(context, adUnitId, timeoutMs, onSuccess, onFail)
        }) { wrapper, error ->
            if (wrapper != null) callback?.onAdLoaded() else callback?.onAdFailedToLoad(-1, error)
        }
    }

    @JvmStatic
    fun showRewarded(activity: Activity, placementKey: String, callback: RewardCallback): Boolean {
        if (AdsManager.isPremium()) {
            callback.onAdFailedToLoad(-1, "Ads disabled for premium user")
            return false
        }
        val provider = AdsManager.activeProvider ?: run {
            callback.onAdFailedToLoad(-1, "No ad provider registered")
            return false
        }
        val k = key(placementKey, AdType.REWARDED)
        val wrapper = peekReady(k, AdType.REWARDED) ?: run {
            callback.onAdFailedToLoad(-1, "Ad not ready")
            return false
        }
        if (!GlobalFullScreenAdCap.tryConsume(activity)) {
            callback.onAdFailedToLoad(-1, "Full-screen ad cooldown active")
            return false
        }
        if (!claimFullScreenSlot()) {
            callback.onAdFailedToLoad(-1, "Another full-screen ad is already showing")
            return false
        }
        consumeReady(k, AdType.REWARDED)
        provider.showRewarded(activity, wrapper.adObject, object : RewardCallback {
            override fun onUserEarnedReward(rewardType: String, rewardAmount: Int) =
                callback.onUserEarnedReward(rewardType, rewardAmount)
            override fun onAdShowed() {
                GlobalFullScreenAdCap.recordShown(activity)
                callback.onAdShowed()
            }
            override fun onAdDismissed() { releaseFullScreenSlot(); callback.onAdDismissed() }
            override fun onAdFailedToLoad(errorCode: Int, errorMessage: String) {
                releaseFullScreenSlot()
                callback.onAdFailedToLoad(errorCode, errorMessage)
            }
            override fun onAdClicked() = callback.onAdClicked()
            override fun onAdImpression() = callback.onAdImpression()
        })
        return true
    }

    @JvmStatic
    fun isRewardedReady(placementKey: String): Boolean =
        peekReady(key(placementKey, AdType.REWARDED), AdType.REWARDED) != null


    // ── App Open ──────────────────────────────────────────────────────────────

    @JvmStatic
    @JvmOverloads
    fun loadAppOpen(context: Context, placementKey: String, config: PlacementConfig, callback: AdCallback? = null) {
        engineLoad(placementKey, AdType.APP_OPEN, config, { adUnitId, timeoutMs, onSuccess, onFail ->
            val provider = AdsManager.activeProvider
            if (provider == null) onFail() else provider.loadAppOpen(context, adUnitId, timeoutMs, onSuccess, onFail)
        }) { wrapper, error ->
            if (wrapper != null) callback?.onAdLoaded() else callback?.onAdFailedToLoad(-1, error)
        }
    }

    @JvmStatic
    @JvmOverloads
    fun showAppOpen(activity: Activity, placementKey: String, callback: AdCallback? = null): Boolean {
        if (AdsManager.isPremium()) {
            callback?.onAdFailedToLoad(-1, "Ads disabled for premium user")
            return false
        }
        val provider = AdsManager.activeProvider ?: run {
            callback?.onAdFailedToLoad(-1, "No ad provider registered")
            return false
        }
        val k = key(placementKey, AdType.APP_OPEN)
        val wrapper = peekReady(k, AdType.APP_OPEN) ?: run {
            callback?.onAdFailedToLoad(-1, "Ad not ready")
            return false
        }
        if (!GlobalFullScreenAdCap.tryConsume(activity)) {
            callback?.onAdFailedToLoad(-1, "Full-screen ad cooldown active")
            return false
        }
        if (!claimFullScreenSlot()) {
            callback?.onAdFailedToLoad(-1, "Another full-screen ad is already showing")
            return false
        }
        consumeReady(k, AdType.APP_OPEN)
        provider.showAppOpen(activity, wrapper.adObject, object : AdCallback {
            override fun onAdShowed() {
                GlobalFullScreenAdCap.recordShown(activity)
                callback?.onAdShowed()
            }
            override fun onAdDismissed() { releaseFullScreenSlot(); callback?.onAdDismissed() }
            override fun onAdFailedToLoad(errorCode: Int, errorMessage: String) {
                releaseFullScreenSlot()
                callback?.onAdFailedToLoad(errorCode, errorMessage)
            }
            override fun onAdClicked() = callback?.onAdClicked() ?: Unit
            override fun onAdImpression() = callback?.onAdImpression() ?: Unit
        })
        return true
    }

    @JvmStatic
    fun isAppOpenReady(placementKey: String): Boolean =
        peekReady(key(placementKey, AdType.APP_OPEN), AdType.APP_OPEN) != null


    // ── Native (AdMob-only) ──────────────────────────────────────────────────

    @JvmStatic
    @JvmOverloads
    fun loadNative(
        context: Context,
        placementKey: String,
        config: PlacementConfig,
        alwaysReload: Boolean = false,
        callback: NativeCallback? = null
    ) {
        if (AdsManager.activeProvider?.name != "admob") {
            callback?.onAdFailedToLoad(-1, "Native ads are only supported with the AdMob provider")
            return
        }
        engineLoad(placementKey, AdType.NATIVE, config, { adUnitId, timeoutMs, onSuccess, onFail ->
            loadNativeSingle(context, adUnitId, timeoutMs, onSuccess, onFail)
        }, alwaysReload = alwaysReload) { wrapper, error ->
            if (wrapper != null) callback?.onAdLoaded() else callback?.onAdFailedToLoad(-1, error)
        }
    }

    /** Removes and returns the pool's cached [NativeAd] for [placementKey], if any. A NativeAd
     * can't be bound to more than one view, so callers must consume (not just peek) it. */
    @JvmStatic
    fun consumeNative(placementKey: String): NativeAd? =
        consumeReady(key(placementKey, AdType.NATIVE), AdType.NATIVE)?.adObject as? NativeAd

    @JvmStatic
    fun isNativeReady(placementKey: String): Boolean =
        peekReady(key(placementKey, AdType.NATIVE), AdType.NATIVE) != null

    private fun loadNativeSingle(
        context: Context,
        adUnitId: String,
        timeoutMs: Long,
        onSuccess: (Any) -> Unit,
        onFail: () -> Unit
    ) {
        val handler = Handler(Looper.getMainLooper())
        var done = false
        val timeout = Runnable { if (!done) { done = true; onFail() } }
        handler.postDelayed(timeout, timeoutMs)

        AdLoader.Builder(context, adUnitId)
            .forNativeAd { ad ->
                if (done) { ad.destroy(); return@forNativeAd }
                done = true
                handler.removeCallbacks(timeout)
                if (TestAdGuard.isTestHeadline(ad.headline)) TestAdGuard.markTestMode(context)
                ad.setOnPaidEventListener { TestAdGuard.onPaidEvent(context, it.valueMicros) }
                onSuccess(ad)
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    if (!done) { done = true; handler.removeCallbacks(timeout); onFail() }
                }
            })
            .withNativeAdOptions(NativeAdOptions.Builder().build())
            .build()
            .loadAd(AdRequest.Builder().build())
    }


    // ── Banner ────────────────────────────────────────────────────────────────

    @JvmStatic
    @JvmOverloads
    fun loadBanner(
        activity: Activity,
        placementKey: String,
        container: ViewGroup,
        config: PlacementConfig,
        adaptive: Boolean = false,
        callback: AdCallback? = null
    ) {
        engineLoad(placementKey, AdType.BANNER, config, { adUnitId, timeoutMs, onSuccess, onFail ->
            val provider = AdsManager.activeProvider
            if (provider == null) onFail() else provider.loadBanner(activity, container, adUnitId, adaptive, callback, onSuccess, onFail)
        }, alwaysReload = true) { wrapper, error ->
            if (wrapper != null) callback?.onAdLoaded() else callback?.onAdFailedToLoad(-1, error)
        }
    }

    /** Peeks the pool's cached banner ad view for [placementKey] without removing it — the
     * provider has already attached it to its container by the time [loadBanner]'s callback
     * fires, so callers only need this to grab the reference for a later destroy. */
    @JvmStatic
    fun getHeldBannerAd(placementKey: String): Any? =
        peekReady(key(placementKey, AdType.BANNER), AdType.BANNER)?.adObject

    @JvmStatic
    fun isBannerReady(placementKey: String): Boolean =
        peekReady(key(placementKey, AdType.BANNER), AdType.BANNER) != null


    // ── Teardown ──────────────────────────────────────────────────────────────

    @Synchronized
    fun removeAllAds() {
        slots.forEach { (_, slot) ->
            slot.ads.forEach { destroyAdObject(it.adType, it.adObject) }
            slot.ads.clear()
        }
    }

    private fun destroyAdObject(adType: AdType, adObject: Any) {
        when (adType) {
            AdType.NATIVE -> (adObject as? NativeAd)?.destroy()
            AdType.BANNER -> AdsManager.activeProvider?.destroyBannerAd(adObject)
            else -> Unit
        }
    }


    // ── Pool/waterfall engine ────────────────────────────────────────────────

    private fun key(placementKey: String, adType: AdType) = "$placementKey|${adType.name}"

    private fun ttlMs(adType: AdType) = if (adType == AdType.APP_OPEN) APP_OPEN_TTL_MS else DEFAULT_TTL_MS

    @Synchronized
    private fun cleanExpired(slot: Slot, adType: AdType) {
        val now = System.currentTimeMillis()
        val expiryMs = ttlMs(adType)
        val expired = slot.ads.filter { now - it.loadTime >= expiryMs }
        if (expired.isEmpty()) return
        slot.ads.removeAll(expired)
        expired.forEach { destroyAdObject(it.adType, it.adObject) }
    }

    @Synchronized
    private fun peekReady(k: String, adType: AdType): AdWrapper? {
        val slot = slots[k] ?: return null
        cleanExpired(slot, adType)
        return slot.ads.firstOrNull()
    }

    @Synchronized
    private fun consumeReady(k: String, adType: AdType): AdWrapper? {
        val slot = slots[k] ?: return null
        cleanExpired(slot, adType)
        return if (slot.ads.isNotEmpty()) slot.ads.removeAt(0) else null
    }

    @Synchronized
    private fun addToPool(k: String, wrapper: AdWrapper, adType: AdType) {
        val slot = slots.getOrPut(k) { Slot() }
        slot.ads.add(wrapper)
        while (slot.ads.size > maxAdsPerPlacement) {
            val worst = slot.ads.sortedWith(compareByDescending<AdWrapper> { it.tier }.thenBy { it.loadTime }).firstOrNull()
                ?: break
            slot.ads.remove(worst)
            destroyAdObject(worst.adType, worst.adObject)
        }
        enforcePlacementCapacity(adType, keepKey = k)
    }

    private fun enforcePlacementCapacity(adType: AdType, keepKey: String) {
        val suffix = "|${adType.name}"
        val limit = if (adType == AdType.NATIVE) maxNativePlacements else maxPlacementsPerType
        while (true) {
            val entries = slots.entries.filter { it.key.endsWith(suffix) && it.value.ads.isNotEmpty() }
            if (entries.size <= limit) return
            val oldest = entries
                .filter { it.key != keepKey }
                .minByOrNull { entry -> entry.value.ads.minOf { it.loadTime } }
                ?: return
            val victim = oldest.value
            victim.ads.forEach { destroyAdObject(it.adType, it.adObject) }
            victim.ads.clear()
        }
    }

    private fun engineLoad(
        placementKey: String,
        adType: AdType,
        config: PlacementConfig,
        loadSingle: (adUnitId: String, timeoutMs: Long, onSuccess: (Any) -> Unit, onFail: () -> Unit) -> Unit,
        alwaysReload: Boolean = false,
        onTerminal: (AdWrapper?, String) -> Unit
    ) {
        if (!AdsManager.isMasterAdsEnabled()) { onTerminal(null, "Ads disabled by master switch"); return }
        if (AdsManager.isPremium()) { onTerminal(null, "Ads disabled for premium user"); return }
        if (!config.enable) { onTerminal(null, "Ads disabled by config"); return }
        if (AdsManager.activeProvider == null) { onTerminal(null, "No ad provider registered"); return }

        val k = key(placementKey, adType)
        val slot = slots.getOrPut(k) { Slot() }

        val decision: LoadDecision = synchronized(this) {
            cleanExpired(slot, adType)
            when {
                !alwaysReload && slot.ads.size >= maxAdsPerPlacement -> LoadDecision.Ready(slot.ads.first())
                slot.isLoading -> { slot.pendingCallbacks.add(onTerminal); LoadDecision.Queued }
                else -> { slot.isLoading = true; LoadDecision.Start }
            }
        }
        when (decision) {
            is LoadDecision.Ready -> { onTerminal(decision.wrapper, ""); return }
            LoadDecision.Queued -> return
            LoadDecision.Start -> Unit
        }

        runThrottled(adType) {
            fun finish(wrapper: AdWrapper?, error: String) {
                val callbacks: List<(AdWrapper?, String) -> Unit>
                synchronized(this) {
                    slot.isLoading = false
                    if (wrapper != null) addToPool(k, wrapper, adType)
                    callbacks = slot.pendingCallbacks.toList()
                    slot.pendingCallbacks.clear()
                }
                releaseThrottle(adType)
                onTerminal(wrapper, error)
                callbacks.forEach { it(wrapper, error) }
            }

            fun loadNormalWaterfall() {
                AdWaterfallLoader<Any>(config) { adUnit, effectiveTimeout, onSuccess, onFail ->
                    loadSingle(adUnit.adUnit, effectiveTimeout, onSuccess, onFail)
                }.load(
                    onSuccess = { ad ->
                        finish(AdWrapper(ad, adType, "", 2, System.currentTimeMillis(), placementKey), "")
                    },
                    onAllFailed = { finish(null, "All ad units failed to load") }
                )
            }

            if (config.highFloor.enable && config.highFloor.adUnit.isNotBlank()) {
                loadSingle(
                    config.highFloor.adUnit, NO_TIMEOUT_MS,
                    { ad -> finish(AdWrapper(ad, adType, config.highFloor.adUnit, 1, System.currentTimeMillis(), placementKey), "") },
                    { loadNormalWaterfall() }
                )
            } else {
                loadNormalWaterfall()
            }
        }
    }

    private fun runThrottled(adType: AdType, action: () -> Unit) {
        val counter = activeRequestsByType.getOrPut(adType) { AtomicInteger(0) }
        if (counter.incrementAndGet() > maxConcurrentPerType) {
            counter.decrementAndGet()
            pendingQueue.getOrPut(adType) { ConcurrentLinkedQueue() }.add(Runnable { runThrottled(adType, action) })
            return
        }
        action()
    }

    private fun releaseThrottle(adType: AdType) {
        activeRequestsByType[adType]?.decrementAndGet()
        pendingQueue[adType]?.poll()?.run()
    }

    // Full-screen mutual exclusion reuses AdsManager's existing isShowingFullScreenAd flag
    // (read by AppOpenResumeHelper etc.) instead of keeping a second, separately-tracked flag.
    @Synchronized
    private fun claimFullScreenSlot(): Boolean {
        if (AdsManager.isShowingFullScreenAd) return false
        AdsManager.notifyFullScreenAdShowing(true)
        return true
    }

    private fun releaseFullScreenSlot() {
        AdsManager.notifyFullScreenAdShowing(false)
    }

    private const val DEFAULT_TTL_MS = 55 * 60 * 1000L
    private const val APP_OPEN_TTL_MS = 4 * 60 * 60 * 1000L

    // Large enough to never fire in practice, but small enough to avoid overflow when Handler
    // adds it to the current uptime — used for the high-floor tier, which has no timeout of its own.
    private const val NO_TIMEOUT_MS = Long.MAX_VALUE / 2
}
