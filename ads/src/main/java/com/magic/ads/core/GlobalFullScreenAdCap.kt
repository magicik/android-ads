package com.magic.ads.core

import android.content.Context
import android.content.SharedPreferences
import com.magic.ads.testguard.TestAdGuard
import java.util.Calendar

/**
 * App-wide gate for showing full-screen ads (Interstitial/Rewarded/AppOpen), regardless of
 * placementKey — a min-interval since the last show, a max-shows-per-day cap, and a "1 shown in
 * every N attempts" throttle, combined. [AdPool.showInterstitial]/[AdPool.showRewarded]/
 * [AdPool.showAppOpen] all call [tryConsume] right before actually showing an ad.
 *
 * While [TestAdGuard.isTestMode] is true, the effective min-interval is never lower than
 * [TEST_MODE_MIN_INTERVAL_MS] regardless of [minIntervalMs] — a safety net against accidentally
 * spamming full-screen test ads during development even before the app has configured its own
 * interval (which defaults to a non-zero value here, but an app could still set it to 0).
 */
internal object GlobalFullScreenAdCap {

    @Volatile
    var minIntervalMs: Long = 50_000L
        private set

    @Volatile
    var maxShowsPerDay: Int = 0
        private set

    @Volatile
    var oneInEveryN: Int = 1
        private set

    private var prefs: SharedPreferences? = null

    fun setMinIntervalSeconds(seconds: Int) {
        minIntervalMs = seconds.coerceAtLeast(0) * 1000L
    }

    fun setMaxShowsPerDay(n: Int) {
        maxShowsPerDay = n.coerceAtLeast(0)
    }

    fun setOneInEveryN(n: Int) {
        oneInEveryN = n.coerceAtLeast(1)
    }

    /** Call right before actually showing an ad. Consumes the "1 in every N" counter on every attempt. */
    fun tryConsume(context: Context, now: Long = System.currentTimeMillis()): Boolean {
        val p = prefsFor(context)
        if (dayCapExceeded(p, now)) return false
        if (withinMinInterval(p, now)) return false
        if (!passesOneInEveryN(p)) return false
        return true
    }

    /** Call only when the ad actually displayed (onAdShowed), to record the day count + last-shown time. */
    fun recordShown(context: Context, now: Long = System.currentTimeMillis()) {
        val p = prefsFor(context)
        val bucket = dayBucket(now)
        val editor = p.edit()
        if (p.getInt(KEY_DAY_BUCKET, -1) != bucket) {
            editor.putInt(KEY_DAY_BUCKET, bucket).putInt(KEY_DAY_COUNT, 0)
        }
        val count = p.getInt(KEY_DAY_COUNT, 0)
        editor.putInt(KEY_DAY_COUNT, count + 1)
        editor.putLong(KEY_LAST_SHOWN_AT, now)
        editor.apply()
    }

    private fun dayCapExceeded(p: SharedPreferences, now: Long): Boolean {
        if (maxShowsPerDay <= 0) return false
        val bucket = dayBucket(now)
        if (p.getInt(KEY_DAY_BUCKET, -1) != bucket) return false
        return p.getInt(KEY_DAY_COUNT, 0) >= maxShowsPerDay
    }

    private fun withinMinInterval(p: SharedPreferences, now: Long): Boolean {
        val effectiveMinIntervalMs =
            if (TestAdGuard.isTestMode) maxOf(minIntervalMs, TEST_MODE_MIN_INTERVAL_MS) else minIntervalMs
        if (effectiveMinIntervalMs <= 0) return false
        val lastShownAt = p.getLong(KEY_LAST_SHOWN_AT, 0L)
        return now - lastShownAt < effectiveMinIntervalMs
    }

    private fun passesOneInEveryN(p: SharedPreferences): Boolean {
        if (oneInEveryN <= 1) return true
        val nextCount = p.getInt(KEY_CALL_COUNT, 0) + 1
        p.edit().putInt(KEY_CALL_COUNT, nextCount).apply()
        return nextCount % oneInEveryN == 0
    }

    private fun dayBucket(now: Long): Int {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = now
        return calendar.get(Calendar.YEAR) * 1000 + calendar.get(Calendar.DAY_OF_YEAR)
    }

    private fun prefsFor(context: Context): SharedPreferences =
        prefs ?: context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .also { prefs = it }

    private const val PREFS_NAME = "magic_ads_global_cap_prefs"
    private const val KEY_DAY_BUCKET = "day_bucket"
    private const val KEY_DAY_COUNT = "day_count"
    private const val KEY_LAST_SHOWN_AT = "last_shown_at"
    private const val KEY_CALL_COUNT = "call_count"
    private const val TEST_MODE_MIN_INTERVAL_MS = 60_000L
}
