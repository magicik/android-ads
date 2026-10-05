package com.magic.ads.helper

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.magic.ads.config.PlacementConfig
import com.magic.ads.core.AdsManager
import com.magic.ads.format.OpenAdManager
import com.magic.ads.listener.AdCallback
import com.magic.ads.listener.AppOpenLoadingListener

/**
 * Wires [OpenAdManager] to the app's foreground/background lifecycle with a **lazy first load,
 * then keep one ad ready** strategy:
 *  - Nothing is loaded at startup or on Activity resumes.
 *  - The first time the user returns to the app (and no ad is cached yet) one ad is loaded at
 *    that moment and shown as soon as it arrives — but only if the app is still in the
 *    foreground within [setReactiveShowTimeoutMs]; otherwise it just stays cached for the next return.
 *    While it waits, a full-screen scrim with a progress bar covers the screen (see
 *    [setShowLoadingOverlay]); the overlay is removed right before the ad shows.
 *  - Once an ad has been shown, exactly one next ad is preloaded right after it finishes. Later
 *    returns only show that cached ad and never trigger a load of their own.
 *  - Safety net: if a return finds nothing cached after that (expired / preload failed), one
 *    silent load is started for the following return (that return shows nothing).
 *
 * [OpenAdManager.loadAd] no-ops when an ad is already cached or a load is already in flight, so
 * redundant calls are harmless.
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
    private var reactiveShowTimeoutMs = DEFAULT_REACTIVE_SHOW_TIMEOUT_MS
    private val mainHandler = Handler(Looper.getMainLooper())
    private val loadingOverlay = AdLoadingOverlay()
    private var loadingTimeout = Runnable {}
    private var showLoadingOverlay = true

    // True once an ad has been shown through this helper; from then on one ad is always kept
    // preloaded and returns never load reactively.
    private var preloadArmed = false

    // Bumped on every return; a reactive load's callback only shows if it is still the latest.
    private var returnToken = 0

    fun setLoadingListener(listener: AppOpenLoadingListener?) {
        loadingListener = listener
    }

    // How long the app must have sat in the background before a resume counts as a real
    // "open" worth showing for. Filters out quick toggle-and-return trips (permission dialogs,
    // share sheets, etc). Disabled by default (0).
    fun setMinBackgroundDurationMs(ms: Long) {
        minBackgroundDurationMs = ms
    }

    // Whether the first-return load covers the screen with a progress overlay while it waits for
    // the ad (hidden right before the ad shows, or on timeout / backgrounding). Default true.
    fun setShowLoadingOverlay(show: Boolean) {
        showLoadingOverlay = show
    }

    // Max time after a return that a reactively-loaded first ad may still be shown. Past this the
    // ad simply stays cached for the next return instead of popping up late. Default 5s.
    fun setReactiveShowTimeoutMs(ms: Long) {
        reactiveShowTimeoutMs = ms
    }

    // Blocks the next resume's show regardless of background duration — use for flows where an
    // ad popping up would derail the user (e.g. an onboarding wizard).
    fun suppressNextResume() {
        pendingSuppression = true
    }

    init {
        application.registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStop(owner: LifecycleOwner) {
        mainHandler.removeCallbacks(loadingTimeout)
        loadingOverlay.hide()
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

        val token = ++returnToken
        when {
            adManager.isAdReady() -> showAd(activity)
            // Already armed: the preloaded ad is gone (expired / failed). Refill silently for the
            // next return rather than making this one wait.
            preloadArmed -> preload()
            // First return so far: load now and show once it arrives.
            else -> loadThenShow(activity, token)
        }
    }

    override fun onActivityResumed(activity: Activity) {
        currentActivity = activity
    }

    private fun isTopActivityAnAd(activity: Activity): Boolean {
        val className = activity.javaClass.name
        return AD_ACTIVITY_CLASS_MARKERS.any { className.contains(it) }
    }

    override fun onActivityStarted(activity: Activity) {
        currentActivity = activity
    }

    override fun onActivityStopped(activity: Activity) {}

    private fun preload() {
        adManager.loadAd(currentActivity ?: application, config)
    }

    private fun loadThenShow(activity: Activity, token: Int) {
        var isSettled = false

        fun settle() {
            if (isSettled) return
            isSettled = true
            mainHandler.removeCallbacks(loadingTimeout)
            loadingOverlay.hide()
        }

        // Past the timeout the overlay goes away and the ad, if it still arrives, just stays cached.
        loadingTimeout = Runnable { settle() }
        val startedAt = System.currentTimeMillis()

        fun onLoadFinished(loaded: Boolean) {
            val wasSettledByTimeout = isSettled
            settle()
            if (!loaded || wasSettledByTimeout) return
            val inTime = System.currentTimeMillis() - startedAt <= reactiveShowTimeoutMs
            val foreground = ProcessLifecycleOwner.get().lifecycle.currentState
                .isAtLeast(Lifecycle.State.STARTED)
            val target = currentActivity
            if (token == returnToken && inTime && foreground && target != null &&
                !target.isFinishing && !target.isDestroyed &&
                !AdsManager.isShowingFullScreenAd && adManager.isAdReady()
            ) {
                showAd(target)
            }
        }

        fun onMain(block: () -> Unit) {
            if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
        }

        adManager.loadAd(activity, config, object : AdCallback {
            override fun onAdLoaded() = onMain { onLoadFinished(true) }
            override fun onAdFailedToLoad(errorCode: Int, errorMessage: String) =
                onMain { onLoadFinished(false) }
        })

        // A load that already resolved synchronously (pool hit / disabled placement) never gets
        // an overlay, so there is no flash.
        if (!isSettled && showLoadingOverlay) {
            loadingOverlay.show(activity)
            mainHandler.postDelayed(loadingTimeout, reactiveShowTimeoutMs)
        }
    }

    private fun showAd(activity: Activity) {
        adManager.showAd(activity, object : AdCallback {
            override fun onAdShowed() {}
            override fun onAdDismissed() {
                preloadArmed = true
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
    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivity === activity) loadingOverlay.hide()
        if (currentActivity === activity) currentActivity = null
    }

    companion object {
        private const val DEFAULT_MIN_BACKGROUND_MS = 0L
        private const val DEFAULT_REACTIVE_SHOW_TIMEOUT_MS = 5_000L
        private val AD_ACTIVITY_CLASS_MARKERS = listOf(
            "com.google.android.gms.ads.AdActivity",
            "com.applovin.adview.AppLovinFullscreenActivity"
        )
    }
}
