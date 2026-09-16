package com.magic.example

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.drawable.toDrawable
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.magic.ads.config.PlacementConfig
import com.magic.ads.core.AdsManager
import com.magic.ads.format.InterstitialAdManager
import com.magic.ads.format.NativeAdManager
import com.magic.ads.format.RewardedAdManager
import com.magic.ads.listener.AdCallback
import com.magic.ads.listener.NativeCallback
import com.magic.ads.listener.RewardCallback
import com.magic.ads.native_ad.NativeAdStyle
import com.magic.ads.native_ad.NativeAdViewBinder
import com.magic.ads.native_ad.NativeLayoutType
import com.magic.example.databinding.ActivityTestAdsBinding
import com.magic.example.databinding.DialogTestBinding

private const val TAG = "TestAdsActivity"

class TestAdsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTestAdsBinding

    private val interstitialAdManager = InterstitialAdManager("demo_interstitial")
    private val rewardedAdManager = RewardedAdManager("demo_rewarded")

    // One NativeAdManager per on-screen slot, each with its own placementKey so AdPool pools
    // them independently — each also holds its own loaded NativeAd instance locally (AdMob
    // doesn't allow reusing one NativeAd across multiple views).
    private val nativeAdManagerSmall = NativeAdManager("demo_native_small")
    private val nativeAdManagerMedium = NativeAdManager("demo_native_medium")
    private val nativeAdManagerLarge = NativeAdManager("demo_native_large")

    // Native SMALL loaded into the old banner slot — tests it as a drop-in banner replacement.
    private val nativeAdManagerBanner = NativeAdManager("demo_native_banner")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTestAdsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        AdsManager.whenInitialized {
            interstitialAdManager.loadAd(this, PlacementConfig.fromJson(Placements.interstitial()))
            rewardedAdManager.loadAd(this, PlacementConfig.fromJson(Placements.rewarded()))
            loadNativeAds()
        }

        binding.btnOpenAds.setOnClickListener {
            Toast.makeText(
                this,
                "App-open ads show automatically on real app resume — background the app and reopen it",
                Toast.LENGTH_LONG
            ).show()
        }

        binding.btnInterAds.setOnClickListener {
            val shown = interstitialAdManager.showAd(this, object : AdCallback {
                override fun onAdDismissed() {
                    Toast.makeText(this@TestAdsActivity, "Ad closed", Toast.LENGTH_SHORT).show()
                    interstitialAdManager.loadAd(this@TestAdsActivity, PlacementConfig.fromJson(Placements.interstitial()))
                }
                override fun onAdFailedToLoad(errorCode: Int, errorMessage: String) {
                    Toast.makeText(this@TestAdsActivity, "Interstitial not ready: $errorMessage", Toast.LENGTH_SHORT).show()
                }
            })
            if (!shown) Log.d(TAG, "Interstitial not shown")
        }

        binding.btnRewardAds.setOnClickListener {
            rewardedAdManager.showAd(this, object : RewardCallback {
                override fun onUserEarnedReward(rewardType: String, rewardAmount: Int) {
                    Toast.makeText(this@TestAdsActivity, "Earned $rewardAmount $rewardType", Toast.LENGTH_SHORT).show()
                }
                override fun onAdDismissed() {
                    rewardedAdManager.loadAd(this@TestAdsActivity, PlacementConfig.fromJson(Placements.rewarded()))
                }
                override fun onAdFailedToLoad(errorCode: Int, errorMessage: String) {
                    Toast.makeText(this@TestAdsActivity, "Reward not ready: $errorMessage", Toast.LENGTH_SHORT).show()
                }
            })
        }

        binding.btnShowDialog.setOnClickListener { showNativeDialog() }

        binding.btnRemoveAds.setOnClickListener {
            AdsManager.setPremium(this, true)
            Toast.makeText(this, "Premium enabled — banner/native torn down automatically", Toast.LENGTH_SHORT).show()
        }
    }

    // Tier 1: built-in layouts, all three sizes, all cosmetically restyled to match this
    // app's theme via the same NativeAdStyle — proves style application is uniform across
    // SMALL/MEDIUM/LARGE despite their different view hierarchies. The container-aware
    // loadAd() overload shows a shimmer skeleton automatically while each one loads.
    private fun loadNativeAds() {
        val style = NativeAdStyle(
            backgroundColor = Color.parseColor("#F5F5F5"),
            cornerRadiusDp = 12f,
            headlineTextColor = Color.BLACK,
            bodyTextColor = Color.DKGRAY,
            ctaBackgroundRes = R.drawable.bg_button,
            ctaTextColor = Color.WHITE
        )
        val config = PlacementConfig.fromJson(Placements.native())
        nativeAdManagerSmall.loadAd(this, binding.nativeAdSmall, config, NativeLayoutType.SMALL, style)
        nativeAdManagerMedium.loadAd(this, binding.nativeAdMedium, config, NativeLayoutType.MEDIUM, style)
        nativeAdManagerLarge.loadAd(this, binding.nativeAdLarge, config, NativeLayoutType.LARGE, style)
        nativeAdManagerBanner.loadAd(this, binding.nativeAdBanner, config, NativeLayoutType.SMALL, style)
    }

    // Tier 2: fully custom layout supplied by the app via NativeAdViewBinder — no built-in
    // layout/style involved at all.
    private fun showNativeDialog() {
        val dialogBinding = DialogTestBinding.inflate(LayoutInflater.from(this))
        val dialogNativeAdManager = NativeAdManager("demo_native_dialog")

        dialogNativeAdManager.loadAd(this, PlacementConfig.fromJson(Placements.native()), object : NativeCallback {
            override fun onAdLoaded() {
                dialogNativeAdManager.showAd(dialogBinding.nativeAd, DialogNativeAdViewBinder())
            }
            override fun onAdFailedToLoad(errorCode: Int, errorMessage: String) {
                Log.d(TAG, "Dialog native ad failed: $errorMessage")
            }
        })

        val alertDialog = AlertDialog.Builder(this, R.style.DialogTheme).create()
        alertDialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        alertDialog.setView(dialogBinding.root)
        alertDialog.setCancelable(true)
        alertDialog.setOnDismissListener { dialogNativeAdManager.destroyCurrentAd() }
        dialogBinding.btn1.setOnClickListener { alertDialog.dismiss() }
        dialogBinding.btn2.setOnClickListener { alertDialog.dismiss() }
        alertDialog.show()
    }

    override fun onDestroy() {
        nativeAdManagerSmall.destroyCurrentAd()
        nativeAdManagerMedium.destroyCurrentAd()
        nativeAdManagerLarge.destroyCurrentAd()
        nativeAdManagerBanner.destroyCurrentAd()
        super.onDestroy()
    }

    /** Minimal hand-built layout to prove Tier 2 doesn't depend on any library-provided XML. */
    private class DialogNativeAdViewBinder : NativeAdViewBinder {
        override fun createView(context: Context): NativeAdView {
            val headline = TextView(context).apply {
                textSize = 16f
                setTypeface(typeface, Typeface.BOLD)
            }
            val body = TextView(context).apply { textSize = 13f }
            val cta = Button(context)
            val column = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(24, 24, 24, 24)
                addView(headline)
                addView(body)
                addView(cta)
            }
            return NativeAdView(context).apply {
                addView(column)
                headlineView = headline
                bodyView = body
                callToActionView = cta
            }
        }

        override fun bind(view: NativeAdView, nativeAd: NativeAd) {
            (view.headlineView as TextView).text = nativeAd.headline
            (view.bodyView as TextView).text = nativeAd.body
            (view.callToActionView as Button).text = nativeAd.callToAction
            view.setNativeAd(nativeAd)
        }
    }
}
