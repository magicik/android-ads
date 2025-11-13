package com.library.ads.admob

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import com.library.ads.provider.config.AdRemoteConfigProvider
import com.library.ads.provider.open.OpenAdManager
import java.util.Date

const val TAG: String = "AppOpenAdManager"

class AdmobOpenAdHelper(
    private val adUnit: String,
    val remoteConfigProvider: AdRemoteConfigProvider,
    private var subscriptionProvider: () -> Boolean,
) : OpenAdManager {

    private var appOpenAd: AppOpenAd? = null
    private var isLoadingAd = false
    var isShowingAd = false
    private var loadTime: Long = 0

    override fun loadAd(activity: Activity?, onComplete: (() -> Unit)?) {
        // Do not load ad if there is an unused ad or one is already loading.
        if (activity == null || !remoteConfigProvider.isOpenAdEnabled() || subscriptionProvider()) {
            Log.d(TAG, "Remote config disabled app open ad, skip loading.")
            onComplete?.invoke()
            return
        }
        if (isLoadingAd || isAdAvailable()) {
            onComplete?.invoke()
            return
        }
        isLoadingAd = true
        val request = AdRequest.Builder().build()
        AppOpenAd.load(activity, adUnit, request, object : AppOpenAd.AppOpenAdLoadCallback() {
            override fun onAdLoaded(ad: AppOpenAd) {
                appOpenAd = ad
                isLoadingAd = false
                loadTime = Date().time
                onComplete?.invoke()
                Log.d(TAG, "onAdLoaded.")
            }

            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                isLoadingAd = false
                onComplete?.invoke()
                Log.d(TAG, "onAdFailedToLoad: " + loadAdError.message)
            }
        })
    }

    override fun onSubscriptionChanged(subscribed: Boolean) {
        // Ensure runs on main thread because we may modify SDK objects / UI state
        val main = Handler(Looper.getMainLooper())
        main.post {
            subscriptionProvider = { subscribed }
            if (subscribed) {
                Log.d(TAG, "User subscribed -> clearing Admob AppOpenAd.")
                // Prevent further loads by checking subscriptionProvider in loadAd/show
                // Remove references and callbacks to avoid showing later / leaking
                try {
                    // There's no explicit destroy for AppOpenAd; remove strong refs and callback
                    appOpenAd?.fullScreenContentCallback = null
                } catch (_: Throwable) {
                    // ignore
                }
                appOpenAd = null
                isLoadingAd = false
                isShowingAd = false
                loadTime = 0
            } else {
                // If unsubscribed, optionally reload ad
                // Note: caller may want to call loadAd explicitly; we keep optional auto-load
                Log.d(TAG, "User unsubscribed -> optionally reload AppOpenAd.")
                // no-op here or call loadAd if you prefer:
                // loadAd(contextAsActivityIfAvailable, null)
            }
        }
    }

    /** Check if ad was loaded more than n hours ago. */
    private fun wasLoadTimeLessThanNHoursAgo(numHours: Long = 4): Boolean {
        val dateDifference: Long = Date().time - loadTime
        val numMilliSecondsPerHour: Long = 3600000
        return dateDifference < numMilliSecondsPerHour * numHours
    }

    /** Check if ad exists and can be shown. */
    override fun isAdAvailable(): Boolean {
        return appOpenAd != null && wasLoadTimeLessThanNHoursAgo(4)
    }

    fun showAdIfAvailable(activity: Activity) {
        showAdIfAvailable(activity, object : OpenAdManager.OnShowAdCompleteListener {
            override fun onShowAdComplete() {
                // Empty because the user will go back to the activity that shows the ad.
            }
        })
    }

    override fun showAdIfAvailable(
        activity: Activity,
        listener: OpenAdManager.OnShowAdCompleteListener?
    ) {
        if (!remoteConfigProvider.isOpenAdEnabled() || subscriptionProvider()) {
            Log.d(TAG, "App open ad is disabled via Remote Config || isSubscribed")
            listener?.onShowAdComplete()
            return
        }
        // If the app open ad is already showing, do not show the ad again.
        if (isShowingAd || AdmobInterstitialAdHelper.isShowingInterstitial) {
            Log.d(TAG, "The app open ad is already showing.")
            listener?.onShowAdComplete()
            return
        }

        // If the app open ad is not available yet, invoke the callback then load the ad.
        if (!isAdAvailable()) {
            Log.d(TAG, "The app open ad is not ready yet.")
            listener?.onShowAdComplete()
            loadAd(activity, null)
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

                listener?.onShowAdComplete()
                loadAd(activity, null)
            }

            /** Called when fullscreen content failed to show. */
            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                appOpenAd = null
                isShowingAd = false
                Log.d(TAG, "onAdFailedToShowFullScreenContent: " + adError.message)
                listener?.onShowAdComplete()
                loadAd(activity, null)
            }

            /** Called when fullscreen content is shown. */
            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "onAdShowedFullScreenContent.")
            }
        }
        isShowingAd = true
        appOpenAd!!.show(activity)
    }
}