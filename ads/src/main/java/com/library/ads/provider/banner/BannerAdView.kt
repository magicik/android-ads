package com.library.ads.provider.banner

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxAdFormat
import com.applovin.mediation.MaxAdViewAdListener
import com.applovin.mediation.MaxError
import com.applovin.mediation.ads.MaxAdView
import com.google.ads.mediation.admob.AdMobAdapter
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.library.ads.provider.config.ProviderAds
import com.magic.ads.R
import androidx.core.content.withStyledAttributes
import com.applovin.sdk.AppLovinSdkUtils

class BannerAdView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : FrameLayout(context, attrs, defStyle) {

    // -------- XML-backed config --------
    private var bgColor: Int = Color.TRANSPARENT

    // AdMob (XML)
    private var adUnitIdAdmobFromXml: String? = null
    private var admobAdSizeName: String = "ADAPTIVE" // BANNER|LARGE_BANNER|MEDIUM_RECTANGLE|FULL_BANNER|LEADERBOARD|ADAPTIVE
    private var admobCollapsible: String? = null     // "top" | "bottom" | null

    // MAX (XML)
    private var adUnitIdMaxFromXml: String? = null
    private var maxUseAdaptiveHeight: Boolean = true
    private var maxFixedHeightDp: Int? = null

    private var provider: ProviderAds = ProviderAds.ADMOB // sẽ bị override qua setProvider()

    private var delegate: BannerDelegate? = null
    private var maxExternalListener: MaxAdViewAdListener? = null // optional cho MAX
    private var subscriptionProvider: () -> Boolean = { false }

    init {
        if (attrs != null) {
            context.withStyledAttributes(attrs, R.styleable.UnifiedBannerAdView) {

                // Provider trong XML chỉ là default;
                // runtime sẽ override bằng RC
                getString(R.styleable.UnifiedBannerAdView_provider)?.lowercase()?.let {
                    provider = if (it == "max") ProviderAds.MAX else ProviderAds.ADMOB
                }

                bgColor =
                    getColor(R.styleable.UnifiedBannerAdView_bannerBackground, Color.TRANSPARENT)

                // AdMob (XML)
                adUnitIdAdmobFromXml = getString(R.styleable.UnifiedBannerAdView_adUnitIdAdmob)
                admobAdSizeName =
                    getString(R.styleable.UnifiedBannerAdView_admobAdSize) ?: "ADAPTIVE"
                admobCollapsible = getString(R.styleable.UnifiedBannerAdView_admobCollapsible)
                    ?.lowercase()?.takeIf { it == "top" || it == "bottom" }

                // MAX (XML)
                adUnitIdMaxFromXml = getString(R.styleable.UnifiedBannerAdView_adUnitIdMax)
                maxUseAdaptiveHeight = getBoolean(
                    R.styleable.UnifiedBannerAdView_maxUseAdaptiveHeight,
                    true
                )
                if (hasValue(R.styleable.UnifiedBannerAdView_maxFixedHeightDp)) {
                    maxFixedHeightDp = getInt(R.styleable.UnifiedBannerAdView_maxFixedHeightDp, 0)
                        .takeIf { it > 0 }
                }
            }
        }

        // Tạo delegate theo provider mặc định (XML) và áp adUnitId từ XML tương ứng
        buildDelegate()
        applyProviderAndXmlAdUnitId()
    }

    fun setSubscriptionProvider(providerFn: () -> Boolean) {
        this.subscriptionProvider = providerFn
        applySubscriptionState() // update immediately
    }

    /** Optional: call this when subscription changes (app can call directly) */
    fun onSubscriptionChanged(subscribed: Boolean) {
        // keep UI changes on main thread
        Handler(Looper.getMainLooper()).post {
            if (subscribed) {
                // hide and free resources immediately
                try { delegate?.destroy() } catch (_: Throwable) {}
                delegate = null
                removeAllViews()
                visibility = View.GONE
            } else {
                // user unsubscribed -> recreate delegate and optional reload
                visibility = View.VISIBLE
                if (delegate == null) {
                    buildDelegate()
                    applyProviderAndXmlAdUnitId()
                }
            }
        }
    }

