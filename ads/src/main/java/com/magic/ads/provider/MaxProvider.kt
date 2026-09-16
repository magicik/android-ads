package com.magic.ads.provider

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxAdFormat
import com.applovin.mediation.MaxAdListener
import com.applovin.mediation.MaxAdViewAdListener
import com.applovin.mediation.MaxError
import com.applovin.mediation.MaxReward
import com.applovin.mediation.MaxRewardedAdListener
import com.applovin.mediation.ads.MaxAdView
import com.applovin.mediation.ads.MaxAppOpenAd
import com.applovin.mediation.ads.MaxInterstitialAd
import com.applovin.mediation.ads.MaxRewardedAd
import com.applovin.sdk.AppLovinMediationProvider
import com.applovin.sdk.AppLovinSdk
import com.applovin.sdk.AppLovinSdkInitializationConfiguration
import com.applovin.sdk.AppLovinSdkUtils
import com.magic.ads.listener.AdCallback
import com.magic.ads.listener.RewardCallback
import com.magic.ads.testguard.TestAdGuard

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

private fun TestAdGuard.reportRevenue(context: Context, ad: MaxAd) {
    onPaidEvent(context, (ad.revenue * 1_000_000.0).toLong())
}

/**
 * [AdSdkProvider] backed by AppLovin MAX.
 *
 * MAX's interstitial/rewarded/app-open ad objects are constructed with an [Activity] (unlike
 * AdMob's static `load(Context, ...)`), so `load*` here needs to resolve one out of whatever
 * [Context] it's handed.
 */
class MaxProvider : AdSdkProvider {

    override val name = "max"

    // Keep the show-time callback alive across the load/show split — MaxAdListener only lets
    // us register one listener per ad object, fixed at construction time.
    private class InterstitialHolder(val maxAd: MaxInterstitialAd) {
        var showCallback: AdCallback? = null
    }

    private class RewardedHolder(val maxAd: MaxRewardedAd) {
        var showCallback: RewardCallback? = null
    }

    private class AppOpenHolder(val maxAd: MaxAppOpenAd) {
        var showCallback: AdCallback? = null
    }

    /** Used only when [loadAppOpen] had no Activity to construct a real MaxAppOpenAd with —
     * load and show then happen together in [showAppOpen], which always gets one. */
    private class AppOpenPlaceholder(val adUnitId: String)

    override fun initialize(context: Context, testDevices: List<String>, sdkKey: String, onReady: () -> Unit) {
        val sdk = AppLovinSdk.getInstance(context)
        val builder = AppLovinSdkInitializationConfiguration.builder(sdkKey, context)
            .setMediationProvider(AppLovinMediationProvider.MAX)
        if (testDevices.isNotEmpty()) {
            builder.setTestDeviceAdvertisingIds(testDevices)
        }
        sdk.initialize(builder.build()) { onReady() }
    }

    // ── Interstitial ──────────────────────────────────────────────────────────

    override fun loadInterstitial(
        context: Context, adUnitId: String, timeoutMs: Long,
        onSuccess: (Any) -> Unit, onFail: () -> Unit
    ) {
        val activity = context.findActivity() ?: run { onFail(); return }

        val handler = Handler(Looper.getMainLooper())
        var reportedToCaller = false
        val timeout = Runnable { if (!reportedToCaller) { reportedToCaller = true; onFail() } }
        handler.postDelayed(timeout, timeoutMs)

        val holderRef = arrayOfNulls<InterstitialHolder>(1)
        val maxAd = MaxInterstitialAd(adUnitId, activity)
        maxAd.setListener(object : MaxAdListener {
            override fun onAdLoaded(ad: MaxAd) {
                handler.removeCallbacks(timeout)
                val holder = InterstitialHolder(maxAd)
                holderRef[0] = holder
                onSuccess(holder)
            }
            override fun onAdLoadFailed(adUnitId: String, error: MaxError) {
                handler.removeCallbacks(timeout)
                if (!reportedToCaller) { reportedToCaller = true; onFail() }
            }
            override fun onAdDisplayed(ad: MaxAd) {
                holderRef[0]?.showCallback?.onAdShowed()
            }
            override fun onAdHidden(ad: MaxAd) {
                holderRef[0]?.showCallback?.onAdDismissed()
                holderRef[0]?.showCallback = null
            }
            override fun onAdClicked(ad: MaxAd) {
                holderRef[0]?.showCallback?.onAdClicked()
            }
            override fun onAdDisplayFailed(ad: MaxAd, error: MaxError) {
                holderRef[0]?.showCallback?.onAdFailedToLoad(error.code, error.message)
                holderRef[0]?.showCallback = null
            }
        })
        maxAd.setRevenueListener { TestAdGuard.reportRevenue(activity, it) }
        maxAd.loadAd()
    }

