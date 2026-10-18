package com.Atom2Universe.app.games.roguelike

import kotlin.math.hypot

/**
 * Le geste « Dessiner l'éclair » (foudre) : on dessine librement, du héros jusqu'à la cible.
 *
 * - Le doigt se pose **n'importe où** ; l'éclair, lui, part toujours du héros ([startX],
 *   [startY]) et rejoint le doigt. Rien n'est chronométré à l'appui. Un seul doigt compte.
 * - Tout ce que le doigt parcourt **remplit la jauge** : [fullLength] de tracé la remplit. La
 *   vitesse ne compte pas, seule la longueur dessinée.
 * - On **lève le doigt sur la cible** (à [targetRadius] de son centre) quand la jauge est dans
 *   la zone : autour de [center], à [perfect] près c'est Parfait, à [good] près c'est Bien.
 *   Levé ailleurs que sur la cible, ou hors de la zone : raté.
 * - La jauge pleine fait **surcharge** : raté tout de suite. Et le geste s'arrête au bout de
 *   [limitMs], fini ou pas.
 *
 * Les points du doigt sont gardés ([traced]) : l'éclair suivra ce dessin. Testé par DrawGestureTest.
 */
internal class DrawGesture(
    val startX: Float,
    val startY: Float,
    val targetX: Float,
    val targetY: Float,
    val targetRadius: Float,
    private val fullLength: Float,
    val center: Float,
    val good: Float,
    val perfect: Float,
    private val limitMs: Float,
    /** L'écart minimal entre deux points gardés du doigt. */
    private val step: Float,
) : TouchGesture {
    override var result: Timing? = null
        private set
    private var pointer = -1
    val started get() = pointer >= 0
    /** La longueur dessinée jusqu'ici. */
    var drawn = 0f
        private set
    var fingerX = 0f
        private set
    var fingerY = 0f
        private set

    /** Les points du doigt, x puis y ; seuls les [tracedCount] premiers comptent. */
    val traced = FloatArray(MAX_POINTS * 2)
    var tracedCount = 0
        private set

    /** La jauge, de 0 (rien dessiné) à 1 (surcharge). */
    val gauge get() = drawn / fullLength
    val onTarget get() = started && hypot(fingerX - targetX, fingerY - targetY) <= targetRadius

    override fun down(id: Int, x: Float, y: Float, t: Float) {
        if (result != null || started) return
        pointer = id
        fingerX = x; fingerY = y
        // L'éclair part du héros lui-même, où que le doigt se pose
        keep(startX, startY, force = true)
        keep(x, y)
    }

    override fun move(id: Int, x: Float, y: Float, t: Float) {
        if (result != null || id != pointer) return
        drawn += hypot(x - fingerX, y - fingerY)
        fingerX = x; fingerY = y
        keep(x, y)
        if (gauge >= 1f) finish(Timing.MISS)
    }

    override fun up(id: Int, x: Float, y: Float, t: Float) {
        if (result != null || id != pointer) return
        // Le point où le doigt se lève compte, puis on juge
        move(id, x, y, t)
        if (result == null) finish(if (onTarget) gaugeGrade(gauge, center, good, perfect) else Timing.MISS)
    }

    override fun update(t: Float) {
        if (result == null && t >= limitMs) finish(Timing.MISS)
    }

    private fun finish(timing: Timing) {
        // L'éclair arrive toujours sur la cible : les dégâts tombent, raté ou pas
        keep(targetX, targetY, force = true)
        result = timing
    }

    private fun keep(x: Float, y: Float, force: Boolean = false) {
        if (tracedCount > 0 && !force &&
            hypot(x - traced[tracedCount * 2 - 2], y - traced[tracedCount * 2 - 1]) < step) return
        if (tracedCount == MAX_POINTS) {
            if (!force) return
            tracedCount--   // la cible remplace le dernier point
        }
        traced[tracedCount * 2] = x
        traced[tracedCount * 2 + 1] = y
        tracedCount++
    }

    companion object {
        const val MAX_POINTS = 500
    }
}
