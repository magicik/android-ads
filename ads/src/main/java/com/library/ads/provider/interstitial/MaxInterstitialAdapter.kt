package com.library.ads.provider.interstitial

import android.app.Activity
import com.library.ads.max.MaxInterstitialAdHelper

class MaxInterstitialAdapter(
    private val maxManager: MaxInterstitialAdHelper
) : InterstitialAdManager {

    override fun isAdReady(): Boolean = maxManager.isAdReady()
    override fun loadAd() {
        maxManager.loadAd()
    }

    override fun showAd(activity: Activity, listener: InterstitialAdManager.OnShowAdCompleteListener) {
        maxManager.show(activity, object : MaxInterstitialAdHelper.OnShowAdCompleteListener {
            override fun onShowAdComplete() {
                listener.onShowAdComplete()
            }
        })
    }
}