package com.library.ads.provider.interstitial

import android.app.Activity

interface InterstitialAdManager {
    fun isAdReady(): Boolean
    fun loadAd()
    fun showAd(activity: Activity, listener: OnShowAdCompleteListener)
    fun onSubscriptionChanged(subscribed: Boolean)

    interface OnShowAdCompleteListener {
        fun onShowAdComplete()
    }
}