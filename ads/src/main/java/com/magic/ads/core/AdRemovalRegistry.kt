package com.magic.ads.core

import java.lang.ref.WeakReference

interface RemovableAd {
    /** Destroy the current ad and hide/clear whatever container it was shown in. */
    fun forceRemoveAd()
}

/**
 * Tracks every live Banner/Native ad manager instance (weakly, so it never leaks an
 * Activity/Fragment) so [AdsManager.setPremium] can shut them all down at once without
 * every screen having to register a listener or call onSubscriptionChanged itself.
 */
object AdRemovalRegistry {

    private val ads = mutableListOf<WeakReference<RemovableAd>>()

    fun register(ad: RemovableAd) {
        ads.removeAll { it.get() == null }
        ads.add(WeakReference(ad))
    }

    fun removeAllAds() {
        ads.forEach { it.get()?.forceRemoveAd() }
        ads.clear()
    }
}
