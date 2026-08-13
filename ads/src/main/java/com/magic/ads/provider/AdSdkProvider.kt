package com.magic.ads.provider

import android.app.Activity
import android.content.Context
import android.view.ViewGroup
import com.magic.ads.listener.AdCallback
import com.magic.ads.listener.RewardCallback

/**
 * One implementation per ad network (AdMob, MAX, ...). Every format manager in `format/`
 * talks only to [com.magic.ads.core.AdsManager.activeProvider] — network-specific SDK
 * calls never leak outside this interface's implementations.
 *
 * No waterfall: each load* method attempts exactly one ad unit and terminates with either
 * [onSuccess] or [onFail]/onAdFailedToLoad — there is no internal retry across multiple units.
 * The ad object crossing the boundary is untyped ([Any]) since each provider returns its own
 * SDK type (InterstitialAd, MaxInterstitialAd holder, ...); callers pass it back unmodified
 * to show*().
 */
interface AdSdkProvider {

    val name: String

    fun initialize(context: Context, testDevices: List<String>, sdkKey: String = "", onReady: () -> Unit)

    // ── Interstitial ──────────────────────────────────────────────────────────

    fun loadInterstitial(
        context: Context,
        adUnitId: String,
        timeoutMs: Long,
        onSuccess: (Any) -> Unit,
        onFail: () -> Unit
    )

    fun showInterstitial(activity: Activity, ad: Any, callback: AdCallback?)

    // ── Rewarded ──────────────────────────────────────────────────────────────

    fun loadRewarded(
        context: Context,
        adUnitId: String,
        timeoutMs: Long,
        onSuccess: (Any) -> Unit,
        onFail: () -> Unit
    )

    fun showRewarded(activity: Activity, ad: Any, callback: RewardCallback)

    // ── Banner ────────────────────────────────────────────────────────────────

    fun loadBanner(
        activity: Activity,
        container: ViewGroup,
        adUnitId: String,
        adaptive: Boolean,
        adCallback: AdCallback?,
        onSuccess: (Any) -> Unit,
        onFail: () -> Unit
    )

    fun destroyBannerAd(adView: Any?)

    // ── App Open ──────────────────────────────────────────────────────────────

    fun loadAppOpen(
        context: Context,
        adUnitId: String,
        timeoutMs: Long,
        onSuccess: (Any) -> Unit,
        onFail: () -> Unit
    )

    fun showAppOpen(activity: Activity, ad: Any, callback: AdCallback?)
}
