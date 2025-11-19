package com.library.ads.max

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.applovin.sdk.AppLovinSdk
import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxAdListener
import com.applovin.mediation.MaxError
import com.applovin.mediation.ads.MaxAppOpenAd
import com.library.ads.provider.config.AdRemoteConfigProvider
import com.library.ads.provider.open.OpenAdManager
import java.util.concurrent.atomic.AtomicInteger

const val TAG_MAX_OPEN = "MaxAppOpenAdManager"

class MaxOpenAdHelper(
    private val adUnit: String,
    private val context: Context,
    private val remoteConfigProvider: AdRemoteConfigProvider,
    private val subscriptionProvider: () -> Boolean,
) : OpenAdManager {

    private var appOpenAd: MaxAppOpenAd? = null
    @Volatile
    var isShowingAd: Boolean = false
        private set

    // NEW: isLoading guard to prevent concurrent loads
    @Volatile
    private var isLoading: Boolean = false

    private var listener: OpenAdManager.OnShowAdCompleteListener? = null
    private var hasInitialized = false

    private val retryCounter = AtomicInteger(0)
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        if (!subscriptionProvider()) {
            Log.d(TAG_MAX_OPEN, "MaxOpenAdHelper created; waiting for SDK ready to load ad.")
        } else {
            Log.d(TAG_MAX_OPEN, "User subscribed -> skip loading MAX open ad at init.")
        }
    }

    fun initializeAfterSdkReady() {
        if (hasInitialized) return
        hasInitialized = true
        if (!subscriptionProvider()) {
            mainHandler.post { loadAd(null, null) }
        }
    }

    override fun loadAd(activity: Activity?, onComplete: (() -> Unit)?) {
        // ensure main thread
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { loadAd(activity, onComplete) }
            return
        }

        if (!remoteConfigProvider.isOpenAdEnabled() || subscriptionProvider()) {
            Log.d(TAG_MAX_OPEN, "Remote config disabled or subscribed -> skip load.")
            onComplete?.invoke()
            return
        }

        // if ad already available, no need to load
        if (isAdAvailable()) {
            Log.d(TAG_MAX_OPEN, "Ad already available -> skip load.")
            onComplete?.invoke()
            return
        }

        // if already loading, skip and return (avoid concurrent loads)
        if (isLoading) {
            Log.d(TAG_MAX_OPEN, "Load already in progress -> skipping concurrent load.")
            onComplete?.invoke()
            return
        }

        // ensure AppLovin initialized
        val sdk = try { AppLovinSdk.getInstance(context) } catch (t: Throwable) {
            Log.w(TAG_MAX_OPEN, "AppLovin getInstance failed: ${t.message}")
            onComplete?.invoke()
            return
        }

        if (!sdk.isInitialized) {
            Log.d(TAG_MAX_OPEN, "AppLovin SDK not initialized yet -> skip load.")
            onComplete?.invoke()
            return
        }

        // mark loading
        isLoading = true

        // reset previous instance safely
        try { appOpenAd?.setListener(null) } catch (_: Throwable) {}
        appOpenAd = MaxAppOpenAd(adUnit, context)
        appOpenAd?.setListener(object : MaxAdListener {
            override fun onAdLoaded(ad: MaxAd) {
                Log.d(TAG_MAX_OPEN, "Ad loaded.")
                retryCounter.set(0)
                isLoading = false
                onComplete?.invoke()
            }

            override fun onAdDisplayFailed(ad: MaxAd, error: MaxError) {
                Log.d(TAG_MAX_OPEN, "Ad display failed: $error")
                isShowingAd = false
                listener?.onShowAdComplete()
                listener = null
                isLoading = false
                scheduleRetryLoad()
            }

            override fun onAdHidden(ad: MaxAd) {
                Log.d(TAG_MAX_OPEN, "Ad hidden.")
                isShowingAd = false
                listener?.onShowAdComplete()
                listener = null
                // schedule next load (avoid immediate concurrent)
                scheduleImmediateLoad()
            }

            override fun onAdDisplayed(ad: MaxAd) {
                Log.d(TAG_MAX_OPEN, "Ad displayed.")
                isShowingAd = true
            }

            override fun onAdLoadFailed(adUnitId: String, error: MaxError) {
                Log.d(TAG_MAX_OPEN, "Ad failed to load: $error")
                isLoading = false
                scheduleRetryLoad()
                onComplete?.invoke()
            }

            override fun onAdClicked(ad: MaxAd) {}
        })

        try {
            appOpenAd?.loadAd()
        } catch (t: Throwable) {
            Log.w(TAG_MAX_OPEN, "Exception on loadAd(): ${t.message}")
            isLoading = false
            onComplete?.invoke()
        }
    }

    private fun scheduleImmediateLoad() {
        mainHandler.post {
            if (!isLoading && !isAdAvailable()) loadAd(null, null)
        }
    }

    private fun scheduleRetryLoad() {
        val attempt = retryCounter.incrementAndGet()
        val delayMs = (30_000L * attempt).coerceAtMost(5 * 60_000L)
        Log.d(TAG_MAX_OPEN, "Scheduling retry load in ${delayMs}ms (attempt $attempt).")
        mainHandler.postDelayed({
            if (!isLoading && !isAdAvailable()) loadAd(null, null)
        }, delayMs)
    }

    override fun onSubscriptionChanged(subscribed: Boolean) {
        mainHandler.post {
            if (subscribed) {
                Log.d(TAG_MAX_OPEN, "User subscribed -> clearing MAX AppOpenAd.")
                try { appOpenAd?.setListener(null) } catch (_: Throwable) {}
                appOpenAd = null
                isShowingAd = false
                isLoading = false
            } else {
                Log.d(TAG_MAX_OPEN, "User unsubscribed -> loading MAX AppOpenAd.")
                loadAd(null, null)
            }
        }
    }

    override fun isAdAvailable(): Boolean {
        return appOpenAd?.isReady == true
    }

    override fun showAdIfAvailable(activity: Activity, listener: OpenAdManager.OnShowAdCompleteListener?) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { showAdIfAvailable(activity, listener) }
            return
        }

        if (!remoteConfigProvider.isOpenAdEnabled() || subscriptionProvider()) {
            Log.d(TAG_MAX_OPEN, "App open ad disabled via remote or subscription.")
            listener?.onShowAdComplete()
            return
        }

        if (isShowingAd) {
            Log.d(TAG_MAX_OPEN, "Ad already showing.")
            listener?.onShowAdComplete()
            return
        }

        if (!isAdAvailable()) {
            Log.d(TAG_MAX_OPEN, "Ad not ready -> calling complete and triggering load.")
            listener?.onShowAdComplete()
            loadAd(activity, null)
            return
        }

        this.listener = listener
        try {
            appOpenAd?.showAd()
        } catch (t: Throwable) {
            Log.w(TAG_MAX_OPEN, "showAd() threw: ${t.message}")
            this.listener?.onShowAdComplete()
            this.listener = null
            isShowingAd = false
            loadAd(activity, null)
        }
    }

    fun destroy() {
        mainHandler.post {
            try { appOpenAd?.setListener(null) } catch (_: Throwable) {}
            appOpenAd = null
            listener = null
            isShowingAd = false
            isLoading = false
            retryCounter.set(0)
        }
    }
}
