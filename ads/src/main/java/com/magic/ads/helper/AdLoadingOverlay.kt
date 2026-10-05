package com.magic.ads.helper

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ProgressBar

/**
 * Full-screen, touch-blocking loading layer: a semi-transparent scrim (the screen underneath
 * stays visible through it) with a [ProgressBar] in the centre. Added to the activity's decor
 * view, so it covers the whole window and needs no Dialog.
 */
internal class AdLoadingOverlay {

    private var view: View? = null

    val isShowing: Boolean get() = view != null

    fun show(activity: Activity) {
        if (view != null || activity.isFinishing || activity.isDestroyed) return
        val root = activity.window?.decorView as? ViewGroup ?: return
        val density = activity.resources.displayMetrics.density
        val overlay = FrameLayout(activity).apply {
            setBackgroundColor(SCRIM_COLOR)
            isClickable = true
            isFocusable = true
            addView(
                ProgressBar(activity).apply {
                    indeterminateTintList = ColorStateList.valueOf(Color.WHITE)
                },
                FrameLayout.LayoutParams((PROGRESS_SIZE_DP * density).toInt(), (PROGRESS_SIZE_DP * density).toInt(), Gravity.CENTER)
            )
        }
        root.addView(
            overlay,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        view = overlay
    }

    fun hide() {
        val overlay = view ?: return
        view = null
        (overlay.parent as? ViewGroup)?.removeView(overlay)
    }

    private companion object {
        const val SCRIM_COLOR = 0x99000000.toInt()
        const val PROGRESS_SIZE_DP = 48
    }
}
