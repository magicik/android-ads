package com.library.ads.admob.native_ads

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.magic.ads.R
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView

///một dạng template cho native, co the tao nhiều template với customview khác nhau kế thừa AdMobNativeViewBinder
class TemplateView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : FrameLayout(context, attrs, defStyle) {

    companion object {
        private const val MEDIUM_TEMPLATE = "medium_template"
        private const val SMALL_TEMPLATE = "small_template"
        private const val LARGE_TEMPLATE = "large_template"
    }

    private var templateType: Int = R.layout.template_view_medium_native_ads
    private var nativeAd: NativeAd? = null

    lateinit var nativeAdView: NativeAdView
    private var primaryView: TextView? = null
    private var secondaryView: TextView? = null
    private var adNotificationView: TextView? = null
    private var ratingBar: RatingBar? = null
    private var tertiaryView: TextView? = null
    private var iconView: ImageView? = null
    private var mediaView: MediaView? = null
    private var callToActionView: Button? = null
    private var background: ConstraintLayout? = null
    private var styles: NativeTemplateStyle? = null

    init {
        val attributes = context.theme.obtainStyledAttributes(attrs, R.styleable.TemplateView, 0, 0)
        try {
            templateType = attributes.getResourceId(
                R.styleable.TemplateView_gnt_template_type, R.layout.template_view_large_native_ads
            )
        } finally {
            attributes.recycle()
        }

        LayoutInflater.from(context).inflate(templateType, this, true)
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        nativeAdView = findViewById(R.id.native_ad_view)
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

    fun bindAd(ad: NativeAd) {
        setNativeAd(ad)
    }

    fun setNativeAd(nativeAd: NativeAd) {
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

        nativeAdView.headlineView = primaryView
        nativeAdView.callToActionView = callToActionView
        nativeAdView.mediaView = mediaView

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
            nativeAdView.starRatingView = ratingBar
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
        nativeAdView.bodyView = tertiaryView

        if (!store.isNullOrEmpty() && advertiser.isNullOrEmpty()) {
            nativeAdView.storeView = secondaryView
        } else if (!advertiser.isNullOrEmpty()) {
            nativeAdView.advertiserView = secondaryView
        }

        nativeAdView.setNativeAd(nativeAd)
    }

    fun destroyNativeAd() {
        nativeAd?.destroy()
        nativeAd = null
    }

    fun getTemplateTypeName(): String = when (templateType) {
        R.layout.template_view_medium_native_ads -> MEDIUM_TEMPLATE
        R.layout.template_view_small_native_ads -> SMALL_TEMPLATE
        R.layout.template_view_large_native_ads -> LARGE_TEMPLATE
        else -> ""
    }

    fun setStyles(styles: NativeTemplateStyle) {
        this.styles = styles
        applyStyles()
    }

    private fun applyStyles() {
        styles?.let { style ->
            style.mainBackgroundDrawable?.let {
                background?.background = it
                primaryView?.background = it
                secondaryView?.background = it
                tertiaryView?.background = it
            }

            style.primaryTextTypeface?.let { primaryView?.typeface = it }
            style.secondaryTextTypeface?.let { secondaryView?.typeface = it }
            style.tertiaryTextTypeface?.let { tertiaryView?.typeface = it }
            style.callToActionTextTypeface?.let { callToActionView?.typeface = it }

            style.primaryTextTypefaceColor?.let { primaryView?.setTextColor(it) }
            style.secondaryTextTypefaceColor?.let { secondaryView?.setTextColor(it) }
            style.tertiaryTextTypefaceColor?.let { tertiaryView?.setTextColor(it) }
            style.callToActionTypefaceColor?.let { callToActionView?.setTextColor(it) }

            if (style.primaryTextSize > 0) primaryView?.textSize = style.primaryTextSize
            if (style.secondaryTextSize > 0) secondaryView?.textSize = style.secondaryTextSize
            if (style.tertiaryTextSize > 0) tertiaryView?.textSize = style.tertiaryTextSize
            if (style.callToActionTextSize > 0) callToActionView?.textSize =
                style.callToActionTextSize

            style.primaryTextBackgroundColor?.let { primaryView?.background = it }
            style.secondaryTextBackgroundColor?.let { secondaryView?.background = it }
            style.tertiaryTextBackgroundColor?.let { tertiaryView?.background = it }
            style.callToActionBackgroundColor?.let { callToActionView?.background = it }

            invalidate()
            requestLayout()
        }
    }
}
