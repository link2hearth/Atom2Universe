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
class FarmCompanionSaveTest {
    private lateinit var prefs: SharedPreferences
    private val start = 1_700_000_000_000L
    @Before fun setUp() {
        prefs = RuntimeEnvironment.getApplication().getSharedPreferences("farm-companion-test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }
    private fun farm(): FarmState = FarmState(prefs).apply {
        cheatAddCoins(5000); livestock.unlock(LivestockKind.CHICKENS)
        assertTrue(buyAnimal(LivestockKind.CHICKENS, false, start))
    }

    @Test fun `identite lien et stock restent apres fermeture et la collecte ne double pas`() {
        val farm = farm()
        val id = farm.livestock.animals.single().id
        assertTrue(farm.renameAnimal(id, "Noisette"))
        assertTrue(farm.petAnimal(id, start))
        assertEquals(1, farm.collectAnimalProducts(now = start + 60_000))
        val restored = FarmState(prefs)
        assertEquals("Noisette", restored.livestock.find(id)!!.name)
        assertEquals(1, restored.livestock.find(id)!!.affection)
        assertEquals(1, restored.livestock.stock(FarmAnimalProduct.EGG))
        assertEquals(0, restored.collectAnimalProducts(now = start + 60_000))
        assertEquals(1, FarmState(prefs).livestock.stock(FarmAnimalProduct.EGG))
    }

    @Test fun `vendre les denrees ne vend pas les animaux et la vente totale inclut tout`() {
        val farm = farm()
        farm.collectAnimalProducts(now = start + 60_000)
        farm.produce[FarmCrop.RADISH.ordinal][0] = 3
        val coins = farm.coins
        assertEquals(4, farm.produceCount())
        assertEquals(12L + 3 * FarmCrop.RADISH.sale, farm.produceValue())
        assertEquals(3L * FarmCrop.RADISH.sale, farm.sellProduce(FarmCrop.RADISH))
        assertEquals(1, farm.livestock.stock(FarmAnimalProduct.EGG))
        assertEquals(12L, farm.sellAnimalProduct(FarmAnimalProduct.EGG, 999))
        assertEquals(0L, farm.sellAnimalProduct(FarmAnimalProduct.EGG, 999))
        assertEquals(1, farm.livestock.animals.size)
        assertEquals(coins + 12 + 3 * FarmCrop.RADISH.sale, FarmState(prefs).coins)
        farm.collectAnimalProducts(now = start + 60_000 + LivestockState.DAY)
        farm.produce[FarmCrop.RADISH.ordinal][0] = 2
        assertEquals(12L + 2 * FarmCrop.RADISH.sale, farm.sellProduce())
        val restored = FarmState(prefs)
        assertEquals(0, restored.produceCount())
        assertEquals(1, restored.livestock.animals.size)
    }

    @Test fun `migration v10 preserve la ferme et tous les anciens champs animaux`() {
        val farm = farm()
        farm.buyAnimal(LivestockKind.CHICKENS, true, start)
        val order = farm.village.orders.first()
        order.items.forEach { farm.produce[it.crop.ordinal][0] = it.count }
        farm.deliverOrder(order.id)
        val legacy = JSONObject(prefs.getString(FarmState.KEY_STATE, null)!!)
        legacy.put("version", 10)
        val herd = legacy.getJSONObject("livestock")
        herd.remove("productionVersion"); herd.remove("products"); herd.remove("firstEggStarted")
        val animals = herd.getJSONArray("animals")
        for (i in 0 until animals.length()) animals.getJSONObject(i).apply {
            remove("name"); remove("affection"); remove("lastPetAt"); remove("productAt")
        }
        prefs.edit().putString(FarmState.KEY_STATE, legacy.toString()).commit()
        val restored = FarmState(prefs)
        assertEquals(2, restored.livestock.animals.size)
        assertEquals(0, restored.livestock.productCount())
        assertFalse(restored.livestock.firstEggAvailable)
        assertTrue(restored.livestock.animals.first().productAt >= System.currentTimeMillis() + LivestockState.DAY - 5000)
        restored.save()
        val migrated = JSONObject(prefs.getString(FarmState.KEY_STATE, null)!!)
        legacy.keys().forEach { key -> if (key !in listOf("version", "livestock")) {
            assertEquals(key, legacy.get(key).toString(), migrated.get(key).toString())
        } }
        val newHerd = migrated.getJSONObject("livestock")
        herd.keys().forEach { key -> if (key != "animals") assertEquals(key, herd.get(key).toString(), newHerd.get(key).toString()) }
        for (i in 0 until animals.length()) animals.getJSONObject(i).keys().forEach { key ->
            assertEquals(key, animals.getJSONObject(i).get(key), newHerd.getJSONArray("animals").getJSONObject(i).get(key))
        }
    }

    @Test fun `donnees optionnelles abimees ne perdent pas les animaux et reset complet`() {
        val farm = farm()
        val json = JSONObject(prefs.getString(FarmState.KEY_STATE, null)!!)
        val herd = json.getJSONObject("livestock")
        herd.put("products", JSONObject().put("EGG", -12))
        herd.getJSONArray("animals").getJSONObject(0).put("affection", -10).put("lastPetAt", -999).put("name", "🐔".repeat(40))
        prefs.edit().putString(FarmState.KEY_STATE, json.toString()).commit()
        val restored = FarmState(prefs)
        assertEquals(farm.coins, restored.coins)
        assertEquals(1, restored.livestock.animals.size)
        assertEquals(0, restored.livestock.animals.single().affection)
        assertEquals(0, restored.livestock.productCount())
        assertEquals("🐔".repeat(24), restored.livestock.animals.single().name)
        restored.cheatReset()
        val reset = FarmState(prefs)
        assertEquals(0, reset.livestock.unlocked)
        assertEquals(0, reset.livestock.productCount())
        assertTrue(reset.livestock.animals.isEmpty())
        assertTrue(reset.livestock.firstEggAvailable)
    }
}
