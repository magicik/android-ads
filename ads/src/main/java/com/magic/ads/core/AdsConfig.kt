package com.magic.ads.core

data class AdsConfig(
    val testDevices: List<String> = emptyList(),
    val isDebug: Boolean = false,
    val maxSdkKey: String = ""
) {
    class Builder {
        private val testDevices = mutableListOf<String>()
        private var isDebug = false
        private var maxSdkKey = ""

        fun addTestDevice(deviceId: String) = apply { testDevices.add(deviceId) }

        fun setDebug(debug: Boolean) = apply { isDebug = debug }

        fun setMaxSdkKey(key: String) = apply { maxSdkKey = key }

        fun build() = AdsConfig(
            testDevices = testDevices.toList(),
            isDebug = isDebug,
            maxSdkKey = maxSdkKey
        )
    }
}
