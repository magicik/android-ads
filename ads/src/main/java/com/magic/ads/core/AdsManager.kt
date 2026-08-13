package com.magic.ads.core

import android.content.Context
import android.content.pm.ApplicationInfo
import com.magic.ads.provider.AdSdkProvider
import com.magic.ads.testguard.TestAdGuard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/**
 * Single entry point of the library. Apps register one [AdSdkProvider] per network they
 * support, then pick the active one with [setActiveProvider] using whatever value their own
 * remote config resolved to — this object never fetches or interprets remote config itself.
 */
object AdsManager {

    private val providers = mutableMapOf<String, AdSdkProvider>()

    var activeProvider: AdSdkProvider? = null
        private set

    private val _initializedState = MutableStateFlow(false)
    val initializedState: StateFlow<Boolean> = _initializedState.asStateFlow()

    private val pendingCallbacks = mutableListOf<() -> Unit>()
    private var isPremium = false
    private var interstitialMinIntervalMs = 0L
    private var lastInterstitialShownAt = 0L

    /** True while any interstitial/rewarded/app-open ad is on screen. Format managers toggle
     * this around their showAd() calls; [com.magic.ads.helper.AppOpenResumeHelper] reads
     * it so a resume triggered by one ad's own full-screen activity finishing doesn't stack
     * another ad on top of it. */
    @Volatile
    var isShowingFullScreenAd: Boolean = false
        private set

    fun notifyFullScreenAdShowing(showing: Boolean) {
        isShowingFullScreenAd = showing
    }

    fun registerProvider(provider: AdSdkProvider) {
        providers[provider.name] = provider
        if (activeProvider == null) activeProvider = provider
    }

    /** [name] is app-supplied (e.g. read from the app's own remote config) — must match a
     * previously [registerProvider]'d [AdSdkProvider.name]. Unknown names are ignored so a bad
     * remote value can't null out an already-working provider. */
    fun setActiveProvider(name: String) {
        providers[name]?.let { activeProvider = it }
    }

    fun initialize(
        context: Context,
        config: AdsConfig = AdsConfig(),
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        if (_initializedState.value) {
            onComplete?.invoke(true)
            return
        }
        val provider = activeProvider ?: run {
            onComplete?.invoke(false)
            return
        }
        isPremium = readPremiumPref(context)
        TestAdGuard.isEnabled =
            (context.applicationContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) == 0
        provider.initialize(context, config.testDevices, config.maxSdkKey) {
            _initializedState.value = true
            pendingCallbacks.forEach { it() }
            pendingCallbacks.clear()
            onComplete?.invoke(true)
        }
    }

    fun whenInitialized(action: () -> Unit) {
        if (_initializedState.value) action() else pendingCallbacks.add(action)
    }

    suspend fun awaitInitialized() {
        initializedState.first { it }
    }

    fun isInitialized() = _initializedState.value

    fun isPremium(): Boolean = isPremium

    fun setPremium(context: Context, premium: Boolean) {
        isPremium = premium
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_IS_PREMIUM, premium)
            .apply()
        if (premium) {
            AdRemovalRegistry.removeAllAds()
        }
    }

    fun setInterstitialMinIntervalSeconds(seconds: Long) {
        interstitialMinIntervalMs = seconds * 1000L
    }

    internal fun canShowInterstitial(): Boolean {
        val minIntervalMs =
            if (TestAdGuard.isTestMode) TEST_MODE_INTERSTITIAL_MIN_INTERVAL_MS else interstitialMinIntervalMs
        return System.currentTimeMillis() - lastInterstitialShownAt >= minIntervalMs
    }

    internal fun notifyInterstitialShown() {
        lastInterstitialShownAt = System.currentTimeMillis()
    }

    private fun readPremiumPref(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_IS_PREMIUM, false)

    private const val PREFS_NAME = "magic_ads_prefs"
    private const val KEY_IS_PREMIUM = "is_premium"
    private const val TEST_MODE_INTERSTITIAL_MIN_INTERVAL_MS = 60_000L
}
