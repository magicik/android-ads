package com.magic.example

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.library.ads.provider.interstitial.InterstitialAdManager
import com.library.ads.provider.interstitial.InterstitialAdManagerImpl
import com.library.ads.provider.reward.RewardAdManager
import com.library.ads.provider.reward.RewardAdManagerImpl
import com.library.ads.provider.reward.RewardShowListener
import com.magic.example.databinding.ActivityTestAdsBinding
import kotlinx.coroutines.launch

class TestAdsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityTestAdsBinding
    lateinit var interstitialAdManager: InterstitialAdManager
    lateinit var rewardManager: RewardAdManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        interstitialAdManager = InterstitialAdManagerImpl(
            context = this,
            remoteConfigProvider = (application as TestAdsApplication).remoteConfigProvider,
            admobAdUnitId = AdMob.INTERSTITIAL_AD_UNIT,
            maxAdUnitId = Max.INTERSTITIAL_AD_UNIT
        )
        rewardManager = RewardAdManagerImpl(
            context = this,
            admobUnit = AdMob.REWARDED_AD_UNIT,
            maxUnit = Max.REWARDED_AD_UNIT,
            remoteConfig = (application as TestAdsApplication).remoteConfigProvider,
        )
        rewardManager.load(this)
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
                this, object : InterstitialAdManager.OnShowAdCompleteListener {
                    override fun onShowAdComplete() {
                        Toast.makeText(this@TestAdsActivity, "Ad Closed", Toast.LENGTH_LONG).show()
                    }

                })
        }
        binding.btnRewardAds.setOnClickListener {
            rewardManager.show(this, object : RewardShowListener {
                override fun onUserEarnedReward(amount: Int, type: String) {
                    Log.d("TestAdsActivity", "Earn reward")
                }

                override fun onAdClosed() {
                    Log.d("TestAdsActivity", "Reward closed")
                }

                override fun onShowFailed(error: String?) {
                    Log.d("TestAdsActivity", "Reward show false")
                }

            })
        }
        binding.adBanner.setProvider((application as TestAdsApplication).remoteConfigProvider.getAdProvider())
        binding.adBanner.setAdUnitIdForCurrentProvider("ca-app-pub-3940256099942544/2014213617", "")
        binding.adBanner.load()
    }
}