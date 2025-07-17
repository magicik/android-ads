package com.library.ads.max

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxAdListener
import com.applovin.mediation.MaxError
import com.applovin.mediation.ads.MaxInterstitialAd
import com.library.ads.provider.config.AdRemoteConfigProvider
import kotlin.math.pow

const val TAG_MAX_INTER = "MaxInterstitialAd"

class MaxInterstitialAdHelper private constructor(
    private val context: Context,
    private val adRemoteConfigProvider: AdRemoteConfigProvider,
    private val adUnitId: String?
) {
    private var interstitialAd: MaxInterstitialAd? = null
    private val handler = Handler(Looper.getMainLooper())
    private var retryAttempt = 0
    private val maxRetry = 3
    private var lastShowTime: Long = 0

    companion object {
        var isShowingInterstitial: Boolean = false
        private val instances = mutableMapOf<String, MaxInterstitialAdHelper>()

        fun getInstance(
            context: Context,
            adRemoteConfigProvider: AdRemoteConfigProvider,
            adUnitId: String?
        ): MaxInterstitialAdHelper {
            return instances.getOrPut(adUnitId ?: "") {
                MaxInterstitialAdHelper(context, adRemoteConfigProvider, adUnitId).apply {
                    loadAd()
                }
            }
        }
    }

    fun loadAd() {
        if (adUnitId == null || adUnitId.isEmpty()) return
        if (!adRemoteConfigProvider.isInterstitialAdEnabled()) {
            Log.d(TAG_MAX_INTER, "Interstitial disabled via remote config.")
            return
        }

        if (interstitialAd == null) {
            interstitialAd = MaxInterstitialAd(adUnitId, context)
            interstitialAd?.setListener(object : MaxAdListener {
                override fun onAdLoaded(ad: MaxAd) {
                    Log.d(TAG_MAX_INTER, "Ad loaded.")
                    retryAttempt = 0
                }

                override fun onAdLoadFailed(adUnitId: String, error: MaxError) {
                    Log.d(TAG_MAX_INTER, "Ad load failed: $error")
                    retryAttempt++
                    val delay = (2.0.pow(retryAttempt.toDouble()) * 500).toLong()
                        .coerceAtMost(5000L)
                    handler.postDelayed({ loadAd() }, delay)
                }

                override fun onAdDisplayFailed(ad: MaxAd, error: MaxError) {
                    Log.d(TAG_MAX_INTER, "Ad display failed.")
                    interstitialAd?.loadAd()
                    isShowingInterstitial = false
                }

                override fun onAdDisplayed(ad: MaxAd) {
                    isShowingInterstitial = true
                    Log.d(TAG_MAX_INTER, "Ad displayed.")
                }

                override fun onAdHidden(ad: MaxAd) {
                    Log.d(TAG_MAX_INTER, "Ad hidden.")
                    isShowingInterstitial = false
                    interstitialAd?.loadAd()
                    lastShowTime = System.currentTimeMillis()
                    showListener?.onShowAdComplete()
                }

                override fun onAdClicked(ad: MaxAd) {}
            })

            interstitialAd?.loadAd()
        }
    }

    private var showListener: OnShowAdCompleteListener? = null

    fun isAdReady(): Boolean = interstitialAd?.isReady == true

    fun show(activity: Activity, listener: OnShowAdCompleteListener? = null) {
        this.showListener = listener

        val now = System.currentTimeMillis()
        val interval = adRemoteConfigProvider.getInterstitialAdInterval() * 1000L
        val enabled = adRemoteConfigProvider.isInterstitialAdEnabled()

        if (!enabled || (now - lastShowTime) < interval) {
            Log.d(TAG_MAX_INTER, "Ad not shown. Enabled: $enabled, interval not met.")
            listener?.onShowAdComplete()
            return
        }

        if (interstitialAd?.isReady == true) {
            interstitialAd?.showAd(activity)
        } else {
            Log.d(TAG_MAX_INTER, "Ad not ready yet.")
            listener?.onShowAdComplete()
            loadAd()
        }
    }

    interface OnShowAdCompleteListener {
        fun onShowAdComplete()
    }
}
