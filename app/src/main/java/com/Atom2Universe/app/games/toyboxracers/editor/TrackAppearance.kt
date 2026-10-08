package com.Atom2Universe.app.games.toyboxracers.editor

import com.Atom2Universe.app.R

/** Apparence d'une bande de piste, et l'accroche que sa matière offre aux roues.
 *
 * `grip` multiplie l'adhérence des pneus (latérale et longitudinale) : la glace
 * laisse partir la voiture, le néon colle un peu plus que le bitume. `drag` est
 * une décélération en unités/s² qui ne s'applique qu'au sol : le sable freine,
 * la glace non — c'est ce qui distingue « ça glisse » de « ça ralentit ».
 */
internal enum class TrackStyle(
    val label: Int,
    val tint: Int,
    val suggestedWidth: Float,
    val accent: Int,
    val grip: Float,
    val drag: Float
) {
    CLASSIC(R.string.toybox_style_classic, 0xFF6F7B91.toInt(), 9f, 0xFFFFE7A8.toInt(), 1.00f, 0f),
    DIRT(R.string.toybox_style_dirt, 0xFF99643D.toInt(), 7f, 0xFFD2A571.toInt(), 0.82f, 0.9f),
    GRASS(R.string.toybox_style_grass, 0xFF508446.toInt(), 6f, 0xFFA3BC62.toInt(), 0.76f, 1.6f),
    SAND(R.string.toybox_style_sand, 0xFFDEC18A.toInt(), 12f, 0xFFFFE5AD.toInt(), 0.70f, 2.4f),
    ICE(R.string.toybox_style_ice, 0xFF94DDE9.toInt(), 10f, 0xFFE9FFFF.toInt(), 0.46f, 0f),
    WOOD(R.string.toybox_style_wood, 0xFFB97C49.toInt(), 5f, 0xFF5B3929.toInt(), 0.94f, 0.2f),
    NEON(R.string.toybox_style_neon, 0xFF292642.toInt(), 8f, 0xFF44FFDD.toInt(), 1.06f, 0f),
    /** Bande de relance : rouler dessus déclenche un Ruban Turbo, sans rien tenir. */
    BOOST(R.string.toybox_style_boost, 0xFF3A2A63.toInt(), 8f, 0xFFFFE061.toInt(), 1.00f, 0f);

    /** Vrai pour les matières qui relancent la voiture au simple contact. */
    val boosts get() = this == BOOST

    companion object {
        fun parse(name: String) = entries.firstOrNull { it.name == name } ?: CLASSIC
        const val BARRIER_HEIGHT = 1.3f
        const val BARRIER_THICKNESS = 0.22f
    }
}
