package com.Atom2Universe.app.games.jigsaw

/** Image-space point held by the finger, preserved across thumbnail and table transforms. */
data class JigsawGrab(val point: PuzzlePoint) {
    fun offsetAt(pointer: PuzzlePoint, turns: Int) = pointer - point.rotated(turns)

    companion object {
        fun fromThumbnail(pointer: PuzzlePoint, thumbnailCenter: PuzzlePoint, thumbnailScale: Float,
            pieceCenter: PuzzlePoint, turns: Int): JigsawGrab {
            val delta = pointer - thumbnailCenter
            return JigsawGrab(pieceCenter + PuzzlePoint(delta.x / thumbnailScale, delta.y / thumbnailScale).rotated(-turns))
        }
    }
}
