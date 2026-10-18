package com.Atom2Universe.app.games.roguelike

import kotlin.math.hypot

/**
 * Le geste « Attiser » (feu, et la Lumière sacrée), à un doigt.
 *
 * - Le doigt se pose **n'importe où** et y reste : la flamme monte sur le héros ([heroX],
 *   [heroY]), la jauge se remplit avec le temps, pleine en [fillMs]. Rien n'est chronométré à
 *   l'appui ; le doigt peut trembler un peu sans rien lancer.
 * - Quand la jauge est dans la zone, on **lance** : un coup de doigt dans n'importe quelle
 *   direction, le doigt s'écarte de [flick] de là où il s'est posé. C'est l'instant jugé
 *   (autour de [center], à [perfect] près Parfait, à [good] près Bien).
 * - Lever le doigt sans lancer éteint la flamme : raté. Jauge pleine, la flamme s'emballe :
 *   raté. Aucun doigt posé au bout de [limitMs] : raté.
 *
 * Un seul doigt compte. [targets] : les cibles du sort, x, y et rayon à la suite (une seule pour
 * un sort sur un ennemi, tous les vivants pour un sort de zone) ; elles ne servent qu'au dessin,
 * la jauge est peinte sur chacune. Testé par RelicGesturesTest.
 */
internal class ChargeGesture(
    val heroX: Float,
    val heroY: Float,
    val targets: FloatArray,
    private val fillMs: Float,
    private val flick: Float,
    val center: Float,
    val good: Float,
    val perfect: Float,
    private val limitMs: Float,
) : TouchGesture {
    override var result: Timing? = null
        private set
    private var holder = -1
    private var holdAt = -1f
    private var downX = 0f
    private var downY = 0f
    val holding get() = holder >= 0

    /** La jauge à l'instant [t] : 0 tant qu'aucun doigt ne tient, 1 = la flamme s'emballe. */
    fun gauge(t: Float) = if (holdAt < 0f) 0f else ((t - holdAt) / fillMs).coerceAtLeast(0f)

    override fun down(id: Int, x: Float, y: Float, t: Float) {
        if (result != null || holding) return
        holder = id
        holdAt = t
        downX = x; downY = y
    }

    override fun move(id: Int, x: Float, y: Float, t: Float) {
        if (result != null || id != holder) return
        if (hypot(x - downX, y - downY) >= flick) result = gaugeGrade(gauge(t), center, good, perfect)
    }

    override fun up(id: Int, x: Float, y: Float, t: Float) {
        if (result != null || id != holder) return
        // Un coup de doigt qui finit en se levant compte encore
        move(id, x, y, t)
        if (result == null) result = Timing.MISS
    }

    override fun update(t: Float) {
        if (result != null) return
        if (holding && gauge(t) >= 1f) result = Timing.MISS
        else if (!holding && t >= limitMs) result = Timing.MISS
    }
}
