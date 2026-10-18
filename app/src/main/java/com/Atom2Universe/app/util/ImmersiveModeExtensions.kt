package com.Atom2Universe.app.util

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.ViewTreeObserver
import android.view.Window
import android.view.WindowManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import androidx.core.content.edit
import com.Atom2Universe.app.AppearanceStyle
import com.Atom2Universe.app.R

/** Screens with separate status/navigation switches share them with their child windows. */
interface SystemBarsPreferenceOwner {
    val showStatusBar: Boolean
    val showNavigationBar: Boolean
}

/** Préférence globale, ou choix de l'écran hôte pour toutes ses fenêtres. */
object SystemBarsManager {
    private const val PREFS_NAME = "audio_hub_prefs"
    private const val KEY_SHOW_SYSTEM_BARS = "show_system_bars"

    /**
     * Retourne true si les barres système doivent être affichées.
     * Par défaut: true (barres visibles).
     */
    fun shouldShowSystemBars(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_SHOW_SYSTEM_BARS, true)
    }

    private fun owner(context: Context): SystemBarsPreferenceOwner? {
        var current = context
        while (current is ContextWrapper) {
            if (current is SystemBarsPreferenceOwner) return current
            val base = current.baseContext
            if (base === current) break
            current = base
        }
        return current as? SystemBarsPreferenceOwner
    }

    fun showStatusBar(context: Context): Boolean = owner(context)?.showStatusBar ?: shouldShowSystemBars(context)
    fun showNavigationBar(context: Context): Boolean = owner(context)?.showNavigationBar ?: shouldShowSystemBars(context)
    internal fun visibleTypes(context: Context): Int =
        (if (showStatusBar(context)) WindowInsetsCompat.Type.statusBars() else 0) or
        (if (showNavigationBar(context)) WindowInsetsCompat.Type.navigationBars() else 0)

    /**
     * Définit si les barres système doivent être affichées.
     */
    fun setShowSystemBars(context: Context, show: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putBoolean(KEY_SHOW_SYSTEM_BARS, show) }
    }
}

/**
 * Configure l'affichage des barres système pour l'activité.
 * Respecte la préférence utilisateur pour afficher ou cacher les barres.
 * Gère les insets manuellement pour garantir que le contenu ne passe pas sous les barres système.
 *
 * Note: enableEdgeToEdge() est appelé dans ThemedActivity.onCreate() pour activer
 * l'affichage de bord à bord de manière conforme à Android 15+.
 */
fun Activity.enableImmersiveMode() {
    window.followSystemBarsPreference(this)
    followWindowFocus(window.decorView) { window.followSystemBarsPreference(this) }
    applySystemBarInsets()
}

/**
 * Une fenêtre par-dessus l'écran (feuille du bas, boîte de dialogue) suit le mode plein écran : sans
 * ça, son ouverture fait réapparaître les barres système. À appeler avant `show()`.
 *
 * La fenêtre s'ouvre d'abord sans prendre le focus (c'est la prise de focus qui ramène les barres),
 * cache les barres pour son compte, puis reprend un focus normal une fois affichée (saisie de texte).
 */
fun Dialog.followImmersiveMode() {
    val w = window ?: return
    val decor = w.decorView
    val binding = decor.getTag(R.id.immersive_dialog_binding) as? DialogBarsBinding
        ?: DialogBarsBinding(w, context).also { decor.setTag(R.id.immersive_dialog_binding, it) }
    binding.prepare()
}

internal fun Window.followSystemBarsPreference(context: Context) {
    applySystemBarsPreference(WindowCompat.getInsetsController(this, decorView), context)
}

internal fun applySystemBarsPreference(controller: WindowInsetsControllerCompat, context: Context) {
    val visible = SystemBarsManager.visibleTypes(context)
    val hidden = (WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars()) and visible.inv()
    val light = AppearanceStyle.isLight(context)
    controller.isAppearanceLightStatusBars = light
    controller.isAppearanceLightNavigationBars = light
    controller.systemBarsBehavior = if (hidden == 0) WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
        else WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    if (visible != 0) controller.show(visible)
    if (hidden != 0) controller.hide(hidden)
}

