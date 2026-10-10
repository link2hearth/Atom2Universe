package com.Atom2Universe.app.games.farm

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class FarmEncountersTest {
    private val now = 1_700_000_000_000L
    private val all = FarmVisitor.entries.toSet()
    @Test fun `une rencontre attend dix jours sans changer ni accumuler`() {
        val state = FarmEncountersState()
        assertTrue(state.ensure(setOf(FarmVisitor.ROBIN), FarmCrop.RADISH, now))
        val first = state.pending!!
        assertFalse(state.ensure(all, FarmCrop.PINEAPPLE, now + 10 * FarmEncountersState.INTERVAL))
        assertEquals(first, state.pending)
        val restored = FarmEncountersState.fromJson(JSONObject(state.toJson().toString()))
        assertEquals(first, restored.pending)
        assertEquals(0, restored.totalGreetings)
    }
    @Test fun `accueil attend 24 heures exactes sans changement a minuit`() {
        val state = FarmEncountersState(); state.ensure(all, FarmCrop.RADISH, now)
        val id = state.pending!!.id
        assertNotNull(state.greet(id, now)); assertNull(state.greet(id, now))
        assertFalse(state.ensure(all, FarmCrop.RADISH, now + FarmEncountersState.INTERVAL - 1))
        assertTrue(state.ensure(all, FarmCrop.RADISH, now + FarmEncountersState.INTERVAL))
        assertTrue(state.pending!!.id > id)
    }
    @Test fun `les visiteurs et leurs trois histoires tournent sans perte de rencontres`() {
        val state = FarmEncountersState()
        val stories = FarmVisitor.entries.associateWith { mutableSetOf<Int>() }
        repeat(12) { turn ->
            val clock = now + turn * FarmEncountersState.INTERVAL
            assertTrue(state.ensure(all, FarmCrop.RADISH, clock))
            val meeting = state.pending!!
            assertEquals(FarmVisitor.entries[turn % 4], meeting.visitor)
            stories.getValue(meeting.visitor) += meeting.story
            assertNotNull(state.greet(meeting.id, clock))
        }
        assertEquals(12, state.totalGreetings)
        FarmVisitor.entries.forEach { assertEquals(3, state.count(it)); assertEquals(setOf(0, 1, 2), stories.getValue(it)) }
    }
    @Test fun `une nouvelle espece est rencontree sans attendre la fin dun grand cycle`() {
        val state = FarmEncountersState()
        repeat(3) { turn ->
            val clock = now + turn * FarmEncountersState.INTERVAL
            state.ensure(setOf(FarmVisitor.ROBIN), FarmCrop.RADISH, clock); state.greet(state.pending!!.id, clock)
        }
        state.ensure(setOf(FarmVisitor.ROBIN, FarmVisitor.BUTTERFLY), FarmCrop.RADISH, now + 3 * FarmEncountersState.INTERVAL)
        assertEquals(FarmVisitor.BUTTERFLY, state.pending!!.visitor)
    }
    @Test fun `reculer lhorloge ne regenere pas une recompense`() {
        val state = FarmEncountersState(); state.ensure(all, FarmCrop.RADISH, now)
        state.greet(state.pending!!.id, now)
        assertFalse(state.ensure(all, FarmCrop.RADISH, now - FarmEncountersState.INTERVAL))
        assertFalse(state.ensure(all, FarmCrop.RADISH, -1))
        assertNull(state.pending)
    }
    @Test fun `surprises modestes et graines de legumes seulement`() {
        FarmCrop.ladder.forEach { crop ->
            val state = FarmEncountersState(); state.ensure(all, crop, now)
            val meeting = state.pending!!
            assertEquals(4, meeting.seeds)
            assertTrue(meeting.coins in 1L..maxOf(12, crop.sale).toLong())
            assertEquals(meeting.coins + 4L * crop.cost, meeting.coinAlternative)
        }
        assertFalse(FarmEncountersState().ensure(all, FarmCrop.APPLE, now))
    }
    @Test fun `json abime garde le carnet sans payer une rencontre invalide`() {
        val state = FarmEncountersState(); state.ensure(all, FarmCrop.RADISH, now)
        state.greet(state.pending!!.id, now)
        state.ensure(all, FarmCrop.RADISH, now + FarmEncountersState.INTERVAL)
        val json = state.toJson(); json.getJSONObject("pending").put("crop", "WHEAT")
        val repaired = FarmEncountersState.fromJson(json)
        assertNull(repaired.pending); assertEquals(1, repaired.count(FarmVisitor.ROBIN))
        assertNull(repaired.greet(2, now))
        assertEquals(repaired.toJson().toString(), FarmEncountersState.fromJson(JSONObject(repaired.toJson().toString())).toJson().toString())
    }
    @Test fun `zone visiteur separable du tresor des parcelles et des chantiers`() {
        val area = FarmVisitorLayout.area
        fun overlaps(other: FarmLayout.Area) = area.left < other.right && area.right > other.left && area.top < other.bottom && area.bottom > other.top
        assertFalse(overlaps(FarmLayout.treasure))
        FarmLayout.lands.forEach { assertFalse(overlaps(FarmLayout.Area(it.x, it.y, it.x + it.width, it.y + it.height))) }
        FarmProject.entries.forEach { assertFalse(overlaps(FarmProjectsLayout.project(it))); assertFalse(overlaps(FarmProjectsLayout.decoration(it))) }
        assertTrue(area.left >= 0 && area.top >= 0)
    }
}
