package com.magic.example

import org.json.JSONObject

/**
 * Stand-in for "whatever the app already fetched from its own remote config" — the ads
 * library never fetches or interprets remote config itself, it only parses the JSON string
 * it's handed via [com.magic.ads.config.PlacementConfig.fromJson]. Hard-coded here since this
 * is just a demo app; a real app would read these strings from Firebase Remote Config, its
 * own backend, etc.
 */
object Placements {
    fun open() = placementJson(AdMob.OPEN_AD_UNIT, Max.OPEN_AD_UNIT)
    fun interstitial() = placementJson(AdMob.INTERSTITIAL_AD_UNIT, Max.INTERSTITIAL_AD_UNIT)
    fun rewarded() = placementJson(AdMob.REWARDED_AD_UNIT, Max.REWARDED_AD_UNIT)
    fun banner() = placementJson(AdMob.BANNER_AD_UNIT, Max.BANNER_AD_UNIT)
    fun native() = placementJson(AdMob.NATIVE_AD_UNIT, "")

    private fun placementJson(admobUnit: String, maxUnit: String): String =
        JSONObject().apply {
            put("enable", true)
            put("timeout_ms", 10_000L)
            put("admob_unit", admobUnit)
            put("max_unit", maxUnit)
        }.toString()
}
