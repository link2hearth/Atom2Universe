package com.Atom2Universe.app.games.toyboxracers

import com.Atom2Universe.app.games.toyboxracers.render.ChaseCamera
import com.Atom2Universe.app.games.toyboxracers.track.PrototypeTrack.Vec3
import com.Atom2Universe.app.games.toyboxracers.track.RoomBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChaseCameraTest {
    @Test fun cameraStaysInFrontOfWallEvenWhenDesiredEyeIsBeyondIt() {
        val wall = RoomBox(0f, 5f, -5f, 20f, 10f, 2f, 0)
        val eye = ChaseCamera.unobstructed(Vec3(0f, 1f, 0f), Vec3(0f, 1f, -10f), listOf(wall))
        assertEquals(-3.75f, eye.z, .001f)
        assertEquals(1f, eye.y, .001f)
    }

    @Test fun furnitureBesideBoomDoesNotShortenCamera() {
        val beside = RoomBox(6f, 2f, -3f, 2f, 4f, 8f, 0)
        val wanted = Vec3(0f, 3f, -8f)
        assertEquals(wanted, ChaseCamera.unobstructed(Vec3(0f, 1f, 0f), wanted, listOf(beside)))
    }

    @Test fun cameraRemainsBelowTheTableItIsDrivingUnder() {
        val top = RoomBox(0f, 5f, 0f, 20f, 2f, 20f, 0)
        val eye = ChaseCamera.unobstructed(Vec3(0f, 1f, 0f), Vec3(0f, 8f, -5f), listOf(top))
        assertTrue(eye.y < top.bottom)
        assertTrue(eye.y > 3f)
    }
}
