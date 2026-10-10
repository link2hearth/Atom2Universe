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
class FarmButcherSaveTest {
    private lateinit var prefs: SharedPreferences
    private val now = 1_700_000_000_000L
    @Before fun setUp() {
        prefs = RuntimeEnvironment.getApplication().getSharedPreferences("farm-butcher-test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }
    private fun withHen() = FarmState(prefs).apply {
        cheatAddCoins(100_000); unlock(1); unlock(2)
        livestock.unlock(LivestockKind.CHICKENS)
        assertTrue(buyAnimal(LivestockKind.CHICKENS, false, now))
        renameAnimal(livestock.animals.single().id, "Noisette")
    }
    private fun withMeat() = withHen().apply {
        assertEquals(2, butcherAnimal(livestock.animals.single().id, now + 60_000))
    }

    @Test fun `transaction sauvegarde viande production et disparition sans gain de pieces`() {
        val farm = withHen()
        val id = farm.livestock.animals.single().id
        val coins = farm.coins
        assertEquals(2, farm.butcherAnimal(id, now + 60_000))
        val saved = prefs.getString(FarmState.KEY_STATE, null)
        assertEquals(0, farm.butcherAnimal(id, now + 60_000))
        assertEquals(saved, prefs.getString(FarmState.KEY_STATE, null))
        val restored = FarmState(prefs)
        assertNull(restored.livestock.find(id))
        assertEquals(2, restored.livestock.stock(FarmMeat.POULTRY))
        assertEquals(1, restored.livestock.stock(FarmAnimalProduct.EGG))
        assertEquals(coins, restored.coins)
        assertEquals(FarmState.MAX_SAVE_VERSION, JSONObject(saved!!).getInt("version"))
    }
    @Test fun `viande manquante ne prend aucun legume ni qualite superieure`() {
        val farm = withHen()
        farm.produce[FarmCrop.PEAS.ordinal][0] = 2; farm.save()
        val before = prefs.getString(FarmState.KEY_STATE, null)
        assertTrue(farm.workshop.knows(FarmRecipe.POULTRY_PEAS))
        assertFalse(farm.startPreparation(FarmRecipe.POULTRY_PEAS, farm.workshop.revision, now))
        assertEquals(before, prefs.getString(FarmState.KEY_STATE, null))
        assertEquals(2, farm.produce[FarmCrop.PEAS.ordinal][0])
        assertEquals(1, farm.livestock.animals.size)
    }
    @Test fun `legume ordinaire manquant ne prend ni viande ni qualite rare`() {
        val farm = withMeat()
        farm.produce[FarmCrop.PEAS.ordinal][1] = 4; farm.save()
        val before = prefs.getString(FarmState.KEY_STATE, null)
        assertFalse(farm.startPreparation(FarmRecipe.POULTRY_PEAS, farm.workshop.revision, now + 60_000))
        assertEquals(before, prefs.getString(FarmState.KEY_STATE, null))
        assertEquals(2, farm.livestock.stock(FarmMeat.POULTRY))
        assertEquals(4, farm.produce[FarmCrop.PEAS.ordinal][1])
    }
    @Test fun `cuisson atomique rejette double clic et attend dix jours sans peremption`() {
        val farm = withMeat()
        farm.produce[FarmCrop.PEAS.ordinal][0] = 2
        val revision = farm.workshop.revision
        assertTrue(farm.startPreparation(FarmRecipe.POULTRY_PEAS, revision, now + 60_000))
        assertFalse(farm.startPreparation(FarmRecipe.POULTRY_PEAS, revision, now + 60_000))
        val restored = FarmState(prefs)
        assertEquals(1, restored.livestock.stock(FarmMeat.POULTRY))
        assertEquals(1, restored.produce[FarmCrop.PEAS.ordinal][0])
        assertEquals(FarmRecipe.POULTRY_PEAS, restored.workshop.jobs.single().recipe)
        assertEquals(1, restored.collectPreparations(now + 10 * LivestockState.DAY))
        assertEquals(0, restored.collectPreparations(now + 10 * LivestockState.DAY))
        assertEquals(1, FarmState(prefs).workshop.count(FarmRecipe.POULTRY_PEAS))
    }
    @Test fun `les plans pleins gardent tous les ingredients de viande`() {
        val farm = withMeat()
        farm.produce[FarmCrop.PEAS.ordinal][0] = 1
        farm.produce[FarmCrop.RADISH.ordinal][0] = 9
        repeat(3) { assertTrue(farm.startPreparation(FarmRecipe.PICKLES, farm.workshop.revision, now + 60_000)) }
        val before = prefs.getString(FarmState.KEY_STATE, null)
        assertFalse(farm.startPreparation(FarmRecipe.POULTRY_PEAS, farm.workshop.revision, now + 60_000))
        assertEquals(before, prefs.getString(FarmState.KEY_STATE, null))
        assertEquals(2, farm.livestock.stock(FarmMeat.POULTRY))
        assertEquals(1, farm.produce[FarmCrop.PEAS.ordinal][0])
    }
    @Test fun `inventaire vente unitaire vente de legumes et vente totale gardent les bons stocks`() {
        val farm = withMeat()
        farm.produce[FarmCrop.RADISH.ordinal][0] = 2
        assertEquals(5, farm.produceCount())
        assertEquals(84L, farm.produceValue())
        assertEquals(24L, farm.sellProduce(FarmCrop.RADISH))
        assertEquals(2, farm.livestock.stock(FarmMeat.POULTRY))
        assertEquals(0L, farm.sellMeat(FarmMeat.POULTRY, -1))
        assertEquals(24L, farm.sellMeat(FarmMeat.POULTRY, 1))
        assertEquals(36L, farm.sellProduce())
        val restored = FarmState(prefs)
        assertEquals(0, restored.produceCount())
        assertEquals(0L, restored.sellMeat(FarmMeat.POULTRY, Int.MAX_VALUE))
    }
    @Test fun `village demande et livre le plat collecte une seule fois sans prendre viande restante`() {
        val farm = withMeat()
        var request = farm.village.orders.firstOrNull { it.preparation == FarmRecipe.POULTRY_PEAS }
        repeat(120) { index ->
            if (request == null) {
                farm.replaceOrder(farm.village.orders[index % 3].id)
                request = farm.village.orders.firstOrNull { it.preparation == FarmRecipe.POULTRY_PEAS }
            }
        }
        assertNotNull(request)
        val order = request!!
        farm.produce[FarmCrop.PEAS.ordinal][0] = 1
        assertTrue(farm.startPreparation(FarmRecipe.POULTRY_PEAS, farm.workshop.revision, now + 60_000))
        assertNull(farm.orderReward(order.id))
        assertNull(farm.deliverOrder(order.id))
        assertEquals(1, farm.collectPreparations(now + 120_000))
        val restored = FarmState(prefs)
        assertEquals(90L, restored.deliverOrder(order.id)!!.coins)
        assertNull(restored.deliverOrder(order.id))
        val saved = FarmState(prefs)
        assertEquals(0, saved.workshop.count(FarmRecipe.POULTRY_PEAS))
        assertEquals(1, saved.livestock.stock(FarmMeat.POULTRY))
        assertEquals(1, saved.livestock.stock(FarmAnimalProduct.EGG))
        assertEquals(1, saved.village.friendship(order.villager))
    }
    @Test fun `refus de reserve pleine ne modifie pas la sauvegarde ni animal`() {
        val farm = withHen()
        val json = JSONObject(prefs.getString(FarmState.KEY_STATE, null)!!)
        json.getJSONObject("livestock").put("meats", JSONObject().put("POULTRY", LivestockState.PRODUCT_CAPACITY))
        prefs.edit().putString(FarmState.KEY_STATE, json.toString()).commit()
        val restored = FarmState(prefs)
        val before = prefs.getString(FarmState.KEY_STATE, null)
        assertEquals(0, restored.butcherAnimal(restored.livestock.animals.single().id, now + 60_000))
        assertEquals(before, prefs.getString(FarmState.KEY_STATE, null))
        assertEquals("Noisette", restored.livestock.animals.single().name)
        assertEquals(0, restored.livestock.stock(FarmAnimalProduct.EGG))
    }
    @Test fun `migration v15 sans viande conserve animaux noms recettes et commandes`() {
        val farm = withHen()
        farm.petAnimal(farm.livestock.animals.single().id, now); farm.save()
        val legacy = JSONObject(prefs.getString(FarmState.KEY_STATE, null)!!)
        legacy.put("version", 15); legacy.getJSONObject("livestock").remove("meats")
        prefs.edit().putString(FarmState.KEY_STATE, legacy.toString()).commit()
        val restored = FarmState(prefs)
        assertEquals(farm.coins, restored.coins)
        assertEquals("Noisette", restored.livestock.animals.single().name)
        assertEquals(1, restored.livestock.animals.single().affection)
        assertEquals(farm.village.orders, restored.village.orders)
        assertEquals(0, restored.livestock.meatCount())
        assertTrue(restored.workshop.knows(FarmRecipe.POULTRY_PEAS))
        restored.save()
        assertEquals(FarmState.MAX_SAVE_VERSION, JSONObject(prefs.getString(FarmState.KEY_STATE, null)!!).getInt("version"))
    }
    @Test fun `reset efface viande et plats et donne une ferme de depart`() {
        val farm = withMeat()
        farm.produce[FarmCrop.PEAS.ordinal][0] = 1
        farm.startPreparation(FarmRecipe.POULTRY_PEAS, farm.workshop.revision, now + 60_000)
        farm.collectPreparations(now + 120_000)
        farm.cheatReset()
        val restored = FarmState(prefs)
        assertEquals(0, restored.livestock.meatCount())
        assertTrue(restored.livestock.animals.isEmpty())
        assertEquals(0, restored.workshop.totalStock())
        assertEquals(listOf(FarmRecipe.PICKLES), restored.workshop.recipes)
    }
}
