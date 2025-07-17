package com.library.ads.provider.native

import android.content.Context
import android.util.Log
import android.view.ViewGroup
import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxError
import com.applovin.mediation.nativeAds.MaxNativeAdListener
import com.applovin.mediation.nativeAds.MaxNativeAdLoader
import com.applovin.mediation.nativeAds.MaxNativeAdView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.library.ads.provider.config.EnumAds

class NativeAdManager(
    private val context: Context,
    private val adContainer: ViewGroup,
    private val provider: String,
    private val adBinder: NativeAdBinder
) {

    private var maxNativeAdLoader: MaxNativeAdLoader? = null
    private var maxNativeAdView: MaxNativeAdView? = null
    private var admobNativeAd: NativeAd? = null
    private var loadedNativeAd: MaxAd? = null

    fun loadAd(adUnitId: String) {
        when (provider) {
            EnumAds.ADMOB.value -> loadAdmobNativeAd(adUnitId)
            EnumAds.MAX.value -> loadMaxNativeAd(adUnitId)
        }
    }

    private fun loadMaxNativeAd(adUnitId: String) {
        val view = adBinder.bindMax(context)
        if (maxNativeAdLoader == null) {
            maxNativeAdLoader = MaxNativeAdLoader(adUnitId, context).apply {
                setNativeAdListener(object : MaxNativeAdListener() {
                    override fun onNativeAdLoaded(nativeAdView: MaxNativeAdView?, ad: MaxAd) {
                        maxNativeAdView?.let { maxNativeAdLoader!!.destroy(loadedNativeAd) }
                        maxNativeAdView = nativeAdView
                        loadedNativeAd = ad
                        nativeAdView?.let {
                            adContainer.removeAllViews()
                            adContainer.addView(it)
                        }
                    }

                    override fun onNativeAdLoadFailed(adUnitId: String, error: MaxError) {
                        Log.e("NativeAdManager", "MAX ad failed: ${error.message}")
                        Log.d("NativeAdManager", "Waterfall: ${error.waterfall}")
                    }
                })
            }
        }

        maxNativeAdLoader?.loadAd(view)
    }

    private fun loadAdmobNativeAd(adUnitId: String) {
        val builder = AdLoader.Builder(context, adUnitId).forNativeAd { nativeAd ->
                admobNativeAd?.destroy()
                admobNativeAd = nativeAd

                val view = adBinder.bindAdMob(context, nativeAd)
                view?.let {
                    adContainer.removeAllViews()
                    adContainer.addView(it)
                }
            }.withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e("NativeAdManager", "AdMob ad failed: ${error.message}")
                }
            })

        builder.build().loadAd(AdRequest.Builder().build())
    }

    fun destroy() {
        admobNativeAd?.destroy()
        admobNativeAd = null

        if (loadedNativeAd != null) {
            maxNativeAdLoader?.destroy(loadedNativeAd)
        }
        maxNativeAdLoader?.destroy()
    }
}