/** Focus changes, not a polling loop: a deliberate swipe can still reveal transient bars. */
internal fun followWindowFocus(view: View, apply: () -> Unit) {
    if (view.getTag(R.id.immersive_focus_binding) != null) return
    val listener = object : View.OnAttachStateChangeListener, ViewTreeObserver.OnWindowFocusChangeListener {
        private var observer: ViewTreeObserver? = null
        override fun onWindowFocusChanged(hasFocus: Boolean) {
            if (hasFocus) apply()
            else view.post { refreshOwnedPopupWindows(view.context) }
        }
        override fun onViewAttachedToWindow(v: View) {
            if (observer?.isAlive == true) return
            observer = v.viewTreeObserver.also { it.addOnWindowFocusChangeListener(this) }
            apply()
        }
        override fun onViewDetachedFromWindow(v: View) {
            observer?.takeIf { it.isAlive }?.removeOnWindowFocusChangeListener(this)
            observer = null
        }
    }
    view.setTag(R.id.immersive_focus_binding, listener)
    view.addOnAttachStateChangeListener(listener)
    if (view.isAttachedToWindow) listener.onViewAttachedToWindow(view)
}

private val attachedDialogs = java.util.Collections.newSetFromMap(
    java.util.WeakHashMap<DialogBarsBinding, Boolean>()
)

/** Does not replace the dialog's onShow/onDismiss handlers or its keyboard configuration. */
private class DialogBarsBinding(private val window: Window, private val context: Context) : View.OnAttachStateChangeListener {
    private val decor = window.decorView
    private var borrowedFocus = false
    private val restoreFocus = Runnable {
        window.followSystemBarsPreference(context)
        releaseFocusFlag()
        window.followSystemBarsPreference(context)
    }

    init {
        decor.addOnAttachStateChangeListener(this)
        followWindowFocus(decor) { window.followSystemBarsPreference(context) }
    }

    fun prepare() {
        if (!decor.isAttachedToWindow &&
            (!SystemBarsManager.showStatusBar(context) || !SystemBarsManager.showNavigationBar(context)) &&
            window.attributes.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE == 0) {
            borrowedFocus = true
            window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        }
        window.followSystemBarsPreference(context)
    }

    private fun releaseFocusFlag() {
        if (borrowedFocus) {
            borrowedFocus = false
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        }
    }

    override fun onViewAttachedToWindow(v: View) {
        attachedDialogs.add(this)
        window.followSystemBarsPreference(context)
        decor.post(restoreFocus)
    }

    override fun onViewDetachedFromWindow(v: View) {
        attachedDialogs.remove(this)
        decor.removeCallbacks(restoreFocus)
        releaseFocusFlag()
    }

    fun refreshFor(activity: Activity) {
        if (context.findActivity() === activity) window.followSystemBarsPreference(context)
    }
}

/**
 * Met à jour le mode d'affichage des barres sans recréer l'activité.
 * Utile pour appliquer immédiatement un changement de préférence.
 */
fun Activity.updateSystemBarsVisibility() {
    window.followSystemBarsPreference(this)
    attachedDialogs.toList().forEach { it.refreshFor(this) }
    refreshOwnedPopupWindows(this)

    // Recalcule les insets
    val rootView = findViewById<View>(android.R.id.content)
    ViewCompat.requestApplyInsets(rootView)
}

// Tag used to track if insets have been applied to avoid double-application
private const val INSETS_APPLIED_TAG = "immersive_insets_applied"

/**
 * Applique les insets des barres système comme padding sur la vue racine.
 * Garantit que le contenu ne passe jamais sous les barres système.
 * Prevents double-application by tracking state via view tag.
 *
 * Relit la préférence à chaque application : barres visibles, ou uniquement
 * les display cutouts (encoches, coins arrondis) en mode immersif.
 */
private fun Activity.applySystemBarInsets() {
    val rootView = findViewById<View>(android.R.id.content)

    // Check if insets listener is already set to avoid double-application
    // Use simple tag (no key) since we only need one tag per view for this purpose
    if (rootView.tag == INSETS_APPLIED_TAG) {
        return
    }
    rootView.tag = INSETS_APPLIED_TAG

    ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, windowInsets ->
        val visibleTypes = SystemBarsManager.visibleTypes(this)

        if (visibleTypes != 0) {
            // Mode barres visibles: applique les insets complets
            val systemBars = windowInsets.getInsets(visibleTypes or WindowInsetsCompat.Type.displayCutout())
            view.updatePadding(
                left = systemBars.left,
                top = systemBars.top,
                right = systemBars.right,
                bottom = systemBars.bottom
            )
        } else {
            // Mode immersif: gère uniquement les display cutouts (encoches, coins arrondis)
            // pour éviter que le contenu soit coupé sur les écrans modernes
            val displayCutout = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout())
            view.updatePadding(
                left = displayCutout.left,
                top = displayCutout.top,
                right = displayCutout.right,
                bottom = displayCutout.bottom
            )
        }

        // Retourne les insets consommés pour éviter la propagation
        WindowInsetsCompat.CONSUMED
    }

    // Force l'application des insets immédiatement
    ViewCompat.requestApplyInsets(rootView)
}
