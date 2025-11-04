package com.library.ads.admob

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.OnUserEarnedRewardListener
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.library.ads.provider.reward.RewardAdManager
import com.library.ads.provider.reward.RewardShowListener
import kotlin.math.pow

private const val TAG_ADMOB_REWARD = "AdMobRewardHelper"

class AdMobRewardHelper(
    private val context: Context,
    private val adUnitId: String?
) : RewardAdManager {

    private val appContext = context.applicationContext
    private var rewardedAd: RewardedAd? = null
    private var isLoading = false
    private val handler = Handler(Looper.getMainLooper())
    private var retryAttempt = 0
    private val maxRetry = 3

    override fun load(context: Context, onComplete: (() -> Unit)?) {
        if (adUnitId.isNullOrEmpty()) {
            onComplete?.invoke()
            return
        }
        if (isLoading || rewardedAd != null) {
            onComplete?.invoke(); return
        }
        isLoading = true
        val request = AdRequest.Builder().build()
        RewardedAd.load(
            appContext,
            adUnitId,
            request,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    Log.d(TAG_ADMOB_REWARD, "AdMob rewarded loaded")
                    rewardedAd = ad
                    isLoading = false
                    retryAttempt = 0
                    onComplete?.invoke()
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.e(TAG_ADMOB_REWARD, "AdMob rewarded failed to load: ${loadAdError.message}")
                    rewardedAd = null
                    isLoading = false
                    retryAttempt++
                    if (retryAttempt < maxRetry) {
                        val delay = (2.0.pow(retryAttempt.toDouble()) * 500).toLong()
                        handler.postDelayed({ load(context, onComplete) }, delay)
                    } else {
                        onComplete?.invoke()
                    }
                }
            }
        )
    }

    override fun isAdReady(): Boolean = rewardedAd != null

    override fun show(activity: Activity, listener: RewardShowListener?) {
        if (rewardedAd == null) {
            listener?.onShowFailed("Ad not ready")
            listener?.onAdClosed()
            load(activity, null)
            return
        }

        // Show loading optionally here (handled by caller or wrapper)
        rewardedAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                Log.d(TAG_ADMOB_REWARD, "AdMob rewarded showed")
            }

            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG_ADMOB_REWARD, "AdMob rewarded dismissed")
                rewardedAd = null
                listener?.onAdClosed()
                // auto reload
                load(activity, null)
            }

            override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                Log.e(TAG_ADMOB_REWARD, "AdMob rewarded failed to show: ${adError.message}")
                rewardedAd = null
                listener?.onShowFailed(adError.message)
                listener?.onAdClosed()
                load(activity, null)
            }
        }

        val onEarned = OnUserEarnedRewardListener { rewardItem: RewardItem ->
            Log.d(TAG_ADMOB_REWARD, "AdMob rewarded earned: ${rewardItem.amount} ${rewardItem.type}")
            listener?.onUserEarnedReward(rewardItem.amount, rewardItem.type)
        }

        // show must be called on UI thread
        activity.runOnUiThread {
            rewardedAd?.show(activity, onEarned)
        }
    }
}
