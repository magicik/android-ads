package com.magic.example

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.library.ads.provider.interstitial.InterstitialAdManager
import com.library.ads.provider.interstitial.InterstitialAdManagerImpl
import com.magic.example.databinding.ActivityTestAdsBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class TestAdsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityTestAdsBinding
    lateinit var interstitialAdManager: InterstitialAdManager
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        interstitialAdManager = InterstitialAdManagerImpl(
            context = this,
            remoteConfigProvider = (application as TestAdsApplication).remoteConfigProvider,
            admobAdUnitId = AdMob.INTERSTITIAL_AD_UNIT,
            maxAdUnitId = Max.INTERSTITIAL_AD_UNIT
        )
        interstitialAdManager.loadAd()
        (application as TestAdsApplication).loadAd(this, null)
        binding = ActivityTestAdsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnOpenAds.setOnClickListener {
            lifecycleScope.launch {
                (application as TestAdsApplication).awaitRemoteReady()
                (application as TestAdsApplication).showAdIfAvailableSuspend(this@TestAdsActivity)
            }
        }

        binding.btnInterAds.setOnClickListener {
            interstitialAdManager.showAd(
                this,
                object : InterstitialAdManager.OnShowAdCompleteListener {
                    override fun onShowAdComplete() {
                        Toast.makeText(this@TestAdsActivity, "Ad Closed", Toast.LENGTH_LONG).show()
                    }

                })
        }
        binding.adBanner.setProvider((application as TestAdsApplication).remoteConfigProvider.getAdProvider())
        binding.adBanner.load()
    }
}