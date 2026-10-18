package com.Atom2Universe.app.games.farm

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class FarmProjectsSaveTest {
    private lateinit var prefs: SharedPreferences
    @Before fun setUp() {
        prefs = RuntimeEnvironment.getApplication().getSharedPreferences("farm-projects-test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }
    private fun deliver(farm: FarmState, villager: FarmVillager = FarmVillager.LUCIE): Long {
        // This guard tests crop deliveries; the workshop suite covers prepared deliveries.
        while (farm.village.orders.first { it.villager == villager }.preparation != null) {
            farm.replaceOrder(farm.village.orders.first { it.villager == villager }.id)
        }
        val order = farm.village.orders.first { it.villager == villager }
        order.items.forEach { farm.produce[it.crop.ordinal][0] = it.count }
        assertNotNull(farm.deliverOrder(order.id))
        return order.id
    }

    @Test fun `la livraison sauvegarde le chantier et un ancien bouton ne donne rien`() {
        val farm = FarmState(prefs)
        val id = deliver(farm)
        val restored = FarmState(prefs)
        assertEquals(1, restored.projects.progress(FarmProject.WELL))
        assertEquals(farm.coins, restored.coins)
        assertEquals(1, restored.village.deliveries)
        val saved = prefs.getString(FarmState.KEY_STATE, null)
        assertNull(farm.deliverOrder(id))
        assertEquals(saved, prefs.getString(FarmState.KEY_STATE, null))
        assertEquals(1, farm.projects.progress(FarmProject.WELL))
        val next = farm.village.orders.first()
        assertNull(farm.deliverOrder(next.id))
        assertTrue(farm.replaceOrder(next.id))
        assertEquals(1, farm.projects.progress(FarmProject.WELL))
    }

    @Test fun `un cadeau depend du bon voisin et sa place persiste apres absence`() {
        val farm = FarmState(prefs)
        repeat(3) { deliver(farm) }
        assertTrue(farm.projects.complete(FarmProject.WELL))
        assertFalse(farm.claimGift(FarmGift.BIRDHOUSE))
        assertTrue(farm.claimGift(FarmGift.FLOWERS))
        assertFalse(farm.claimGift(FarmGift.FLOWERS))
        val coins = farm.coins
        assertTrue(farm.placeGift(FarmGift.FLOWERS, FarmProject.POND))
        farm.cheatSkipTime(10 * 24 * 3_600_000L)
        val restored = FarmState(prefs)
        assertEquals(coins, restored.coins)
        assertEquals(FarmProject.POND, restored.projects.location(FarmGift.FLOWERS))
        assertTrue(restored.storeGift(FarmProject.POND))
        assertTrue(FarmState(prefs).projects.owns(FarmGift.FLOWERS))
        assertNull(FarmState(prefs).projects.location(FarmGift.FLOWERS))
    }

    @Test fun `migration v9 compte les anciennes livraisons sans modifier la ferme`() {
        val farm = FarmState(prefs)
        farm.cheatAddCoins(1234)
        repeat(5) { deliver(farm) }
        farm.clean(0); farm.plant(0, 1000L); farm.waterMany(listOf(0), 1000L)
        farm.save()
        val legacy = JSONObject(prefs.getString(FarmState.KEY_STATE, null)!!)
        legacy.put("version", 9); legacy.remove("projects")
        prefs.edit().putString(FarmState.KEY_STATE, legacy.toString()).commit()
        val restored = FarmState(prefs)
        assertTrue(restored.projects.complete(FarmProject.WELL))
        assertEquals(2, restored.projects.reserve)
        assertTrue(restored.claimGift(FarmGift.FLOWERS))
        // Claiming the scenery gift must not change any previously saved farm fields.
        val migrated = JSONObject(prefs.getString(FarmState.KEY_STATE, null)!!)
        legacy.keys().forEach { key ->
            if (key != "version") assertEquals(key, legacy.get(key).toString(), migrated.get(key).toString())
        }
        assertTrue(restored.selectProject(FarmProject.POND))
        assertEquals(2, FarmState(prefs).projects.progress(FarmProject.POND))
    }

    @Test fun `un sous document abime preserve la ferme et le reset inclut les decorations`() {
        val farm = FarmState(prefs)
        farm.cheatAddCoins(4321)
        repeat(3) { deliver(farm) }
        farm.claimGift(FarmGift.FLOWERS); farm.placeGift(FarmGift.FLOWERS, FarmProject.WELL)
        val saved = JSONObject(prefs.getString(FarmState.KEY_STATE, null)!!)
        saved.put("projects", "broken")
        prefs.edit().putString(FarmState.KEY_STATE, saved.toString()).commit()
        val restored = FarmState(prefs)
        assertEquals(farm.coins, restored.coins)
        assertEquals(farm.village.toJson().toString(), restored.village.toJson().toString())
        assertTrue(restored.projects.complete(FarmProject.WELL))
        assertTrue(restored.claimGift(FarmGift.FLOWERS))
        restored.cheatReset()
        val reset = FarmState(prefs)
        assertEquals(FarmProject.WELL, reset.projects.active)
        assertEquals(0, reset.projects.reserve)
        assertEquals(0, reset.projects.progress(FarmProject.WELL))
        assertFalse(reset.projects.owns(FarmGift.FLOWERS))
        assertEquals(0, reset.village.deliveries)
    }
}
