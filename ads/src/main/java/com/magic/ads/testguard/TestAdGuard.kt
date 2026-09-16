package com.magic.ads.testguard

import android.content.Context
import android.content.SharedPreferences

/**
 * Network-agnostic test-ad detector. Providers report paid-event value and native ad
 * headline text; this object never touches AdMob/MAX types directly so it stays usable
 * regardless of which [com.magic.ads.provider.AdSdkProvider] is active.
 *
 * [isTestMode] is derived from persisted cumulative revenue rather than an in-memory sticky
 * flag: it defaults to true on a fresh install (no revenue yet) and only flips false once any
 * positive-value paid event has ever accumulated, so a single fluke zero-value fill can't taint
 * test-mode detection for the rest of the app's lifetime the way a one-shot flag would.
 */
object TestAdGuard {

    @Volatile
    var isEnabled = true

    @Volatile
    var isTestMode = false
        private set

    private var initialized = false
    private var prefs: SharedPreferences? = null

    /** Call once, e.g. from [com.magic.ads.core.AdsManager.initialize], to load the persisted
     * revenue/native-test state before any ad has a chance to report into this object. */
    fun init(context: Context) {
        if (initialized || !isEnabled) return
        initialized = true
        recompute(prefsFor(context))
    }

    fun markTestMode(context: Context) {
        if (!isEnabled) return
        val p = prefsFor(context)
        p.edit().putBoolean(KEY_IS_NATIVE_TEST, true).apply()
        recompute(p)
    }

    /** Call with the paid event's value (micros). Only positive values are ever recorded —
     * a zero-value fill is a no-op here, not evidence either way. */
    fun onPaidEvent(context: Context, valueMicros: Long) {
        if (!isEnabled || valueMicros <= 0L) return
        val p = prefsFor(context)
        val total = p.getLong(KEY_TOTAL_REVENUE_MICROS, 0L) + valueMicros
        p.edit().putLong(KEY_TOTAL_REVENUE_MICROS, total).apply()
        recompute(p)
    }

    fun isTestHeadline(headline: String?): Boolean {
        if (headline.isNullOrBlank()) return false
        return headline.contains("Test Ad", ignoreCase = true) ||
                headline.contains("Ad Test", ignoreCase = true)
    }

    private fun recompute(p: SharedPreferences) {
        val isNativeTest = p.getBoolean(KEY_IS_NATIVE_TEST, false)
        val totalRevenueMicros = p.getLong(KEY_TOTAL_REVENUE_MICROS, 0L)
        isTestMode = isNativeTest || totalRevenueMicros <= 0L
    }

    private fun prefsFor(context: Context): SharedPreferences =
        prefs ?: context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .also { prefs = it }

    private const val PREFS_NAME = "magic_ads_test_guard_prefs"
    private const val KEY_IS_NATIVE_TEST = "is_native_test"
    private const val KEY_TOTAL_REVENUE_MICROS = "total_revenue_micros"
}
