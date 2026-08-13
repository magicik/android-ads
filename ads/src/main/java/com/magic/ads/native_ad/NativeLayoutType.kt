package com.magic.ads.native_ad

/** Which built-in Tier 1 layout [com.magic.ads.format.NativeAdManager.showAd] inflates. */
enum class NativeLayoutType {
    /** Compact banner-sized row: icon, headline+badge, one line of body, CTA. No media, no
     * rating bar — for spots where a full card doesn't fit (e.g. replacing a banner slot). */
    SMALL,

    /** Card with icon, headline+badge, rating-or-advertiser line, body, media, full-width CTA. */
    MEDIUM,

    /** Same layout as [MEDIUM], larger media/text/icon sizing for more prominent placements. */
    LARGE
}
