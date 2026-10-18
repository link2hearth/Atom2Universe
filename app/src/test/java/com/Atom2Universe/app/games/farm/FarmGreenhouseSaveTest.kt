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
class FarmGreenhouseSaveTest {
    private lateinit var prefs: SharedPreferences
    private val now = 1_700_000_000_000L
    @Before fun setUp() {
        prefs = RuntimeEnvironment.getApplication().getSharedPreferences("farm-greenhouse-test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }
    private fun start(farm: FarmState, flower: FarmFlower, crossed: Boolean = false, time: Long = now, slot: Int? = null) =
        farm.startFlower(flower, crossed, farm.greenhouse.revision, time, slot)
    private fun bouquetIngredients() = FarmState(prefs).apply {
        listOf(FarmFlower.DAISY_WHITE, FarmFlower.TULIP_RED, FarmFlower.IRIS_BLUE).forEach { assertTrue(start(this, it)) }
        assertEquals(3, collectFlowers(now + 86_400_000))
    }

    @Test fun `culture et premiere minute sont sauvegardees sans payer ni toucher aux demandes`() {
        val farm = FarmState(prefs)
        val coins = farm.coins; val orders = farm.village.orders
        assertFalse(farm.workshop.knows(FarmRecipe.GARDEN_BOUQUET))
        val revision = farm.greenhouse.revision
        assertTrue(farm.startFlower(FarmFlower.DAISY_WHITE, false, revision, now, 4))
        assertFalse(farm.startFlower(FarmFlower.DAISY_WHITE, false, revision, now, 5))
        val restored = FarmState(prefs)
        assertEquals(coins, restored.coins); assertEquals(orders, restored.village.orders)
        assertEquals(now + 60_000, restored.greenhouse.at(4)!!.readyAt)
        assertTrue(restored.workshop.knows(FarmRecipe.GARDEN_BOUQUET))
        assertEquals(1, restored.collectFlowers(now + 60_000))
        assertEquals(1, FarmState(prefs).greenhouse.count(FarmFlower.DAISY_WHITE))
    }
    @Test fun `croisement retire et sauvegarde les deux parents une fois`() {
        val farm = FarmState(prefs)
        start(farm, FarmFlower.TULIP_RED); start(farm, FarmFlower.TULIP_YELLOW)
        farm.collectFlowers(now + 86_400_000)
        val revision = farm.greenhouse.revision
        assertTrue(farm.startFlower(FarmFlower.TULIP_ORANGE, true, revision, now + 86_400_000))
        assertFalse(farm.startFlower(FarmFlower.TULIP_ORANGE, true, revision, now + 86_400_000))
        val restored = FarmState(prefs)
        assertEquals(0, restored.greenhouse.count(FarmFlower.TULIP_RED))
        assertEquals(0, restored.greenhouse.count(FarmFlower.TULIP_YELLOW))
        assertEquals(FarmFlower.TULIP_ORANGE, restored.greenhouse.jobs.single().flower)
        assertFalse(restored.greenhouse.knows(FarmFlower.TULIP_ORANGE))
        assertEquals(1, restored.collectFlowers(now + 86_400_000 + FarmGreenhouseState.CROSS_MILLIS))
        assertTrue(FarmState(prefs).greenhouse.knows(FarmFlower.TULIP_ORANGE))
    }
    @Test fun `un croisement impossible ne modifie pas la sauvegarde`() {
        val farm = FarmState(prefs); farm.save()
        val before = prefs.getString(FarmState.KEY_STATE, null)
        assertFalse(start(farm, FarmFlower.DAISY_GOLD, true))
        assertFalse(start(farm, FarmFlower.DAISY_GOLD, false))
        assertEquals(before, prefs.getString(FarmState.KEY_STATE, null))
    }
    @Test fun `bouquet exige tous les ingredients puis prend les fleurs de facon atomique`() {
        val farm = bouquetIngredients()
        farm.sellFlower(FarmFlower.IRIS_BLUE, 1)
        val before = prefs.getString(FarmState.KEY_STATE, null)
        assertFalse(farm.startPreparation(FarmRecipe.GARDEN_BOUQUET, farm.workshop.revision, now + 86_400_000))
        assertEquals(before, prefs.getString(FarmState.KEY_STATE, null))
        assertEquals(1, farm.greenhouse.count(FarmFlower.DAISY_WHITE)); assertEquals(1, farm.greenhouse.count(FarmFlower.TULIP_RED))
        start(farm, FarmFlower.IRIS_BLUE, time = now + 86_400_000)
        farm.collectFlowers(now + 2 * 86_400_000)
        assertTrue(farm.startPreparation(FarmRecipe.GARDEN_BOUQUET, farm.workshop.revision, now + 2 * 86_400_000))
        val restored = FarmState(prefs)
        assertEquals(0, restored.greenhouse.totalStock())
        assertEquals(FarmRecipe.GARDEN_BOUQUET, restored.workshop.jobs.single().recipe)
        assertEquals(1, restored.collectPreparations(now + 2 * 86_400_000 + 60_000))
    }
    @Test fun `bouquet collecte peut etre livre avec prime lien et chantier`() {
        val farm = bouquetIngredients()
        assertTrue(farm.startPreparation(FarmRecipe.GARDEN_BOUQUET, farm.workshop.revision, now + 86_400_000))
        farm.collectPreparations(now + 86_400_000 + 60_000)
        repeat(20) {
            if (farm.village.orders.none { it.preparation == FarmRecipe.GARDEN_BOUQUET }) farm.replaceOrder(farm.village.orders.first().id)
        }
        val order = farm.village.orders.first { it.preparation == FarmRecipe.GARDEN_BOUQUET }
        val restored = FarmState(prefs)
        assertEquals(40L, restored.deliverOrder(order.id)!!.coins)
        assertEquals(1, restored.village.friendship(order.villager))
        assertEquals(1, restored.projects.progress(FarmProject.WELL))
        assertNull(restored.deliverOrder(order.id))
        assertEquals(0, restored.workshop.count(FarmRecipe.GARDEN_BOUQUET))
    }
    @Test fun `vente totale inclut fleurs et creations sans perdre le carnet`() {
        val farm = bouquetIngredients()
        val coins = farm.coins
        assertEquals(3, farm.produceCount()); assertEquals(24L, farm.produceValue())
        assertEquals(0L, farm.sellProduce(FarmCrop.RADISH))
        assertEquals(24L, farm.sellProduce()); assertEquals(coins + 24, farm.coins)
        val restored = FarmState(prefs)
        assertEquals(0, restored.greenhouse.totalStock()); assertEquals(6, restored.greenhouse.collection.size)
        assertTrue(start(restored, FarmFlower.TULIP_RED, time = now + 86_400_000))
    }
    @Test fun `ancienne ferme v13 conserve stocks pieces arbres demandes et demarre la serre vide`() {
        val farm = FarmState(prefs)
        farm.cheatAddCoins(8000); farm.unlock(1); farm.changeUse(1, FarmLandUse.ORCHARD); farm.cleanForVisit(1)
        farm.buy(FarmCrop.APPLE, 1); farm.plantAll(FarmCrop.APPLE, now)
        farm.produce[FarmCrop.RADISH.ordinal][1] = 5; farm.save()
        val json = JSONObject(prefs.getString(FarmState.KEY_STATE, null)!!).put("version", 13)
        json.remove("greenhouse"); prefs.edit().putString(FarmState.KEY_STATE, json.toString()).commit()
        val restored = FarmState(prefs)
        assertEquals(farm.coins, restored.coins); assertEquals(farm.village.orders, restored.village.orders)
        assertEquals(5, restored.produce[FarmCrop.RADISH.ordinal][1])
        assertEquals(farm.plots[FarmLayout.cells(1).first], restored.plots[FarmLayout.cells(1).first])
        assertFalse(restored.greenhouse.introUsed); assertTrue(restored.greenhouse.jobs.isEmpty())
        assertEquals(0, restored.greenhouse.totalStock())
        restored.save(); assertEquals(FarmState.MAX_SAVE_VERSION, JSONObject(prefs.getString(FarmState.KEY_STATE, null)!!).getInt("version"))
    }
    @Test fun `decoration reset et sous document abime restent independants du reste de la ferme`() {
        val farm = bouquetIngredients(); assertTrue(farm.displayFlower(FarmFlower.IRIS_BLUE))
        assertEquals(FarmFlower.IRIS_BLUE, FarmState(prefs).greenhouse.displayFlower)
        val json = JSONObject(prefs.getString(FarmState.KEY_STATE, null)!!).put("greenhouse", "broken")
        prefs.edit().putString(FarmState.KEY_STATE, json.toString()).commit()
        val repaired = FarmState(prefs)
        assertEquals(farm.coins, repaired.coins); assertEquals(farm.village.orders, repaired.village.orders)
        assertTrue(repaired.greenhouse.jobs.isEmpty())
        assertEquals(FarmFlower.DAISY_WHITE, repaired.greenhouse.displayFlower)
        repaired.cheatReset()
        val reset = FarmState(prefs)
        assertFalse(reset.greenhouse.introUsed); assertEquals(6, reset.greenhouse.collection.size)
        assertFalse(reset.workshop.knows(FarmRecipe.GARDEN_BOUQUET))
        assertTrue(reset.greenhouse.jobs.isEmpty())
    }
}
