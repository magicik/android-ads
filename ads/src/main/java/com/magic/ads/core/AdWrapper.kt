package com.magic.ads.core

/**
 * One successfully-loaded ad sitting in [AdPool]. [tier] is 1 for a high-floor fill, 2 for a
 * normal waterfall fill — [AdPool] evicts lower-tier/older wrappers first when a placement's
 * pool is over capacity.
 */
data class AdWrapper(
    val adObject: Any,
    val adType: AdType,
    val adUnitId: String,
    val tier: Int,
    val loadTime: Long,
    val placementKey: String
)
