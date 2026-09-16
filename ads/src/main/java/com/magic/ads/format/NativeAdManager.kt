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
import com.google.android.gms.ads.nativead.AdChoicesView
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.magic.ads.R
import com.magic.ads.config.PlacementConfig
import com.magic.ads.core.AdPool
import com.magic.ads.core.AdRemovalRegistry
import com.magic.ads.core.AdsManager
import com.magic.ads.core.DialogVisibilityTracker
import com.magic.ads.core.RemovableAd
import com.magic.ads.listener.NativeCallback
import com.magic.ads.native_ad.NativeAdStyle
import com.magic.ads.native_ad.NativeAdViewBinder
import com.magic.ads.native_ad.NativeLayoutType
import com.magic.ads.testguard.TestAdGuard

/**
 * One placement's native ad, identified by [placementKey]. AdMob-only for now: native rendering
 * is fundamentally different per network (AdMob's NativeAdView vs. MAX's own native view/binder
 * types), so unlike the other formats this doesn't go through
 * [com.magic.ads.provider.AdSdkProvider] — a MAX native path would need its own parallel binder
 * type, not a forced shared abstraction.
 *
 * The actual fetch/waterfall/pool is delegated to [AdPool.loadNative]; this class keeps holding
 * the currently-displayed [NativeAd] locally (as before) since AdMob doesn't allow reusing one
 * NativeAd across multiple views — [AdPool.consumeNative] removes it from the pool the moment
 * this manager takes it, so the same ad object can never be handed to two managers.
 *
 * Two ways to render, matching the library's "UI must stay app-controlled" requirement:
 *  - [showAd] with [NativeLayoutType]/[NativeAdStyle]: one of the built-in layouts
 *    (SMALL/MEDIUM/LARGE), cosmetically restyled.
 *  - [showAd] with [NativeAdViewBinder]: the app's own layout entirely.
 *
 * Auto-refresh: if [PlacementConfig.refreshSeconds]/[PlacementConfig.refreshCount] are set (both
 * default to 0, i.e. off), a currently-shown ad is silently reloaded and re-bound into the same
 * container on that interval, up to that many times. A refresh tick is skipped (and rescheduled)
 * while the container isn't attached/visible, a full-screen ad is showing
 * ([AdsManager.isShowingFullScreenAd]), a dialog is covering the screen
 * ([DialogVisibilityTracker.isAnyDialogShowing]), or the app is in [TestAdGuard.isTestMode] —
 * refresh never runs against test ad inventory.
 */
class NativeAdManager(private val placementKey: String) : RemovableAd {

    private var nativeAd: NativeAd? = null
    private var currentContainer: ViewGroup? = null

    /** Container + root view inflated by [showLoading] that's still waiting for [showAd]/[clearLoading]. */
    private var loadingView: Pair<ViewGroup, View>? = null

