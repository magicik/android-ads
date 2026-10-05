package com.magic.ads.listener

import android.app.Activity

/**
 * Optional hook for [com.magic.ads.helper.AppOpenResumeHelper]. The first return to the app
 * may load an ad reactively behind a full-screen loading overlay (shown on arrival only within a short window); every later return
 * shows an already-cached ad instantly or nothing — see [AppOpenResumeHelper]'s class doc.
 */
interface AppOpenLoadingListener {
    /** Fires once the resume's ad decision is settled — whether an ad was shown and dismissed,
     * or none was ready to show. Use this to unblock/continue whatever the app was waiting on
     * (e.g. dismiss its own splash screen). */
    fun onAdFinished(activity: Activity)
}
