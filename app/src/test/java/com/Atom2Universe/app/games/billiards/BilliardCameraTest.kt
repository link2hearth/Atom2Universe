package com.Atom2Universe.app.games.billiards

import com.Atom2Universe.app.games.billiards.core.*
import com.Atom2Universe.app.games.billiards.render.*
import org.junit.Assert.*
import org.junit.Test

class BilliardCameraTest {
    private val table=BilliardTable(TableFamily.POOL)
    private fun frame(moving: Boolean=false)=BilliardFrame(table.rack(Discipline.EIGHT),emptyList(),0,Shot(.37),moving)
    private fun pose(camera: BilliardCamera,frame: BilliardFrame=frame())=
        camera.pose(frame,table.length.toFloat(),table.width.toFloat(),1.4f,.65f)

    @Test fun topViewIsExactlyVerticalAndOrthographic() {
        for(portrait in listOf(false,true)) {
            val camera=BilliardCamera().apply { top(portrait) }
            val pose=pose(camera)
            assertNotNull(pose.orthographicHalfHeight)
            assertEquals(pose.eye.x,pose.target.x,1e-10)
            assertEquals(pose.eye.z,pose.target.z,1e-10)
            assertEquals(0.0,(pose.eye-pose.target).dot(pose.up),1e-10)
            assertEquals(1.0,pose.up.length(),1e-10)
        }
    }

    @Test fun wideViewBacksUpOnTheSameSightLine() {
        val close=pose(BilliardCamera().apply { select(BilliardCameraMode.CLOSE) })
        val wide=pose(BilliardCamera().apply { select(BilliardCameraMode.WIDE) })
        assertEquals(close.target,wide.target)
        assertTrue((wide.eye-wide.target).length()>(close.eye-close.target).length()*2)
        assertTrue(((wide.eye-wide.target).unit()-(close.eye-close.target).unit()).length()<1e-9)
    }

    @Test fun shotPullsBackButDoesNotOverrideLaterCameraGestures() {
        val camera=BilliardCamera().apply { aim(); dolly(2f) }
        pose(camera)
        camera.watchShot(.37)
        assertEquals(BilliardCameraMode.FREE,camera.mode)
        assertEquals(1f,camera.zoom,0f)
        camera.orbit(40f,10f); camera.pan(.15f,.08f); camera.dolly(1.4f)
        val manual=camera.state()
        repeat(5) { pose(camera,frame(moving=true)) }
        assertEquals(manual,camera.state())
    }

    @Test fun twoDimensionalViewKeepsItsAngleDuringPanAndShots() {
        val camera=BilliardCamera().apply { top(true) }
        val before=camera.state()
        camera.orbit(70f,30f); camera.pan(.2f,.1f); camera.dolly(1.6f)
        assertEquals(before.yaw,camera.yaw,0f); assertEquals(before.pitch,camera.pitch,0f)
        camera.watchShot(.37)
        assertEquals(BilliardCameraMode.TOP,camera.mode)
        assertEquals(before.yaw,camera.yaw,0f)
        assertEquals(0f,camera.x,0f); assertEquals(1f,camera.zoom,0f)
        assertNotNull(pose(camera).orthographicHalfHeight)
    }

    @Test fun rotatingTheDeviceAdaptsTheTopViewWithoutLosingControlState() {
        val camera=BilliardCamera().apply { top(false); pan(.1f,.2f); dolly(1.2f) }
        val state=camera.state()
        val restored=BilliardCamera().apply { restore(state,true) }
        assertEquals(BilliardCameraMode.TOP,restored.mode)
        assertEquals(state.zoom,restored.zoom,0f); assertEquals(state.x,restored.x,0f)
        val direction=pose(restored).up
        assertTrue(direction.x<-.99); assertTrue(kotlin.math.abs(direction.z)<.001)
    }
}