    override fun showInterstitial(activity: Activity, ad: Any, callback: AdCallback?) {
        val holder = ad as? InterstitialHolder ?: run {
            callback?.onAdFailedToLoad(-1, "Invalid MAX ad object"); return
        }
        holder.showCallback = callback
        holder.maxAd.showAd(activity)
    }

    // ── Rewarded ──────────────────────────────────────────────────────────────

    override fun loadRewarded(
        context: Context, adUnitId: String, timeoutMs: Long,
        onSuccess: (Any) -> Unit, onFail: () -> Unit
    ) {
        val activity = context.findActivity() ?: run { onFail(); return }

        val handler = Handler(Looper.getMainLooper())
        var done = false
        val timeout = Runnable { if (!done) { done = true; onFail() } }
        handler.postDelayed(timeout, timeoutMs)

        val holderRef = arrayOfNulls<RewardedHolder>(1)
        val maxAd = MaxRewardedAd.getInstance(adUnitId, activity)
        maxAd.setListener(object : MaxRewardedAdListener {
            override fun onAdLoaded(ad: MaxAd) {
                if (!done) {
                    done = true
                    handler.removeCallbacks(timeout)
                    val holder = RewardedHolder(maxAd)
                    holderRef[0] = holder
                    onSuccess(holder)
                }
            }
            override fun onAdLoadFailed(adUnitId: String, error: MaxError) {
                if (!done) { done = true; handler.removeCallbacks(timeout); onFail() }
            }
            override fun onAdDisplayed(ad: MaxAd) = holderRef[0]?.showCallback?.onAdShowed() ?: Unit
            override fun onAdHidden(ad: MaxAd) {
                holderRef[0]?.showCallback?.onAdDismissed()
                holderRef[0]?.showCallback = null
            }
            override fun onAdClicked(ad: MaxAd) = holderRef[0]?.showCallback?.onAdClicked() ?: Unit
            override fun onAdDisplayFailed(ad: MaxAd, error: MaxError) {
                holderRef[0]?.showCallback?.onAdFailedToLoad(error.code, error.message)
                holderRef[0]?.showCallback = null
            }
            override fun onUserRewarded(ad: MaxAd, reward: MaxReward) {
                holderRef[0]?.showCallback?.onUserEarnedReward(reward.label, reward.amount)
            }
        })
        maxAd.setRevenueListener { TestAdGuard.reportRevenue(activity, it) }
        maxAd.loadAd()
    }

    override fun showRewarded(activity: Activity, ad: Any, callback: RewardCallback) {
        val holder = ad as? RewardedHolder ?: run {
            callback.onAdFailedToLoad(-1, "Invalid MAX ad object"); return
        }
        holder.showCallback = callback
        holder.maxAd.showAd(activity)
    }

    // ── Banner ────────────────────────────────────────────────────────────────

