package com.Atom2Universe.app.games.jigsaw

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JigsawPieceDoubleTapTest {
    private val piece = PieceGroup(mutableListOf(0), inTray = false)
    private val other = PieceGroup(mutableListOf(1), inTray = false)
    private val point = PuzzlePoint(180f, 470f)

    @Test fun `two rapid taps on the same piece trigger one return`() {
        val taps = JigsawPieceDoubleTap(300, 50f)
        assertFalse(taps.tap(piece, point, 1000))
        assertTrue(taps.tap(piece, point + PuzzlePoint(8f, -3f), 1240))
        assertFalse(taps.tap(piece, point, 1400))
    }

    @Test fun `tapping another piece does not return the first one`() {
        val taps = JigsawPieceDoubleTap(300, 50f)
        assertFalse(taps.tap(piece, point, 1000))
        assertFalse(taps.tap(other, point, 1100))
        assertFalse(taps.tap(piece, point, 1200))
    }

    @Test fun `slow or far apart taps and duplicate touch samples do not trigger return`() {
        val taps = JigsawPieceDoubleTap(300, 50f)
        assertFalse(taps.tap(piece, point, 1000))
        assertFalse(taps.tap(piece, point, 1400))
        assertFalse(taps.tap(piece, point + PuzzlePoint(45f, 45f), 1500))
        assertFalse(taps.tap(piece, point + PuzzlePoint(45f, 45f), 1500))
    }

    @Test fun `a drag scroll or cancelled gesture between taps cancels the double tap`() {
        val taps = JigsawPieceDoubleTap(300, 50f)
        assertFalse(taps.tap(piece, point, 1000))
        taps.reset()
        assertFalse(taps.tap(piece, point, 1150))
        assertTrue(taps.tap(piece, point, 1300))
    }
}
