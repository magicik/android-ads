package com.magic.ads.core

import java.util.concurrent.atomic.AtomicInteger

/**
 * App-wide "is any dialog covering the screen right now" flag. [NativeAdManager]'s auto-refresh
 * cycle checks this (alongside [AdsManager.isShowingFullScreenAd]) before swapping a visible
 * native ad's content out from under the user — a screen that shows its own dialogs (e.g. a
 * native ad inside a dialog) should call [onDialogShown]/[onDialogDismissed] around that.
 */
object DialogVisibilityTracker {

    private val activeCount = AtomicInteger(0)

    fun isAnyDialogShowing(): Boolean = activeCount.get() > 0

    fun onDialogShown() {
        activeCount.incrementAndGet()
    }

    fun onDialogDismissed() {
        activeCount.updateAndGet { (it - 1).coerceAtLeast(0) }
    }
}
