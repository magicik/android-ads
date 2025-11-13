package com.library.ads.provider.open

import android.app.Activity

interface OpenAdManager {
    fun isAdAvailable(): Boolean
    fun showAdIfAvailable(activity: Activity, listener: OnShowAdCompleteListener?)
    fun loadAd(activity: Activity?, onComplete: (() -> Unit)? = null)
    fun onSubscriptionChanged(subscribed: Boolean)

    interface OnShowAdCompleteListener {
        fun onShowAdComplete()
    }
}