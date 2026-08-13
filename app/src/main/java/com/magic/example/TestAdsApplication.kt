package com.magic.example

import android.app.Application
import com.magic.ads.config.PlacementConfig
import com.magic.ads.core.AdsConfig
import com.magic.ads.core.AdsManager
import com.magic.ads.format.OpenAdManager
import com.magic.ads.helper.AppOpenResumeHelper
import com.magic.ads.provider.AdMobProvider
import com.magic.ads.provider.MaxProvider

class TestAdsApplication : Application() {

    private val openAdManager = OpenAdManager()

    lateinit var appOpenResumeHelper: AppOpenResumeHelper
        private set

    override fun onCreate() {
        super.onCreate()

        AdsManager.registerProvider(AdMobProvider())
        AdsManager.registerProvider(MaxProvider())
        // Normally read from the app's own remote config ("admob"/"max"); hard-coded for the demo.
        AdsManager.setActiveProvider("admob")

        AdsManager.initialize(
            context = this,
            config = AdsConfig.Builder()
                .setMaxSdkKey(Max.MAX_SDK_KEY)
                .build()
        )

        appOpenResumeHelper = AppOpenResumeHelper(
            application = this,
            adManager = openAdManager,
            config = PlacementConfig.fromJson(Placements.open())
        )
    }
}
