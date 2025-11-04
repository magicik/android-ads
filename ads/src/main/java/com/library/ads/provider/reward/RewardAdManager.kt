// RewardAdManager.kt
package com.library.ads.provider.reward

import android.app.Activity
import android.content.Context

interface RewardAdManager {
    fun load(context: Context, onComplete: (() -> Unit)? = null)
    fun isAdReady(): Boolean
    fun show(activity: Activity, listener: RewardShowListener? = null)
}

interface RewardShowListener {
    /** Called when user earned reward (amount/currency from network) */
    fun onUserEarnedReward(amount: Int, type: String)
    /** Called after the ad is dismissed or could not be shown; continue app flow */
    fun onAdClosed()
    /** Called when show failed */
    fun onShowFailed(error: String?)
}
