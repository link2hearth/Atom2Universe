package com.Atom2Universe.app.games.golf

import com.Atom2Universe.app.games.golf.classic.render.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sqrt

/** Geometric invariants of the rig; no Android renderer, screenshots or simulation bench. */
class GolferPoseTest {
    private fun distance(a: GVec,b: GVec): Float {
        val d=a-b
        return sqrt(d.dot(d))
    }

    @Test fun bothHandsStayAttachedAndLimbsKeepTheirLengthThroughoutTheSwing() {
        val pose=GolferPose()
        for(female in listOf(false,true)) for(putt in listOf(false,true))
            for(power in listOf(0f,.08f,.4f,1f)) for(slope in listOf(-.06f,0f,.06f)) for(step in 0..100) {
                pose.update(GolferPose.TOP+step/100f*(1f-GolferPose.TOP),power,putt,0f,female,
                    leadGround=slope,trailGround=-slope,ballGround=slope)
                assertTrue(pose.matrices.all { it.isFinite() })
                for(side in 0..1) {
                    val elbow=pose.point(GolferPose.UPPER_L+side,0f,GolferPose.ARM_LENGTH,0f)
                    val forearm=pose.point(GolferPose.FORE_L+side,0f,0f,0f)
                    assertEquals("Elbow seam",0f,distance(elbow,forearm),.001f)
                    val wrist=pose.point(GolferPose.FORE_L+side,0f,GolferPose.ARM_LENGTH,0f)
                    val grip=pose.point(GolferPose.HAND_L+side,0f,.035f,0f)
                    assertEquals("Wrist seam",0f,distance(wrist,grip),.001f)
                    val ankle=pose.point(GolferPose.SHIN_L+side,0f,.405f,0f)
                    val shoe=pose.point(GolferPose.FOOT_L+side,0f,.105f,0f)
                    assertEquals("Ankle seam",0f,distance(ankle,shoe),.001f)
                }
            }
    }

    @Test fun releasingAShortShotDoesNotJumpToAFullBackswing() {
        val pose=GolferPose()
        for(female in listOf(false,true)) for(putt in listOf(false,true)) for(power in listOf(0f,.04f,.25f,.7f,1f)) {
            pose.update(GolferPose.TOP*power,power,putt,0f,female)
            val pulled=pose.matrices.copyOf()
            pose.update(GolferPose.releaseProgress(0f),power,putt,0f,female)
            assertArrayEquals(pulled,pose.matrices,.0001f)
            pose.update(GolferPose.releaseProgress(.00001f),power,putt,0f,female)
            assertArrayEquals(pulled,pose.matrices,.0001f)
        }
    }

    @Test fun clubReachesTheBallAtTheSharedImpactInstant() {
        val pose=GolferPose()
        for(female in listOf(false,true)) for(putt in listOf(false,true)) for(power in listOf(.03f,.5f,1f)) {
            pose.update(GolferPose.releaseProgress(GolferPose.STRIKE_SECONDS),power,putt,0f,female)
            val head=pose.point(GolferPose.CLUB,0f,1.04f,0f)
            assertEquals(0f,head.x,.001f)
            assertEquals(.035f,head.y,.002f)
            assertEquals(.983f,head.z,.002f)
        }
    }

    @Test fun clubMovesThroughContactWithoutEasingToAStopOnTheBall() {
        val pose=GolferPose()
        pose.update(GolferPose.releaseProgress(GolferPose.STRIKE_SECONDS-.002f),1f,false,0f,false)
        val before=pose.point(GolferPose.CLUB,0f,1.04f,0f)
        pose.update(GolferPose.releaseProgress(GolferPose.STRIKE_SECONDS+.002f),1f,false,0f,false)
        val after=pose.point(GolferPose.CLUB,0f,1.04f,0f)
        assertTrue(before.x>.02f)
        assertTrue(after.x<-.02f)
    }

    @Test fun everyWardrobeCombinationHasValidGeometryAndFitsInOneMesh() {
        for(female in listOf(false,true)) for(outfit in 0..2) for(skin in 0..2) {
            val mesh=GolferGeometry.build(GolferAppearance(female,outfit,skin),false,true)
            assertEquals(0,mesh.size%(GolferGeometry.STRIDE*3))
            assertTrue(mesh.size/GolferGeometry.STRIDE/3<30000)
            assertTrue(mesh.all { it.isFinite() })
            for(i in mesh.indices step GolferGeometry.STRIDE) {
                val n=GVec(mesh[i+3],mesh[i+4],mesh[i+5])
                // Ellipsoid poles can have coincident vertices, but their normals must remain unit length.
                assertEquals(1f,n.dot(n),.002f)
                assertTrue(mesh[i+9].toInt() in 0 until GolferPose.COUNT)
            }
        }
    }
}
