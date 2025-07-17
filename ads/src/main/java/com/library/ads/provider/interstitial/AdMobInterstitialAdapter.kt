package com.library.ads.provider.interstitial

import android.app.Activity
import com.library.ads.admob.AdmobInterstitialAdHelper

class AdMobInterstitialAdapter(
    private val admobManager: AdmobInterstitialAdHelper
) : InterstitialAdManager {

    override fun isAdReady(): Boolean = admobManager.isAdReady()
    override fun loadAd() {
        admobManager.loadAd()
    }

    override fun showAd(
        activity: Activity,
        listener: InterstitialAdManager.OnShowAdCompleteListener
    ) {
        admobManager.show(activity, object : AdmobInterstitialAdHelper.OnShowAdCompleteListener {
            override fun onShowAdComplete() {
                listener.onShowAdComplete()
            }
        })
    }
}