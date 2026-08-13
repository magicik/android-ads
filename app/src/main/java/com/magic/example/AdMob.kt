package com.magic.example

/** Google's public demo ad units — always fill and safe to ship in a sample app. */
object AdMob {
    const val OPEN_AD_UNIT = "ca-app-pub-3940256099942544/9257395921"
    const val INTERSTITIAL_AD_UNIT = "ca-app-pub-3940256099942544/1033173712"
    const val REWARDED_AD_UNIT = "ca-app-pub-3940256099942544/5224354917"
    const val BANNER_AD_UNIT = "ca-app-pub-3940256099942544/6300978111"
    const val NATIVE_AD_UNIT = "ca-app-pub-3940256099942544/2247696110"
}

/** No public demo units exist for AppLovin MAX — fill these in from your own dashboard to
 * exercise the MAX path (AdsManager.setActiveProvider("max") switches to it at runtime). */
object Max {
    const val MAX_SDK_KEY = ""
    const val OPEN_AD_UNIT = ""
    const val INTERSTITIAL_AD_UNIT = ""
    const val REWARDED_AD_UNIT = ""
    const val BANNER_AD_UNIT = ""
}
