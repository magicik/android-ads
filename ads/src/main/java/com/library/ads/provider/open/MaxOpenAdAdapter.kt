package com.library.ads.provider.open

import android.app.Activity
import com.library.ads.max.MaxOpenAdHelper

class MaxOpenAdAdapter(
    private val maxManager: MaxOpenAdHelper
) : OpenAdManager {

    override fun isAdAvailable(): Boolean = maxManager.isAdAvailable()

    override fun showAdIfAvailable(
        activity: Activity, listener: OpenAdManager.OnShowAdCompleteListener?
    ) {
        maxManager.showAdIfAvailable(
            activity, object : MaxOpenAdHelper.OnShowAdCompleteListener {
                override fun onShowAdComplete() {
                    listener?.onShowAdComplete()
                }
            })
    }

    override fun loadAd(activity: Activity?, onComplete: (() -> Unit)?) {
        onComplete?.invoke()
    }
}