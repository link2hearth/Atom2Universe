package com.Atom2Universe.app.games.roguelike

/**
 * Le geste qu'une relique demande à son lancer.
 * Un geste par élément, joué n'importe où sur l'écran. Chacun se lit sur un cercle peint autour
 * de la cible ou du héros, avec sa zone verte (Bien) et dorée (Parfait). Le Sablier se retourne
 * librement ; seule l'attaque ordinaire garde la barre de frappe.
 */
internal enum class RelicGesture {
    /** La barre et sa flèche, comme l'attaque à l'arme. */
    SWIPE,
    /** Foudre : dessiner l'éclair du héros à la cible ([DrawGesture]). */
    DRAW_BOLT,
    /** Feu et sacré : tenir pour attiser la flamme, puis la lancer d'un coup de doigt ([ChargeGesture]). */
    CHARGE,
    /** Glace : le doigt immobile, le givre monte ([FreezeGesture]). */
    FREEZE,
    /** Poison : tapoter pour garder la dose dans la zone ([DoseGesture]). */
    DOSE,
    /** Cri de guerre : tenir pour faire grandir l'onde, puis relâcher. */
    WAVE,
    /** Sablier : le retourner d'un demi-tour, sans contrainte de timing. */
    HOURGLASS,
    /** Les éclairs errants : une aiguille fait des allers-retours sur un cadran ([DialGesture]). */
    DIAL;

    companion object {
        fun of(relic: Relic): RelicGesture = when {
            relic == Relic.WAR_CRY -> WAVE
            relic == Relic.HOURGLASS -> HOURGLASS
            relic == Relic.HASTE || relic.element == Element.PHYSICAL -> DIAL
            relic == Relic.CHAIN_LIGHTNING || relic == Relic.MAGIC_MISSILE -> DIAL
            relic.element == Element.LIGHTNING -> DRAW_BOLT
            relic.element == Element.FIRE || relic.element == Element.HOLY -> CHARGE
            relic.element == Element.ICE -> FREEZE
            relic.element == Element.POISON -> DOSE
            else -> SWIPE
        }

        fun dialZones(relic: Relic): Int = if (relic.element == Element.PHYSICAL) 2 else 3
    }
}

/**
 * Ce que l'écran de combat transmet à un geste de relique : les doigts (un identifiant par
 * doigt, plusieurs à la fois possibles) et le temps qui passe. Pixels d'écran, millisecondes
 * depuis le début du geste. Le geste rend une note, comme la barre de frappe.
 */
internal interface TouchGesture {
    /** Null tant que le geste n'est pas fini. */
    val result: Timing?
    fun down(id: Int, x: Float, y: Float, t: Float)
    fun move(id: Int, x: Float, y: Float, t: Float)
    fun up(id: Int, x: Float, y: Float, t: Float)
    /** À chaque image : le temps passe même quand aucun doigt ne bouge. */
    fun update(t: Float)
}

/** La note d'une jauge levée à [gauge] : la zone de la barre de frappe, autour de [center]. */
internal fun gaugeGrade(gauge: Float, center: Float, good: Float, perfect: Float): Timing = when {
    kotlin.math.abs(gauge - center) <= perfect -> Timing.PERFECT
    kotlin.math.abs(gauge - center) <= good -> Timing.GOOD
    else -> Timing.MISS
}
