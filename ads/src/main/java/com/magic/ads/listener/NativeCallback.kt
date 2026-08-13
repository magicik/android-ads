package com.magic.ads.listener

interface NativeCallback {
    fun onAdLoaded() {}
    fun onAdFailedToLoad(errorCode: Int, errorMessage: String) {}
}
