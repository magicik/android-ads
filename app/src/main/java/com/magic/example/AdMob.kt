package com.magic.example

import androidx.multidex.BuildConfig

object AdMob {
    val OPEN_AD_UNIT = if(BuildConfig.DEBUG) {
        "ca-app-pub-3940256099942544/9257395921"
    } else {
        "ca-app-pub-3940256099942544/9257395921"
    }
    val INTERSTITIAL_AD_UNIT = if(BuildConfig.DEBUG) {
        "ca-app-pub-3940256099942544/1033173712"
    } else {
        "ca-app-pub-3940256099942544/1033173712"
    }
    val REWARDED_AD_UNIT = if(BuildConfig.DEBUG) {
        "ca-app-pub-xxx/reward"
    } else {
        "ca-app-pub-xxx/reward"
    }
    val BANNER_AD_UNIT = if(BuildConfig.DEBUG) {
        "ca-app-pub-3940256099942544/6300978111"
    } else {
        "ca-app-pub-3940256099942544/6300978111"
    }
    val NATIVE_AD_UNIT = if(BuildConfig.DEBUG) {
        "ca-app-pub-3940256099942544/2247696110"
    } else {
        "ca-app-pub-3940256099942544/2247696110"
    }
}

object Max {
    const val MAX_SDK_KEY = ""
    const val OPEN_AD_UNIT = ""
    const val INTERSTITIAL_AD_UNIT = ""
    const val REWARDED_AD_UNIT = "max-rewarded-ad-unit-id"
    const val BANNER_AD_UNIT = ""
    const val NATIVE_AD_UNIT = ""
}