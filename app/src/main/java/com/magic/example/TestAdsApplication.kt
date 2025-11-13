package com.magic.example

import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.library.ads.AdsApplication
import com.library.ads.provider.config.AdRemoteConfigProvider
import com.library.ads.provider.config.BaseAdRemoteConfigProvider

class TestAdsApplication : AdsApplication() {
    override val admobOpenAdId: String
        get() = AdMob.OPEN_AD_UNIT
    override val maxOpenAdId: String
        get() = Max.OPEN_AD_UNIT
    override val maxSdkKey: String
        get() = Max.MAX_SDK_KEY
    override var remoteConfigProvider: AdRemoteConfigProvider = BaseAdRemoteConfigProvider()
    override val subscriptionProvider: () -> Boolean = { false }

    override fun onCreate() {
        val options: FirebaseOptions =
            FirebaseOptions.Builder().setApplicationId("1:1234567890:android:abc123") // Required
                .setApiKey("AIza...") // Required
                .setDatabaseUrl("https://your-project.firebaseio.com") // Optional
                .setProjectId("your-project-id") // Required
                .build()
        FirebaseApp.initializeApp(this, options)
        super.onCreate()
        handleUserSubscribe(true)
    }

    fun handleUserSubscribe(subscribed: Boolean) {
        onSubscriptionChanged(subscribed) // notify OpenAdManager
    }
}