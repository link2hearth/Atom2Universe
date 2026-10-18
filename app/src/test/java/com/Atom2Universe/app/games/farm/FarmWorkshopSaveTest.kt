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
class FarmWorkshopSaveTest {
    private lateinit var prefs: SharedPreferences
    private val now = 1_700_000_000_000L
    @Before fun setUp() {
        prefs = RuntimeEnvironment.getApplication().getSharedPreferences("farm-workshop-test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }
    private fun withEgg(): FarmState = FarmState(prefs).apply {
        cheatAddCoins(5000); unlock(1); livestock.unlock(LivestockKind.CHICKENS)
        buyAnimal(LivestockKind.CHICKENS, false, now)
        collectAnimalProducts(now = now + 60_000)
        discoverRecipes(); save()
    }

    @Test fun `un ingredient ordinaire manquant ne prend ni oeuf ni qualites superieures`() {
        val farm = withEgg()
        farm.produce[FarmCrop.LETTUCE.ordinal][FarmCropQuality.RARE.ordinal] = 7
        farm.save()
        val before = prefs.getString(FarmState.KEY_STATE, null)
        assertFalse(farm.startPreparation(FarmRecipe.EGG_SALAD, farm.workshop.revision, now + 60_000))
        assertEquals(1, farm.livestock.stock(FarmAnimalProduct.EGG))
        assertEquals(7, farm.produce[FarmCrop.LETTUCE.ordinal][FarmCropQuality.RARE.ordinal])
        assertEquals(before, prefs.getString(FarmState.KEY_STATE, null))
    }
    @Test fun `lancement atomique sauvegarde la recette et rejette le meme bouton`() {
        val farm = withEgg()
        farm.produce[FarmCrop.LETTUCE.ordinal][0] = 2
        farm.produce[FarmCrop.LETTUCE.ordinal][1] = 7
        val coins = farm.coins
        val revision = farm.workshop.revision
        assertTrue(farm.startPreparation(FarmRecipe.EGG_SALAD, revision, now + 60_000))
        assertFalse(farm.startPreparation(FarmRecipe.EGG_SALAD, revision, now + 60_000))
        val restored = FarmState(prefs)
        assertEquals(1, restored.workshop.jobs.size)
        assertEquals(FarmRecipe.EGG_SALAD, restored.workshop.jobs.single().recipe)
        assertEquals(now + 120_000, restored.workshop.jobs.single().readyAt)
        assertEquals(1, restored.produce[FarmCrop.LETTUCE.ordinal][0])
        assertEquals(7, restored.produce[FarmCrop.LETTUCE.ordinal][1])
        assertEquals(0, restored.livestock.stock(FarmAnimalProduct.EGG))
        assertEquals(coins, restored.coins)
    }
    @Test fun `les plans pleins ne consomment aucun ingredient supplementaire`() {
        val farm = FarmState(prefs)
        farm.produce[FarmCrop.RADISH.ordinal][0] = 24
        repeat(3) { assertTrue(farm.startPreparation(FarmRecipe.PICKLES, farm.workshop.revision, now)) }
        val before = prefs.getString(FarmState.KEY_STATE, null)
        assertFalse(farm.startPreparation(FarmRecipe.PICKLES, farm.workshop.revision, now))
        assertEquals(15, farm.produce[FarmCrop.RADISH.ordinal][0])
        assertEquals(before, prefs.getString(FarmState.KEY_STATE, null))
        assertEquals(3, FarmState(prefs).workshop.jobs.size)
    }
    @Test fun `le village exige la creation collectee puis livre une seule fois avec lien et chantier`() {
        val farm = FarmState(prefs)
        farm.produce[FarmCrop.RADISH.ordinal][0] = 12
        val first = farm.village.orders.first()
        assertNotNull(farm.deliverOrder(first.id))
        val request = farm.village.orders.first()
        assertEquals(FarmRecipe.PICKLES, request.preparation)
        assertTrue(farm.startPreparation(FarmRecipe.PICKLES, farm.workshop.revision, now))
        assertNull(farm.orderReward(request.id))
        val before = prefs.getString(FarmState.KEY_STATE, null)
        assertNull(farm.deliverOrder(request.id))
        assertEquals(before, prefs.getString(FarmState.KEY_STATE, null))
        assertEquals(1, farm.collectPreparations(now + 60_000))
        val restored = FarmState(prefs)
        assertEquals(request, restored.village.find(request.id))
        val rawStock = restored.produce[FarmCrop.RADISH.ordinal][0]
        val coins = restored.coins
        assertEquals(60L, restored.orderReward(request.id))
        assertEquals(60L, restored.deliverOrder(request.id)!!.coins)
        assertNull(restored.deliverOrder(request.id))
        val saved = FarmState(prefs)
        assertEquals(coins + 60, saved.coins)
        assertEquals(0, saved.workshop.count(FarmRecipe.PICKLES))
        assertEquals(rawStock, saved.produce[FarmCrop.RADISH.ordinal][0])
        assertEquals(2, saved.village.friendship(FarmVillager.LUCIE))
        assertEquals(2, saved.projects.progress(FarmProject.WELL))
    }
    @Test fun `les creations sont dans inventaire et la vente totale mais pas dans une vente de legumes`() {
        val farm = FarmState(prefs)
        farm.produce[FarmCrop.RADISH.ordinal][0] = 5
        farm.startPreparation(FarmRecipe.PICKLES, farm.workshop.revision, now)
        farm.collectPreparations(now + 60_000)
        assertEquals(3, farm.produceCount())
        assertEquals(48L + 2 * FarmCrop.RADISH.sale, farm.produceValue())
        assertEquals(2L * FarmCrop.RADISH.sale, farm.sellProduce(FarmCrop.RADISH))
        assertEquals(1, farm.workshop.count(FarmRecipe.PICKLES))
        assertEquals(48L, farm.sellPreparation(FarmRecipe.PICKLES, 999))
        assertEquals(0L, farm.sellPreparation(FarmRecipe.PICKLES, 999))
        farm.produce[FarmCrop.RADISH.ordinal][0] = 3
        farm.startPreparation(FarmRecipe.PICKLES, farm.workshop.revision, now + 60_000)
        farm.collectPreparations(now + 60_000 + FarmRecipe.PICKLES.durationMillis)
        assertEquals(48L, farm.sellProduce())
        assertEquals(0, FarmState(prefs).produceCount())
    }
    @Test fun `migration v11 preserve les demandes existantes et toutes les autres progressions`() {
        val farm = withEgg()
        val id = farm.livestock.animals.single().id
        farm.renameAnimal(id, "Noisette"); farm.petAnimal(id, now + 60_000)
        farm.save()
        val legacy = JSONObject(prefs.getString(FarmState.KEY_STATE, null)!!)
        legacy.put("version", 11); legacy.remove("workshop")
        legacy.getJSONObject("village").remove("offered")
        prefs.edit().putString(FarmState.KEY_STATE, legacy.toString()).commit()
        val restored = FarmState(prefs)
        assertTrue(restored.workshop.knows(FarmRecipe.PICKLES))
        assertTrue(restored.workshop.knows(FarmRecipe.EGG_SALAD))
        assertFalse(restored.workshop.introUsed)
        restored.save()
        val migrated = JSONObject(prefs.getString(FarmState.KEY_STATE, null)!!)
        legacy.keys().forEach { key -> if (key !in listOf("version", "village")) {
            assertEquals(key, legacy.get(key).toString(), migrated.get(key).toString())
        } }
        val oldVillage = legacy.getJSONObject("village")
        oldVillage.keys().forEach { key -> assertEquals(key, oldVillage.get(key).toString(), migrated.getJSONObject("village").get(key).toString()) }
        assertEquals("Noisette", restored.livestock.find(id)!!.name)
    }
    @Test fun `atelier optionnel abime ne reinitialise pas la ferme et reset efface creations`() {
        val farm = withEgg()
        farm.produce[FarmCrop.RADISH.ordinal][0] = 3
        farm.startPreparation(FarmRecipe.PICKLES, farm.workshop.revision, now + 60_000)
        farm.collectPreparations(now + 120_000)
        val json = JSONObject(prefs.getString(FarmState.KEY_STATE, null)!!)
        json.put("workshop", "broken")
        prefs.edit().putString(FarmState.KEY_STATE, json.toString()).commit()
        val restored = FarmState(prefs)
        assertEquals(farm.coins, restored.coins)
        assertEquals(1, restored.livestock.animals.size)
        assertEquals(farm.village.orders, restored.village.orders)
        restored.cheatReset()
        val reset = FarmState(prefs)
        assertTrue(reset.workshop.jobs.isEmpty())
        assertEquals(0, reset.workshop.totalStock())
        assertFalse(reset.workshop.introUsed)
        assertEquals(listOf(FarmRecipe.PICKLES), reset.workshop.recipes)
    }
}
