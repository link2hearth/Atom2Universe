package com.Atom2Universe.app.games.farm

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class FarmWorkshopTest {
    private val now = 1_700_000_000_000L
    private fun workshop(parcels: Int = 23, products: Set<FarmAnimalProduct> = FarmAnimalProduct.entries.toSet()) =
        FarmWorkshopState().apply { discover(parcels, products) }
    private fun start(state: FarmWorkshopState, recipe: FarmRecipe = FarmRecipe.PICKLES, time: Long = now) {
        assertTrue(state.start(recipe, time, state.revision))
    }

    @Test fun `chaque recette paie au moins un tiers de plus que ses ingredients ordinaires`() {
        assertEquals(24, FarmRecipe.entries.size)
        FarmRecipe.entries.forEach {
            assertTrue(it.crops.isNotEmpty() || it.products.isNotEmpty() || it.flowers.isNotEmpty() || it.meats.isNotEmpty())
            assertTrue(it.minutes > 0)
            assertTrue(it.sale.toLong() * 3 >= it.ingredientValue.toLong() * 4)
        }
    }
    @Test fun `les recettes se decouvrent avec les bonnes cultures et productions puis restent connues`() {
        val state = workshop(1, emptySet())
        assertEquals(listOf(FarmRecipe.PICKLES), state.recipes)
        assertFalse(state.discover(2, emptySet()))
        assertTrue(state.discover(3, setOf(FarmAnimalProduct.EGG)))
        assertEquals(listOf(FarmRecipe.PICKLES, FarmRecipe.SOUP, FarmRecipe.EGG_SALAD), state.recipes)
        assertFalse(state.knows(FarmRecipe.TRUFFLE_PAN))
        assertTrue(state.discover(5, FarmAnimalProduct.entries.toSet()))
        assertEquals(FarmRecipe.entries.filter { recipe -> recipe.crops.all { it.crop.rank in 1..5 } &&
            recipe.flowers.isEmpty() && recipe.meats.isEmpty() && recipe.dishesNeeded == 0 }, state.recipes)
        assertFalse(state.discover(1, emptySet()))
        assertEquals(7, state.recipes.size)
    }
    @Test fun `trois plans paralleles puis refus sans mutation`() {
        val state = workshop()
        repeat(3) { start(state) }
        assertEquals(listOf(now + 60_000, now + FarmRecipe.PICKLES.durationMillis, now + FarmRecipe.PICKLES.durationMillis),
            state.jobs.map { it.readyAt })
        val before = state.toJson().toString()
        assertFalse(state.start(FarmRecipe.PICKLES, now, state.revision))
        assertEquals(before, state.toJson().toString())
        assertEquals(3, state.jobs.map { it.id }.distinct().size)
    }
    @Test fun `les soixante secondes initiales ne reviennent pas apres collecte vente et sauvegarde`() {
        val state = workshop(); start(state)
        assertEquals(0, state.collect(now + 59_999))
        assertEquals(1, state.collect(now + 60_000))
        state.take(FarmRecipe.PICKLES, 1)
        val restored = FarmWorkshopState.fromJson(state.toJson())
        start(restored, time = now + 60_000)
        assertEquals(now + 60_000 + FarmRecipe.PICKLES.durationMillis, restored.jobs.single().readyAt)
    }
    @Test fun `les creations attendent dix jours sans expirer ni lancer une autre preparation`() {
        val state = workshop()
        start(state); start(state, FarmRecipe.SOUP); start(state, FarmRecipe.CHEESE)
        val before = state.toJson().toString()
        val later = now + 10 * LivestockState.DAY
        assertEquals(3, state.readyCount(later))
        assertEquals(before, state.toJson().toString())
        assertEquals(3, state.collect(later))
        assertEquals(0, state.collect(later))
        assertEquals(3, state.totalStock())
        assertTrue(state.jobs.isEmpty())
    }
    @Test fun `une creation prete reste sur son plan si son stock est plein`() {
        val state = workshop(); start(state)
        val json = state.toJson().put("stock", JSONObject().put("PICKLES", FarmWorkshopState.STOCK_CAPACITY))
        val restored = FarmWorkshopState.fromJson(json)
        val job = restored.jobs.single()
        assertEquals(0, restored.collect(now + LivestockState.DAY))
        assertEquals(job, restored.jobs.single())
        assertEquals(1, restored.take(FarmRecipe.PICKLES, 1))
        assertEquals(1, restored.collect(now + LivestockState.DAY))
        assertEquals(FarmWorkshopState.STOCK_CAPACITY, restored.count(FarmRecipe.PICKLES))
    }
    @Test fun `la collecte prend les stocks disponibles et garde les autres creations`() {
        val state = workshop(); start(state); start(state, FarmRecipe.SOUP)
        val restored = FarmWorkshopState.fromJson(state.toJson().put("stock", JSONObject().put("PICKLES", FarmWorkshopState.STOCK_CAPACITY)))
        assertEquals(1, restored.collect(now + LivestockState.DAY))
        assertEquals(FarmRecipe.PICKLES, restored.jobs.single().recipe)
        assertEquals(1, restored.count(FarmRecipe.SOUP))
    }
    @Test fun `un ancien bouton ne lance rien apres un lancement ou une collecte`() {
        val state = workshop()
        val revision = state.revision
        start(state)
        assertFalse(state.start(FarmRecipe.SOUP, now, revision))
        val occupiedRevision = state.revision
        state.collect(now + 60_000)
        assertFalse(state.start(FarmRecipe.PICKLES, now + 60_000, occupiedRevision))
        assertTrue(state.jobs.isEmpty())
    }
    @Test fun `reculer l horloge ne donne ni creation ni preparation instantanee`() {
        val state = workshop(); start(state)
        assertFalse(state.start(FarmRecipe.PICKLES, -1, state.revision))
        state.collect(now + 60_000)
        start(state, time = now - LivestockState.DAY)
        assertEquals(now + 60_000 + FarmRecipe.PICKLES.durationMillis, state.jobs.single().readyAt)
        assertEquals(0, state.collect(now - LivestockState.DAY))
    }
    @Test fun `aller retour conserve recettes stocks dates et introduction`() {
        val state = workshop(); start(state); state.collect(now + 60_000)
        start(state, FarmRecipe.SCARF, now + 60_000)
        val json = state.toJson().toString()
        assertEquals(json, FarmWorkshopState.fromJson(JSONObject(json)).toJson().toString())
    }
    @Test fun `les champs optionnels abimes ne produisent pas de jobs doubles ou de stock negatif`() {
        val state = workshop(); start(state)
        val job = state.toJson().getJSONArray("jobs").getJSONObject(0)
        val jobs = JSONArray().put(job).put(JSONObject(job.toString())).put(JSONObject().put("id", 2).put("recipe", "UNKNOWN").put("readyAt", now))
        val restored = FarmWorkshopState.fromJson(state.toJson().put("jobs", jobs).put("stock", JSONObject().put("SOUP", -99)))
        assertEquals(1, restored.jobs.size)
        assertEquals(0, restored.count(FarmRecipe.SOUP))
        assertTrue(restored.knows(FarmRecipe.PICKLES))
    }
    @Test fun `les demandes utilisent seulement les recettes connues et gardent deux paniers de legumes`() {
        val recipes = listOf(FarmRecipe.PICKLES, FarmRecipe.SOUP)
        val village = FarmVillageState().apply { ensureOrders(3, recipes) }
        val seen = mutableSetOf<FarmRecipe>()
        val residents = mutableSetOf<FarmVillager>()
        repeat(120) { index ->
            village.orders.forEach { order -> order.preparation?.let {
                assertTrue(it in recipes); assertTrue(order.items.isEmpty()); seen += it; residents += order.villager
            } }
            assertTrue(village.orders.count { it.preparation != null } <= 1)
            val order = village.orders[index % 3]
            assertTrue(village.replace(order.id, 3, recipes))
        }
        assertEquals(recipes.toSet(), seen)
        assertEquals(FarmVillager.entries.toSet(), residents)
    }
    @Test fun `les nouvelles decouvertes ne changent pas les demandes existantes`() {
        val village = FarmVillageState().apply { ensureOrders(1) }
        val before = village.toJson().toString()
        village.ensureOrders(23, FarmRecipe.entries.toList())
        assertEquals(before, village.toJson().toString())
        repeat(3) { village.replace(village.orders.first().id, 23, FarmRecipe.entries.toList()) }
        val saved = village.toJson().toString()
        val restored = FarmVillageState.fromJson(JSONObject(saved))
        restored.ensureOrders(23, emptyList())
        assertEquals(saved, restored.toJson().toString())
    }
}
