package com.magic.ads.core

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
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

    /** Global kill switch for [AdPool]-backed loads — independent of [isPremium], for a remote
     * "ads off" flag. Defaults to on. */
    @Volatile
    private var masterAdsEnabled = true

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
        val isDebuggable =
            (context.applicationContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        TestAdGuard.isEnabled = !isDebuggable && isInstalledFromPlayStore(context.applicationContext)
        TestAdGuard.init(context.applicationContext)
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
            AdPool.removeAllAds()
        }
    }

    fun isMasterAdsEnabled(): Boolean = masterAdsEnabled

    /** Remote "ads off" switch. Turning this off also purges every ad [AdPool] has cached. */
    fun setMasterAdsEnabled(enabled: Boolean) {
        masterAdsEnabled = enabled
        if (!enabled) AdPool.removeAllAds()
    }

    fun applyOptions(options: AdsOptions) {
        AdPool.setMaxAdsPerPlacement(options.maxAdsPerPlacement)
        AdPool.setMaxNativePlacements(options.maxNativePlacements)
        AdPool.setMaxPlacementsPerType(options.maxPlacementsPerType)
        AdPool.setMaxConcurrentPerType(options.maxConcurrentPerType)
        AdPool.setFullScreenMinIntervalSeconds(options.fullScreenMinIntervalSeconds)
        AdPool.setFullScreenMaxShowsPerDay(options.fullScreenMaxShowsPerDay)
        AdPool.setFullScreenOneInEveryN(options.fullScreenOneInEveryN)
        setMasterAdsEnabled(options.masterAdsEnabled)
    }

    private fun readPremiumPref(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_IS_PREMIUM, false)

    /** Distinguishes a real Play Store install from a sideloaded/adb-installed one, even when
     * the APK itself is a non-debuggable release build (e.g. an internal QA build shared as a
     * raw APK) — [TestAdGuard] should stay off for those, same as it does for debug builds. */
    private fun isInstalledFromPlayStore(context: Context): Boolean {
        val packageName = context.packageName
        val installer = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.packageManager.getInstallSourceInfo(packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getInstallerPackageName(packageName)
            }
        } catch (e: Exception) {
            null
        }
        return installer == "com.android.vending"
    }

    private const val PREFS_NAME = "magic_ads_prefs"
    private const val KEY_IS_PREMIUM = "is_premium"
}
