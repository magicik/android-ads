package com.magic.ads.helper

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.magic.ads.config.PlacementConfig
import com.magic.ads.core.AdsManager
import com.magic.ads.format.OpenAdManager
import com.magic.ads.listener.AdCallback
import com.magic.ads.listener.AppOpenLoadingListener

/**
 * Wires [OpenAdManager] to the app's foreground/background lifecycle using a **preload-first**
 * strategy: the ad is always fetched ahead of time — once SDK init completes, and again on
 * every Activity resume and right after every show finishes — never reactively at the moment
 * of showing. A real app resume only ever shows an already-cached ad; if none is ready yet,
 * that resume simply shows nothing instead of blocking on a fresh network load.
 *
 * [preload] is safe to call from multiple trigger points because [OpenAdManager.loadAd] itself
 * no-ops when an ad is already cached or a load is already in flight — so this class never
 * needs to track "did I already ask for one" bookkeeping of its own.
 *
 * Construct one instance per [Application] and keep it alive for the process lifetime (e.g. a
 * field on your Application subclass).
 */
class AppOpenResumeHelper(
    private val application: Application,
    private val adManager: OpenAdManager,
    private val config: PlacementConfig
) : Application.ActivityLifecycleCallbacks, DefaultLifecycleObserver {

    private var currentActivity: Activity? = null
    private var isFirstLaunch = true
    private var loadingListener: AppOpenLoadingListener? = null
    private var pendingSuppression = false
    private var backgroundedAt = 0L
    private var minBackgroundDurationMs = DEFAULT_MIN_BACKGROUND_MS

    fun setLoadingListener(listener: AppOpenLoadingListener?) {
        loadingListener = listener
    }

    // How long the app must have sat in the background before a resume counts as a real
    // "open" worth showing for. Filters out quick toggle-and-return trips (permission dialogs,
    // share sheets, etc). Disabled by default (0).
    fun setMinBackgroundDurationMs(ms: Long) {
        minBackgroundDurationMs = ms
    }

    // Blocks the next resume's show regardless of background duration — use for flows where an
    // ad popping up would derail the user (e.g. an onboarding wizard).
    fun suppressNextResume() {
        pendingSuppression = true
    }

    init {
        application.registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        // First preload attempt, ahead of any resume. Runs with the Application context since
        // no Activity exists yet this early — fine for AdMob; MAX defers its real load until
        // an Activity is available (see MaxProvider.loadAppOpen), which onActivityResumed below
        // then triggers.
        AdsManager.whenInitialized { preload() }
    }

    override fun onStop(owner: LifecycleOwner) {
        backgroundedAt = System.currentTimeMillis()
    }

    // Process-level foreground signal: unlike onActivityStarted, this doesn't fire when an
    // ad's own full-screen Activity is pushed on top of / popped off the host Activity, so it
    // won't misfire mid-ad. [AdsManager.isShowingFullScreenAd] is the primary guard for that;
    // the class-name check below is a defensive fallback for the moment right around SDK
    // Activity transitions.
    override fun onStart(owner: LifecycleOwner) {
        if (isFirstLaunch) { isFirstLaunch = false; return }
        if (AdsManager.isShowingFullScreenAd) return
        val activity = currentActivity ?: return
        if (isTopActivityAnAd(activity)) return

        if (pendingSuppression) {
            pendingSuppression = false
            return
        }

        val backgroundDurationMs = System.currentTimeMillis() - backgroundedAt
        if (backgroundDurationMs < minBackgroundDurationMs) return

        // Never load here — only show what's already cached. If nothing is ready, this resume
        // shows nothing; the next preload() (onActivityResumed, just below) fills it in for
        // whichever resume comes after this one.
        if (adManager.isAdReady()) showAd(activity)
    }

    override fun onActivityResumed(activity: Activity) {
        currentActivity = activity
        preload()
    }

    private fun isTopActivityAnAd(activity: Activity): Boolean {
        val className = activity.javaClass.name
        return AD_ACTIVITY_CLASS_MARKERS.any { className.contains(it) }
    }

    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}

    private fun preload() {
        adManager.loadAd(currentActivity ?: application, config)
    }

    private fun showAd(activity: Activity) {
        adManager.showAd(activity, object : AdCallback {
            override fun onAdShowed() {}
            override fun onAdDismissed() {
                loadingListener?.onAdFinished(activity)
                preload()
            }
            override fun onAdFailedToLoad(errorCode: Int, errorMessage: String) {
                loadingListener?.onAdFinished(activity)
                preload()
            }
        })
    }

    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}

    companion object {
        private const val DEFAULT_MIN_BACKGROUND_MS = 0L
        private val AD_ACTIVITY_CLASS_MARKERS = listOf(
            "com.google.android.gms.ads.AdActivity",
            "com.applovin.adview.AppLovinFullscreenActivity"
        )
    }
}
