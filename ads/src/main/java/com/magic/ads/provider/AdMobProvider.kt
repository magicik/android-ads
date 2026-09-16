package com.magic.ads.provider

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.magic.ads.listener.AdCallback
import com.magic.ads.listener.RewardCallback
import com.magic.ads.testguard.TestAdGuard

/** [AdSdkProvider] backed by Google Mobile Ads (AdMob). */
class AdMobProvider : AdSdkProvider {

    override val name = "admob"

    override fun initialize(context: Context, testDevices: List<String>, sdkKey: String, onReady: () -> Unit) {
        if (testDevices.isNotEmpty()) {
            MobileAds.setRequestConfiguration(
                RequestConfiguration.Builder().setTestDeviceIds(testDevices).build()
            )
        }
        MobileAds.initialize(context) { onReady() }
    }

    // ── Interstitial ──────────────────────────────────────────────────────────

    override fun loadInterstitial(
        context: Context, adUnitId: String, timeoutMs: Long,
        onSuccess: (Any) -> Unit, onFail: () -> Unit
    ) {
        val handler = Handler(Looper.getMainLooper())
        var reportedToCaller = false
        val timeout = Runnable {
            if (!reportedToCaller) { reportedToCaller = true; onFail() }
        }
        handler.postDelayed(timeout, timeoutMs)

        InterstitialAd.load(context, adUnitId, AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    handler.removeCallbacks(timeout)
                    ad.setOnPaidEventListener { TestAdGuard.onPaidEvent(context, it.valueMicros) }
                    // Always deliver — even a late fill arriving after the timeout already
                    // reported onFail() is still cached by the format manager's generation
                    // guard for the next showAd(), instead of being discarded.
                    onSuccess(ad)
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    handler.removeCallbacks(timeout)
                    if (!reportedToCaller) { reportedToCaller = true; onFail() }
                }
            })
    }

    override fun showInterstitial(activity: Activity, ad: Any, callback: AdCallback?) {
        val interstitial = ad as? InterstitialAd ?: run {
            callback?.onAdFailedToLoad(-1, "Invalid ad object"); return
        }
        interstitial.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() = callback?.onAdShowed() ?: Unit
            override fun onAdDismissedFullScreenContent() = callback?.onAdDismissed() ?: Unit
            override fun onAdFailedToShowFullScreenContent(error: AdError) =
                callback?.onAdFailedToLoad(error.code, error.message) ?: Unit
            override fun onAdClicked() = callback?.onAdClicked() ?: Unit
            override fun onAdImpression() = callback?.onAdImpression() ?: Unit
        }
        interstitial.show(activity)
    }

    // ── Rewarded ──────────────────────────────────────────────────────────────

    override fun loadRewarded(
        context: Context, adUnitId: String, timeoutMs: Long,
        onSuccess: (Any) -> Unit, onFail: () -> Unit
    ) {
        val handler = Handler(Looper.getMainLooper())
        var done = false
        val timeout = Runnable { if (!done) { done = true; onFail() } }
        handler.postDelayed(timeout, timeoutMs)

        RewardedAd.load(context, adUnitId, AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    if (!done) {
                        done = true
                        handler.removeCallbacks(timeout)
                        ad.setOnPaidEventListener { TestAdGuard.onPaidEvent(context, it.valueMicros) }
                        onSuccess(ad)
                    }
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    if (!done) { done = true; handler.removeCallbacks(timeout); onFail() }
                }
            })
    }

    override fun showRewarded(activity: Activity, ad: Any, callback: RewardCallback) {
        val rewarded = ad as? RewardedAd ?: run {
            callback.onAdFailedToLoad(-1, "Invalid ad object"); return
        }
        rewarded.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() = callback.onAdShowed()
            override fun onAdDismissedFullScreenContent() = callback.onAdDismissed()
            override fun onAdFailedToShowFullScreenContent(error: AdError) =
                callback.onAdFailedToLoad(error.code, error.message)
            override fun onAdClicked() = callback.onAdClicked()
            override fun onAdImpression() = callback.onAdImpression()
        }
        rewarded.show(activity) { reward -> callback.onUserEarnedReward(reward.type, reward.amount) }
    }

    // ── Banner ────────────────────────────────────────────────────────────────

    override fun loadBanner(
        activity: Activity, container: ViewGroup, adUnitId: String, adaptive: Boolean,
        adCallback: AdCallback?, onSuccess: (Any) -> Unit, onFail: () -> Unit
    ) {
        val adView = AdView(activity)
        adView.adUnitId = adUnitId
        adView.setAdSize(resolveAdSize(activity, adaptive))
        adView.setOnPaidEventListener { TestAdGuard.onPaidEvent(activity, it.valueMicros) }
        adView.adListener = object : AdListener() {
            override fun onAdLoaded() {
                container.removeAllViews()
                container.addView(adView)
                container.visibility = View.VISIBLE
                onSuccess(adView)
            }
            override fun onAdFailedToLoad(error: LoadAdError) { adView.destroy(); onFail() }
            override fun onAdClicked() = adCallback?.onAdClicked() ?: Unit
            override fun onAdImpression() = adCallback?.onAdImpression() ?: Unit
            override fun onAdOpened() = adCallback?.onAdShowed() ?: Unit
            override fun onAdClosed() = adCallback?.onAdDismissed() ?: Unit
        }
        adView.loadAd(AdRequest.Builder().build())
    }

    override fun destroyBannerAd(adView: Any?) {
        (adView as? AdView)?.destroy()
    }

    // ── App Open ──────────────────────────────────────────────────────────────

    override fun loadAppOpen(
        context: Context, adUnitId: String, timeoutMs: Long,
        onSuccess: (Any) -> Unit, onFail: () -> Unit
    ) {
        AppOpenAd.load(context, adUnitId, AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    ad.setOnPaidEventListener { TestAdGuard.onPaidEvent(context, it.valueMicros) }
                    onSuccess(ad)
                }
                override fun onAdFailedToLoad(error: LoadAdError) = onFail()
            })
    }

    override fun showAppOpen(activity: Activity, ad: Any, callback: AdCallback?) {
        val appOpen = ad as? AppOpenAd ?: run {
            callback?.onAdFailedToLoad(-1, "Invalid ad object"); return
        }
        appOpen.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() = callback?.onAdShowed() ?: Unit
            override fun onAdDismissedFullScreenContent() = callback?.onAdDismissed() ?: Unit
            override fun onAdFailedToShowFullScreenContent(error: AdError) =
                callback?.onAdFailedToLoad(error.code, error.message) ?: Unit
            override fun onAdClicked() = callback?.onAdClicked() ?: Unit
            override fun onAdImpression() = callback?.onAdImpression() ?: Unit
        }
        appOpen.show(activity)
    }

    private fun resolveAdSize(activity: Activity, adaptive: Boolean): AdSize {
        if (!adaptive) return AdSize.BANNER
        val dm = activity.resources.displayMetrics
        val adWidth = (dm.widthPixels / dm.density).toInt()
        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, adWidth)
    }
}
