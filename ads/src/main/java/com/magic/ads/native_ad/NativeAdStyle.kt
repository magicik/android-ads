package com.magic.ads.native_ad

import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.StyleRes

/**
 * Tier 1 of native ad rendering: cosmetic overrides applied to the library's built-in default
 * layout (see `res/layout/native_ad_default.xml`). Covers the common case — matching
 * an app's theme colors/typography — without the app writing any layout XML.
 *
 * Anything the null values here can't reach (a fundamentally different arrangement of
 * headline/media/CTA, extra decorations, etc.) is Tier 2: [NativeAdViewBinder].
 */
data class NativeAdStyle(
    @ColorInt val backgroundColor: Int? = null,
    @DrawableRes val backgroundDrawableRes: Int? = null,
    val cornerRadiusDp: Float? = null,
    @ColorInt val headlineTextColor: Int? = null,
    @StyleRes val headlineTextAppearanceRes: Int? = null,
    @ColorInt val bodyTextColor: Int? = null,
    @StyleRes val bodyTextAppearanceRes: Int? = null,
    @DrawableRes val ctaBackgroundRes: Int? = null,
    @ColorInt val ctaTextColor: Int? = null,
    @StyleRes val ctaTextAppearanceRes: Int? = null,
)
