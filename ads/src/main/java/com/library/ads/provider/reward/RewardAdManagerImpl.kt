// UnifiedRewardManager.kt
package com.library.ads.provider.reward

import android.app.Activity
import android.content.Context
import com.library.ads.admob.AdMobRewardHelper
import com.library.ads.max.MaxRewardHelper
import com.library.ads.provider.config.AdRemoteConfigProvider

class RewardAdManagerImpl(
    private val context: Context,
    private val admobUnit: String?,
    private val maxUnit: String?,
    private val remoteConfig: AdRemoteConfigProvider
) : RewardAdManager {

    private val impl: RewardAdManager by lazy {
        when (remoteConfig.getAdProvider()) {
            "admob" -> AdMobRewardHelper(context, admobUnit)
            "max" -> MaxRewardHelper(context, maxUnit)
            else -> AdMobRewardHelper(context, admobUnit)
        }
    }

    override fun load(context: Context, onComplete: (() -> Unit)?) = impl.load(context, onComplete)
    override fun isAdReady(): Boolean = impl.isAdReady()
    override fun show(activity: Activity, listener: RewardShowListener?) = impl.show(activity, listener)
}
