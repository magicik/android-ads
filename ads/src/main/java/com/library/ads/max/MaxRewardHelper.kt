// MaxRewardHelper.kt
package com.library.ads.max

import android.app.Activity
import android.content.Context
import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxError
import com.applovin.mediation.MaxRewardedAdListener
import com.applovin.mediation.ads.MaxRewardedAd
import com.library.ads.provider.reward.RewardAdManager
import com.library.ads.provider.reward.RewardShowListener

class MaxRewardHelper(
    private val context: Context,
    private val adUnitId: String?
) : RewardAdManager {

    private var rewardedAd: MaxRewardedAd? = null
    private var isLoading = false

    init {
        if (!adUnitId.isNullOrEmpty()) {
            rewardedAd = MaxRewardedAd.getInstance(adUnitId, context)
        }
    }

    override fun load(context: Context, onComplete: (() -> Unit)?) {
        if (adUnitId.isNullOrEmpty()) {
            onComplete?.invoke(); return
        }
        if (isLoading) { onComplete?.invoke(); return }
        isLoading = true

        rewardedAd = MaxRewardedAd.getInstance(adUnitId, context)
        rewardedAd?.setListener(object : MaxRewardedAdListener {
            override fun onAdLoaded(ad: MaxAd) {
                isLoading = false
                onComplete?.invoke()
            }
            override fun onAdLoadFailed(adUnitId: String, error: MaxError) {
                isLoading = false
                onComplete?.invoke()
            }
            override fun onAdDisplayed(ad: MaxAd) {}
            override fun onAdHidden(ad: MaxAd) { }
            override fun onAdClicked(ad: MaxAd) {}
            override fun onAdDisplayFailed(ad: MaxAd, error: MaxError) {
                isLoading = false
                onComplete?.invoke()
            }
            override fun onUserRewarded(ad: MaxAd, reward: com.applovin.mediation.MaxReward) {
                // handled in show callback below
            }
        })

        rewardedAd?.loadAd()
    }

    override fun isAdReady(): Boolean {
        return rewardedAd?.isReady == true
    }

    override fun show(activity: Activity, listener: RewardShowListener?) {
        if (adUnitId.isNullOrEmpty()) {
            listener?.onShowFailed("No ad unit")
            listener?.onAdClosed()
            return
        }
        rewardedAd = MaxRewardedAd.getInstance(adUnitId, activity)
        rewardedAd?.setListener(object : MaxRewardedAdListener {
            override fun onAdLoaded(ad: MaxAd) { /* nothing */ }
            override fun onAdLoadFailed(adUnitId: String, error: MaxError) {
                listener?.onShowFailed(error.message)
                listener?.onAdClosed()
            }
            override fun onAdDisplayed(ad: MaxAd) {
                // hide loading if used
            }
            override fun onAdHidden(ad: MaxAd) {
                listener?.onAdClosed()
                // reload
                load(activity, null)
            }
            override fun onAdClicked(ad: MaxAd) {}

            override fun onAdDisplayFailed(ad: MaxAd, error: MaxError) {
                listener?.onShowFailed(error.message)
                listener?.onAdClosed()
                load(activity, null)
            }
            override fun onUserRewarded(ad: MaxAd, reward: com.applovin.mediation.MaxReward) {
                listener?.onUserEarnedReward(reward.amount.toInt(), reward.label)
            }
        })

        if (rewardedAd?.isReady == true) {
            activity.runOnUiThread { rewardedAd?.showAd(activity) }
        } else {
            listener?.onShowFailed("Not ready")
            listener?.onAdClosed()
            load(activity, null)
        }
    }
}
