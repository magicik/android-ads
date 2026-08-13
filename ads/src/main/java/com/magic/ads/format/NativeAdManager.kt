package com.magic.ads.format

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.TextView
import androidx.core.widget.TextViewCompat
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.AdChoicesView
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView
import com.magic.ads.R
import com.magic.ads.config.PlacementConfig
import com.magic.ads.core.AdRemovalRegistry
import com.magic.ads.core.AdsManager
import com.magic.ads.core.RemovableAd
import com.magic.ads.listener.NativeCallback
import com.magic.ads.native_ad.NativeAdStyle
import com.magic.ads.native_ad.NativeAdViewBinder
import com.magic.ads.native_ad.NativeLayoutType
import com.magic.ads.testguard.TestAdGuard

/**
 * One placement's native ad. AdMob-only for now: native rendering is fundamentally different
 * per network (AdMob's NativeAdView vs. MAX's own native view/binder types), so unlike the
 * other formats this doesn't go through [com.magic.ads.provider.AdSdkProvider] — a MAX
 * native path would need its own parallel binder type, not a forced shared abstraction.
 *
 * Two ways to render, matching the library's "UI must stay app-controlled" requirement:
 *  - [showAd] with [NativeLayoutType]/[NativeAdStyle]: one of the built-in layouts
 *    (SMALL/MEDIUM/LARGE), cosmetically restyled.
 *  - [showAd] with [NativeAdViewBinder]: the app's own layout entirely.
 */
class NativeAdManager : RemovableAd {

    private var nativeAd: NativeAd? = null
    private var currentContainer: ViewGroup? = null
    private var isLoading = false
    private val pendingCallbacks = mutableListOf<NativeCallback>()
    private var loadGeneration = 0
    private var resolvedGeneration = 0

    /** Container + root view inflated by [showLoading] that's still waiting for [showAd]/[clearLoading]. */
    private var loadingView: Pair<ViewGroup, View>? = null

    init {
        AdRemovalRegistry.register(this)
    }

    fun isAdReady() = nativeAd != null

    /**
     * Load an ad and render it into [container] with one of the built-in layouts. Unless
     * [showLoadingPlaceholder] is set to false, a shimmer skeleton matching [layoutType] is
     * shown in [container] immediately, then swapped for the real ad once it loads — callers
     * don't need to call [showLoading]/[showAd] themselves.
     */
    fun loadAd(
        context: Context,
        container: ViewGroup,
        config: PlacementConfig,
        layoutType: NativeLayoutType = NativeLayoutType.MEDIUM,
        style: NativeAdStyle = NativeAdStyle(),
        showLoadingPlaceholder: Boolean = true,
        callback: NativeCallback? = null
    ) {
        if (showLoadingPlaceholder) showLoading(container, layoutType)
        loadAd(context, config, object : NativeCallback {
            override fun onAdLoaded() {
                showAd(container, layoutType, style)
                callback?.onAdLoaded()
            }
            override fun onAdFailedToLoad(errorCode: Int, errorMessage: String) {
                clearLoading(container)
                callback?.onAdFailedToLoad(errorCode, errorMessage)
            }
        })
    }

