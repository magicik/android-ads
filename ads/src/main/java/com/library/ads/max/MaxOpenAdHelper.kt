package com.library.ads.max

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxAdListener
import com.applovin.mediation.MaxError
import com.applovin.mediation.ads.MaxAppOpenAd
import com.library.ads.admob.TAG
import com.library.ads.provider.config.AdRemoteConfigProvider
import com.library.ads.provider.open.OpenAdManager

const val TAG_MAX_OPEN = "MaxAppOpenAdManager"

class MaxOpenAdHelper(
    private val adUnit: String,
    private val context: Context,
    private val remoteConfigProvider: AdRemoteConfigProvider,
    private val subscriptionProvider: () -> Boolean,
): OpenAdManager {
    private var appOpenAd: MaxAppOpenAd? = null
    var isShowingAd: Boolean = false
        private set

    private var listener: OpenAdManager.OnShowAdCompleteListener? = null

    init {
        if (!subscriptionProvider()) {
            loadAd(null, null)
        } else {
            Log.d(TAG_MAX_OPEN, "User subscribed -> skip loading MAX open ad at init.")
        }

    }

    override fun loadAd(activity: Activity?, onComplete: (() -> Unit)?) {
        if (!remoteConfigProvider.isOpenAdEnabled() || subscriptionProvider()) {
            Log.d(TAG, "Remote config disabled app open ad, skip loading.")
            return
        }
        appOpenAd = MaxAppOpenAd(adUnit, context)
        appOpenAd?.setListener(object : MaxAdListener {
            override fun onAdLoaded(ad: MaxAd) {
                Log.d(TAG_MAX_OPEN, "Ad loaded.")
            }

            override fun onAdDisplayFailed(ad: MaxAd, error: MaxError) {
                Log.d(TAG_MAX_OPEN, "Ad display failed: $error")
                isShowingAd = false
                listener?.onShowAdComplete()
                loadAd(null, null)
            }

            override fun onAdHidden(ad: MaxAd) {
                Log.d(TAG_MAX_OPEN, "Ad hidden.")
                isShowingAd = false
                listener?.onShowAdComplete()
                loadAd(null, null)
            }

            override fun onAdDisplayed(ad: MaxAd) {
                Log.d(TAG_MAX_OPEN, "Ad displayed.")
                isShowingAd = true
            }

            override fun onAdLoadFailed(adUnitId: String, error: MaxError) {
                Log.d(TAG_MAX_OPEN, "Ad failed to load: $error")
            }

            override fun onAdClicked(ad: MaxAd) {}
        })

        appOpenAd?.loadAd()
    }

    override fun onSubscriptionChanged(subscribed: Boolean) {
        Handler(Looper.getMainLooper()).post {
            if (subscribed) {
                Log.d(TAG_MAX_OPEN, "User subscribed -> clearing MAX AppOpenAd.")
                try {
                    appOpenAd?.setListener(null)
                } catch (t: Throwable) { /* ignore */ }
                appOpenAd = null
                isShowingAd = false
            } else {
                Log.d(TAG_MAX_OPEN, "User unsubscribed -> loading MAX AppOpenAd.")
                // recreate/load if remote config allows
                loadAd(null, null)
            }
        }
    }

    override fun isAdAvailable(): Boolean {
        return appOpenAd?.isReady == true
    }

    override fun showAdIfAvailable(activity: Activity, listener: OpenAdManager.OnShowAdCompleteListener?) {
        if (!remoteConfigProvider.isOpenAdEnabled() || subscriptionProvider()) {
            Log.d(TAG, "App open ad is disabled via Remote Config.")
            listener?.onShowAdComplete()
            return
        }
        if (isShowingAd || !isAdAvailable()) {
            Log.d(TAG_MAX_OPEN, "Ad not ready or already showing.")
            listener?.onShowAdComplete()
            loadAd(null, null)
            return
        }

        this.listener = listener
        appOpenAd?.showAd()
    }
}