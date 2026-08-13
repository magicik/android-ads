package com.magic.ads.listener

import android.app.Activity

/**
 * Optional hook for [com.magic.ads.helper.AppOpenResumeHelper]. Since app-open ads are
 * preloaded ahead of time (never fetched reactively at show time — see [AppOpenResumeHelper]'s
 * class doc), there's no "loading" moment to show a spinner for: a resume either shows an
 * already-cached ad instantly or shows nothing at all.
 */
interface AppOpenLoadingListener {
    /** Fires once the resume's ad decision is settled — whether an ad was shown and dismissed,
     * or none was ready to show. Use this to unblock/continue whatever the app was waiting on
     * (e.g. dismiss its own splash screen). */
    fun onAdFinished(activity: Activity)
}
