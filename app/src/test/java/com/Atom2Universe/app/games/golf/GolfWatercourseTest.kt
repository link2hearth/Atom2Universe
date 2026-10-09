package com.Atom2Universe.app.games.golf

import com.Atom2Universe.app.games.golf.classic.core.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class GolfWatercourseTest {
    private val holes=ClassicCourse.holes+HeatherCourse.holes+WildDetoursCourse.holes+VertigoCourse.holes

    @Test fun designedLakesAreOneConnectedBodyWithOneFlatWaterLevel() {
        for(hole in holes) {
            val lakes=hole.hazards.filter { it.lie==GolfLie.WATER }
            assertTrue("One composed lake on ${hole.number}",lakes.size<=1)
            for(lake in lakes) {
                val course=checkNotNull(lake.watercourse)
                for(p in course.samples) {
                    val x=lake.x+p.x;val z=lake.z+p.z
                    assertEquals("Wet spine ${hole.number} at $x/$z",GolfLie.WATER,hole.lieAt(x,z))
                    assertEquals("Single water level ${hole.number}",hole.waterHeight(lake),hole.heightAt(x,z),.002f)
                }
                // Flood fill the actual collision footprint, including all arms and end basins.
                val step=3f
                val nx=ceil(lake.rx*2/step).toInt()+3;val nz=ceil(lake.rz*2/step).toInt()+3
                val wet=BooleanArray(nx*nz) { i ->
                    lake.contains(lake.x-lake.rx-step+i%nx*step,lake.z-lake.rz-step+i/nx*step)
                }
                val visited=BooleanArray(wet.size)
                val queue=ArrayDeque<Int>()
                val first=wet.indexOfFirst { it };assertTrue(first>=0)
                visited[first]=true;queue.add(first)
                while(queue.isNotEmpty()) {
                    val i=queue.removeFirst()
                    for(j in intArrayOf(i-1,i+1,i-nx,i+nx)) {
                        if(j !in wet.indices || abs(j%nx-i%nx)+abs(j/nx-i/nx)!=1 || !wet[j] || visited[j]) continue
                        visited[j]=true;queue.add(j)
                    }
                }
                assertEquals("Disconnected basin ${hole.number}",wet.count { it },visited.count { it })
            }
        }
    }

    @Test fun windingBanksStayContinuousAndWaterMatchesTheContour() {
        for(hole in holes) for(lake in hole.hazards.filter { it.watercourse!=null }) {
            val points=lake.watercourse!!.samples
            for(i in 1 until points.lastIndex step 8) {
                val p=points[i];val a=points[i-1];val b=points[i+1]
                val span=hypot(b.x-a.x,b.z-a.z)
                for(side in listOf(-1f,1f)) {
                    val dx=(b.z-a.z)/span*side;val dz=(a.x-b.x)/span*side
                    var lo=0f;var hi=max(lake.rx,lake.rz)*3f
                    repeat(24) {
                        val r=(lo+hi)*.5f
                        if(lake.contains(lake.x+p.x+dx*r,lake.z+p.z+dz*r))lo=r else hi=r
                    }
                    val x=lake.x+p.x+dx*lo;val z=lake.z+p.z+dz*lo
                    assertTrue(lake.contains(x-dx*.02f,z-dz*.02f))
                    assertFalse(lake.contains(x+dx*.02f,z+dz*.02f))
                    if(hole.greenSignedDistance(x,z)>10f) assertEquals("Bank ${hole.number}",
                        hole.heightAt(x-dx*.001f,z-dz*.001f),hole.heightAt(x+dx*.001f,z+dz*.001f),.015f)
                }
            }
        }
    }

    @Test fun spineCanTurnBackWithoutFillingTheDryPeninsula() {
        val lake=GolfWatercourse.lake(GolfWaterNode(-35f,0f,10f),GolfWaterNode(-35f,60f,12f),
            GolfWaterNode(0f,90f,13f),GolfWaterNode(35f,60f,12f),GolfWaterNode(35f,0f,10f))
        assertFalse("A dry peninsula inside a horseshoe",lake.contains(0f,45f))
        assertTrue(lake.contains(-35f,45f))
        assertTrue(lake.contains(35f,45f))
        assertTrue(lake.contains(0f,90f))
        val shifted=lake.copy(x=lake.x+100f,z=lake.z-30f)
        assertEquals(lake.signedDistance(0f,45f),shifted.signedDistance(100f,15f),.0001f)
    }
}
