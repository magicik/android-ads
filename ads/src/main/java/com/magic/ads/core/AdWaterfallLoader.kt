package com.magic.ads.core

import com.magic.ads.config.AdUnitConfig
import com.magic.ads.config.PlacementConfig

/**
 * Tries each of [PlacementConfig.enabledAds] in array order until one succeeds or the placement's
 * total timeout budget is spent. Per-attempt timeout is `min(unit's own timeoutMs, remaining
 * global budget)`. Used by [AdPool] for the normal (non-high-floor) waterfall tier.
 */
internal class AdWaterfallLoader<T>(
    private val config: PlacementConfig,
    private val tryLoad: (
        adUnit: AdUnitConfig,
        effectiveTimeoutMs: Long,
        onSuccess: (T) -> Unit,
        onFail: () -> Unit
    ) -> Unit
) {
    private val enabledAds = config.enabledAds
    private var currentIndex = 0
    private val startTime = System.currentTimeMillis()

    fun load(onSuccess: (T) -> Unit, onAllFailed: () -> Unit) {
        if (enabledAds.isEmpty()) {
            onAllFailed()
            return
        }
        tryNext(onSuccess, onAllFailed)
    }

    private fun tryNext(onSuccess: (T) -> Unit, onAllFailed: () -> Unit) {
        if (currentIndex >= enabledAds.size) {
            onAllFailed()
            return
        }
        val elapsed = System.currentTimeMillis() - startTime
        if (elapsed >= config.totalTimeoutMs) {
            onAllFailed()
            return
        }
        val adConfig = enabledAds[currentIndex]
        val remaining = config.totalTimeoutMs - elapsed
        val effectiveTimeout = minOf(adConfig.timeoutMs, remaining)

        tryLoad(
            adConfig,
            effectiveTimeout,
            onSuccess,
            {
                currentIndex++
                tryNext(onSuccess, onAllFailed)
            }
        )
    }
}