    /** Gọi sau khi đọc provider từ Remote Config. */
    fun setProvider(p: String) {
        val convertProvider = when(p) {
            ProviderAds.ADMOB.value -> ProviderAds.ADMOB
            ProviderAds.MAX.value -> ProviderAds.MAX
            else -> ProviderAds.ADMOB
        }
        if (provider == convertProvider) return
        provider = convertProvider
        recreateDelegate()
        applyProviderAndXmlAdUnitId() // chọn đúng adUnitId từ XML theo provider
    }

    /** Lấy provider hiện tại. */
    fun getCurrentProvider(): ProviderAds = provider

    /** Lấy adUnitId đang dùng ứng với provider hiện tại. */
    fun getAdUnitIdForCurrentProvider(): String? = when (provider) {
        ProviderAds.ADMOB -> adUnitIdAdmobFromXml
        ProviderAds.MAX   -> adUnitIdMaxFromXml
    }

    /**
     * Đặt adUnitId theo provider hiện tại.
     * Gọi trươc khi load(). Tự động recreate view con nếu id khác để tuân thủ rule “set once”.
     */
    fun setAdUnitIdForCurrentProvider(admobUnit: String?, maxUnit: String?) {
        if (subscriptionProvider()) {
            applySubscriptionState()
            return
        }
        if (admobUnit.isNullOrEmpty() && maxUnit.isNullOrEmpty()) {
            return
        }
        val trimmedAdmob = admobUnit?.trim()
        val trimmedMax = maxUnit?.trim()

        when (provider) {
            ProviderAds.ADMOB -> {
                if (trimmedAdmob == null) return
                adUnitIdAdmobFromXml = trimmedAdmob
                (delegate as? AdMobDelegate)?.ensureCreatedWithId(trimmedAdmob)
                (delegate as? AdMobDelegate)?.applyConfig(
                    sizeName = admobAdSizeName,
                    bg = bgColor,
                    collapsible = admobCollapsible
                )
            }
            ProviderAds.MAX -> {
                if (trimmedMax == null) return
                adUnitIdMaxFromXml = trimmedMax
                (delegate as? MaxDelegate)?.ensureCreatedWithId(trimmedMax)
                (delegate as? MaxDelegate)?.applyConfig(
                    bg = bgColor,
                    useAdaptive = maxUseAdaptiveHeight,
                    fixedHeightDp = maxFixedHeightDp,
                    listener = maxExternalListener
                )
            }
        }
    }

    private fun applySubscriptionState() {
        if (subscriptionProvider()) {
            try { delegate?.destroy() } catch (_: Throwable) {}
            delegate = null
            removeAllViews()
            visibility = View.GONE
        } else {
            visibility = View.VISIBLE
            if (delegate == null) {
                buildDelegate()
                applyProviderAndXmlAdUnitId()
            }
        }
    }

    /** (Tuỳ chọn) nhận callback từ MAX. */
    fun setMaxAdListener(listener: MaxAdViewAdListener?) {
        maxExternalListener = listener
        (delegate as? MaxDelegate)?.setListener(listener)
    }

    /** Chủ động load banner (sau khi đã setProvider). */
    fun load() {
        if (subscriptionProvider()) {
            applySubscriptionState()
            return
        }
        if (!hasValidUnitIdForCurrentProvider()) return
        delegate?.load()
    }

    fun pause()  { delegate?.pause() }
    fun resume() { delegate?.resume() }
    fun destroy(){ delegate?.destroy() }

    private fun hasValidUnitIdForCurrentProvider(): Boolean {
        return when (provider) {
            ProviderAds.ADMOB -> !adUnitIdAdmobFromXml.isNullOrBlank()
            ProviderAds.MAX   -> !adUnitIdMaxFromXml.isNullOrBlank()
        }
    }

