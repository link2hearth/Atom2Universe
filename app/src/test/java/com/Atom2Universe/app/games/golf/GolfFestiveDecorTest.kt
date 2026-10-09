package com.Atom2Universe.app.games.golf

import com.Atom2Universe.app.games.golf.classic.core.*
import com.Atom2Universe.app.games.golf.classic.render.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class GolfFestiveDecorTest {
    @Test fun terrainProjectionReusesHeightsWithoutMovingAnyVertex() {
        val model = MeshBuilder().apply {
            box(0f, 0f, 0f, 3f, 2f, 4f, C(.4f, .5f, .6f))
            ellipsoid(1f, 3f, -1f, .6f, .7f, .8f, C(.7f, .5f, .3f))
        }
        val field = ClassicMesh::class.java.getDeclaredField("vertices").apply { isAccessible = true }
        val source = field.get(model.build()) as FloatArray
        fun ground(x: Float, z: Float) = .013f * x + .002f * z * z
        val origin = P(-31f, 2f, 43f); val yaw = .37f; val scale = .8f
        var queries = 0
        val projected = MeshBuilder().apply {
            append(model, origin, yaw, scale, mirrorX = -1f, groundHeight = { x, z ->
                queries++; ground(x, z)
            })
        }.build()
        val actual = field.get(projected) as FloatArray
        assertEquals(source.size, actual.size)
        for (i in source.indices step 6) {
            val x = source[i] * scale * -1f; val z = source[i + 2] * scale
            val wx = origin.x + x * cos(yaw) + z * sin(yaw)
            val wz = origin.z - x * sin(yaw) + z * cos(yaw)
            assertEquals(wx, actual[i], 0f)
            assertEquals(ground(wx, wz) + source[i + 1] * scale, actual[i + 1], 0f)
            assertEquals(wz, actual[i + 2], 0f)
            for (c in 3..5) assertEquals(source[i + c], actual[i + c], 0f)
        }
        assertTrue("Shared triangle corners should not repeat terrain queries", queries < projected.count / 3)
    }

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
