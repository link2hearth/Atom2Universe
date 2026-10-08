package com.Atom2Universe.app.games.golf

import com.Atom2Universe.app.games.golf.classic.core.ClassicCourse
import com.Atom2Universe.app.games.golf.classic.core.GolfLie
import com.Atom2Universe.app.games.golf.classic.render.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.floor

/** Geometry constraints that keep vegetation affordable and the playing surfaces readable. */
class ClassicSceneryTest {
    @Test fun grassIsRepeatableBoundedAndAvoidsProtectedSurfaces() {
        var generated=0
        for(hole in ClassicCourse.holes) {
            for(z in listOf(0f,hole.length*.5f,hole.length)) for(offset in listOf(-30f,0f,30f)) {
                val x=floor((hole.fairwayCenter(z)+offset)/GrassGeometry.TILE).toInt()
                val tileZ=floor(z/GrassGeometry.TILE).toInt()
                val data=GrassGeometry.build(hole,x,tileZ)
                assertArrayEquals(data,GrassGeometry.build(hole,x,tileZ),0f)
                assertTrue(data.all{it.isFinite()})
                assertTrue(data.size/GrassGeometry.STRIDE/3<=GrassGeometry.MAX_TRIANGLES)
                generated+=data.size
                for(i in data.indices step GrassGeometry.STRIDE) {
                    val rx=data[i+6];val rz=data[i+8]
                    assertTrue(hole.lieAt(rx,rz) in listOf(GolfLie.ROUGH,GolfLie.FAIRWAY,GolfLie.TEE))
                    assertTrue(hole.greenSignedDistance(rx,rz)>.75f)
                    assertTrue(hole.hazards.none{it.signedDistance(rx,rz)<.29f})
                    assertTrue(abs(rx-hole.pathX(rz))>1.5f)
                }
            }
        }
        assertTrue(generated>0)
        assertTrue(GrassGeometry.MAX_TRIANGLES*GrassGeometry.MAX_VISIBLE_TILES<32000)
    }

    @Test fun decorStaysWithinTheMobileGeometryBudgetOnEveryHole() {
        for(hole in ClassicCourse.holes) {
            val chunks=ClassicDecor(hole).build()
            assertTrue(chunks.isNotEmpty())
            assertTrue("Hole ${hole.number}",chunks.sumOf{it.count/3}<50000)
            assertTrue(chunks.size<90)
        }
    }

    @Test fun cullingKeepsIntersectingBatchesAndRejectsAllSixOutsideDirections() {
        val vp=FloatArray(16){if(it%5==0)1f else 0f}
        fun box(x:Float,y:Float,z:Float)=MeshBuilder().apply {
            box(x,y,z,1f,1f,1f,C(1f,1f,1f))
        }.build()
        assertTrue(box(0f,0f,0f).visible(vp))
        assertTrue(box(1.25f,0f,0f).visible(vp))
        for(s in listOf(-4f,4f)) {
            assertFalse(box(s,0f,0f).visible(vp))
            assertFalse(box(0f,s,0f).visible(vp))
            assertFalse(box(0f,0f,s).visible(vp))
        }
    }
}
