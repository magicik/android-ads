package com.library.ads.provider.native_ad

import android.graphics.Rect
import android.view.View
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList

object NativeVisibilityManager {
    private val entries = CopyOnWriteArrayList<Entry>()
    private val mainScope = MainScope()

    var hideUsingInvisible: Boolean = true

    data class Entry(
        val viewRef: WeakReference<View>,
        var priority: Int,
        var isModal: Boolean = false
    )

    fun register(view: View, priority: Int = 0, isModal: Boolean = false) {
        unregister(view)
        entries.add(Entry(WeakReference(view), priority, isModal))
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) { /* no-op */ }
            override fun onViewDetachedFromWindow(v: View) {
                unregister(v)
                v.removeOnAttachStateChangeListener(this)
            }
        })
        // recompute visibility on main thread
        mainScope.launch { recomputeVisibility() }
    }

    fun unregister(view: View) {
        var removed = false
        val itr = entries.iterator()
        while (itr.hasNext()) {
            val e = itr.next()
            val v = e.viewRef.get()
            if (v == null || v == view) {
                entries.remove(e)
                removed = true
            }
        }
        if (removed) mainScope.launch { recomputeVisibility() }
    }

    fun update(view: View, priority: Int = 0, isModal: Boolean = false) {
        entries.forEach { e ->
            val v = e.viewRef.get()
            if (v == view) {
                e.priority = priority
                e.isModal = isModal
            }
        }
        mainScope.launch { recomputeVisibility() }
    }

    private fun recomputeVisibility() {
        val alive = entries.filter { it.viewRef.get() != null }
        entries.clear()
        entries.addAll(alive)

        val modal = entries.filter { it.isModal && it.viewRef.get()?.isShown == true }
            .maxByOrNull { it.priority }

        if (modal != null) {
            val modalView = modal.viewRef.get()!!
            entries.forEach { e ->
                val v = e.viewRef.get()
                if (v == null) return@forEach
                if (v == modalView) {
                    setVisible(v, true)
                } else {
                    if (e.priority < modal.priority) setVisible(v, false) else {
                        val overlap = viewsOverlap(modalView, v)
                        if (overlap) setVisible(v, false) else setVisible(v, true)
                    }
                }
            }
            return
        }

        val shownEntries = entries.filter { it.viewRef.get()?.isShown == true }
        if (shownEntries.isEmpty()) {
            entries.forEach { e -> e.viewRef.get()?.let { setVisible(it, true) } }
            return
        }

        val sorted = shownEntries.sortedByDescending { it.priority }

        val top = sorted.first()
        val topView = top.viewRef.get() ?: return
        setVisible(topView, true)
        for (e in sorted.drop(1)) {
            val v = e.viewRef.get() ?: continue
            val overlap = viewsOverlap(topView, v)
            if (overlap) {
                setVisible(v, false)
            } else {
                setVisible(v, false)
            }
        }
    }

    private fun setVisible(view: View, visible: Boolean) {
        if (visible) {
            view.visibility = View.VISIBLE
            view.alpha = 1f
            view.isClickable = true
        } else {
            if (hideUsingInvisible) {
                view.visibility = View.INVISIBLE
            } else {
                view.visibility = View.GONE
            }
            view.alpha = 0f
            view.isClickable = false
        }
    }

    private fun viewsOverlap(a: View, b: View): Boolean {
        if (!a.isShown || !b.isShown) return false
        val ra = Rect(); val rb = Rect()
        val ok1 = a.getGlobalVisibleRect(ra)
        val ok2 = b.getGlobalVisibleRect(rb)
        if (!ok1 || !ok2) return false
        return Rect.intersects(ra, rb)
    }
}