    /**
     * Load-only, container-less variant for callers that need to decide *when* (or whether)
     * to bind the ad themselves — e.g. Tier 2 custom layouts, or a screen racing the load
     * against its own timeout.
     */
    fun loadAd(context: Context, config: PlacementConfig, callback: NativeCallback? = null) {
        val provider = AdsManager.activeProvider
        if (AdsManager.isPremium()) {
            callback?.onAdFailedToLoad(-1, "Ads disabled for premium user")
            return
        }
        if (!config.enable) {
            callback?.onAdFailedToLoad(-1, "Ads disabled by config")
            return
        }
        if (provider == null || provider.name != "admob") {
            callback?.onAdFailedToLoad(-1, "Native ads are only supported with the AdMob provider")
            return
        }
        if (isAdReady()) {
            callback?.onAdLoaded()
            return
        }
        if (isLoading) {
            callback?.let { pendingCallbacks.add(it) }
            return
        }
        val adUnitId = config.adUnitFor(provider.name)
        if (adUnitId.isBlank()) {
            callback?.onAdFailedToLoad(-1, "Ad unit not configured for admob")
            return
        }

        isLoading = true
        val generation = ++loadGeneration
        val handler = Handler(Looper.getMainLooper())
        var done = false
        val timeout = Runnable {
            if (!done) { done = true; notifyAllFailed("Native ad timed out", callback, generation) }
        }
        handler.postDelayed(timeout, config.timeoutMs)

        AdLoader.Builder(context, adUnitId)
            .forNativeAd { ad ->
                if (done) { ad.destroy(); return@forNativeAd }
                done = true
                handler.removeCallbacks(timeout)
                if (TestAdGuard.isTestHeadline(ad.headline)) TestAdGuard.markTestMode()
                ad.setOnPaidEventListener { TestAdGuard.onPaidEvent(it.valueMicros) }
                notifySuccess(ad, callback, generation)
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    if (!done) {
                        done = true
                        handler.removeCallbacks(timeout)
                        notifyAllFailed("Native ad failed to load", callback, generation)
                    }
                }
            })
            .withNativeAdOptions(NativeAdOptions.Builder().build())
            .build()
            .loadAd(AdRequest.Builder().build())
    }

    /**
     * Inflate a shimmer skeleton matching [layoutType] into [container] right away, so a
     * loading placeholder is visible while a load is in flight. Call [showAd] (or
     * [clearLoading] on failure) with the same container once it resolves.
     */
    fun showLoading(container: ViewGroup, layoutType: NativeLayoutType = NativeLayoutType.MEDIUM) {
        val rootView = LayoutInflater.from(container.context).inflate(shimmerResFor(layoutType), container, false)
        container.removeAllViews()
        container.addView(rootView)
        container.visibility = View.VISIBLE
        (rootView as? ShimmerFrameLayout)?.startShimmer()
        loadingView = container to rootView
    }

    /**
     * Hides the [showLoading] placeholder in [container] when the load ends up failing — stops
     * its shimmer animation and removes it. No-op if [container] has no pending placeholder
     * (e.g. [showAd] already consumed it).
     */
    fun clearLoading(container: ViewGroup) {
        val pending = loadingView ?: return
        if (pending.first !== container) return
        (pending.second as? ShimmerFrameLayout)?.stopShimmer()
        container.removeAllViews()
        container.visibility = View.GONE
        loadingView = null
    }

    /** Tier 1 — one of the built-in [layoutType] layouts, cosmetically restyled via [style]. */
    fun showAd(
        container: ViewGroup,
        layoutType: NativeLayoutType = NativeLayoutType.MEDIUM,
        style: NativeAdStyle = NativeAdStyle()
    ): Boolean {
        if (AdsManager.isPremium()) return false
        val ad = nativeAd ?: return false
        stopLoadingIfPresent(container)
        val rootView = LayoutInflater.from(container.context)
            .inflate(layoutResFor(layoutType), container, false) as NativeAdView
        applyStyle(rootView, style)
        bindBuiltInLayout(rootView, ad)
        container.removeAllViews()
        container.addView(rootView)
        container.visibility = View.VISIBLE
        currentContainer = container
        return true
    }

    /** Tier 2 — the app's own layout, via [binder]. */
    fun showAd(container: ViewGroup, binder: NativeAdViewBinder): Boolean {
        if (AdsManager.isPremium()) return false
        val ad = nativeAd ?: return false
        stopLoadingIfPresent(container)
        val view = binder.createView(container.context)
        binder.bind(view, ad)
        container.removeAllViews()
        container.addView(view)
        container.visibility = View.VISIBLE
        currentContainer = container
        return true
    }

    fun destroyCurrentAd() {
        nativeAd?.destroy()
        nativeAd = null
    }

    /** Called by [AdRemovalRegistry] when ads are removed by purchase. */
    override fun forceRemoveAd() {
        nativeAd?.destroy()
        nativeAd = null
        currentContainer?.removeAllViews()
        currentContainer?.visibility = View.GONE
        currentContainer = null
        loadingView?.let { (container, rootView) ->
            (rootView as? ShimmerFrameLayout)?.stopShimmer()
            container.removeAllViews()
            container.visibility = View.GONE
        }
        loadingView = null
    }

    private fun stopLoadingIfPresent(container: ViewGroup) {
        val pending = loadingView ?: return
        if (pending.first !== container) return
        (pending.second as? ShimmerFrameLayout)?.stopShimmer()
        loadingView = null
    }

    private fun shimmerResFor(layoutType: NativeLayoutType): Int = when (layoutType) {
        NativeLayoutType.SMALL -> R.layout.native_ad_small_shimmer
        NativeLayoutType.MEDIUM -> R.layout.native_ad_medium_shimmer
        NativeLayoutType.LARGE -> R.layout.native_ad_large_shimmer
    }

    private fun notifySuccess(ad: NativeAd, callback: NativeCallback?, generation: Int) {
        nativeAd?.destroy()
        nativeAd = ad
        if (generation <= resolvedGeneration) return
        resolvedGeneration = generation
        isLoading = false
        callback?.onAdLoaded()
        pendingCallbacks.forEach { it.onAdLoaded() }
        pendingCallbacks.clear()
    }

    private fun notifyAllFailed(message: String, callback: NativeCallback?, generation: Int) {
        if (generation <= resolvedGeneration) return
        resolvedGeneration = generation
        isLoading = false
        callback?.onAdFailedToLoad(-1, message)
        pendingCallbacks.forEach { it.onAdFailedToLoad(-1, message) }
        pendingCallbacks.clear()
    }

    private fun layoutResFor(layoutType: NativeLayoutType): Int = when (layoutType) {
        NativeLayoutType.SMALL -> R.layout.native_ad_small
        NativeLayoutType.MEDIUM -> R.layout.native_ad_medium
        NativeLayoutType.LARGE -> R.layout.native_ad_large
    }

    /**
     * Shared across all three built-in layouts — SMALL simply has no ad_media/ad_rating_bar
     * views, so those lookups come back null and the corresponding binding step is skipped.
     * ad_badge ("Ad" indicator) mirrors the old template's rule: visible whenever a headline
     * is present.
     */
    private fun bindBuiltInLayout(adView: NativeAdView, ad: NativeAd) {
        val headline = adView.findViewById<TextView>(R.id.ad_headline)
        val body = adView.findViewById<TextView?>(R.id.ad_body)
        val cta = adView.findViewById<Button?>(R.id.ad_call_to_action)
        val icon = adView.findViewById<ImageView?>(R.id.ad_icon)
        val media = adView.findViewById<MediaView?>(R.id.ad_media)
        val advertiser = adView.findViewById<TextView?>(R.id.ad_advertiser)
        val ratingBar = adView.findViewById<RatingBar?>(R.id.ad_rating_bar)
        val badge = adView.findViewById<TextView?>(R.id.ad_badge)
        val adChoices = adView.findViewById<AdChoicesView?>(R.id.ad_choices)

        adView.headlineView = headline
        adView.bodyView = body
        adView.callToActionView = cta
        adView.iconView = icon
        adView.mediaView = media

        headline.text = ad.headline
        badge?.visibility = if (!ad.headline.isNullOrEmpty()) View.VISIBLE else View.GONE

        body?.text = ad.body
        body?.visibility = if (!ad.body.isNullOrEmpty()) View.VISIBLE else View.GONE

        cta?.text = ad.callToAction

        ad.icon?.let {
            icon?.setImageDrawable(it.drawable)
            icon?.visibility = View.VISIBLE
        } ?: run { icon?.visibility = View.GONE }
        ad.mediaContent?.let { media?.mediaContent = it }

        // Secondary line: star rating takes priority over store/advertiser text, matching the
        // old template's behavior — both convey similar "how trustworthy is this" info, so
        // showing both would be redundant.
        val starRating = ad.starRating
        if (ratingBar != null && starRating != null && starRating > 0) {
            ratingBar.rating = starRating.toFloat()
            ratingBar.visibility = View.VISIBLE
            adView.starRatingView = ratingBar
            advertiser?.visibility = View.GONE
        } else {
            ratingBar?.visibility = View.GONE
            val secondaryText = ad.advertiser?.takeIf { it.isNotEmpty() } ?: ad.store
            advertiser?.text = secondaryText
            advertiser?.visibility = if (!secondaryText.isNullOrEmpty()) View.VISIBLE else View.GONE
            if (!ad.store.isNullOrEmpty() && ad.advertiser.isNullOrEmpty()) {
                adView.storeView = advertiser
            } else if (!ad.advertiser.isNullOrEmpty()) {
                adView.advertiserView = advertiser
            }
        }

        adChoices?.let { adView.setAdChoicesView(it) }
        adView.setNativeAd(ad)
    }

    private fun applyStyle(adView: NativeAdView, style: NativeAdStyle) {
        val card = adView.findViewById<View>(R.id.native_ad_card)
        when {
            style.backgroundDrawableRes != null -> card.setBackgroundResource(style.backgroundDrawableRes)
            style.backgroundColor != null || style.cornerRadiusDp != null -> {
                card.background = GradientDrawable().apply {
                    setColor(style.backgroundColor ?: android.graphics.Color.TRANSPARENT)
                    style.cornerRadiusDp?.let { cornerRadius = it * card.resources.displayMetrics.density }
                }
            }
        }

        val headline = adView.findViewById<TextView>(R.id.ad_headline)
        style.headlineTextColor?.let { headline.setTextColor(it) }
        style.headlineTextAppearanceRes?.let { TextViewCompat.setTextAppearance(headline, it) }

        val body = adView.findViewById<TextView?>(R.id.ad_body)
        style.bodyTextColor?.let { body?.setTextColor(it) }
        style.bodyTextAppearanceRes?.let { res -> body?.let { TextViewCompat.setTextAppearance(it, res) } }

        val cta = adView.findViewById<Button?>(R.id.ad_call_to_action)
        style.ctaBackgroundRes?.let { cta?.setBackgroundResource(it) }
        style.ctaTextColor?.let { cta?.setTextColor(it) }
        style.ctaTextAppearanceRes?.let { res -> cta?.let { TextViewCompat.setTextAppearance(it, res) } }
    }
}