    // ── Auto-refresh state ──────────────────────────────────────────────────
    private var refreshContext: Context? = null
    private var refreshConfig: PlacementConfig? = null
    private var refreshContainer: ViewGroup? = null
    private var refreshLayoutType: NativeLayoutType = NativeLayoutType.MEDIUM
    private var refreshStyle: NativeAdStyle = NativeAdStyle()
    private var refreshBinder: NativeAdViewBinder? = null
    private var refreshCyclesDone = 0
    private var isRefreshingAd = false
    private val refreshHandler = Handler(Looper.getMainLooper())
    private var refreshRunnable: Runnable? = null
    private val refreshDetachListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {}
        override fun onViewDetachedFromWindow(v: View) = cancelRefresh()
    }

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
        if (isAdReady()) {
            callback?.onAdLoaded()
            return
        }
        if (config.goneWithTestMode && TestAdGuard.isTestMode) {
            callback?.onAdFailedToLoad(-1, "gone_with_test_mode")
            return
        }
        refreshContext = context.applicationContext
        refreshConfig = config
        refreshCyclesDone = 0
        AdPool.loadNative(context, placementKey, config, callback = object : NativeCallback {
            override fun onAdLoaded() {
                val ad = AdPool.consumeNative(placementKey)
                if (ad == null) {
                    callback?.onAdFailedToLoad(-1, "Native ad was already consumed")
                    return
                }
                nativeAd?.destroy()
                nativeAd = ad
                callback?.onAdLoaded()
            }
            override fun onAdFailedToLoad(errorCode: Int, errorMessage: String) {
                callback?.onAdFailedToLoad(errorCode, errorMessage)
            }
        })
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

        refreshLayoutType = layoutType
        refreshStyle = style
        refreshBinder = null
        registerRefreshContainer(container)
        scheduleRefresh()
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

        refreshBinder = binder
        registerRefreshContainer(container)
        scheduleRefresh()
        return true
    }

    fun destroyCurrentAd() {
        nativeAd?.destroy()
        nativeAd = null
        stopRefreshCycle()
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
        stopRefreshCycle()
    }

    // ── Auto-refresh cycle ───────────────────────────────────────────────────

    private fun registerRefreshContainer(container: ViewGroup) {
        if (refreshContainer === container) return
        refreshContainer?.removeOnAttachStateChangeListener(refreshDetachListener)
        refreshContainer = container
        container.addOnAttachStateChangeListener(refreshDetachListener)
    }

    private fun cancelRefresh() {
        refreshRunnable?.let { refreshHandler.removeCallbacks(it) }
        refreshRunnable = null
    }

    private fun stopRefreshCycle() {
        cancelRefresh()
        refreshContainer?.removeOnAttachStateChangeListener(refreshDetachListener)
        refreshContainer = null
        refreshConfig = null
        refreshContext = null
        refreshBinder = null
        refreshCyclesDone = 0
    }

    private fun scheduleRefresh() {
        cancelRefresh()
        if (TestAdGuard.isTestMode) return
        val config = refreshConfig ?: return
        if (config.refreshSeconds <= 0 || config.refreshCount <= 0) return
        if (refreshCyclesDone >= config.refreshCount) return
        val refreshMs = config.refreshSeconds * 1000L
        val runnable = Runnable { performRefresh() }
        refreshRunnable = runnable
        refreshHandler.postDelayed(runnable, refreshMs)
    }

    private fun performRefresh() {
        if (TestAdGuard.isTestMode) return
        val container = refreshContainer
        val context = refreshContext
        val config = refreshConfig
        if (container == null || context == null || config == null) return
        if (!container.isAttachedToWindow || !container.isShown || isRefreshingAd) {
            scheduleRefresh()
            return
        }
        if (AdsManager.isShowingFullScreenAd || DialogVisibilityTracker.isAnyDialogShowing()) {
            scheduleRefresh()
            return
        }
        isRefreshingAd = true
        AdPool.loadNative(context, placementKey, config, alwaysReload = true, callback = object : NativeCallback {
            override fun onAdLoaded() {
                isRefreshingAd = false
                val ad = AdPool.consumeNative(placementKey)
                if (ad == null) { scheduleRefresh(); return }
                if (refreshContainer !== container) { ad.destroy(); return }
                nativeAd?.destroy()
                nativeAd = ad
                refreshCyclesDone++
                val binder = refreshBinder
                if (binder != null) showAd(container, binder) else showAd(container, refreshLayoutType, refreshStyle)
            }
            override fun onAdFailedToLoad(errorCode: Int, errorMessage: String) {
                isRefreshingAd = false
                scheduleRefresh()
            }
        })
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
        NativeLayoutType.FULLSCREEN -> R.layout.native_ad_fullscreen_shimmer
    }

    private fun layoutResFor(layoutType: NativeLayoutType): Int = when (layoutType) {
        NativeLayoutType.SMALL -> R.layout.native_ad_small
        NativeLayoutType.MEDIUM -> R.layout.native_ad_medium
        NativeLayoutType.LARGE -> R.layout.native_ad_large
        NativeLayoutType.FULLSCREEN -> R.layout.native_ad_fullscreen
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
