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

/** Real preferences and Android geometry: guards the currency/inventory/save transaction. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class FarmVillageSaveTest {
    private lateinit var prefs: SharedPreferences

    @Before
    fun setUp() {
        prefs = RuntimeEnvironment.getApplication().getSharedPreferences("farm-village-test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    @Test
    fun `livrer est atomique et le meme bouton ne paie pas deux fois`() {
        val farm = FarmState(prefs)
        val order = farm.village.orders.first()
        val item = order.items.single()
        farm.produce[item.crop.ordinal] = intArrayOf(item.count - 2, 1, 1, 5)
        val coins = farm.coins
        val expected = item.crop.sale.toLong() * (item.count + 3) * 125 / 100
        assertEquals(expected, farm.orderReward(order.id))
        val delivery = farm.deliverOrder(order.id)!!
        assertEquals(expected, delivery.coins)
        assertEquals(coins + expected, farm.coins)
        assertArrayEquals(intArrayOf(0, 0, 0, 5), farm.produce[item.crop.ordinal])
        assertEquals(1, farm.village.friendship(order.villager))
        val after = prefs.getString(FarmState.KEY_STATE, null)
        assertNull(farm.deliverOrder(order.id))
        assertEquals(after, prefs.getString(FarmState.KEY_STATE, null))
        val restored = FarmState(prefs)
        assertEquals(farm.coins, restored.coins)
        assertArrayEquals(farm.produce[item.crop.ordinal], restored.produce[item.crop.ordinal])
        assertEquals(farm.village.toJson().toString(), restored.village.toJson().toString())
    }

    @Test
    fun `une demande melangee incomplete ne touche ni pieces ni stock ni lien`() {
        val farm = FarmState(prefs)
        farm.cheatAddCoins(10_000)
        farm.unlock(1); farm.unlock(2)
        val iris = farm.village.orders.last()
        farm.replaceOrder(iris.id)
        // Cycle until this resident has a two-crop basket.
        repeat(8) {
            val order = farm.village.orders.last()
            if (order.items.size < 2) farm.replaceOrder(order.id)
        }
        val order = farm.village.orders.last()
        assertEquals(2, order.items.size)
        val first = order.items.first()
        farm.produce[first.crop.ordinal][0] = first.count
        farm.save()
        val before = prefs.getString(FarmState.KEY_STATE, null)
        assertNull(farm.deliverOrder(order.id))
        farm.save()
        assertEquals(before, prefs.getString(FarmState.KEY_STATE, null))
    }

    @Test
    fun `une absence de dix jours ne change ni demande ni relation`() {
        val farm = FarmState(prefs)
        val order = farm.village.orders.first()
        order.items.forEach { farm.produce[it.crop.ordinal][0] = it.count }
        farm.deliverOrder(order.id)
        val before = farm.village.toJson().toString()
        farm.cheatSkipTime(10 * 24 * 3_600_000L)
        farm.advanceLivestock(System.currentTimeMillis() + 10 * 24 * 3_600_000L)
        farm.advanceFields()
        farm.save()
        assertEquals(before, FarmState(prefs).village.toJson().toString())
    }

    @Test
    fun `remplacer reste gratuit et conserve les produits et la relation`() {
        val farm = FarmState(prefs)
        val order = farm.village.orders.first()
        order.items.forEach { farm.produce[it.crop.ordinal][0] = it.count + 8 }
        farm.deliverOrder(order.id)
        val coins = farm.coins
        val stock = farm.produce.map { it.toList() }
        val next = farm.village.orders.first()
        assertTrue(farm.replaceOrder(next.id))
        assertEquals(coins, farm.coins)
        assertEquals(stock, farm.produce.map { it.toList() })
        assertEquals(1, FarmState(prefs).village.friendship(order.villager))
    }

    @Test
    fun `une sauvegarde v7 conserve toute la ferme et ajoute seulement le village`() {
        val farm = FarmState(prefs)
        farm.cheatAddCoins(12_345)
        farm.buy(FarmCrop.RADISH, 3)
        farm.clean(0); farm.plant(0, 1000L); farm.waterMany(listOf(0), 1000L)
        farm.produce[FarmCrop.RADISH.ordinal][FarmCropQuality.EPIC.ordinal] = 7
        farm.save()
        val legacy = JSONObject(prefs.getString(FarmState.KEY_STATE, null)!!)
        legacy.put("version", 7); legacy.remove("village")
        legacy.remove("welcomePlantings")
        val legacyPlots = legacy.getJSONArray("plots")
        for (i in 0 until legacyPlots.length()) legacyPlots.getJSONObject(i).remove("welcome")
        prefs.edit().putString(FarmState.KEY_STATE, legacy.toString()).commit()
        val restored = FarmState(prefs)
        assertEquals(3, restored.village.orders.size)
        restored.save()
        val migrated = JSONObject(prefs.getString(FarmState.KEY_STATE, null)!!)
        assertEquals(FarmState.MAX_SAVE_VERSION, migrated.getInt("version"))
        val migratedPlots = migrated.getJSONArray("plots")
        for (i in 0 until migratedPlots.length()) {
            assertFalse(migratedPlots.getJSONObject(i).getBoolean("welcome"))
            migratedPlots.getJSONObject(i).remove("welcome")
        }
        legacy.keys().forEach { key ->
            if (key != "version") assertEquals("Migration : $key", legacy.get(key).toString(), migrated.get(key).toString())
        }
    }

    @Test
    fun `un village abime ne reinitialise pas la ferme et le reset dev inclut le village`() {
        val farm = FarmState(prefs)
        farm.cheatAddCoins(5432)
        val order = farm.village.orders.first()
        order.items.forEach { farm.produce[it.crop.ordinal][0] = it.count }
        farm.deliverOrder(order.id)
        val json = JSONObject(prefs.getString(FarmState.KEY_STATE, null)!!)
        json.getJSONObject("village").put("orders", "broken")
        prefs.edit().putString(FarmState.KEY_STATE, json.toString()).commit()
        val restored = FarmState(prefs)
        assertEquals(farm.coins, restored.coins)
        assertEquals(3, restored.village.orders.size)
        restored.cheatReset()
        assertEquals(FarmState.STARTING_COINS, restored.coins)
        assertEquals(0, restored.village.deliveries)
        assertEquals(3, FarmState(prefs).village.orders.size)
    }
}
