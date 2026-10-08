package com.Atom2Universe.app.games.roguelike

import kotlin.math.abs

/**
 * Le geste « Cadran » (les éclairs errants), façon *Pop the Lock*.
 *
 * - Autour de la cible, une aiguille tourne dans le sens des aiguilles d'une montre, à
 *   [degPerMs] degrés par milliseconde, vers une **zone** peinte sur le cadran.
 * - On touche l'écran, **n'importe où**, quand l'aiguille passe dans la zone : à [perfectMs]
 *   près de son centre Parfait, à [goodMs] près Bien, sinon raté (pas de second essai).
 * - À chaque touche, **l'aiguille repart dans l'autre sens** vers une nouvelle zone, à [gaps]
 *   degrés plus loin. Une zone dépassée sans toucher est ratée, et l'aiguille repart aussi.
 * - Autant de zones que de [gaps] ; la note finale est la moyenne (raté 0, bien 1, parfait 2) :
 *   au moins 5/3 pour Parfait, au moins 1 pour Bien (à deux zones : deux Parfait pour Parfait).
 *
 * Les angles sont en degrés, comme `Canvas.drawArc` : 0 à trois heures, −90 à midi, le sens
 * positif est celui des aiguilles. La cible ([targetX], [targetY], [targetRadius]) ne sert qu'au
 * dessin. Testé par RelicGesturesTest.
 */
internal class DialGesture(
    val targetX: Float,
    val targetY: Float,
    val targetRadius: Float,
    private val gaps: FloatArray,
    val degPerMs: Float,
    private val goodMs: Float,
    private val perfectMs: Float,
    startAngle: Float = -90f,
) : TouchGesture {
    override var result: Timing? = null
        private set
    /** La note de chaque zone, null tant qu'elle n'est pas jouée. */
    val grades = arrayOfNulls<Timing>(gaps.size)
    /** Où était l'aiguille quand chaque zone a été jouée : on y laisse une marque. */
    val playedAt = FloatArray(gaps.size)
    /** La zone en cours. */
    var zone = 0
        private set
    /** 1 dans le sens des aiguilles, −1 dans l'autre. */
    var direction = 1f
        private set
    /** Le centre de la zone en cours. */
    var zoneAngle = startAngle + gaps[0]
        private set
    private var originAngle = startAngle
    private var originT = 0f

    /** La demi-largeur de la zone verte et de la zone dorée, en degrés. */
    val goodDeg get() = goodMs * degPerMs
    val perfectDeg get() = perfectMs * degPerMs

    /** L'angle de l'aiguille à l'instant [t]. */
    fun needle(t: Float) = originAngle + direction * degPerMs * (t - originT)

    /** L'instant où l'aiguille passe au centre de la zone en cours. */
    private val arrival get() = originT + gaps[zone] / degPerMs

    override fun down(id: Int, x: Float, y: Float, t: Float) {
        if (result != null) return
        val error = abs(t - arrival)
        play(when {
            error <= perfectMs -> Timing.PERFECT
            error <= goodMs -> Timing.GOOD
            else -> Timing.MISS
        }, t)
    }

    override fun move(id: Int, x: Float, y: Float, t: Float) {}
    override fun up(id: Int, x: Float, y: Float, t: Float) {}

    override fun update(t: Float) {
        // Chaque zone dépassée est ratée, au moment où sa fenêtre se ferme
        while (result == null && t > arrival + goodMs) play(Timing.MISS, arrival + goodMs)
    }

    private fun play(grade: Timing, t: Float) {
        grades[zone] = grade
        val angle = needle(t)
        playedAt[zone] = angle
        zone++
        if (zone == gaps.size) {
            val score = grades.sumOf { it!!.ordinal }.toFloat() / grades.size
            result = when {
                score >= 5f / 3f -> Timing.PERFECT
                score >= 1f -> Timing.GOOD
                else -> Timing.MISS
            }
            return
        }
        // L'aiguille repart dans l'autre sens, vers la zone suivante
        originAngle = angle
        originT = t
        direction = -direction
        zoneAngle = angle + direction * gaps[zone]
    }
}
