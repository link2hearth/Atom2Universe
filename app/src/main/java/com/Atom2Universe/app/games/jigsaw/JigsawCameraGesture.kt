package com.Atom2Universe.app.games.jigsaw

import kotlin.math.hypot

data class JigsawCamera(val offset: PuzzlePoint, val scale: Float)

/** The world point between two fingers follows their midpoint, even at the zoom limits. */
class JigsawCameraGesture(first: PuzzlePoint, second: PuzzlePoint, private val start: JigsawCamera) {
    private val midpoint = (first + second).let { PuzzlePoint(it.x / 2f, it.y / 2f) }
    private val span = hypot(first.x - second.x, first.y - second.y).coerceAtLeast(1f)
    private val anchor = PuzzlePoint((midpoint.x - start.offset.x) / start.scale,
        (midpoint.y - start.offset.y) / start.scale)

    fun move(first: PuzzlePoint, second: PuzzlePoint, minScale: Float, maxScale: Float): JigsawCamera {
        val focus = (first + second).let { PuzzlePoint(it.x / 2f, it.y / 2f) }
        val distance = hypot(first.x - second.x, first.y - second.y).coerceAtLeast(1f)
        val scale = (start.scale * distance / span).coerceIn(minScale, maxScale)
        return JigsawCamera(PuzzlePoint(focus.x - anchor.x * scale, focus.y - anchor.y * scale), scale)
    }
}