    private fun applyProviderAndXmlAdUnitId() {
        if (subscriptionProvider()) {
            applySubscriptionState() // will hide / destroy if subscribed
            return
        }
        when (provider) {
            ProviderAds.ADMOB -> {
                val id = adUnitIdAdmobFromXml ?: return
                (delegate as? AdMobDelegate)?.ensureCreatedWithId(id)
                (delegate as? AdMobDelegate)?.applyConfig(
                    sizeName = admobAdSizeName,
                    bg = bgColor,
                    collapsible = admobCollapsible
                )
            }
            ProviderAds.MAX -> {
                val id = adUnitIdMaxFromXml ?: return
                (delegate as? MaxDelegate)?.ensureCreatedWithId(id)
                (delegate as? MaxDelegate)?.applyConfig(
                    bg = bgColor,
                    useAdaptive = maxUseAdaptiveHeight,
                    fixedHeightDp = maxFixedHeightDp,
                    listener = maxExternalListener
                )
            }
        }
    }

    private fun recreateDelegate() {
        delegate?.destroy()
        removeAllViews()
        delegate = null
        buildDelegate()
    }

    private fun buildDelegate() {
        when (provider) {
            ProviderAds.ADMOB -> {
                delegate = AdMobDelegate(context, this)
            }
            ProviderAds.MAX -> {
                val activity = findActivity(context)
                    ?: throw IllegalStateException("UnifiedBannerAdView (MAX) requires an Activity context.")
                delegate = MaxDelegate(activity, this)
            }
        }
    }

    private fun findActivity(ctx: Context): Activity? {
        var c: Context? = ctx
        while (c is ContextWrapper) {
            if (c is Activity) return c
            c = c.baseContext
        }
        return null
    }

    // --------------- Delegates ---------------

    private interface BannerDelegate {
        fun load()
        fun pause() {}
        fun resume() {}
        fun destroy()
    }

    // ======== AdMob ========
    private class AdMobDelegate(
        private val ctx: Context,
        private val view: FrameLayout
    ) : BannerDelegate {

        private var adView: AdView? = null
        private var lastAppliedSizeName: String = "ADAPTIVE"
        private var lastAppliedBg: Int = Color.TRANSPARENT
        private var lastAppliedCollapsible: String? = null // "top" | "bottom" | null

        fun ensureCreatedWithId(id: String) {
            // Nếu đã có và id khác → recreate (AdMob chỉ set adUnitId 1 lần)
            if (adView != null && adView?.adUnitId != id) {
                destroy()
            }
            if (adView == null) {
                adView = AdView(ctx).apply {
                    setAdSize(resolveAdSize(lastAppliedSizeName, view))
                    setBackgroundColor(lastAppliedBg)
                    adUnitId = id
                    layoutParams = LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        LayoutParams.WRAP_CONTENT
                    )
                }
                view.removeAllViews()
                view.addView(adView)
            }
        }

        fun applyConfig(sizeName: String, bg: Int, collapsible: String?) {
            lastAppliedSizeName = sizeName
            lastAppliedBg = bg
            lastAppliedCollapsible = collapsible

            adView?.let {
                it.setBackgroundColor(bg)
                view.requestLayout()
            }
        }

        override fun load() {
            val view = adView ?: return

            val request: AdRequest = if (!lastAppliedCollapsible.isNullOrBlank()) {
                val extras = Bundle().apply { putString("collapsible", lastAppliedCollapsible) }
                AdRequest.Builder()
                    .addNetworkExtrasBundle(AdMobAdapter::class.java, extras)
                    .build()
            } else {
                AdRequest.Builder().build()
            }

            view.loadAd(request)
        }

        override fun destroy() {
            adView?.destroy()
            view.removeAllViews()
            adView = null
        }

