package com.library.ads

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import com.library.ads.provider.config.ProviderAds
import com.library.ads.provider.open.OpenAdManager
import com.library.ads.provider.open.OpenAdManagerImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

abstract class AdsApplication : MultiDexApplication(), Application.ActivityLifecycleCallbacks,
    LifecycleObserver {
    var appOpenAdManager: OpenAdManager? = null

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
    protected open val subscriptionProvider: () -> Boolean = { false }

    private var lifecycleEventObserver = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_STOP -> {
                //your code here
            }

            Lifecycle.Event.ON_START -> {
                currentActivity?.let {
                    if (checkCurrentScreenShowOpenAds()) {
                        // only show when both remote+sdk ready and manager created
                        appScope.launch {
                            awaitRemoteAndSdkReady()
                            createAppOpenAdManagerIfNeeded()
                            appOpenAdManager?.showAdIfAvailable(it, null)
                        }
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

    private val _sdkReady = MutableStateFlow(false)
    val sdkReady: StateFlow<Boolean> = _sdkReady

    fun checkCurrentScreenShowOpenAds(): Boolean {
        val activity = currentActivity ?: return false
        val currentFragment = getCurrentFragment(activity)
        return currentFragment?.javaClass?.simpleName !in excludedScreen && activity.javaClass.simpleName !in excludedScreen
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
        if (isMainProcess()) {
            initAds()
        }
        appScope.launch {
            // 1) fetch remote trước
            try {
                remoteConfigProvider.fetchAndActivate()
            } catch (t: Throwable) {
                // ignore fetch errors, still mark remote ready so app can continue
            }
            _remoteReady.value = true

            // Wait until both remote and sdk ready
            awaitRemoteAndSdkReady()
            // Safe to create manager and pre-load
            createAppOpenAdManagerIfNeeded()

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

    suspend fun awaitRemoteAndSdkReady() {
        if (!(_remoteReady.value && _sdkReady.value)) {
            combine(remoteReady, sdkReady) { r, s -> r && s }.filter { it }.first()
        }
    }


    // Backwards compatible method name: will wait for both
    fun whenRemoteReady(block: () -> Unit) {
        if (_remoteReady.value && _sdkReady.value) block() else {
            appScope.launch {
                combine(remoteReady, sdkReady) { r, s -> r && s }.filter { it }.first()
                block()
            }
        }
    }

    /**
     * Ensure manager exists. Call only from Main thread (appScope is Main).
     */
    private fun createAppOpenAdManagerIfNeeded() {
        if (appOpenAdManager != null) return


        val provider = try {
            remoteConfigProvider.getAdProvider()
        } catch (t: Throwable) {
            ProviderAds.ADMOB.value
        }


        val impl = OpenAdManagerImpl(
            context = this@AdsApplication,
            admobAdUnitId = admobOpenAdId,
            maxAdUnitId = maxOpenAdId,
            remoteConfigProvider = remoteConfigProvider,
            subscriptionProvider = subscriptionProvider
        )
        appOpenAdManager = impl

        // Notify impl that sdk is ready so it can initialize internal adapters/helpers
        try {
            impl.createImplWhenReady(provider, true)
        } catch (t: Throwable) {
            // ignore - optional method
        }
    }

    fun showAdIfAvailable(
        activity: Activity, onShowAdCompleteListener: OpenAdManager.OnShowAdCompleteListener
    ) {
        // Ensure both ready and manager created; queue if not
        appScope.launch {
            awaitRemoteAndSdkReady()
            createAppOpenAdManagerIfNeeded()
            appOpenAdManager?.showAdIfAvailable(activity, onShowAdCompleteListener) ?: run {
                // fallback: call complete so caller can continue
                onShowAdCompleteListener.onShowAdComplete()
            }
        }
    }

    suspend fun showAdIfAvailableSuspend(activity: Activity) {
        awaitRemoteAndSdkReady()
        createAppOpenAdManagerIfNeeded()


        suspendCancellableCoroutine<Unit> { cont ->
            val resumed = AtomicBoolean(false)
            fun resumeOnce() {
                if (resumed.compareAndSet(false, true)) {
                    if (cont.isActive) {
                        cont.resume(Unit)
                    }
                }
            }
            appOpenAdManager?.showAdIfAvailable(
                activity,
                object : OpenAdManager.OnShowAdCompleteListener {
                    override fun onShowAdComplete() {
                        resumeOnce()
                    }
                }) ?: run {
                resumeOnce()
            }

            cont.invokeOnCancellation {
                // optional: cancel ad showing if your manager supports it
            }

        }
    }

    fun loadAd(activity: Activity?, onLoadAdComplete: (() -> Unit)?) {
        appScope.launch {
            awaitRemoteAndSdkReady()
            createAppOpenAdManagerIfNeeded()
            appOpenAdManager?.loadAd(activity, onLoadAdComplete) ?: onLoadAdComplete?.invoke()
        }
    }


    suspend fun loadAdSuspend(activity: Activity) {
        awaitRemoteAndSdkReady()
        createAppOpenAdManagerIfNeeded()
        suspendCancellableCoroutine<Unit> { cont ->
            val resumed = AtomicBoolean(false)
            fun resumeOnce() {
                if (resumed.compareAndSet(false, true)) {
                    if (cont.isActive) {
                        cont.resume(Unit)
                    }
                }
            }
            appOpenAdManager?.loadAd(activity) {
                resumeOnce()
            } ?: resumeOnce()
        }
    }


    suspend fun awaitIsOpenAdAvailable(): Boolean {
        awaitRemoteAndSdkReady() // chờ remote config + sdk fetch xong
        createAppOpenAdManagerIfNeeded()
        return appOpenAdManager?.isAdAvailable() ?: false
    }


    fun initAds() {
// Init AdMob
        MobileAds.initialize(this) {}


// Ensure mediation provider set as early as possible
        try {
            AppLovinSdk.getInstance(this).settings.setVerboseLogging(true)
            val initConfig = AppLovinSdkInitializationConfiguration.builder(maxSdkKey)
                .setMediationProvider(AppLovinMediationProvider.MAX).build()


            AppLovinSdk.getInstance(this).initialize(initConfig) { sdkConfig ->
                _sdkReady.value = true
            }
        } catch (t: Throwable) {
            // If AppLovin library not available or initialize fails, still mark sdk ready to avoid blocking
            Handler(Looper.getMainLooper()).post {
                _sdkReady.value = true
            }
        }
    }


    fun onSubscriptionChanged(subscribed: Boolean) {
        // forward to manager if present
        appOpenAdManager?.onSubscriptionChanged(subscribed)
    }


    private fun isMainProcess(): Boolean {
        val pid = android.os.Process.myPid()
        val manager = getSystemService(ACTIVITY_SERVICE) as? ActivityManager ?: return true
        val myProcess = manager.runningAppProcesses?.firstOrNull { it.pid == pid }
        return myProcess?.processName == packageName
    }
}