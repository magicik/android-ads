package com.library.ads

import android.app.Activity
import android.app.Application
import android.os.Bundle
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
                    if (checkCurrentScreenShowOpenAds()) {
                        if (_remoteReady.value) appOpenAdManager.showAdIfAvailable(
                            it, null
                        )
                    }
                }
            }

            else -> {}
        }
    }

    // ---- thêm scope & cờ sẵn sàng
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _remoteReady = MutableStateFlow(false)
    val remoteReady: StateFlow<Boolean> = _remoteReady

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
        appScope.launch {
            // 1) fetch remote trước
            val ok = remoteConfigProvider.fetchAndActivate()
            _remoteReady.value = true

            // 2) đọc provider sau khi fetch xong
            val provider = remoteConfigProvider.getAdProvider()

            // 3) init ads theo provider
            initAds()

            // 4) add lifecycle observer sau khi ads đã init
            ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleEventObserver)
        }
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

    suspend fun awaitRemoteReady() {
        if (!_remoteReady.value) {
            remoteReady.filter { it }.first()
        }
    }

    fun whenRemoteReady(block: () -> Unit) {
        if (_remoteReady.value) block() else {
            appScope.launch {
                remoteReady.filter { it }.first()
                block()
            }
        }
    }


    fun showAdIfAvailable(
        activity: Activity, onShowAdCompleteListener: OpenAdManager.OnShowAdCompleteListener
    ) {
        whenRemoteReady {
            appOpenAdManager.showAdIfAvailable(activity, onShowAdCompleteListener)
        }
    }

    suspend fun showAdIfAvailableSuspend(activity: Activity) {
        awaitRemoteReady()
        suspendCancellableCoroutine<Unit> { cont ->
            showAdIfAvailable(activity, object : OpenAdManager.OnShowAdCompleteListener {
                override fun onShowAdComplete() {
                    cont.resume(Unit)
                }
            })
        }
    }

    fun loadAd(activity: Activity, onLoadAdComplete: (() -> Unit)?) {
        whenRemoteReady {
            appOpenAdManager.loadAd(activity, onLoadAdComplete)
        }
    }

    suspend fun loadAdSuspend(activity: Activity) {
        awaitRemoteReady()
        suspendCancellableCoroutine<Unit> { cont ->
            loadAd(activity) {
                cont.resume(Unit)
            }
        }
    }

    suspend fun awaitIsOpenAdAvailable(): Boolean {
        awaitRemoteReady() // chờ remote config fetch xong
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