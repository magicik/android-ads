package com.magic.example

import org.json.JSONObject

/**
 * Stand-in for "whatever the app already fetched from its own remote config" — the ads
 * library never fetches or interprets remote config itself, it only parses the JSON string
 * it's handed via [com.magic.ads.config.PlacementConfig.fromJson]. Hard-coded here since this
 * is just a demo app; a real app would read these strings from Firebase Remote Config, its
 * own backend, etc.
 *
 * Waterfall schema: [PlacementConfig.listAds] tries each unit in order within
 * [total_timeout_ms]. This demo only has one real ad unit per network, so each placement's
 * `list_ads` just has a single entry — a real app with multiple mediation units per network
 * would list them here in fallback order.
 */
object Placements {
    fun open() = placementJson(AdMob.OPEN_AD_UNIT)
    fun interstitial() = placementJson(AdMob.INTERSTITIAL_AD_UNIT)
    fun rewarded() = placementJson(AdMob.REWARDED_AD_UNIT)
    fun banner() = placementJson(AdMob.BANNER_AD_UNIT)
    fun native() = placementJson(AdMob.NATIVE_AD_UNIT)

    private fun placementJson(adUnit: String): String =
        JSONObject().apply {
            put("enable", true)
            put("total_timeout_ms", 15_000L)
            put("list_ads", org.json.JSONArray().put(
                JSONObject().apply {
                    put("enable_ad", true)
                    put("type", "admob")
                    put("timeout_ms", 10_000L)
                    put("adunit", adUnit)
                }
            ))
        }.toString()
}
