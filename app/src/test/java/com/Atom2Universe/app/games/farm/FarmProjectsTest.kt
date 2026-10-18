package com.Atom2Universe.app.games.farm

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class FarmProjectsTest {
    @Test fun `changer de chantier conserve chaque apport`() {
        val state = FarmProjectsState()
        state.sync(1)
        assertTrue(state.select(FarmProject.POND, 1))
        state.sync(3)
        assertEquals(1, state.progress(FarmProject.WELL))
        assertEquals(2, state.progress(FarmProject.POND))
        assertTrue(state.select(FarmProject.WELL, 3))
        state.sync(4)
        assertEquals(2, state.progress(FarmProject.WELL))
        assertEquals(2, state.progress(FarmProject.POND))
    }

    @Test fun `la reserve attend le choix et le surplus est conserve`() {
        val state = FarmProjectsState()
        state.sync(8)
        assertTrue(state.complete(FarmProject.WELL))
        assertNull(state.active)
        assertEquals(5, state.reserve)
        assertFalse(state.select(FarmProject.WELL, 8))
        assertTrue(state.select(FarmProject.POND, 8))
        assertEquals(5, state.progress(FarmProject.POND))
        state.sync(10)
        assertTrue(state.complete(FarmProject.POND))
        assertEquals(1, state.reserve)
        assertTrue(state.select(FarmProject.PICNIC, 10))
        state.sync(18)
        assertTrue(FarmProject.entries.all { state.complete(it) })
        assertNull(state.active)
        assertEquals(0, state.reserve)
    }

    @Test fun `reappliquer le compteur de livraisons ne redonne pas de progression`() {
        val state = FarmProjectsState()
        state.sync(2)
        repeat(10) { state.sync(2) }
        assertEquals(2, state.progress(FarmProject.WELL))
        assertEquals(0, state.reserve)
        assertFalse(state.select(FarmProject.WELL, 2))
    }

    @Test fun `chaque projet a quatre etats accessibles et une prochaine etape positive`() {
        FarmProject.entries.forEach { project ->
            assertEquals(setOf(0, 1, 2, 3), (0..project.helpNeeded).map { project.stage(it) }.toSet())
            for (progress in 0 until project.helpNeeded) {
                val next = project.nextStageAt(progress)
                assertTrue(next > progress && next <= project.helpNeeded)
                assertTrue(project.stage(next) > project.stage(progress))
            }
        }
    }

    @Test fun `la sauvegarde conserve choix reserve et chantiers partiels sans horloge`() {
        val state = FarmProjectsState()
        state.sync(1); state.select(FarmProject.POND, 1); state.sync(4)
        val saved = state.toJson().toString()
        val restored = FarmProjectsState.fromJson(JSONObject(saved), 4)
        assertEquals(saved, restored.toJson().toString())
        assertEquals(FarmProject.POND, restored.active)
        assertEquals(1, restored.progress(FarmProject.WELL))
        assertEquals(3, restored.progress(FarmProject.POND))
        assertEquals(0, restored.reserve)
        restored.sync(7)
        assertEquals(0, restored.reserve)
        restored.sync(12)
        assertEquals(5, FarmProjectsState.fromJson(restored.toJson(), 12).reserve)
    }

    @Test fun `une sauvegarde abimee ne cree pas de contributions ou de cadeaux places`() {
        val json = JSONObject().put("active", "UNKNOWN")
            .put("progress", JSONObject().put("WELL", -5).put("POND", 9999).put("PICNIC", 9999))
            .put("gifts", JSONObject().put("BIRDHOUSE", true))
            .put("placements", JSONObject().put("WELL", "FLOWERS").put("POND", "BIRDHOUSE").put("PICNIC", "BIRDHOUSE"))
        val state = FarmProjectsState.fromJson(json, 4)
        assertEquals(4, FarmProject.entries.sumOf { state.progress(it) })
        assertNull(state.decoration(FarmProject.WELL))
        assertEquals(FarmGift.BIRDHOUSE, state.decoration(FarmProject.POND))
        assertNull(state.decoration(FarmProject.PICNIC))
    }

    @Test fun `les cadeaux attendent leur palier et ne sont recuperables qu une fois`() {
        val state = FarmProjectsState()
        FarmGift.entries.forEach { gift ->
            assertFalse(state.claim(gift, 2))
            assertFalse(state.owns(gift))
            assertTrue(state.claim(gift, 3))
            assertFalse(state.claim(gift, 15))
            assertTrue(FarmProjectsState.fromJson(state.toJson(), 0).owns(gift))
        }
    }

    @Test fun `deplacer remplacer et ranger ne consomme pas les objets`() {
        val state = FarmProjectsState()
        assertFalse(state.place(FarmGift.FLOWERS, FarmProject.WELL))
        state.claim(FarmGift.FLOWERS, 3); state.claim(FarmGift.BIRDHOUSE, 3)
        assertTrue(state.place(FarmGift.FLOWERS, FarmProject.WELL))
        assertTrue(state.place(FarmGift.FLOWERS, FarmProject.POND))
        assertNull(state.decoration(FarmProject.WELL))
        assertFalse(state.place(FarmGift.FLOWERS, FarmProject.POND))
        assertTrue(state.place(FarmGift.BIRDHOUSE, FarmProject.POND))
        assertNull(state.location(FarmGift.FLOWERS))
        assertTrue(state.owns(FarmGift.FLOWERS))
        val restored = FarmProjectsState.fromJson(state.toJson(), 0)
        assertEquals(FarmProject.POND, restored.location(FarmGift.BIRDHOUSE))
        assertTrue(restored.store(FarmProject.POND))
        assertFalse(restored.store(FarmProject.POND))
        assertTrue(restored.owns(FarmGift.BIRDHOUSE))
    }

    @Test fun `les jardins ne couvrent ni cultures ni routes ni tresor`() {
        fun overlap(a: FarmLayout.Area, b: FarmLayout.Area) = a.left < b.right && a.right > b.left && a.top < b.bottom && a.bottom > b.top
        val areas = FarmProject.entries.flatMap { listOf(FarmProjectsLayout.project(it), FarmProjectsLayout.decoration(it)) }
        areas.forEachIndexed { index, area ->
            assertTrue(area.left >= 0 && area.top >= 0 && area.right <= FarmLayout.worldWidth && area.bottom <= FarmLayout.worldHeight)
            assertFalse(overlap(area, FarmLayout.treasure))
            assertFalse(overlap(area, FarmLayout.yard))
            assertTrue(area.right < FarmLayout.spineX - 70 || area.left > FarmLayout.spineX + 70)
            FarmLayout.lands.forEach { land ->
                assertFalse(overlap(area, FarmLayout.Area(land.x - 50, land.y - 50, land.x + land.width + 50, land.y + land.height + 50)))
                assertTrue(area.bottom < FarmLayout.laneY(land.band) - 50)
            }
            areas.drop(index + 1).forEach { assertFalse(overlap(area, it)) }
        }
    }
}
