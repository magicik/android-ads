package com.magic.ads.listener

interface AdCallback {
    fun onAdLoaded() {}
    fun onAdFailedToLoad(errorCode: Int, errorMessage: String) {}
    fun onAdShowed() {}
    fun onAdDismissed() {}
    fun onAdClicked() {}
    fun onAdImpression() {}
}
