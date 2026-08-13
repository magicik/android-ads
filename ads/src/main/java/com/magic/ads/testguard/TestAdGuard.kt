package com.magic.ads.testguard

/**
 * Network-agnostic test-ad detector. Providers report paid-event value and native ad
 * headline text; this object never touches AdMob/MAX types directly so it stays usable
 * regardless of which [com.magic.ads.provider.AdSdkProvider] is active.
 */
object TestAdGuard {

    @Volatile
    var isEnabled = true

    @Volatile
    var isTestMode = false
        private set

    fun markTestMode() {
        if (isEnabled) isTestMode = true
    }

    /** Call with the paid event's value (micros). A zero-value fill is always a test ad. */
    fun onPaidEvent(valueMicros: Long) {
        if (valueMicros == 0L) markTestMode()
    }

    fun isTestHeadline(headline: String?): Boolean {
        if (headline.isNullOrBlank()) return false
        return headline.contains("Test Ad", ignoreCase = true) ||
                headline.contains("Ad Test", ignoreCase = true)
    }
}
