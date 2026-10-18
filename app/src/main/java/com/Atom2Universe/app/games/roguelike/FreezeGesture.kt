package com.Atom2Universe.app.games.roguelike

import kotlin.math.hypot

/**
 * Le geste « Figer » (glace) : le doigt se pose **n'importe où** et ne bouge plus.
 *
 * - Tant qu'il reste immobile (à [stillRadius] près de là où il s'est arrêté), le givre monte :
 *   la jauge se remplit en [fillMs].
 * - S'il bouge, **tout fond** : la jauge retombe à zéro et repart de là où il s'arrête.
 * - On **lève le doigt** quand la jauge est dans la zone (autour de [center], à [perfect] près
 *   Parfait, à [good] près Bien). Jauge pleine, la glace éclate : raté. Au bout de [limitMs] : raté.
 *
 * Un seul doigt compte. La cible ([targetX], [targetY], [targetRadius]) ne sert qu'au dessin :
 * c'est sur elle qu'on peint la jauge. Testé par RelicGesturesTest.
 */
internal class FreezeGesture(
    val targetX: Float,
    val targetY: Float,
    val targetRadius: Float,
    private val stillRadius: Float,
    private val fillMs: Float,
    val center: Float,
    val good: Float,
    val perfect: Float,
    private val limitMs: Float,
    /** La Peau de givre se dessine sur le héros, même si le doigt est ailleurs. */
    val onHero: Boolean = false,
) : TouchGesture {
    override var result: Timing? = null
        private set
    private var pointer = -1
    val pressed get() = pointer >= 0
    var anchorX = 0f
        private set
    var anchorY = 0f
        private set
    private var frostFrom = 0f
    /** Le dernier moment où le givre a fondu, ou -1 : pour le montrer. */
    var meltedAt = -1f
        private set

    /** La jauge à l'instant [t] : le givre accumulé depuis que le doigt s'est arrêté. */
    fun gauge(t: Float) = if (!pressed) 0f else ((t - frostFrom) / fillMs).coerceAtLeast(0f)

    override fun down(id: Int, x: Float, y: Float, t: Float) {
        if (result != null || pressed) return
        pointer = id
        anchorX = x; anchorY = y
        frostFrom = t
    }

    override fun move(id: Int, x: Float, y: Float, t: Float) {
        if (result != null || id != pointer) return
        if (hypot(x - anchorX, y - anchorY) <= stillRadius) return
        if (gauge(t) > 0f) meltedAt = t
        anchorX = x; anchorY = y
        frostFrom = t
    }

    override fun up(id: Int, x: Float, y: Float, t: Float) {
        if (result != null || id != pointer) return
        move(id, x, y, t)
        result = gaugeGrade(gauge(t), center, good, perfect)
    }

    override fun update(t: Float) {
        if (result != null) return
        if (gauge(t) >= 1f || t >= limitMs) result = Timing.MISS
    }
}
