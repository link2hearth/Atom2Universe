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
class FarmCookingSaveTest {
    private lateinit var prefs: SharedPreferences
    private val now = 1_700_000_000_000L
    @Before fun setUp() {
        prefs = RuntimeEnvironment.getApplication().getSharedPreferences("farm-kitchen-test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }
    private fun pantry(): FarmState {
        val farm = FarmState(prefs)
        farm.cheatAddCoins(1_000_000_000); repeat(6) { assertTrue(farm.unlock(it + 1)) }
        farm.livestock.restore(farm.livestock.toJson().put("products", JSONObject().put("EGG", 10).put("MILK", 10)), now)
        farm.save()
        val json = JSONObject(prefs.getString(FarmState.KEY_STATE, null)!!)
        val stock = JSONObject()
        FarmRecipe.entries.filter { it.food && it.dishesNeeded == 0 }.take(8).forEach { stock.put(it.name, 1) }
        val workshop = json.getJSONObject("workshop").put("stock", stock)
        workshop.remove("cooked"); workshop.remove("practiced")
        prefs.edit().putString(FarmState.KEY_STATE, json.toString()).commit()
        return FarmState(prefs)
    }

    @Test fun `abandonner les gestes ne retire rien et ne donne aucune experience`() {
        val farm = FarmState(prefs)
        farm.produce[FarmCrop.RADISH.ordinal][0] = 3; farm.save()
        val before = prefs.getString(FarmState.KEY_STATE, null)
        val session = FarmCookingSession(FarmRecipe.PICKLES)
        repeat(4) { session.assist() }; session.next()
        assertFalse(session.complete)
        assertEquals(3, farm.produce[FarmCrop.RADISH.ordinal][0])
        assertEquals(0, farm.workshop.totalDishes)
        assertTrue(farm.workshop.jobs.isEmpty())
        assertEquals(before, prefs.getString(FarmState.KEY_STATE, null))
    }
    @Test fun `fin de gestes suivie de transaction conserve pratique et collecte unique`() {
        val farm = FarmState(prefs)
        farm.produce[FarmCrop.RADISH.ordinal][0] = 6
        val session = FarmCookingSession(FarmRecipe.PICKLES)
        while (!session.complete) { session.assist(); if (session.progress >= .99999f) session.next() }
        val revision = farm.workshop.revision
        assertTrue(farm.startPreparation(FarmRecipe.PICKLES, revision, now, handsOn = session.complete))
        assertFalse(farm.startPreparation(FarmRecipe.PICKLES, revision, now, handsOn = true))
        val restored = FarmState(prefs)
        assertTrue(restored.workshop.jobs.single().handsOn)
        assertEquals(0, restored.workshop.distinctDishes)
        assertEquals(1, restored.collectPreparations(now + 60_000))
        assertEquals(0, restored.collectPreparations(now + 60_000))
        val saved = FarmState(prefs)
        assertEquals(1, saved.workshop.cooked(FarmRecipe.PICKLES))
        assertEquals(1, saved.workshop.practiced(FarmRecipe.PICKLES))
        assertEquals(3, saved.produce[FarmCrop.RADISH.ordinal][0])
    }
    @Test fun `preparation directe participe au livre sans pratique manuelle`() {
        val farm = FarmState(prefs)
        farm.produce[FarmCrop.RADISH.ordinal][0] = 3
        assertTrue(farm.startPreparation(FarmRecipe.PICKLES, farm.workshop.revision, now))
        farm.collectPreparations(now + 60_000)
        assertEquals(1, farm.workshop.distinctDishes)
        assertEquals(0, farm.workshop.practiced(FarmRecipe.PICKLES))
    }
    @Test fun `pages et marque pages persistent apres vente fermeture et longue absence`() {
        val farm = pantry()
        assertEquals(8, farm.workshop.distinctDishes)
        assertTrue(farm.workshop.knows(FarmRecipe.CORNBREAD))
        assertTrue(farm.workshop.knows(FarmRecipe.EGG_SANDWICH))
        assertFalse(farm.workshop.knows(FarmRecipe.TOMATO_SOUP))
        assertTrue(farm.toggleFavoriteRecipe(FarmRecipe.EGG_SANDWICH))
        farm.sellProduce()
        val restored = FarmState(prefs)
        restored.collectPreparations(now + 10 * LivestockState.DAY)
        assertEquals(8, restored.workshop.distinctDishes)
        assertTrue(restored.workshop.favorite(FarmRecipe.EGG_SANDWICH))
        assertTrue(restored.workshop.knows(FarmRecipe.CORNBREAD))
        assertEquals(0, restored.workshop.totalStock())
    }
    @Test fun `pain puis sandwich consomment les bonnes preparations sans effacer historique`() {
        val farm = pantry()
        farm.produce[FarmCrop.CORN.ordinal][0] = 2
        farm.produce[FarmCrop.LETTUCE.ordinal][0] = 1
        farm.produce[FarmCrop.LETTUCE.ordinal][1] = 4
        assertTrue(farm.startPreparation(FarmRecipe.CORNBREAD, farm.workshop.revision, now, true))
        val breadReady = now + FarmRecipe.CORNBREAD.durationMillis * 80 / 100
        assertEquals(breadReady, farm.workshop.jobs.single().readyAt)
        assertEquals(1, farm.collectPreparations(breadReady))
        assertEquals(1, farm.workshop.count(FarmRecipe.CORNBREAD))
        assertTrue(farm.startPreparation(FarmRecipe.EGG_SANDWICH, farm.workshop.revision, breadReady, true))
        assertEquals(0, farm.workshop.count(FarmRecipe.CORNBREAD))
        assertEquals(1, farm.workshop.cooked(FarmRecipe.CORNBREAD))
        assertEquals(4, farm.produce[FarmCrop.LETTUCE.ordinal][1])
        val saved = FarmState(prefs)
        assertEquals(8, saved.livestock.stock(FarmAnimalProduct.EGG))
        assertEquals(1, saved.collectPreparations(breadReady + FarmRecipe.EGG_SANDWICH.durationMillis * 80 / 100))
        assertEquals(1, FarmState(prefs).workshop.practiced(FarmRecipe.EGG_SANDWICH))
    }
    @Test fun `pain absent ne retire ni fromage ni legumes`() {
        val farm = pantry()
        farm.produce[FarmCrop.LETTUCE.ordinal][0] = 1; farm.save()
        val before = prefs.getString(FarmState.KEY_STATE, null)
        assertFalse(farm.startPreparation(FarmRecipe.CHEESE_SANDWICH, farm.workshop.revision, now, true))
        assertEquals(before, prefs.getString(FarmState.KEY_STATE, null))
        assertEquals(1, farm.workshop.count(FarmRecipe.CHEESE))
        assertEquals(1, farm.produce[FarmCrop.LETTUCE.ordinal][0])
        assertEquals(0, farm.workshop.count(FarmRecipe.CORNBREAD))
    }
    @Test fun `migration v16 ajoute livre sans retirer recette ni plat ni demande`() {
        val farm = pantry()
        val orders = farm.village.orders; val coins = farm.coins
        val json = JSONObject(prefs.getString(FarmState.KEY_STATE, null)!!).put("version", 16)
        val workshop = json.getJSONObject("workshop")
        workshop.remove("cooked"); workshop.remove("practiced"); workshop.remove("favorites")
        prefs.edit().putString(FarmState.KEY_STATE, json.toString()).commit()
        val restored = FarmState(prefs)
        assertEquals(orders, restored.village.orders)
        assertEquals(coins, restored.coins)
        assertEquals(8, restored.workshop.totalStock())
        assertEquals(8, restored.workshop.distinctDishes)
        assertEquals(0, restored.workshop.practiced(FarmRecipe.PICKLES))
        restored.save()
        assertEquals(FarmState.MAX_SAVE_VERSION, JSONObject(prefs.getString(FarmState.KEY_STATE, null)!!).getInt("version"))
    }
    @Test fun `reset efface historique favoris et nouvelles pages`() {
        val farm = pantry()
        farm.toggleFavoriteRecipe(FarmRecipe.EGG_SANDWICH)
        farm.cheatReset()
        val restored = FarmState(prefs)
        assertEquals(0, restored.workshop.totalDishes)
        assertEquals(0, restored.workshop.distinctDishes)
        assertFalse(restored.workshop.favorite(FarmRecipe.EGG_SANDWICH))
        assertFalse(restored.workshop.knows(FarmRecipe.CORNBREAD))
        assertEquals(listOf(FarmRecipe.PICKLES), restored.workshop.recipes)
    }
}
