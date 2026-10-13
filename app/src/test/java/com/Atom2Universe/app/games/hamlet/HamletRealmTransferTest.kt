package com.Atom2Universe.app.games.hamlet

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class HamletRealmTransferTest {
    private var time = 1_700_000_000_000L
    private fun game(creative: Boolean = false) = HamletGame({ time }, Random(14)).apply { startNew(creative) }

    @Test fun `founding charges once then keeps a shared purse`() {
        val g = game()
        g.coins = 60_000
        assertFalse(g.realms.found())
        g.xp = Hamlet.xpTable[14].toLong()
        val originalIds = g.objs.keys.toSet()
        assertTrue(g.realms.found())
        assertEquals(10_000L, g.coins)
        assertEquals(2, g.realms.current)
        assertEquals(2, g.realms.count)
        assertTrue(g.objs.keys.none { it in originalIds })
        g.addCoins(137)
        val xp = g.xp
        assertTrue(g.realms.switchTo(1))
        assertEquals(originalIds, g.objs.keys)
        assertEquals(10_137L, g.coins)
        assertEquals(xp, g.xp)
        assertEquals(1L, g.stats["realms"])
        assertFalse(g.realms.switchTo(7))
        assertEquals(1, g.realms.current)
    }

    @Test fun `creative islands exceed five and remain independently editable`() {
        val g = game(true)
        g.realms.rename(1, "Verger")
        val field = g.objs.values.first { it.t == "field" }
        assertTrue(g.plant(field, "corn"))
        g.hall = 4; g.expand = 2
        val firstGround = g.paths.copyOf()
        repeat(7) { assertTrue(g.realms.found()) }
        assertEquals(8, g.realms.count)
        assertEquals(250L, g.coins)
        assertEquals(1, g.hall)
        assertEquals(0, g.expand)
        g.realms.rename(8, "Ateliers")
        val lastIds = g.objs.keys.toSet()
        time += 46_000
        assertTrue(g.realms.switchTo(1))
        assertEquals("Verger", g.realms.name())
        assertEquals(4, g.hall); assertEquals(2, g.expand)
        assertArrayEquals(firstGround, g.paths)
        assertEquals(Status.RIPE, g.status(g.objs.getValue(field.id)))
        assertTrue(g.realms.switchTo(8))
        assertEquals("Ateliers", g.realms.name())
        assertEquals(lastIds, g.objs.keys)
    }

    @Test fun `all realms names terrain and global adventure round trip`() {
        val g = game(true)
        g.realms.rename(1, "Jardin de l’été")
        g.realms.found(); g.realms.rename(2, "Le port")
        g.hall = 3; g.expand = 4
        g.coins = 12_345; g.gems = 78; g.quest = 5; g.questBase = 3
        g.adventure.frontier = 17; g.journey.supplies = 82
        g.visitT = time + 300_000
        val prepared = HamletTransfer.inspect(HamletTransfer.exportWorld(g))
        assertTrue(prepared.issues.toString(), prepared.valid)
        assertTrue(prepared.warnings.isEmpty())
        val restored = game()
        assertTrue(HamletTransfer.apply(restored, prepared))
        assertEquals(2, restored.realms.current)
        assertEquals("Le port", restored.realms.name())
        assertEquals(12_345L, restored.coins)
        assertEquals(17, restored.adventure.frontier)
        assertEquals(82, restored.journey.supplies)
        assertEquals(g.visitT, restored.visitT)
        assertEquals(3, restored.hall); assertEquals(4, restored.expand)
        val ids = restored.objs.keys.toSet()
        assertTrue(restored.realms.switchTo(1))
        assertEquals("Jardin de l’été", restored.realms.name())
        assertTrue(restored.objs.keys.none { it in ids })
        assertEquals(17, restored.adventure.frontier)
        assertEquals(12_345L, restored.coins)
    }

    @Test fun `old native saves migrate to one realm without losing the village`() {
        val g = game()
        val json = JSONObject(HamletSave.toJson(g)).put("version", 4)
        json.remove("realmNo"); json.remove("realmName"); json.remove("parked")
        val restored = game(true)
        restored.realms.found()
        assertTrue(HamletSave.fromJson(restored, json.toString()))
        assertEquals(1, restored.realms.count)
        assertEquals(1, restored.realms.current)
        assertEquals(g.objs.keys, restored.objs.keys)
        assertFalse(restored.sandbox)
    }

    @Test fun `bad active or parked objects never partially overwrite the live world`() {
        val g = game(true)
        g.realms.found()
        val before = HamletSave.toJson(g)
        val unknown = JSONObject(before)
        unknown.getJSONArray("objs").getJSONObject(0).put("t", "missing_definition")
        assertFalse(HamletTransfer.importWorld(g, unknown.toString()))
        assertEquals(before, HamletSave.toJson(g))
        val duplicate = JSONObject(before)
        duplicate.getJSONArray("parked").getJSONObject(0).getJSONArray("objs").getJSONObject(0)
            .put("id", duplicate.getJSONArray("objs").getJSONObject(0).getInt("id"))
        assertFalse(HamletSave.fromJson(g, duplicate.toString()))
        assertEquals(before, HamletSave.toJson(g))
        val overlap = JSONObject(before)
        val objects = overlap.getJSONArray("objs")
        objects.put(JSONObject(objects.getJSONObject(0).toString()).put("id", 9000).put("t", "cottage"))
        assertFalse(HamletTransfer.importWorld(g, overlap.toString()))
        assertEquals(before, HamletSave.toJson(g))
    }

    @Test fun `legacy conversion explicitly retains crops money and realm names`() {
        val legacy = JSONObject().put("v", 1).put("castle", 1).put("name", "Vieux jardin")
            .put("coins", 7654).put("mojo", 8).put("xp", 123).put("sandbox", false).put("qbase", 14)
            .put("paths", JSONArray().put(31 * 64 + 31).put(1))
            .put("objs", JSONArray()
                .put(JSONObject().put("id", 1).put("t", "castle").put("x", 25).put("y", 22))
                .put(JSONObject().put("id", 2).put("t", "farm").put("x", 24).put("y", 34).put("c", "corn").put("t0", time)))
        val g = game()
        val original = HamletSave.toJson(g)
        val p = HamletTransfer.inspect(legacy.toString())
        assertTrue(p.issues.toString(), p.valid)
        assertTrue(p.legacy); assertTrue(p.warnings.isNotEmpty())
        assertFalse(HamletTransfer.apply(g, p))
        assertEquals(original, HamletSave.toJson(g))
        assertTrue(HamletTransfer.apply(g, p, acceptWarnings = true))
        assertEquals(7654L, g.coins); assertEquals(8, g.gems)
        assertEquals("Vieux jardin", g.realms.name())
        assertEquals(26, g.objs.getValue(1).x); assertEquals(23, g.objs.getValue(1).y)
        assertEquals("corn", g.objs.getValue(2).crop)
        assertEquals(time, g.objs.getValue(2).t0)
        assertEquals(14L, g.questBase)
    }

    @Test fun `malformed paths future versions and realm numbering are rejected`() {
        val base = HamletSave.toJson(game(true))
        assertFalse(HamletTransfer.inspect(JSONObject(base).put("version", 999).toString()).valid)
        assertFalse(HamletTransfer.inspect(JSONObject(base).put("realmNo", 7).toString()).valid)
        assertFalse(HamletTransfer.inspect(JSONObject(base).put("paths", JSONArray().put(8)).toString()).valid)
        assertFalse(HamletTransfer.inspect(JSONObject(base).put("paths", JSONArray().put(8).put(999)).toString()).valid)
    }

    @Test fun `recent catalogue picks persist and new game resets realms`() {
        val g = game(true)
        g.buildHistory.restoreRecent(listOf("cottage", "field", "missing", "hall", "cottage"))
        assertEquals(listOf("cottage", "field"), g.buildHistory.recent)
        assertTrue(g.realms.found())
        val loaded = game()
        assertTrue(HamletSave.fromJson(loaded, HamletSave.toJson(g)))
        assertEquals(g.buildHistory.recent, loaded.buildHistory.recent)
        g.startNew(true)
        assertEquals(1, g.realms.count)
    }

    @Test fun `creative templates create local land without replacing shared progress`() {
        val g = game(true)
        g.coins = 9876; g.adventure.frontier = 20; g.journey.supplies = 43
        assertTrue(g.realms.found(HamletRealms.Template.EMPTY))
        assertEquals(1, g.objs.size); assertEquals(Hamlet.MAX_REALM, g.realmSize())
        assertTrue(g.paths.all { it == 0 })
        assertTrue(g.realms.found(HamletRealms.Template.SHOWCASE))
        assertTrue(g.objs.size > 25)
        assertEquals(9876L, g.coins); assertEquals(20, g.adventure.frontier); assertEquals(43, g.journey.supplies)
        assertTrue(g.realms.switchTo(2)); assertEquals(1, g.objs.size)
        val regular = game()
        assertFalse(regular.realms.found(HamletRealms.Template.EMPTY))
    }

    @Test fun `legacy ground codes crop aliases and reordered quest objectives are translated by identity`() {
        val json = JSONObject().put("castle", 1).put("quest", 15).put("qbase", 6)
            .put("stats", JSONObject().put("cat_house", 8))
            .put("paths", JSONArray().put(100).put(14).put(101).put(15).put(102).put(16).put(103).put(17))
            .put("objs", JSONArray()
                .put(JSONObject().put("id", 1).put("t", "castle").put("x", 25).put("y", 22))
                .put(JSONObject().put("id", 2).put("t", "magicfarm").put("x", 24).put("y", 34).put("c", "cauliflower").put("t0", time)))
        val prepared = HamletTransfer.inspect(json.toString())
        assertTrue(prepared.issues.toString(), prepared.valid)
        val g = game()
        assertTrue(HamletTransfer.apply(g, prepared, true))
        assertEquals(Hamlet.pathCode["lavaflow"], g.paths[100])
        assertEquals(Hamlet.pathCode["haypath"], g.paths[101])
        assertEquals(Hamlet.pathCode["icepath"], g.paths[102])
        assertEquals(Hamlet.pathCode["sandpatch"], g.paths[103])
        assertEquals("glowcabbage", g.objs.getValue(2).crop)
        assertEquals("cat_home", Hamlet.quests[g.quest].key)
        assertEquals(3, Hamlet.quests[g.quest].n)
        assertEquals(6L, g.questBase)
        assertEquals(8L, g.stats["cat_home"])
    }
}
