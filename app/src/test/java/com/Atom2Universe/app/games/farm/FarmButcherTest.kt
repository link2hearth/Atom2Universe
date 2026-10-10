package com.Atom2Universe.app.games.farm

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class FarmButcherTest {
    private val now = 1_700_000_000_000L
    private fun herd(kind: LivestockKind = LivestockKind.CHICKENS, male: Boolean = false) = LivestockState().apply {
        LivestockKind.entries.take(kind.ordinal + 1).forEach { unlock(it) }
        assertTrue(buy(kind, male, now))
        advance(now)
    }

    @Test fun `transformer et vendre rapporte moins que acheter pour chaque espece`() {
        FarmMeat.entries.forEach { meat ->
            assertEquals(meat.kind.sale, meat.portions * meat.sale)
            assertTrue(meat.portions * meat.sale < meat.kind.price)
            val state = herd(meat.kind)
            val id = state.animals.single().id
            assertEquals(meat.portions, state.butcher(id, now))
            assertTrue(state.animals.isEmpty())
            assertEquals(meat.kind.sale.toLong(), state.meatValue())
            assertEquals(0, state.butcher(id, now))
            assertEquals(meat.portions, state.meatCount())
        }
    }
    @Test fun `production prete est recuperee avant le depart meme apres dix jours`() {
        FarmMeat.entries.forEach { meat ->
            val state = herd(meat.kind)
            val animal = state.animals.single()
            val product = animal.product!!
            assertEquals(meat.portions, state.butcher(animal.id, now + 10 * LivestockState.DAY))
            assertEquals(1, state.stock(product))
            assertEquals(0, state.collectProducts(clock = now + 20 * LivestockState.DAY))
            assertEquals(1, state.stock(product))
        }
    }
    @Test fun `un stock de viande insuffisant refuse sans prendre la production ni animal`() {
        val original = herd()
        val state = LivestockState().apply { restore(original.toJson().put("meats",
            JSONObject().put("POULTRY", LivestockState.PRODUCT_CAPACITY - 1)), now) }
        val before = state.toJson().toString()
        assertEquals(0, state.butcher(state.animals.single().id, now + 60_000))
        assertEquals(before, state.toJson().toString())
    }
    @Test fun `production prete pleine refuse aussi la transformation sans mutation`() {
        val original = herd()
        val state = LivestockState().apply { restore(original.toJson().put("products",
            JSONObject().put("EGG", LivestockState.PRODUCT_CAPACITY)), now) }
        val before = state.toJson().toString()
        assertEquals(0, state.butcher(state.animals.single().id, now + 60_000))
        assertEquals(before, state.toJson().toString())
        assertEquals(1, state.takeProduct(FarmAnimalProduct.EGG, 1))
        assertEquals(2, state.butcher(state.animals.single().id, now + 60_000))
        assertEquals(LivestockState.PRODUCT_CAPACITY, state.stock(FarmAnimalProduct.EGG))
    }
    @Test fun `la derniere place et une production pas encore prete sont respectees`() {
        val original = herd()
        val state = LivestockState().apply { restore(original.toJson().put("meats",
            JSONObject().put("POULTRY", LivestockState.PRODUCT_CAPACITY - 2)), now) }
        assertEquals(2, state.butcher(state.animals.single().id, now + 59_999))
        assertEquals(LivestockState.PRODUCT_CAPACITY, state.stock(FarmMeat.POULTRY))
        assertEquals(0, state.stock(FarmAnimalProduct.EGG))
    }
    @Test fun `un petit et un identifiant disparu ne peuvent pas etre transformes`() {
        val state = herd()
        state.buy(LivestockKind.CHICKENS, true, now)
        state.advance(now + LivestockKind.CHICKENS.cycleMillis)
        val chick = state.animals.first { !it.adult }
        val before = state.toJson().toString()
        assertEquals(0, state.butcher(chick.id, now + LivestockKind.CHICKENS.cycleMillis))
        assertEquals(0, state.butcher(999, now))
        assertEquals(0, state.butcher(state.animals.first().id, -1))
        assertEquals(before, state.toJson().toString())
    }
    @Test fun `retirer le dernier male arrete les naissances et preserve le fumier deja acquis`() {
        val state = herd(male = true)
        state.buy(LivestockKind.CHICKENS, false, now)
        val male = state.animals.first { it.male }
        val female = state.animals.first { !it.male }
        assertTrue(female.birthAt > 0)
        assertEquals(2, state.butcher(male.id, now + LivestockState.DAY))
        assertEquals(8L, state.manure)
        assertEquals(0L, female.birthAt)
        state.advance(now + 10 * LivestockState.DAY)
        assertEquals(1, state.animals.size)
        state.buy(LivestockKind.CHICKENS, true, now + 10 * LivestockState.DAY)
        assertEquals(now + 10 * LivestockState.DAY + LivestockKind.CHICKENS.cycleMillis, female.birthAt)
    }
    @Test fun `stocks seuls ne periment pas et lecture ancienne ou abimee reste sure`() {
        val state = herd()
        state.butcher(state.animals.single().id, now)
        val json = state.toJson()
        val restored = LivestockState().apply { restore(JSONObject(json.toString()), now + 10 * LivestockState.DAY) }
        assertEquals(json.toString(), restored.toJson().toString())
        assertEquals(0, restored.takeMeat(FarmMeat.POULTRY, -1))
        assertEquals(2, restored.takeMeat(FarmMeat.POULTRY, Int.MAX_VALUE))
        assertEquals(0, restored.takeMeat(FarmMeat.POULTRY, 1))
        json.remove("meats")
        restored.restore(json, now)
        assertEquals(0, restored.meatCount())
        json.put("meats", JSONObject().put("POULTRY", -10).put("BEEF", Int.MAX_VALUE).put("PORK", "broken"))
        restored.restore(json, now)
        assertEquals(0, restored.stock(FarmMeat.POULTRY))
        assertEquals(0, restored.stock(FarmMeat.PORK))
        assertEquals(LivestockState.PRODUCT_CAPACITY, restored.stock(FarmMeat.BEEF))
        restored.reset(); assertEquals(0, restored.meatCount())
    }
    @Test fun `recettes de viande decouvertes selon ingredients et restent connues`() {
        val state = FarmWorkshopState()
        state.discover(23, FarmAnimalProduct.entries.toSet())
        assertFalse(state.knows(FarmRecipe.POULTRY_PEAS))
        state.discover(2, emptySet(), meats = setOf(FarmMeat.POULTRY))
        assertFalse(state.knows(FarmRecipe.POULTRY_PEAS))
        state.discover(3, emptySet(), meats = setOf(FarmMeat.POULTRY))
        assertTrue(state.knows(FarmRecipe.POULTRY_PEAS))
        assertFalse(state.knows(FarmRecipe.PORK_SKEWERS))
        state.discover(23, FarmAnimalProduct.entries.toSet(), FarmCrop.trees.toSet(), FarmFlower.entries.toSet(), FarmMeat.entries.toSet())
        assertEquals(FarmRecipe.entries.filter { it.dishesNeeded == 0 }, state.recipes)
        state.discover(1, emptySet())
        assertEquals(FarmRecipe.entries.filter { it.dishesNeeded == 0 }, FarmWorkshopState.fromJson(state.toJson()).recipes)
    }
}
