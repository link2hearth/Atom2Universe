package com.Atom2Universe.app.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inspector.WindowInspector
import android.widget.PopupWindow
import androidx.core.view.ViewCompat

/** Use public window inspection for native menus whose popup window is not exposed. */
internal fun refreshOwnedPopupWindows(context: Context) {
    if (Build.VERSION.SDK_INT < 29) return
    val owner = context.findActivity() ?: return
    if (owner.isFinishing || owner.isDestroyed) return
    WindowInspector.getGlobalWindowViews().forEach { root ->
        if (root !== owner.window.decorView && root.isAttachedToWindow &&
            root.context.findActivity() === owner) {
            applyPopupBars(root, root.context)
            followWindowFocus(root) { applyPopupBars(root, root.context) }
        }
    }
}

/** Prefer a focused dialog's content to a system toast while the activity is underneath it. */
internal fun focusedContentRoot(activity: Activity): View? {
    val content = activity.findViewById<View>(android.R.id.content)
    if (content?.isAttachedToWindow == true && content.hasWindowFocus()) return content
    if (Build.VERSION.SDK_INT >= 29) {
        for (root in WindowInspector.getGlobalWindowViews()) {
            if (root.isAttachedToWindow && root.hasWindowFocus() && root.context.findActivity() === activity) {
                // Anchored menus have no android.R.id.content: don't insert feedback into their rows.
                root.findViewById<View>(android.R.id.content)?.let { return it }
            }
        }
    }
    return null
}

private fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper && current !is Activity) {
        val base = current.baseContext
        if (base === current) return null
        current = base
    }
    return current as? Activity
}

@Suppress("DEPRECATION")
private fun applyPopupBars(root: View, context: Context) {
    if (Build.VERSION.SDK_INT >= 30) {
        ViewCompat.getWindowInsetsController(root)?.let { applySystemBarsPreference(it, context) }
    } else {
        // Pre-Android 11 insets controllers can resolve the activity's window instead of the
        // popup's window. Apply legacy flags directly to the actual popup root on those devices.
        val visibilityFlags = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        val appearanceFlags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        val hiddenFlags = if (SystemBarsManager.shouldShowSystemBars(context)) 0 else
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        val lightFlags = if (com.Atom2Universe.app.AppearanceStyle.isLight(context)) appearanceFlags else 0
        root.systemUiVisibility = (root.systemUiVisibility and (visibilityFlags or appearanceFlags).inv()) or
            hiddenFlags or lightFlags
    }
}

/** Retains outside taps, dismiss listeners, keyboard navigation and all existing popup content. */
class ImmersivePopupWindow(content: View, width: Int, height: Int, focusable: Boolean = false) :
    PopupWindow(PopupBarsContext(content.context)) {
    init {
        contentView = content
        this.width = width
        this.height = height
        isFocusable = focusable
    }
}

class ImmersivePopupMenu(context: Context, anchor: View, gravity: Int = Gravity.NO_GRAVITY) :
    android.widget.PopupMenu(PopupBarsContext(context), anchor, gravity) {
    private val host = context
    override fun show() {
        super.show()
        refreshOwnedPopupWindows(host)
    }
}

class ImmersiveSupportPopupMenu(context: Context, anchor: View, gravity: Int = Gravity.NO_GRAVITY) :
    androidx.appcompat.widget.PopupMenu(PopupBarsContext(context), anchor, gravity) {
    private val host = context
    override fun show() {
        super.show()
        refreshOwnedPopupWindows(host)
    }
}

/**
 * Native and AppCompat menus own private PopupWindows. Intercept only their public WindowManager
 * service so even Android 8/9 and cascading submenus can inherit immersion before taking focus.
 * The real manager retains the activity's window token, placement and removal behaviour.
 */
private class PopupBarsContext(context: Context) : ContextWrapper(context) {
    private val popupManager by lazy {
        val actual = baseContext.getSystemService(WINDOW_SERVICE) as WindowManager
        object : WindowManager by actual {
            // La délégation Kotlin ne transmet pas les méthodes par défaut Java : sans ces deux
            // redéfinitions, PopupMenu plante (UnsupportedOperationException) sur Android 11+.
            @androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
            override fun getMaximumWindowMetrics() = actual.maximumWindowMetrics

            @androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
            override fun getCurrentWindowMetrics() = actual.currentWindowMetrics

            override fun addView(view: View, params: ViewGroup.LayoutParams) {
                val windowParams = params as? WindowManager.LayoutParams
                val borrowedFocus = windowParams != null &&
                    !SystemBarsManager.shouldShowSystemBars(this@PopupBarsContext) &&
                    windowParams.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE == 0
                if (borrowedFocus) windowParams!!.flags = windowParams.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE

                val restore = Runnable {
                    if (view.isAttachedToWindow) {
                        applyPopupBars(view, this@PopupBarsContext)
                        if (borrowedFocus) {
                            val current = view.layoutParams as WindowManager.LayoutParams
                            current.flags = current.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
                            actual.updateViewLayout(view, current)
                        }
                        applyPopupBars(view, this@PopupBarsContext)
                    }
                }
                view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) {
                        applyPopupBars(v, this@PopupBarsContext)
                        v.post(restore)
                    }
                    override fun onViewDetachedFromWindow(v: View) {
                        v.removeCallbacks(restore)
                        v.removeOnAttachStateChangeListener(this)
                    }
                })
                followWindowFocus(view) { applyPopupBars(view, this@PopupBarsContext) }
                actual.addView(view, params)
            }
        }
    }

    override fun getSystemService(name: String): Any? =
        if (name == WINDOW_SERVICE) popupManager else super.getSystemService(name)
}