        private fun resolveAdSize(name: String, container: FrameLayout): AdSize {
            return when (name.uppercase()) {
                "BANNER" -> AdSize.BANNER
                "LARGE_BANNER" -> AdSize.LARGE_BANNER
                "MEDIUM_RECTANGLE" -> AdSize.MEDIUM_RECTANGLE
                "FULL_BANNER" -> AdSize.FULL_BANNER
                "LEADERBOARD" -> AdSize.LEADERBOARD
                "ADAPTIVE" -> {
                    val dm = container.resources.displayMetrics
                    val density = dm.density
                    var adWidthPx = container.width.toFloat()
                    if (adWidthPx == 0f) adWidthPx = dm.widthPixels.toFloat()
                    val adWidth = (adWidthPx / density).toInt()
                    AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(ctx, adWidth)
                }
                else -> AdSize.BANNER
            }
        }
    }

    // ======== AppLovin MAX ========
    private class MaxDelegate(
        private val activity: Activity,
        private val parent: FrameLayout
    ) : BannerDelegate, MaxAdViewAdListener {

        private var maxView: MaxAdView? = null
        private var lastBg: Int = Color.TRANSPARENT
        private var lastUseAdaptive: Boolean = true
        private var lastFixedHeightDp: Int? = null
        private var lastListener: MaxAdViewAdListener? = null

        fun ensureCreatedWithId(id: String) {
            if (maxView != null && maxView?.adUnitId != id) {
                destroy()
            }
            if (maxView == null) {
                maxView = MaxAdView(id, activity).apply {
                    setListener(lastListener ?: this@MaxDelegate)
                    setBackgroundColor(lastBg)
                    layoutParams = LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        resolveHeightPx()
                    )
                }
                parent.removeAllViews()
                parent.addView(maxView)
            }
        }

        fun applyConfig(
            bg: Int,
            useAdaptive: Boolean,
            fixedHeightDp: Int?,
            listener: MaxAdViewAdListener?
        ) {
            lastBg = bg
            lastUseAdaptive = useAdaptive
            lastFixedHeightDp = fixedHeightDp
            lastListener = listener

            maxView?.apply {
                setListener(listener ?: this@MaxDelegate)
                setBackgroundColor(bg)
                layoutParams = LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    resolveHeightPx()
                )
            }
        }

        fun setListener(l: MaxAdViewAdListener?) {
            lastListener = l
            maxView?.setListener(l ?: this)
        }

        override fun load() {
            maxView?.loadAd()
        }

        override fun destroy() {
            maxView?.let {
                parent.removeView(it)
                it.destroy()
            }
            maxView = null
        }

        private fun resolveHeightPx(): Int {
            lastFixedHeightDp?.let { return dpToPx(it) }
            return if (lastUseAdaptive) {
                val adaptiveSize = MaxAdFormat.BANNER.getAdaptiveSize(activity)
                AppLovinSdkUtils.dpToPx(activity, adaptiveSize.height)
            } else {
                dpToPx(50)
            }
        }

        private fun dpToPx(dp: Int): Int {
            val d = activity.resources.displayMetrics.density
            return (dp * d).toInt()
        }

        // Forward listener nếu dev có set; nếu không set, mặc định no-op
        override fun onAdLoaded(ad: MaxAd) { lastListener?.onAdLoaded(ad) }
        override fun onAdLoadFailed(adUnitId: String, error: MaxError) { lastListener?.onAdLoadFailed(adUnitId, error) }
        override fun onAdDisplayFailed(ad: MaxAd, error: MaxError) { lastListener?.onAdDisplayFailed(ad, error) }
        override fun onAdClicked(ad: MaxAd) { lastListener?.onAdClicked(ad) }
        override fun onAdExpanded(ad: MaxAd) { lastListener?.onAdExpanded(ad) }
        override fun onAdCollapsed(ad: MaxAd) { lastListener?.onAdCollapsed(ad) }
        override fun onAdDisplayed(ad: MaxAd) { lastListener?.onAdDisplayed(ad) } // banner: thường không dùng
        override fun onAdHidden(ad: MaxAd) { lastListener?.onAdHidden(ad) }       // banner: thường không dùng
    }
}