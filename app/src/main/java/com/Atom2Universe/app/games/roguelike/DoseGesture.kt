package com.Atom2Universe.app.games.roguelike

import kotlin.math.max
import kotlin.math.min

/**
 * Le geste « Doser » (poison) : on tapote, vite, **n'importe où** sur l'écran.
 *
 * - Chaque touche **verse une dose** : la jauge monte de [bump]. Entre deux touches elle **se vide toute seule** ([drainPerMs]).
 * - Il faut la **garder dans la zone verte** (autour de [center], à [good] près). Le geste dure
 *   [durationMs] depuis la première touche ; les [warmupMs] premières servent à y monter, le reste
 *   est compté : la part du temps passée dans la zone fait la note ([perfectShare] pour Parfait,
 *   [goodShare] pour Bien).
 * - Trop versé (jauge pleine) : raté tout de suite. Personne ne touche au bout de [limitMs] : raté.
 *
 * Plusieurs doigts peuvent tapoter. La cible ([targetX], [targetY], [targetRadius]) ne sert
 * qu'au dessin : c'est sur elle qu'on peint la jauge. Testé par RelicGesturesTest.
 */
internal class DoseGesture(
    val targetX: Float,
    val targetY: Float,
    val targetRadius: Float,
    private val bump: Float,
    private val drainPerMs: Float,
    val durationMs: Float,
    val warmupMs: Float,
    val center: Float,
    val good: Float,
    private val perfectShare: Float,
    private val goodShare: Float,
    private val limitMs: Float,
    /** Les Lames empoisonnées montrent l'arme qu'on enduit au centre du viseur. */
    val onWeapon: Boolean = false,
) : TouchGesture {
    override var result: Timing? = null
        private set
    /** Le moment de la première dose, ou -1. */
    var firstAt = -1f
        private set
    private var level = 0f
    private var levelAt = 0f
    private var inZone = 0f
    /** Les instants des dernières doses, pour les bulles. */
    val doseTimes = FloatArray(DOSE_MEMORY) { -1f }
    private var doses = 0

    /** La jauge à l'instant [t] (entre deux doses elle descend en ligne droite, sans passer sous 0). */
    fun gauge(t: Float) = max(0f, level - drainPerMs * (t - levelAt))

    /** La part du temps compté passée dans la zone jusqu'ici (0 avant le décompte). */
    fun share(t: Float): Float {
        if (firstAt < 0f) return 0f
        val counted = min(t, firstAt + durationMs) - (firstAt + warmupMs)
        return if (counted <= 0f) 0f else ((inZone + zoneTime(levelAt, t)) / counted).coerceIn(0f, 1f)
    }

    override fun down(id: Int, x: Float, y: Float, t: Float) {
        if (result != null) return
        if (firstAt < 0f) { firstAt = t; levelAt = t }
        advance(t)
        level += bump
        doseTimes[doses++ % DOSE_MEMORY] = t
        if (level >= 1f) result = Timing.MISS
    }

    override fun move(id: Int, x: Float, y: Float, t: Float) {}
    override fun up(id: Int, x: Float, y: Float, t: Float) {}

    override fun update(t: Float) {
        if (result != null) return
        if (firstAt < 0f) {
            if (t >= limitMs) result = Timing.MISS
            return
        }
        val end = firstAt + durationMs
        if (t < end) return
        advance(end)
        val share = inZone / (durationMs - warmupMs)
        result = when {
            share >= perfectShare -> Timing.PERFECT
            share >= goodShare -> Timing.GOOD
            else -> Timing.MISS
        }
    }

    /** Fait descendre la jauge jusqu'à [t], en comptant le temps passé dans la zone. */
    private fun advance(t: Float) {
        inZone += zoneTime(levelAt, t)
        level = gauge(t)
        levelAt = t
    }

    /**
     * Le temps passé dans la zone entre [from] et [to], la jauge partant de [level] à [from] et
     * descendant en ligne droite ; seule la part après l'échauffement et avant la fin compte.
     */
    private fun zoneTime(from: Float, to: Float): Float {
        val a = max(from, firstAt + warmupMs)
        val b = min(to, firstAt + durationMs)
        if (b <= a) return 0f
        val hi = center + good
        val lo = center - good
        // La jauge passe sous [hi] à enterAt, puis sous [lo] à leaveAt
        val enterAt = from + (level - hi) / drainPerMs
        val leaveAt = from + (level - lo) / drainPerMs
        return max(0f, min(b, leaveAt) - max(a, enterAt))
    }

    companion object {
        const val DOSE_MEMORY = 16
    }
}
