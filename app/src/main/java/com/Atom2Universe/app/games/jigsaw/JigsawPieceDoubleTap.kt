package com.Atom2Universe.app.games.jigsaw

/** A return gesture requires two stationary taps on the same loose piece. */
class JigsawPieceDoubleTap(private val timeoutMs: Long, private val slop: Float) {
    private var previous: PieceGroup? = null
    private var position = PuzzlePoint(0f, 0f)
    private var timeMs = 0L

    fun tap(group: PieceGroup, point: PuzzlePoint, time: Long): Boolean {
        val delta = point - position
        val matched = previous === group && time - timeMs in 1L..timeoutMs &&
            delta.x * delta.x + delta.y * delta.y <= slop * slop
        reset()
        if (!matched) {
            previous = group; position = point; timeMs = time
        }
        return matched
    }

    fun reset() { previous = null }
}
