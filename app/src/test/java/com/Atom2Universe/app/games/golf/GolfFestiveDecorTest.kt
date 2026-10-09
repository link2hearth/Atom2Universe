package com.Atom2Universe.app.games.golf

import com.Atom2Universe.app.games.golf.classic.core.*
import com.Atom2Universe.app.games.golf.classic.render.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class GolfFestiveDecorTest {
    @Test fun seasonalVillagesStayOutsideEveryPlayableCorridorAndHazard() {
        for(hole in HeatherCourse.holes+VertigoCourse.holes+SnowPeaksCourse.holes) {
            val decor=ClassicFestiveDecor(hole)
            assertTrue("${hole.decorTheme} hole ${hole.number}: missing landmarks",decor.sites.size>=2)
            for(site in decor.sites) for(dx in listOf(-8f,0f,8f)) for(dz in listOf(-8f,0f,8f)) {
                val x=site.origin.x+dx; val z=site.origin.z+dz
                assertEquals(GolfLie.ROUGH,hole.lieAt(x,z))
                assertTrue(hole.fairwaySignedDistance(x,z)>4f)
                assertTrue(hole.greenSignedDistance(x,z)>5f)
                assertTrue(hole.hazards.none { it.signedDistance(x,z)<3f })
            }
            val vertices=decor.scenery.sumOf { it.count }
            assertTrue("${hole.decorTheme} geometry budget: $vertices",vertices<550_000)
        }
    }

    @Test fun sharedModelsAndCloseRabbitHaveFiniteBoundedGeometry() {
        val meshes=listOf(GolfToyboxModels.mushroom.build(),GolfToyboxModels.snowman.build())+
            GolfRabbit(false).meshes+GolfRabbit(true).meshes+
            listOf(HeatherCourse.holes.first(),VertigoCourse.holes.first(),SnowPeaksCourse.holes.first())
                .flatMap { ClassicFestiveDecor(it).animatedMeshes }
        val field=ClassicMesh::class.java.getDeclaredField("vertices").apply { isAccessible=true }
        for(mesh in meshes) {
            val data=field.get(mesh) as FloatArray
            assertTrue(mesh.count>0)
            assertEquals(0,mesh.count%3)
            assertTrue(data.all { it.isFinite() })
            for(i in data.indices step 6) {
                assertTrue(abs(data[i])<10f && abs(data[i+1])<10f && abs(data[i+2])<10f)
                for(c in 3..5) assertTrue(data[i+c] in 0f..1.1f)
            }
        }
    }
}
