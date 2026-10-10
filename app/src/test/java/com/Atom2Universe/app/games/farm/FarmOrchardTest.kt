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
class FarmOrchardTest {
    private lateinit var prefs: SharedPreferences
    private val now = 1_700_000_000_000L
    private val cell = FarmLayout.cells(1).first
    @Before fun setUp() {
        prefs = RuntimeEnvironment.getApplication().getSharedPreferences("farm-orchard-test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }
    private fun orchard(parcels: Int = 2) = FarmState(prefs).apply {
        cheatAddCoins(100_000)
        for (i in 1 until parcels) assertTrue(unlock(i))
        assertTrue(changeUse(1, FarmLandUse.ORCHARD)); cleanForVisit(1)
    }
    private fun plant(farm: FarmState, crop: FarmCrop = FarmCrop.APPLE, count: Int = 1): Int {
        assertTrue(farm.buy(crop, count))
        return farm.plantAll(crop, now, 1)
    }

    @Test fun `conversion gratuite sur sol vide en gardant un potager`() {
        val farm = FarmState(prefs)
        assertFalse(farm.changeUse(0, FarmLandUse.ORCHARD))
        assertFalse(farm.changeUse(-1, FarmLandUse.ORCHARD))
        assertFalse(farm.changeUse(1, FarmLandUse.ORCHARD))
        farm.cheatAddCoins(1000); assertTrue(farm.unlock(1))
        val coins = farm.coins
        assertTrue(farm.changeUse(1, FarmLandUse.ORCHARD))
        assertEquals(coins, farm.coins)
        assertFalse(farm.changeUse(0, FarmLandUse.ORCHARD))
        farm.cleanForVisit(1); plant(farm)
        assertFalse(farm.changeUse(1, FarmLandUse.CROPS))
        farm.clear(cell)
        assertTrue(farm.changeUse(1, FarmLandUse.CROPS))
        assertEquals(FarmLandUse.CROPS, FarmState(prefs).parcels[1].use)
    }
    @Test fun `aucune conversion ne detruit les legumes presents`() {
        val farm = orchard()
        assertTrue(farm.changeUse(1, FarmLandUse.CROPS))
        assertEquals(12, farm.plantAll(FarmCrop.RADISH, now, 1))
        val before = prefs.getString(FarmState.KEY_STATE, null)
        assertFalse(farm.changeUse(1, FarmLandUse.ORCHARD))
        assertEquals(before, prefs.getString(FarmState.KEY_STATE, null))
        assertEquals(FarmCrop.RADISH, farm.plots[cell].crop)
    }
    @Test fun `prix permanents especes debloquees et sol compatible`() {
        val farm = orchard()
        assertTrue(farm.cropUnlocked(FarmCrop.APPLE)); assertFalse(farm.cropUnlocked(FarmCrop.PEAR))
        assertFalse(farm.buy(FarmCrop.PEAR, 1))
        val coins = farm.coins
        assertTrue(farm.buy(FarmCrop.APPLE, 2)); assertEquals(coins - 240, farm.coins)
        farm.cleanForVisit(0)
        assertEquals(0, farm.plantAll(FarmCrop.APPLE, now, 0))
        assertEquals(0, farm.plantAll(FarmCrop.RADISH, now, 1))
        assertEquals(2, farm.plantAll(FarmCrop.APPLE, now, 1))
        assertEquals(0, farm.seeds[FarmCrop.APPLE.ordinal])
        assertTrue(farm.unlock(2)); assertTrue(farm.cropUnlocked(FarmCrop.PEAR))
        assertTrue(farm.unlock(3)); assertTrue(farm.cropUnlocked(FarmCrop.CHERRY))
    }
    @Test fun `acheter puis quitter ne consomme pas la premiere pousse acceleree`() {
        val farm = orchard(); farm.buy(FarmCrop.APPLE, 2)
        val restored = FarmState(prefs)
        assertFalse(restored.orchardIntroUsed)
        assertEquals(2, restored.plantAll(FarmCrop.APPLE, now))
        assertEquals(60, restored.plots[cell].duration())
        assertEquals(12 * 3600, restored.plots[cell + 1].duration())
        assertTrue(restored.plots[cell].watered)
        assertFalse(restored.plots[cell].rich)
        assertTrue(restored.waterMany(listOf(cell, cell + 1), now).isEmpty())
        assertEquals(0, restored.visitSummary(1, now).thirsty)
        val again = FarmState(prefs)
        assertEquals(60, again.plots[cell].duration()); assertTrue(again.orchardIntroUsed)
    }
    @Test fun `absence longue donne une seule recolte et la collecte relance sans replanter`() {
        val farm = orchard(); plant(farm)
        assertEquals(0, farm.harvest(cell, now + 59_999).count)
        val later = now + 10 * 86_400_000L
        val result = farm.harvestMany(listOf(cell, cell), later)
        assertEquals(3, result.count); assertEquals(36, result.value)
        assertEquals(FarmCrop.APPLE, farm.plots[cell].crop)
        assertTrue(farm.plots[cell].established); assertTrue(farm.plots[cell].watered)
        assertEquals(24 * 3600, farm.plots[cell].duration())
        assertEquals(0, farm.harvest(cell, later).count)
        assertEquals(0, farm.harvest(cell, later + 86_399_999).count)
        assertEquals(3, farm.harvest(cell, later + 86_400_000).count)
        assertEquals(6, FarmState(prefs).cropProduceTotal(FarmCrop.APPLE))
        assertEquals(2, farm.harvests)
    }
    @Test fun `chaque espece garde son propre cycle de production`() {
        val farm = orchard(4)
        FarmCrop.trees.forEach { crop -> plant(farm, crop) }
        farm.cheatCompleteGrowth()
        val actual = System.currentTimeMillis()
        assertEquals(9, farm.harvestMany(farm.orchardCells(), actual).count)
        assertEquals(listOf(24, 36, 48), (cell..cell + 2).map { farm.plots[it].duration() / 3600 })
        assertTrue((cell..cell + 2).all { farm.plots[it].watered && !farm.plots[it].welcome })
    }
    @Test fun `stock plein laisse la recolte jusqu a une vente sans perdre de fruits`() {
        val farm = orchard(); plant(farm, count = 2)
        farm.produce[FarmCrop.APPLE.ordinal][0] = 999_998
        val planted = farm.plots[cell].planted
        val later = now + 86_400_000
        assertEquals(0, farm.harvestMany(farm.orchardCells(), later).count)
        assertEquals(planted, farm.plots[cell].planted)
        assertEquals(0, farm.harvests)
        assertTrue(farm.plots[cell].progress(later) == 1f)
        farm.sellProduce(FarmCrop.APPLE)
        assertEquals(6, farm.harvestMany(farm.orchardCells(), later).count)
    }
    @Test fun `decouverte des recettes a la plantation reste apres retrait du dernier arbre`() {
        val farm = orchard()
        assertFalse(farm.workshop.knows(FarmRecipe.APPLE_COMPOTE))
        farm.buy(FarmCrop.APPLE, 1)
        assertFalse(farm.workshop.knows(FarmRecipe.APPLE_COMPOTE))
        farm.plantAll(FarmCrop.APPLE, now)
        assertTrue(farm.workshop.knows(FarmRecipe.APPLE_COMPOTE))
        assertFalse(farm.workshop.knows(FarmRecipe.PEAR_SYRUP))
        farm.clear(cell)
        val restored = FarmState(prefs)
        assertEquals(setOf(FarmCrop.APPLE), restored.orchardSpecies)
        assertTrue(restored.workshop.knows(FarmRecipe.APPLE_COMPOTE))
        val coins = restored.coins; restored.clear(cell)
        assertEquals(coins, restored.coins)
    }
    @Test fun `les fruits collectes deviennent une preparation vendable`() {
        val farm = orchard(); plant(farm)
        farm.harvest(cell, now + 60_000)
        assertTrue(farm.startPreparation(FarmRecipe.APPLE_COMPOTE, farm.workshop.revision, now + 60_000))
        assertEquals(0, farm.cropProduceTotal(FarmCrop.APPLE))
        assertEquals(1, farm.collectPreparations(now + 120_000))
        val restored = FarmState(prefs)
        assertEquals(48L, restored.sellPreparation(FarmRecipe.APPLE_COMPOTE, 1))
        assertEquals(0, restored.workshop.count(FarmRecipe.APPLE_COMPOTE))
    }
    @Test fun `une seule demande de fruits connus persiste et se livre une seule fois`() {
        val farm = orchard(); plant(farm)
        val old = farm.village.orders.toList()
        val initial = FarmState(prefs)
        assertEquals(old, initial.village.orders)
        repeat(30) {
            val villager = FarmVillager.entries[it % 3]
            val id = farm.village.orders.first { order -> order.villager == villager }.id
            farm.replaceOrder(id)
            assertTrue(farm.village.orders.count { order -> order.items.any { item -> item.crop.tree } } <= 1)
            assertTrue(farm.village.orders.flatMap { order -> order.items }.filter { item -> item.crop.tree }.all { item -> item.crop == FarmCrop.APPLE })
        }
        var request = farm.village.orders.firstOrNull { order -> order.items.any { it.crop.tree } }
        repeat(20) {
            if (request == null) {
                farm.replaceOrder(farm.village.orders.first().id)
                request = farm.village.orders.firstOrNull { order -> order.items.any { item -> item.crop.tree } }
            }
        }
        val fruit = requireNotNull(request)
        farm.harvest(cell, now + 60_000)
        val restored = FarmState(prefs)
        assertEquals(fruit, restored.village.find(fruit.id))
        assertEquals(45L, restored.deliverOrder(fruit.id)!!.coins)
        assertNull(restored.deliverOrder(fruit.id))
        assertEquals(1, restored.village.friendship(fruit.villager))
        assertEquals(0, restored.cropProduceTotal(FarmCrop.APPLE))
    }
    @Test fun `arbres v12 gardent leur avancement qualite et pieces a la migration`() {
        val farm = orchard(4)
        plant(farm, FarmCrop.PEAR)
        val current = System.currentTimeMillis()
        val json = JSONObject(prefs.getString(FarmState.KEY_STATE, null)!!)
        json.put("version", 12); json.remove("orchard")
        json.getJSONArray("plots").getJSONObject(cell).put("welcome", false).put("watered", true)
            .put("planted", current - 36 * 3600_000L).put("critical", true)
        prefs.edit().putString(FarmState.KEY_STATE, json.toString()).commit()
        val restored = FarmState(prefs)
        val progress = restored.plots[cell].progress(System.currentTimeMillis())
        assertTrue("fraction inherited: $progress", progress in .49f.. .51f)
        assertEquals(farm.coins, restored.coins)
        assertEquals(farm.village.orders, restored.village.orders)
        assertEquals(FarmCropQuality.RARE, restored.plots[cell].quality())
        assertTrue(restored.orchardIntroUsed)
        assertTrue(restored.workshop.knows(FarmRecipe.PEAR_SYRUP))
        restored.save(); assertEquals(FarmState.MAX_SAVE_VERSION, JSONObject(prefs.getString(FarmState.KEY_STATE, null)!!).getInt("version"))
    }
    @Test fun `arbre herite non arrose reprend et fruit herite decouvre la recette`() {
        val farm = orchard(); plant(farm)
        val json = JSONObject(prefs.getString(FarmState.KEY_STATE, null)!!)
        json.put("version", 12); json.remove("orchard")
        json.getJSONArray("plots").getJSONObject(cell).put("watered", false).put("welcome", false)
        json.getJSONObject("produce").getJSONObject("CHERRY").put("COMMON", 3)
        prefs.edit().putString(FarmState.KEY_STATE, json.toString()).commit()
        val restored = FarmState(prefs)
        assertTrue(restored.plots[cell].watered)
        assertTrue(restored.plots[cell].progress(System.currentTimeMillis()) < .01f)
        assertTrue(restored.workshop.knows(FarmRecipe.CHERRY_JAM))
    }
    @Test fun `reset rend le verger neuf avec sa premiere pousse`() {
        val farm = orchard(); plant(farm); farm.cheatReset()
        val restored = FarmState(prefs)
        assertFalse(restored.orchardIntroUsed)
        assertTrue(restored.orchardSpecies.isEmpty())
        assertFalse(restored.workshop.knows(FarmRecipe.APPLE_COMPOTE))
        assertTrue(restored.orchardCells().isEmpty())
        assertEquals(1, restored.unlockedParcels)
    }
}
