package com.Atom2Universe.app.games.golf

import com.Atom2Universe.app.games.golf.classic.core.*
import com.Atom2Universe.app.games.golf.classic.render.*
import org.junit.Assert.*
import org.junit.Test

class ClassicLakeSurfaceTest {
    @Test fun lightingCacheKeepsThePlayableSurfaceAndCarriesTheDryBankContour() {
        for(hole in listOf(VertigoCourse.holes[5],ClassicCourse.holes[17])) {
            val mesh=ClassicLandscape(hole).terrain()
            val vertices=GroundMesh::class.java.getDeclaredField("vertices").apply { isAccessible=true }.get(mesh) as FloatArray
            val lakes=hole.hazards.filter { it.lie==GolfLie.WATER }
            var banks=0; var checked=0
            for(i in vertices.indices step GroundBuilder.STRIDE*17) {
                if(vertices[i+13]!=1f) continue // Distant landscape and paths have their own height.
                val x=vertices[i]; val z=vertices[i+2]
                assertEquals("Rendered ground must still follow physics",hole.heightAt(x,z),vertices[i+1],.00002f)
                assertTrue("Finite bounded lighting",vertices[i+12] in 0f..1f)
                val distance=lakes.minOf { it.signedDistance(x,z) }.coerceAtMost(1000f)
                assertEquals("Unclamped shore distance for smooth banks",distance,vertices[i+14],.001f)
                if(distance in 1f..8f) {
                    assertEquals("The bank remains dry",0f,vertices[i+9],0f)
                    banks++
                }
                checked++
            }
            assertTrue(checked>1000)
            assertTrue("Check dry bank samples, not just the water",banks>30)
        }
    }
}
