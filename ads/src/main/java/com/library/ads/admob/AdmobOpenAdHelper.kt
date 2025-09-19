package com.library.ads.admob

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import com.library.ads.provider.config.AdRemoteConfigProvider
import java.util.Date

const val TAG: String = "AppOpenAdManager"

class AdmobOpenAdHelper(
    private val adUnit: String,
    val remoteConfigProvider: AdRemoteConfigProvider,
) {

    private var appOpenAd: AppOpenAd? = null
    private var isLoadingAd = false
    var isShowingAd = false
    private var loadTime: Long = 0

    fun fetchAd(context: Context, onLoadAdComplete: (() -> Unit)?) {
        // Do not load ad if there is an unused ad or one is already loading.
        if (!remoteConfigProvider.isOpenAdEnabled()) {
            Log.d(TAG, "Remote config disabled app open ad, skip loading.")
            onLoadAdComplete?.invoke()
            return
        }
        if (isLoadingAd || isAdAvailable()) {
            onLoadAdComplete?.invoke()
            return
        }
        isLoadingAd = true
        val request = AdRequest.Builder().build()
        AppOpenAd.load(context, adUnit, request, object : AppOpenAd.AppOpenAdLoadCallback() {
            override fun onAdLoaded(ad: AppOpenAd) {
                appOpenAd = ad
                isLoadingAd = false
                loadTime = Date().time
                onLoadAdComplete?.invoke()
                Log.d(TAG, "onAdLoaded.")
            }

            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                isLoadingAd = false
                onLoadAdComplete?.invoke()
                Log.d(TAG, "onAdFailedToLoad: " + loadAdError.message)
            }
        })
    }

    /** Check if ad was loaded more than n hours ago. */
    private fun wasLoadTimeLessThanNHoursAgo(numHours: Long = 4): Boolean {
        val dateDifference: Long = Date().time - loadTime
        val numMilliSecondsPerHour: Long = 3600000
        return dateDifference < numMilliSecondsPerHour * numHours
    }

    /** Check if ad exists and can be shown. */
    fun isAdAvailable(): Boolean {
        return appOpenAd != null && wasLoadTimeLessThanNHoursAgo(4)
    }

    fun showAdIfAvailable(activity: Activity) {
        showAdIfAvailable(activity, object : OnShowAdCompleteListener {
            override fun onShowAdComplete() {
                // Empty because the user will go back to the activity that shows the ad.
            }
        })
    }

    fun showAdIfAvailable(activity: Activity, onShowAdCompleteListener: OnShowAdCompleteListener) {
        if (!remoteConfigProvider.isOpenAdEnabled()) {
            Log.d(TAG, "App open ad is disabled via Remote Config.")
            onShowAdCompleteListener.onShowAdComplete()
            return
        }
        // If the app open ad is already showing, do not show the ad again.
        if (isShowingAd || AdmobInterstitialAdHelper.isShowingInterstitial) {
            Log.d(TAG, "The app open ad is already showing.")
            onShowAdCompleteListener.onShowAdComplete()
            return
        }

        // If the app open ad is not available yet, invoke the callback then load the ad.
        if (!isAdAvailable()) {
            Log.d(TAG, "The app open ad is not ready yet.")
            onShowAdCompleteListener.onShowAdComplete()
            fetchAd(activity, null)
            return
        }

        Log.d(TAG, "Will show ad.")

        appOpenAd!!.fullScreenContentCallback = object : FullScreenContentCallback() {
            /** Called when full screen content is dismissed. */
            override fun onAdDismissedFullScreenContent() {
                // Set the reference to null so isAdAvailable() returns false.
                appOpenAd = null
                isShowingAd = false
                Log.d(TAG, "onAdDismissedFullScreenContent.")

                onShowAdCompleteListener.onShowAdComplete()
                fetchAd(activity, null)
            }

            /** Called when fullscreen content failed to show. */
            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                appOpenAd = null
                isShowingAd = false
                Log.d(TAG, "onAdFailedToShowFullScreenContent: " + adError.message)
                onShowAdCompleteListener.onShowAdComplete()
                fetchAd(activity, null)
            }

            /** Called when fullscreen content is shown. */
            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "onAdShowedFullScreenContent.")
            }
        }
        isShowingAd = true
        appOpenAd!!.show(activity)
    }

    interface OnShowAdCompleteListener {
        fun onShowAdComplete()
    }
}