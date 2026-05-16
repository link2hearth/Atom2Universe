package com.Atom2Universe.app.util

import android.app.Activity
import android.content.Context
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import androidx.core.content.edit

/**
 * Gestionnaire du mode d'affichage des barres système.
 * Permet de basculer entre barres visibles et mode immersif (barres cachées).
 */
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
    val showBars = SystemBarsManager.shouldShowSystemBars(this)

    val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)

    // Configure l'apparence des icônes de barres système (icônes blanches sur fond sombre)
    windowInsetsController.isAppearanceLightStatusBars = false
    windowInsetsController.isAppearanceLightNavigationBars = false

    if (showBars) {
        // Mode avec barres visibles
        windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
    } else {
        // Mode immersif: cache les barres, elles réapparaissent avec un swipe
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    // Applique les insets pour gérer le padding selon la préférence utilisateur
    applySystemBarInsets(showBars)
}

/**
 * Met à jour le mode d'affichage des barres sans recréer l'activité.
 * Utile pour appliquer immédiatement un changement de préférence.
 */
fun Activity.updateSystemBarsVisibility() {
    val showBars = SystemBarsManager.shouldShowSystemBars(this)
    val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)

    if (showBars) {
        windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
    } else {
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    // Recalcule les insets
    val rootView = findViewById<View>(android.R.id.content)
    ViewCompat.requestApplyInsets(rootView)
}

/**
 * Applique la visibilité des barres système de façon indépendante (migré depuis Atom2Universe).
 */
fun Activity.applySystemBarsVisibility(showStatusBar: Boolean, showNavBar: Boolean) {
    WindowCompat.setDecorFitsSystemWindows(window, showStatusBar || showNavBar)
    val controller = WindowCompat.getInsetsController(window, window.decorView)
    controller.systemBarsBehavior =
        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    if (showStatusBar) controller.show(WindowInsetsCompat.Type.statusBars())
    else controller.hide(WindowInsetsCompat.Type.statusBars())
    if (showNavBar) controller.show(WindowInsetsCompat.Type.navigationBars())
    else controller.hide(WindowInsetsCompat.Type.navigationBars())
}

// Tag used to track if insets have been applied to avoid double-application
private const val INSETS_APPLIED_TAG = "immersive_insets_applied"

/**
 * Applique les insets des barres système comme padding sur la vue racine.
 * Garantit que le contenu ne passe jamais sous les barres système.
 * Prevents double-application by tracking state via view tag.
 *
 * @param showBars Si true, applique le padding pour les barres. Si false, gère
 *                 uniquement les display cutouts (encoches, coins arrondis).
 */
private fun Activity.applySystemBarInsets(showBars: Boolean) {
    val rootView = findViewById<View>(android.R.id.content)

    // Check if insets listener is already set to avoid double-application
    // Use simple tag (no key) since we only need one tag per view for this purpose
    if (rootView.tag == INSETS_APPLIED_TAG) {
        return
    }
    rootView.tag = INSETS_APPLIED_TAG

    ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, windowInsets ->
        val showBarsNow = SystemBarsManager.shouldShowSystemBars(this)

        if (showBarsNow) {
            // Mode barres visibles: applique les insets complets
            val systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
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
