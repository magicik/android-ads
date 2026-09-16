package com.magic.ads.config

import org.json.JSONObject

/** One ad unit in a placement's waterfall, tried in list order by [com.magic.ads.core.AdWaterfallLoader]. */
data class AdUnitConfig(
    val enableAd: Boolean = false,
    val type: String = "",
    val timeoutMs: Long = 10_000L,
    val adUnit: String = ""
)

/** An optional tier tried before the normal waterfall, with no timeout of its own — see [AdPool.engineLoad]. */
data class HighFloorConfig(
    val enable: Boolean = false,
    val adUnit: String = ""
) {
    companion object {
        fun fromJson(obj: JSONObject?): HighFloorConfig {
            obj ?: return HighFloorConfig()
            return try {
                HighFloorConfig(
                    enable = obj.optBoolean("enable", false),
                    adUnit = obj.optString("adunit", "")
                )
            } catch (e: Exception) {
                HighFloorConfig()
            }
        }
    }
}

/**
 * Config for a single ad placement, sourced from a raw JSON string the app already fetched from
 * wherever it keeps remote config (Firebase, its own backend, ...) — this library never fetches
 * or interprets remote config itself, it only parses the value it's handed.
 *
 * Waterfall: [listAds] is an ordered list of ad units tried in sequence by [AdPool]/
 * [com.magic.ads.core.AdWaterfallLoader] within [totalTimeoutMs]; an optional [highFloor] unit is
 * tried first, with no timeout of its own. [AdUnitConfig.type] is parsed but not used to filter
 * by network — same as the reference schema this was ported from — because this library only
 * ever has one active [com.magic.ads.provider.AdSdkProvider] at a time; the app is responsible
 * for supplying ad unit IDs valid for whichever provider is currently active.
 *
 * [refreshSeconds]/[refreshCount]/[goneWithTestMode] are native-only, ignored by every other
 * format:
 *  - [com.magic.ads.format.NativeAdManager] reloads a currently-shown native ad every
 *    [refreshSeconds] seconds, up to [refreshCount] times. Both default to 0 (disabled).
 *  - [goneWithTestMode]: once [com.magic.ads.testguard.TestAdGuard.isTestMode] has already
 *    latched true (from an earlier test-ad fill anywhere in the app), a placement with this set
 *    fails its native load immediately instead of even attempting it — for a slot the app wants
 *    to just not exist at all once it's known to be running against test inventory, as opposed
 *    to loading and showing a real "Test Ad".
 */
data class PlacementConfig(
    val enable: Boolean = false,
    val totalTimeoutMs: Long = 15_000L,
    val listAds: List<AdUnitConfig> = emptyList(),
    val highFloor: HighFloorConfig = HighFloorConfig(),
    val refreshSeconds: Int = 0,
    val refreshCount: Int = 0,
    val goneWithTestMode: Boolean = false
) {
    val enabledAds: List<AdUnitConfig>
        get() = if (enable) listAds.filter { it.enableAd && it.adUnit.isNotBlank() } else emptyList()

    companion object {
        fun fromJson(json: String): PlacementConfig {
            return try {
                val obj = JSONObject(json)
                PlacementConfig(
                    enable = obj.optBoolean("enable", false),
                    totalTimeoutMs = obj.optLong("total_timeout_ms", 15_000L),
                    listAds = obj.optJSONArray("list_ads")?.let { arr ->
                        (0 until arr.length()).map { i ->
                            val ad = arr.getJSONObject(i)
                            AdUnitConfig(
                                enableAd = ad.optBoolean("enable_ad", false),
                                type = ad.optString("type", ""),
                                timeoutMs = ad.optLong("timeout_ms", 10_000L),
                                adUnit = ad.optString("adunit", "")
                            )
                        }
                    } ?: emptyList(),
                    highFloor = HighFloorConfig.fromJson(obj.optJSONObject("high_floor")),
                    refreshSeconds = obj.optInt("refresh_seconds", 0),
                    refreshCount = obj.optInt("refresh_count", 0),
                    goneWithTestMode = obj.optBoolean("gone_with_test_mode", false)
                )
            } catch (e: Exception) {
                PlacementConfig()
            }
        }
    }
}
