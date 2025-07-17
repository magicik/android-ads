package com.library.ads

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.multidex.MultiDexApplication
import com.applovin.sdk.AppLovinMediationProvider
import com.applovin.sdk.AppLovinSdk
import com.applovin.sdk.AppLovinSdkInitializationConfiguration
import com.google.android.gms.ads.MobileAds
import com.google.firebase.FirebaseApp
import com.library.ads.provider.config.AdRemoteConfigProvider
import com.library.ads.provider.open.OpenAdManager
import com.library.ads.provider.open.OpenAdManagerImpl
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

abstract class AdsApplication : MultiDexApplication(), Application.ActivityLifecycleCallbacks,
    LifecycleObserver {
    lateinit var appOpenAdManager: OpenAdManager
    //Admob Open Unit Id
    abstract val admobOpenAdId: String
    //Max Open Unit Id
    abstract val maxOpenAdId: String
    //MAX_SDK_KEY
    abstract val maxSdkKey: String
    ///Remote config
    abstract var remoteConfigProvider: AdRemoteConfigProvider
    protected var currentActivity: Activity? = null
    //Fragments or activities with this name will not show open ads.
    protected open var excludedScreen: List<String> = listOf("AdActivity")
    private var lifecycleEventObserver = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_STOP -> {
                //your code here
            }

            Lifecycle.Event.ON_START -> {
                currentActivity?.let {
                    if (checkCurrentScreenShowOpenAds()) appOpenAdManager.showAdIfAvailable(
                        it, null
                    )
                }
            }

            else -> {}
        }
    }

    fun checkCurrentScreenShowOpenAds(): Boolean {
        val currentFragment = getCurrentFragment(currentActivity!!)
        return currentFragment?.javaClass?.simpleName !in excludedScreen && currentActivity?.javaClass?.simpleName !in excludedScreen
    }

    fun getCurrentFragment(activity: Activity): Fragment? {
        if (activity is FragmentActivity) {
            val navHostFragment = activity.supportFragmentManager.primaryNavigationFragment
            return navHostFragment?.childFragmentManager?.fragments?.firstOrNull()
        }
        return null
    }

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(this)
        FirebaseApp.initializeApp(this)
        remoteConfigProvider.fetchAndActivate()
        val provider = remoteConfigProvider.getAdProvider()
        initAds()
        ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleEventObserver)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {

    }

    override fun onActivityStarted(activity: Activity) {
        currentActivity = activity
    }

    override fun onActivityResumed(activity: Activity) {
    }

    override fun onActivityPaused(activity: Activity) {
    }

    override fun onActivityStopped(activity: Activity) {
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
    }

    override fun onActivityDestroyed(activity: Activity) {
    }

    fun showAdIfAvailable(
        activity: Activity, onShowAdCompleteListener: OpenAdManager.OnShowAdCompleteListener
    ) {
        appOpenAdManager.showAdIfAvailable(activity, onShowAdCompleteListener)
    }

    suspend fun showAdIfAvailableSuspend(activity: Activity) =
        suspendCancellableCoroutine<Unit> { cont ->
            showAdIfAvailable(activity, object : OpenAdManager.OnShowAdCompleteListener {
                override fun onShowAdComplete() {
                    cont.resume(Unit)
                }
            })
        }

    fun loadAd(activity: Activity, onLoadAdComplete: (() -> Unit)?) {
        appOpenAdManager.loadAd(activity, onLoadAdComplete)
    }

    suspend fun loadAdSuspend(activity: Activity) = suspendCancellableCoroutine<Unit> { cont ->
        loadAd(activity) {
            cont.resume(Unit)
        }
    }

    fun isOpenAdAvailable(): Boolean {
        return appOpenAdManager.isAdAvailable()
    }

    fun initAds() {
        MobileAds.initialize(this) {}
        val initConfig = AppLovinSdkInitializationConfiguration.builder(maxSdkKey, this)
            .setMediationProvider(AppLovinMediationProvider.MAX).build()
        AppLovinSdk.getInstance(this).initialize(initConfig) { sdkConfig ->
        }
        appOpenAdManager = OpenAdManagerImpl(
            context = this,
            admobAdUnitId = admobOpenAdId,
            maxAdUnitId = maxOpenAdId,
            remoteConfigProvider = remoteConfigProvider
        )
    }
}