package com.library.ads.admob.native_ad

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.constraintlayout.widget.ConstraintLayout
import com.magic.ads.R
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import androidx.core.view.isNotEmpty

/**
 * TemplateView làm 2 việc:
 *  - Có thể lấy template từ XML attribute `gnt_template_type`
 *  - Hoặc allow setTemplate(@LayoutRes) / setTemplate(View) từ code
 *
 *  Bind an toàn: bindAd() sẽ post nếu view chưa inflate xong.
 */
class AdmobTemplateView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : FrameLayout(context, attrs, defStyle), AdMobNativeViewBinder {
    override val nativeView: View get() = this

    companion object {
        @LayoutRes
        private val DEFAULT_TEMPLATE = R.layout.template_view_large_native_ads
    }

    @LayoutRes
    private var templateRes: Int? = null

    private var nativeAdViewInternal: NativeAdView? = null
    val nativeAdView: NativeAdView?
        get() = nativeAdViewInternal

    private var primaryView: TextView? = null
    private var secondaryView: TextView? = null
    private var adNotificationView: TextView? = null
    private var ratingBar: RatingBar? = null
    private var tertiaryView: TextView? = null
    private var iconView: ImageView? = null
    private var mediaView: MediaView? = null
    private var callToActionView: Button? = null
    private var background: ConstraintLayout? = null

    private var nativeAd: NativeAd? = null

    init {
        // read XML attribute if present
        attrs?.let {
            val a = context.theme.obtainStyledAttributes(it, R.styleable.TemplateView, 0, 0)
            try {
                val res = a.getResourceId(
                    R.styleable.TemplateView_gnt_template_type,
                    0
                )
                templateRes = if (res != 0) res else null
            } finally {
                a.recycle()
            }
        }

        ensureInflated()
    }

    /**
     * Ensure there's an inflated template in this FrameLayout.
     * If already inflated with the same res, do nothing.
     */
    private fun ensureInflated() {
        val resToInflate = templateRes ?: DEFAULT_TEMPLATE
        if (isNotEmpty()) {
            return
        }

        LayoutInflater.from(context).inflate(resToInflate, this, true)
        findViewsAfterInflate()
    }

    private fun findViewsAfterInflate() {
        // attempt to find known ids, they may be null for custom templates
        nativeAdViewInternal = findViewById(R.id.native_ad_view)
        primaryView = findViewById(R.id.primary)
        secondaryView = findViewById(R.id.secondary)
        tertiaryView = findViewById(R.id.body)
        adNotificationView = findViewById(R.id.ad_notification_view)
        ratingBar = findViewById(R.id.rating_bar)
        ratingBar?.isEnabled = false
        callToActionView = findViewById(R.id.cta)
        iconView = findViewById(R.id.icon)
        mediaView = findViewById(R.id.media_view)
        background = findViewById(R.id.background)
    }

    /** Public API: set layout resource programmatically. If called after inflate, it re-inflates. */
    fun setTemplate(@LayoutRes resId: Int) {
        if (resId == templateRes) return // no-op
        templateRes = resId
        reInflate()
    }

    /** Public API: set a custom view (you can inflate your own View and pass here) */
    fun setTemplate(customView: View) {
        // clear existing children and add custom view
        removeAllViews()
        templateRes = null
        addView(customView)
        findViewsAfterInflate()
    }

    private fun reInflate() {
        // remove existing children and inflate new templateRes (or default)
        removeAllViews()
        val resToInflate = templateRes ?: DEFAULT_TEMPLATE
        LayoutInflater.from(context).inflate(resToInflate, this, true)
        findViewsAfterInflate()
    }

    override fun bindAd(ad: NativeAd) {
        // nếu view chưa inflate xong, đẩy vào message queue để bind sau
        if (!isViewReady()) {
            post { bindAd(ad) }
            return
        }
        setNativeAd(ad)
    }

    private fun isViewReady(): Boolean {
        // require at least nativeAdView and primaryView for correct binding; adjust if custom layouts differ
        return nativeAdViewInternal != null && primaryView != null
    }

    private fun setNativeAd(nativeAd: NativeAd) {
        this.nativeAd = nativeAd

        val store = nativeAd.store
        val advertiser = nativeAd.advertiser
        val headline = nativeAd.headline
        val body = nativeAd.body
        val cta = nativeAd.callToAction
        val starRating = nativeAd.starRating
        val icon = nativeAd.icon

        val secondaryText = when {
            !store.isNullOrEmpty() && advertiser.isNullOrEmpty() -> store
            !advertiser.isNullOrEmpty() -> advertiser
            else -> ""
        }

        nativeAdViewInternal?.headlineView = primaryView
        nativeAdViewInternal?.callToActionView = callToActionView
        nativeAdViewInternal?.mediaView = mediaView

        primaryView?.text = headline
        adNotificationView?.visibility = if (!headline.isNullOrEmpty()) VISIBLE else GONE

        if (!cta.isNullOrEmpty()) {
            callToActionView?.visibility = VISIBLE
            callToActionView?.text = cta
        } else {
            callToActionView?.visibility = GONE
        }

        if (starRating != null && starRating > 0) {
            ratingBar?.visibility = VISIBLE
            ratingBar?.rating = starRating.toFloat()
            nativeAdViewInternal?.starRatingView = ratingBar
            secondaryView?.visibility = GONE
        } else {
            secondaryView?.text = secondaryText
            secondaryView?.visibility = VISIBLE
            ratingBar?.visibility = GONE
        }

        if (icon != null) {
            iconView?.setImageDrawable(icon.drawable)
            iconView?.visibility = VISIBLE
        } else {
            iconView?.visibility = GONE
        }

        tertiaryView?.text = body
        nativeAdViewInternal?.bodyView = tertiaryView

        if (!store.isNullOrEmpty() && advertiser.isNullOrEmpty()) {
            nativeAdViewInternal?.storeView = secondaryView
        } else if (!advertiser.isNullOrEmpty()) {
            nativeAdViewInternal?.advertiserView = secondaryView
        }

        nativeAdViewInternal?.setNativeAd(nativeAd)
    }

    override fun destroy() {
        nativeAd?.destroy()
        nativeAd = null
    }
}
