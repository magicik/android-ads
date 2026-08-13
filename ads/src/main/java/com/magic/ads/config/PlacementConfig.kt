package com.magic.ads.config

import org.json.JSONObject

/**
 * Config for a single ad placement, sourced from a raw JSON string the app already fetched
 * from wherever it keeps remote config (Firebase, its own backend, ...) — this library never
 * fetches or interprets remote config itself, it only parses the value it's handed.
 *
 * No waterfall: at most one ad unit per network. If [admobUnit]/[maxUnit] (whichever matches
 * the active provider) fails to load, the placement simply has no ad — callers do not retry
 * with a different unit.
 */
data class PlacementConfig(
    val enable: Boolean = false,
    val timeoutMs: Long = 10_000L,
    val admobUnit: String = "",
    val maxUnit: String = ""
) {
    fun adUnitFor(providerName: String): String = when (providerName) {
        "max" -> maxUnit
        else -> admobUnit
    }

    companion object {
        fun fromJson(json: String): PlacementConfig {
            return try {
                val obj = JSONObject(json)
                PlacementConfig(
                    enable = obj.optBoolean("enable", false),
                    timeoutMs = obj.optLong("timeout_ms", 10_000L),
                    admobUnit = obj.optString("admob_unit", ""),
                    maxUnit = obj.optString("max_unit", "")
                )
            } catch (e: Exception) {
                PlacementConfig()
            }
        }
    }
}
