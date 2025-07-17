package com.library.ads.provider.open

import android.app.Activity

interface OpenAdManager {
    fun isAdAvailable(): Boolean
    fun showAdIfAvailable(activity: Activity, listener: OnShowAdCompleteListener?)
    fun loadAd(activity: Activity? = null, onComplete: (() -> Unit)? = null)

    interface OnShowAdCompleteListener {
        fun onShowAdComplete()
    }
}

class EmptyAppOpenAdManager : OpenAdManager {

    override fun isAdAvailable(): Boolean = false
    override fun showAdIfAvailable(
        activity: Activity,
        listener: OpenAdManager.OnShowAdCompleteListener?
    ) {
        listener?.onShowAdComplete()
    }

    override fun loadAd(activity: Activity?, onComplete: (() -> Unit)?) {
        onComplete?.invoke()
    }
}