package com.Atom2Universe.app.games.farm

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class FarmGreenhouseTest {
    private val now = 1_700_000_000_000L
    private fun start(state: FarmGreenhouseState, flower: FarmFlower = FarmFlower.DAISY_WHITE, crossed: Boolean = false, time: Long = now, slot: Int? = null) =
        state.start(flower, crossed, state.revision, time, slot)
    private fun stocked() = FarmGreenhouseState.fromJson(JSONObject().put("introUsed", true)
        .put("stock", JSONObject().apply { FarmFlower.entries.filter { it.base }.forEach { put(it.name, 3) } }))

    @Test fun `six varietes initiales et trois croisements garantis sans fleurs rares obligatoires`() {
        val state = FarmGreenhouseState()
        assertEquals(9, FarmFlower.entries.size)
        assertEquals(6, state.collection.size)
        assertTrue(state.recipeFlowers.isEmpty())
        FarmFlower.entries.filterNot { it.base }.forEach { flower ->
            assertFalse(state.knows(flower)); assertEquals(2, flower.parents.size)
            assertTrue(flower.parents.all { it.base && it.family == flower.family })
        }
        assertTrue(FarmRecipe.entries.flatMap { it.flowers }.all { it.flower.base })
        assertEquals(24, FarmRecipe.GARDEN_BOUQUET.ingredientValue)
        assertEquals(32, FarmRecipe.GARDEN_BOUQUET.sale)
    }
    @Test fun `six cultures gratuites paralleles et une seule introduction`() {
        val state = FarmGreenhouseState()
        FarmFlower.entries.filter { it.base }.forEach { assertTrue(start(state, it)) }
        assertEquals(6, state.jobs.size); assertEquals(6, state.jobs.map { it.slot }.distinct().size)
        assertEquals(now + 60_000, state.jobs.first().readyAt)
        assertEquals(now + 12 * 3_600_000L, state.jobs[1].readyAt)
        assertEquals(now + 18 * 3_600_000L, state.jobs[2].readyAt)
        assertEquals(now + 24 * 3_600_000L, state.jobs.last().readyAt)
        val before = state.toJson().toString()
        assertFalse(start(state)); assertEquals(before, state.toJson().toString())
        assertEquals(0, state.collect(now + 59_999))
        assertEquals(1, state.collect(now + 60_000)); assertTrue(start(state, time = now + 60_000))
        assertEquals(now + 60_000 + 12 * 3_600_000L, state.at(0)!!.readyAt)
    }
    @Test fun `absence de dix jours laisse exactement six fleurs et ne replante pas`() {
        val state = FarmGreenhouseState(); repeat(6) { assertTrue(start(state)) }
        val later = now + 10 * 86_400_000L
        val restored = FarmGreenhouseState.fromJson(JSONObject(state.toJson().toString()))
        assertEquals(6, restored.readyCount(later)); assertEquals(6, restored.collect(later))
        assertEquals(6, restored.totalStock()); assertTrue(restored.jobs.isEmpty())
        assertEquals(0, restored.collect(later)); assertEquals(0, restored.collect(later + 86_400_000))
    }
    @Test fun `croisement retire exactement deux parents et decouvre uniquement a la collecte`() {
        val state = stocked()
        assertTrue(start(state, FarmFlower.DAISY_GOLD, true))
        assertEquals(2, state.count(FarmFlower.DAISY_WHITE)); assertEquals(2, state.count(FarmFlower.DAISY_YELLOW))
        assertFalse(state.knows(FarmFlower.DAISY_GOLD))
        assertEquals(now + FarmGreenhouseState.CROSS_MILLIS, state.jobs.single().readyAt)
        val restored = FarmGreenhouseState.fromJson(JSONObject(state.toJson().toString()))
        assertFalse(restored.knows(FarmFlower.DAISY_GOLD))
        assertEquals(0, restored.collect(now + FarmGreenhouseState.CROSS_MILLIS - 1))
        assertEquals(1, restored.collect(now + FarmGreenhouseState.CROSS_MILLIS))
        assertTrue(restored.knows(FarmFlower.DAISY_GOLD)); assertEquals(1, restored.count(FarmFlower.DAISY_GOLD))
        restored.take(FarmFlower.DAISY_GOLD, 1)
        assertTrue(start(restored, FarmFlower.DAISY_GOLD, time = now + FarmGreenhouseState.CROSS_MILLIS))
        assertEquals(2, restored.count(FarmFlower.DAISY_WHITE))
    }
    @Test fun `un parent manquant ou un bouton perime ne retire rien`() {
        val state = stocked(); state.take(FarmFlower.IRIS_WHITE, 3)
        val before = state.toJson().toString()
        assertFalse(start(state, FarmFlower.IRIS_LILAC, true)); assertEquals(before, state.toJson().toString())
        val revision = state.revision
        assertTrue(start(state, FarmFlower.TULIP_ORANGE, true))
        assertFalse(state.start(FarmFlower.TULIP_ORANGE, true, revision, now))
        assertEquals(2, state.count(FarmFlower.TULIP_RED)); assertEquals(2, state.count(FarmFlower.TULIP_YELLOW))
        assertFalse(start(state, FarmFlower.DAISY_WHITE, true))
    }
    @Test fun `une jardiniere occupee pleine ou invalide ne consomme pas les parents`() {
        val state = stocked(); assertTrue(start(state, slot = 3))
        val before = state.toJson().toString()
        assertFalse(start(state, FarmFlower.DAISY_GOLD, true, slot = 3))
        assertFalse(start(state, slot = -1)); assertFalse(start(state, slot = 6))
        assertEquals(before, state.toJson().toString())
        repeat(5) { assertTrue(start(state)) }
        assertFalse(start(state, FarmFlower.DAISY_GOLD, true))
        assertEquals(3, state.count(FarmFlower.DAISY_WHITE))
    }
    @Test fun `stock plein preserve une fleur en place et permet la collecte des autres`() {
        val state = FarmGreenhouseState.fromJson(JSONObject().put("stock", JSONObject().put("DAISY_WHITE", 999_999)))
        assertTrue(start(state)); assertTrue(start(state, FarmFlower.TULIP_RED))
        assertEquals(1, state.collect(now + 86_400_000L))
        assertEquals(1, state.jobs.size); assertEquals(FarmFlower.DAISY_WHITE, state.jobs.single().flower)
        state.take(FarmFlower.DAISY_WHITE, 1)
        assertEquals(1, state.collect(now + 86_400_000L)); assertEquals(999_999, state.count(FarmFlower.DAISY_WHITE))
    }
    @Test fun `decouverte et decoration persistent sans stock ni consommation`() {
        val state = stocked()
        assertFalse(state.display(FarmFlower.IRIS_LILAC))
        assertTrue(start(state, FarmFlower.IRIS_LILAC, true)); state.collect(now + FarmGreenhouseState.CROSS_MILLIS)
        assertTrue(state.display(FarmFlower.IRIS_LILAC)); assertEquals(1, state.count(FarmFlower.IRIS_LILAC))
        state.take(FarmFlower.IRIS_LILAC, 1)
        val restored = FarmGreenhouseState.fromJson(JSONObject(state.toJson().toString()))
        assertTrue(restored.knows(FarmFlower.IRIS_LILAC)); assertEquals(FarmFlower.IRIS_LILAC, restored.displayFlower)
        assertEquals(0, restored.count(FarmFlower.IRIS_LILAC))
    }
    @Test fun `horloge reculee et echeances exactes ne donnent pas de fleurs supplementaires`() {
        val state = FarmGreenhouseState(); assertTrue(start(state)); assertFalse(start(state, time = -1))
        state.collect(now + 60_000)
        assertTrue(start(state, time = now - 86_400_000))
        val job = state.jobs.single()
        assertEquals(now + 60_000 + 12 * 3_600_000L, job.readyAt)
        assertTrue(job.progress(job.readyAt - 1) < 1f)
        assertEquals(0, state.collect(job.readyAt - 1)); assertEquals(1, state.collect(job.readyAt))
    }
    @Test fun `json abime repare les cultures sans doublons et les stocks sans negatifs`() {
        val state = FarmGreenhouseState(); start(state)
        val json = state.toJson(); val job = json.getJSONArray("jobs").getJSONObject(0)
        val rawJobs = JSONArray().put(job).put(JSONObject(job.toString()).put("slot", 1))
            .put(JSONObject(job.toString()).put("id", 2).put("slot", 8))
            .put(JSONObject(job.toString()).put("id", 3).put("slot", 2).put("readyAt", -1))
        val repaired = FarmGreenhouseState.fromJson(json.put("jobs", rawJobs).put("stock", JSONObject().put("IRIS_BLUE", -8)))
        assertEquals(1, repaired.jobs.size); assertEquals(0, repaired.count(FarmFlower.IRIS_BLUE))
        assertEquals(FarmFlower.DAISY_WHITE, repaired.displayFlower)
        assertEquals(repaired.toJson().toString(), FarmGreenhouseState.fromJson(JSONObject(repaired.toJson().toString())).toJson().toString())
    }
    @Test fun `chaque couleur croisee peut etre obtenue sans tirage aleatoire`() {
        val state = stocked()
        FarmFlower.entries.filterNot { it.base }.forEach { assertTrue(start(state, it, true)) }
        assertEquals(3, state.collect(now + FarmGreenhouseState.CROSS_MILLIS))
        assertEquals(FarmFlower.entries.toSet(), state.collection)
    }
}
