package com.magic.ads.native_ad

/** Which built-in Tier 1 layout [com.magic.ads.format.NativeAdManager.showAd] inflates. */
enum class NativeLayoutType {
    /** Compact banner-sized row: icon, headline+badge, one line of body, CTA. No media, no
     * rating bar — for spots where a full card doesn't fit (e.g. replacing a banner slot). */
    SMALL,

    /** Card with icon, headline+badge, rating-or-advertiser line, body, media, full-width CTA. */
    MEDIUM,

    /** Same layout as [MEDIUM], larger media/text/icon sizing for more prominent placements. */
    LARGE,

    /** Edge-to-edge, fills its container: badge+AdChoices row, icon+headline+advertiser row,
     * body, a large flexible MediaView, full-width CTA pinned to the bottom. For a placement
     * meant to occupy the whole screen (e.g. a native ad used in place of an interstitial). */
    FULLSCREEN
}