    override fun loadBanner(
        activity: Activity, container: ViewGroup, adUnitId: String, adaptive: Boolean,
        adCallback: AdCallback?, onSuccess: (Any) -> Unit, onFail: () -> Unit
    ) {
        val adView = MaxAdView(adUnitId, activity)
        val heightPx = AppLovinSdkUtils.dpToPx(activity, MaxAdFormat.BANNER.size.height)
        adView.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, heightPx)
        adView.setListener(object : MaxAdViewAdListener {
            override fun onAdLoaded(ad: MaxAd) {
                container.removeAllViews()
                container.addView(adView)
                container.visibility = View.VISIBLE
                onSuccess(adView)
            }
            override fun onAdLoadFailed(adUnitId: String, error: MaxError) {
                adView.destroy(); onFail()
            }
            override fun onAdClicked(ad: MaxAd) = adCallback?.onAdClicked() ?: Unit
            override fun onAdExpanded(ad: MaxAd) {}
            override fun onAdCollapsed(ad: MaxAd) {}
            override fun onAdDisplayed(ad: MaxAd) = adCallback?.onAdShowed() ?: Unit
            override fun onAdHidden(ad: MaxAd) = adCallback?.onAdDismissed() ?: Unit
            override fun onAdDisplayFailed(ad: MaxAd, error: MaxError) {}
        })
        adView.setRevenueListener { TestAdGuard.reportRevenue(activity, it) }
        adView.loadAd()
    }

    override fun destroyBannerAd(adView: Any?) {
        (adView as? MaxAdView)?.destroy()
    }

    // ── App Open ──────────────────────────────────────────────────────────────

    override fun loadAppOpen(
        context: Context, adUnitId: String, timeoutMs: Long,
        onSuccess: (Any) -> Unit, onFail: () -> Unit
    ) {
        val activity = context.findActivity() ?: run {
            // No Activity yet (e.g. called with an Application context) — defer the real
            // load to showAppOpen(), which always receives one.
            onSuccess(AppOpenPlaceholder(adUnitId))
            return
        }

        val handler = Handler(Looper.getMainLooper())
        var reportedToCaller = false
        val timeout = Runnable { if (!reportedToCaller) { reportedToCaller = true; onFail() } }
        handler.postDelayed(timeout, timeoutMs)

        val holderRef = arrayOfNulls<AppOpenHolder>(1)
        val maxAd = MaxAppOpenAd(adUnitId, activity)
        maxAd.setListener(object : MaxAdListener {
            override fun onAdLoaded(ad: MaxAd) {
                handler.removeCallbacks(timeout)
                val holder = AppOpenHolder(maxAd)
                holderRef[0] = holder
                onSuccess(holder)
            }
            override fun onAdLoadFailed(adUnitId: String, error: MaxError) {
                handler.removeCallbacks(timeout)
                if (!reportedToCaller) { reportedToCaller = true; onFail() }
            }
            override fun onAdDisplayed(ad: MaxAd) = holderRef[0]?.showCallback?.onAdShowed() ?: Unit
            override fun onAdHidden(ad: MaxAd) {
                holderRef[0]?.showCallback?.onAdDismissed()
                holderRef[0]?.showCallback = null
            }
            override fun onAdClicked(ad: MaxAd) = holderRef[0]?.showCallback?.onAdClicked() ?: Unit
            override fun onAdDisplayFailed(ad: MaxAd, error: MaxError) {
                holderRef[0]?.showCallback?.onAdFailedToLoad(error.code, error.message)
                holderRef[0]?.showCallback = null
            }
        })
        maxAd.setRevenueListener { TestAdGuard.reportRevenue(activity, it) }
        maxAd.loadAd()
    }

    override fun showAppOpen(activity: Activity, ad: Any, callback: AdCallback?) {
        when (ad) {
            is AppOpenHolder -> {
                ad.showCallback = callback
                ad.maxAd.showAd("")
            }
            is AppOpenPlaceholder -> {
                val maxAd = MaxAppOpenAd(ad.adUnitId, activity)
                maxAd.setListener(object : MaxAdListener {
                    override fun onAdLoaded(loadedAd: MaxAd) { maxAd.showAd("") }
                    override fun onAdLoadFailed(adUnitId: String, error: MaxError) {
                        callback?.onAdFailedToLoad(error.code, error.message)
                    }
                    override fun onAdDisplayed(loadedAd: MaxAd) = callback?.onAdShowed() ?: Unit
                    override fun onAdHidden(loadedAd: MaxAd) = callback?.onAdDismissed() ?: Unit
                    override fun onAdClicked(loadedAd: MaxAd) = callback?.onAdClicked() ?: Unit
                    override fun onAdDisplayFailed(loadedAd: MaxAd, error: MaxError) {
                        callback?.onAdFailedToLoad(error.code, error.message)
                    }
                })
                maxAd.setRevenueListener { TestAdGuard.reportRevenue(activity, it) }
                maxAd.loadAd()
            }
            else -> callback?.onAdFailedToLoad(-1, "Invalid MAX ad object")
        }
    }
}
