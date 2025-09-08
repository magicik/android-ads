package com.library.ads.provider.config

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

open class BaseAdRemoteConfigProvider : AdRemoteConfigProvider {
    protected val remoteConfig: FirebaseRemoteConfig by lazy { FirebaseRemoteConfig.getInstance() }

    companion object {
        private const val DEFAULT_INTER_ENABLED = true
        private const val DEFAULT_OPEN_ENABLED = true
        private const val DEFAULT_INTER_INTERVAL_SECONDS = 60
        private const val DEFAULT_OPEN_INTERVAL_SECONDS = 60

        val DEFAULTS: Map<String, Any> = mapOf(
            "ad_inter_enabled" to DEFAULT_INTER_ENABLED,
            "ad_open_enabled" to DEFAULT_OPEN_ENABLED,
            "ad_inter_interval" to DEFAULT_INTER_INTERVAL_SECONDS,
            "ad_open_interval" to DEFAULT_OPEN_INTERVAL_SECONDS,
            "ad_provider" to EnumAds.ADMOB.value ///max || admob
        )
    }

    override suspend fun fetchAndActivate() {
        suspendCancellableCoroutine { cont ->
            val settings =
                FirebaseRemoteConfigSettings.Builder().setMinimumFetchIntervalInSeconds(3600)
                    .build()

            remoteConfig.setConfigSettingsAsync(settings)

            // Chỉ set default của ads
            remoteConfig.setDefaultsAsync(DEFAULTS)

            remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
                cont.resume(task.isSuccessful)
            }
        }
    }

    override fun isOpenAdEnabled(): Boolean {
        return remoteConfig.getBoolean("ad_open_enabled")
    }

    override fun isInterstitialAdEnabled(): Boolean {
        return remoteConfig.getBoolean("ad_inter_enabled")
    }

    override fun getInterstitialAdInterval(): Int {
        return remoteConfig.getLong("ad_inter_interval").toInt()
    }

    override fun getOpenAdInterval(): Int {
        return remoteConfig.getLong("ad_open_interval").toInt()
    }

    override fun getAdProvider(): String {
        return remoteConfig.getString("ad_provider").lowercase()
    }
}

interface AdRemoteConfigProvider {
    fun isInterstitialAdEnabled(): Boolean
    fun isOpenAdEnabled(): Boolean
    fun getInterstitialAdInterval(): Int
    fun getOpenAdInterval(): Int
    fun getAdProvider(): String
    suspend fun fetchAndActivate()
}

enum class EnumAds(val value: String) {
    ADMOB("admob"), MAX("max");
}