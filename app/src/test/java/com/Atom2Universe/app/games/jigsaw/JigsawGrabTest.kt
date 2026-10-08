package com.Atom2Universe.app.games.jigsaw

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class JigsawGrabTest {
    @Test fun `off centre pickup stays under the finger when leaving a scrolled rotated thumbnail`() {
        for (size in JigsawSize.entries) for (turns in 0..3) {
            val game = JigsawGame("image", size, 17, true)
            val center = game.center(size.count - 2)
            val pickedPoint = center + PuzzlePoint(size.cell * .27f, -size.cell * .31f)
            val thumbnailCenter = PuzzlePoint(217f, 842f) // Slot after horizontal scrolling.
            val thumbnailScale = 62f / size.cell
            val down = render(pickedPoint - center, PuzzlePoint(0f, 0f), thumbnailCenter, thumbnailScale, turns)
            val grip = JigsawGrab.fromThumbnail(down, thumbnailCenter, thumbnailScale, center, turns)
            assertPoint(pickedPoint, grip.point)

            for (scale in listOf(.08f, .4f, .75f, 2.4f)) {
                val camera = PuzzlePoint(-1300f, 180f)
                for (finger in listOf(down + PuzzlePoint(13f, -110f), PuzzlePoint(380f, 290f), PuzzlePoint(102f, 720f))) {
                    val pointerWorld = PuzzlePoint((finger.x - camera.x) / scale, (finger.y - camera.y) / scale)
                    val offset = grip.offsetAt(pointerWorld, turns)
                    assertPoint(finger, render(pickedPoint, offset, camera, scale, turns))
                }
            }
        }
    }

    @Test fun `thumbnail preview and resizing keep the original picked point stationary`() {
        val center = PuzzlePoint(375f, 525f)
        val pickedPoint = PuzzlePoint(392f, 487f)
        val thumbnailCenter = PuzzlePoint(220f, 810f)
        val thumbnailScale = .7f
        for (turns in 0..3) {
            val down = render(pickedPoint - center, PuzzlePoint(0f, 0f), thumbnailCenter, thumbnailScale, turns)
            val grip = JigsawGrab.fromThumbnail(down, thumbnailCenter, thumbnailScale, center, turns)
            val finger = down + PuzzlePoint(-21f, -85f)
            // Canvas translates by the finger, scales, rotates, then translates by minus the grip.
            for (visualScale in listOf(.1f, .4f, .7f, 1.1f)) {
                assertPoint(finger, render(pickedPoint - grip.point, PuzzlePoint(0f, 0f), finger, visualScale, turns))
            }
        }
    }

    @Test fun `table pickup preserves group position and tracks movement across the tray edge`() {
        val localPoint = PuzzlePoint(273f, 618f)
        val offset = PuzzlePoint(-480f, 215f)
        val camera = PuzzlePoint(620f, -190f)
        val scale = .56f
        for (turns in 0..3) {
            val down = render(localPoint, offset, camera, scale, turns)
            val world = PuzzlePoint((down.x - camera.x) / scale, (down.y - camera.y) / scale)
            val grip = JigsawGrab((world - offset).rotated(-turns))
            assertPoint(offset, grip.offsetAt(world, turns))
            for (dy in listOf(-300f, 200f, 520f)) {
                val finger = down + PuzzlePoint(46f, dy)
                val pointer = PuzzlePoint((finger.x - camera.x) / scale, (finger.y - camera.y) / scale)
                assertPoint(finger, render(localPoint, grip.offsetAt(pointer, turns), camera, scale, turns))
            }
        }
    }

    /** Independent forward transform matching the Canvas translate/scale/rotate order. */
    private fun render(point: PuzzlePoint, offset: PuzzlePoint, camera: PuzzlePoint, scale: Float, turns: Int): PuzzlePoint {
        val radians = turns * Math.PI / 2
        val x = point.x * cos(radians) - point.y * sin(radians) + offset.x
        val y = point.x * sin(radians) + point.y * cos(radians) + offset.y
        return PuzzlePoint((camera.x + x * scale).toFloat(), (camera.y + y * scale).toFloat())
    }

    private fun assertPoint(expected: PuzzlePoint, actual: PuzzlePoint) {
        assertEquals(expected.x, actual.x, .002f)
        assertEquals(expected.y, actual.y, .002f)
    }
}
