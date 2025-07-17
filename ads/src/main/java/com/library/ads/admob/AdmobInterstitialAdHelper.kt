package com.library.ads.admob

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.library.ads.provider.config.AdRemoteConfigProvider
import kotlin.math.pow

const val TAG_INTER = "InterstitialAds"

class AdmobInterstitialAdHelper(
    val context: Context,
    val adRemoteConfigProvider: AdRemoteConfigProvider,
    private val adUnitId: String?,
) {
    private val appContext = context.applicationContext
    private var interstitialAd: InterstitialAd? = null
    private var adIsLoading = false
    private val handler = Handler(Looper.getMainLooper())
    private var retryAttempt = 0
    private val maxRetry = 3

    companion object {
        var isShowingInterstitial: Boolean = false
        private val instances = mutableMapOf<String, AdmobInterstitialAdHelper>()
        fun getInstance(
            context: Context, adRemoteConfigProvider: AdRemoteConfigProvider, adUnitId: String?
        ): AdmobInterstitialAdHelper {
            return instances.getOrPut(adUnitId ?: "") {
                AdmobInterstitialAdHelper(context, adRemoteConfigProvider, adUnitId).apply {
                    loadAd()
                }
            }
        }

    }

    fun loadAd() {
        if (adUnitId == null || adUnitId.isEmpty()) return
        if (!adRemoteConfigProvider.isInterstitialAdEnabled()) {
            Log.d(TAG, "Interstitial loading is disabled by remote config")
            return
        }
        if (adIsLoading) return
        adIsLoading = true
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(appContext, adUnitId, adRequest, object : InterstitialAdLoadCallback() {
            override fun onAdFailedToLoad(adError: LoadAdError) {
                Log.d(TAG_INTER, adError.message)
                interstitialAd = null
                adIsLoading = false
                retryAttempt++

                if (retryAttempt < maxRetry) {
                    val delay = (2.0.pow(retryAttempt.toDouble()) * 500).toLong()
                    handler.postDelayed({ loadAd() }, delay) // exponential backoff
                } else {
                    Log.e(TAG, "Max retry reached. Will not retry further.")
                }
            }

            override fun onAdLoaded(ad: InterstitialAd) {
                Log.d(TAG_INTER, "Ad was loaded.")
                interstitialAd = ad
                adIsLoading = false
                retryAttempt = 0
            }
        })
    }

    fun isAdReady(): Boolean {
        return interstitialAd != null
    }

    private var lastShowTime: Long = 0

    fun show(activity: Activity, listener: OnShowAdCompleteListener? = null) {
        val now = System.currentTimeMillis()
        val interval = adRemoteConfigProvider.getInterstitialAdInterval() * 1000
        val enabled = adRemoteConfigProvider.isInterstitialAdEnabled()
        if (!enabled || now - lastShowTime < interval) {
            Log.d(TAG, "Ad not shown. Enabled: $enabled, interval not reached.")
            listener?.onShowAdComplete()
            return
        }
        if (interstitialAd != null) {
            isShowingInterstitial = true
            interstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.d(TAG_INTER, "Ad was dismissed.")
                    isShowingInterstitial = false
                    interstitialAd = null
                    lastShowTime = System.currentTimeMillis()
                    listener?.onShowAdComplete()
                    loadAd()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    Log.d(TAG_INTER, "Ad failed to show.")
                    isShowingInterstitial = false
                    interstitialAd = null
                    listener?.onShowAdComplete()
                    loadAd()
                }

                override fun onAdShowedFullScreenContent() {
                    Log.d(TAG_INTER, "Ad showed fullscreen content.")
                }
            }
            interstitialAd?.show(activity)
        } else {
            listener?.onShowAdComplete()
            loadAd()
        }
    }

    interface OnShowAdCompleteListener {
        fun onShowAdComplete()
    }
}