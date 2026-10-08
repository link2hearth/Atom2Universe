package com.Atom2Universe.app.games.jigsaw

import org.junit.Assert.*
import org.junit.Test

class JigsawCameraGestureTest {
    private val first = PuzzlePoint(100f, 300f)
    private val second = PuzzlePoint(300f, 300f)
    private val start = JigsawCamera(PuzzlePoint(24f, -76f), .8f)

    @Test fun `moving two parallel fingers pans without requiring any change in span`() {
        val gesture = JigsawCameraGesture(first, second, start)
        val delta = PuzzlePoint(63f, -109f)
        val result = gesture.move(first + delta, second + delta, .1f, 5f)
        assertPoint(start.offset + delta, result.offset)
        assertEquals(start.scale, result.scale, .001f)
    }

    @Test fun `pinching and panning together keeps the same world point under the midpoint`() {
        val gesture = JigsawCameraGesture(first, second, start)
        val result = gesture.move(PuzzlePoint(60f, 450f), PuzzlePoint(460f, 450f), .1f, 5f)
        assertEquals(1.6f, result.scale, .001f)
        assertPoint(PuzzlePoint(260f, 450f), project(anchor(), result))
    }

    @Test fun `panning remains available when zoom reaches either limit`() {
        val gesture = JigsawCameraGesture(first, second, start)
        val low = gesture.move(PuzzlePoint(248f, 490f), PuzzlePoint(252f, 490f), .2f, 1.2f)
        assertEquals(.2f, low.scale, .001f)
        assertPoint(PuzzlePoint(250f, 490f), project(anchor(), low))
        val high = gesture.move(PuzzlePoint(-800f, 200f), PuzzlePoint(1200f, 200f), .2f, 1.2f)
        assertEquals(1.2f, high.scale, .001f)
        assertPoint(PuzzlePoint(200f, 200f), project(anchor(), high))
    }

    @Test fun `rebasing a changed pair of fingers preserves the camera without a jump`() {
        val previous = JigsawCameraGesture(first, second, start).move(PuzzlePoint(40f, 400f), PuzzlePoint(360f, 400f), .1f, 5f)
        val a = PuzzlePoint(80f, 240f); val b = PuzzlePoint(310f, 570f)
        val rebased = JigsawCameraGesture(a, b, previous).move(a, b, .1f, 5f)
        assertPoint(previous.offset, rebased.offset)
        assertEquals(previous.scale, rebased.scale, .001f)
    }

    @Test fun `overlapping fingers produce a finite clamped camera`() {
        val result = JigsawCameraGesture(first, second, start).move(PuzzlePoint(225f, 310f), PuzzlePoint(225f, 310f), .15f, 5f)
        assertEquals(.15f, result.scale, .001f)
        assertTrue(result.offset.x.isFinite() && result.offset.y.isFinite())
    }

    private fun anchor() = PuzzlePoint((200f - start.offset.x) / start.scale, (300f - start.offset.y) / start.scale)
    private fun project(point: PuzzlePoint, camera: JigsawCamera) = PuzzlePoint(
        camera.offset.x + point.x * camera.scale, camera.offset.y + point.y * camera.scale)
    private fun assertPoint(expected: PuzzlePoint, actual: PuzzlePoint) {
        assertEquals(expected.x, actual.x, .001f); assertEquals(expected.y, actual.y, .001f)
    }
}
