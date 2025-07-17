package com.library.ads.max

import android.app.Activity
import android.content.Context
import android.util.Log
import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxAdListener
import com.applovin.mediation.MaxError
import com.applovin.mediation.ads.MaxAppOpenAd
import com.library.ads.admob.TAG
import com.library.ads.provider.config.AdRemoteConfigProvider

const val TAG_MAX_OPEN = "MaxAppOpenAdManager"

class MaxOpenAdHelper(
    private val adUnit: String,
    private val context: Context,
    private val remoteConfigProvider: AdRemoteConfigProvider,
) {
    private var appOpenAd: MaxAppOpenAd? = null
    var isShowingAd: Boolean = false
        private set

    private var listener: OnShowAdCompleteListener? = null

    init {
        loadAd()
    }

    private fun loadAd() {
        if (!remoteConfigProvider.isOpenAdEnabled()) {
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
                loadAd()
            }

            override fun onAdHidden(ad: MaxAd) {
                Log.d(TAG_MAX_OPEN, "Ad hidden.")
                isShowingAd = false
                listener?.onShowAdComplete()
                loadAd()
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

    fun isAdAvailable(): Boolean {
        return appOpenAd?.isReady == true
    }

    fun showAdIfAvailable(activity: Activity, onShowAdCompleteListener: OnShowAdCompleteListener) {
        if (!remoteConfigProvider.isOpenAdEnabled()) {
            Log.d(TAG, "App open ad is disabled via Remote Config.")
            onShowAdCompleteListener.onShowAdComplete()
            return
        }
        if (isShowingAd || !isAdAvailable()) {
            Log.d(TAG_MAX_OPEN, "Ad not ready or already showing.")
            onShowAdCompleteListener.onShowAdComplete()
            loadAd()
            return
        }

        this.listener = onShowAdCompleteListener
        appOpenAd?.showAd()
    }

    interface OnShowAdCompleteListener {
        fun onShowAdComplete()
    }
}