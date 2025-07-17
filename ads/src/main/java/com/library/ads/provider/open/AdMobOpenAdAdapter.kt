package com.library.ads.provider.open

import android.app.Activity
import android.util.Log
import com.library.ads.admob.AdmobOpenAdHelper

class AdMobOpenAdAdapter(
    private val admobManager: AdmobOpenAdHelper
) : OpenAdManager {

    override fun isAdAvailable(): Boolean = admobManager.isAdAvailable()

    override fun showAdIfAvailable(
        activity: Activity,
        listener: OpenAdManager.OnShowAdCompleteListener?
    ) {
        admobManager.showAdIfAvailable(activity, object : AdmobOpenAdHelper.OnShowAdCompleteListener {
            override fun onShowAdComplete() {
                listener?.onShowAdComplete()
            }
        })
    }

    override fun loadAd(activity: Activity?, onComplete: (() -> Unit)?) {
        activity?.let {
            admobManager.fetchAd(it, onComplete)
        } ?: onComplete?.invoke()
    }
}