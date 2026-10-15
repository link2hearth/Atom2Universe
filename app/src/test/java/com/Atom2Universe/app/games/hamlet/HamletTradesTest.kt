package com.Atom2Universe.app.games.hamlet

import com.Atom2Universe.app.games.hamlet.AdventureResource.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class HamletTradesTest {
    private var time = 1_800_000_000_000L
    private fun game() = HamletGame({ time }, Random(42)).apply { startNew(false) }
    private fun install(g: HamletGame, id: String): Obj {
        g.sandbox = true
        val start = g.realmOrigin()
        for (y in start until start + g.realmSize()) for (x in start until start + g.realmSize()) {
            if (g.canPlace(id, x, y, false)) {
                val o = g.buy(id, x, y, false)!!; g.sandbox = false; return o
            }
        }
        error("No room for $id")
    }
    private fun fund(g: HamletGame, cost: VillageCost, count: Int = 1) {
        cost.resources.forEach { (r, n) -> g.adventure.bag[r.ordinal] = n * count }
        cost.crops.forEach { (id, n) -> g.pantry[id] = n * count }
        g.coins = maxOf(g.coins, cost.coins * count)
        g.journey.supplies = cost.supplies * count
    }
    private fun deliver(g: HamletGame, o: Obj) {
        o.order = Order("Ada"); assertTrue(g.acceptOrder(o))
        time += o.def.job * 1000L; assertTrue(g.deliver(o))
    }

    @Test fun everyShopHasAnExplicitAffordableRecipeAndNoFutureCrop() {
        val shops = Hamlet.defs.filter { it.cat == Cat.SHOP }
        assertTrue(shops.size > 100)
        for (d in shops) for (rank in 1..HamletEconomy.MAX_TIER) {
            val r = HamletIndustry.recipe(d.id, rank)
            assertTrue(d.id, r.cost.resources.values.all { it > 0 })
            assertTrue(d.id, r.output.values.all { it > 0 })
            for ((crop, units) in r.cost.crops) {
                assertTrue("${d.id}: $crop not unlocked", Hamlet.cropById.getValue(crop).lvl <= d.lvl)
                assertTrue("${d.id}: crop exceeds starter storage", units in 1..60)
            }
            assertFalse("${d.id}: invitations bypass caravan costs", TICKETS in r.output)
        }
    }

    @Test fun productionGraphCanBeBootstrappedAtEveryUnlockWithoutCircularDependencies() {
        val g = game()
        for (level in 1..40) for (chapter in 0..12) {
            val available = mutableSetOf(FOOD, TIMBER, STONE, ORE, BOOKS, CRYSTAL, RUNES, TICKETS)
            val defs = Hamlet.defs.filter { it.lvl <= level && g.journey.buildingChapter(it.id) <= chapter }
            defs.filter { it.id in HamletLife.produce }.forEach { available += HamletIndustry.products(it.id, 1).keys }
            do {
                val before = available.size
                defs.filter { it.cat == Cat.SHOP }.forEach {
                    val r = HamletIndustry.recipe(it.id, 1)
                    if (available.containsAll(r.cost.resources.keys)) available += r.output.keys
                }
            } while (available.size > before)
            for (d in defs.filter { it.cat == Cat.SHOP }) {
                assertTrue("${d.id} level $level chapter $chapter: missing ${HamletIndustry.recipe(d.id, 1).cost.resources.keys - available}",
                    available.containsAll(HamletIndustry.recipe(d.id, 1).cost.resources.keys))
            }
        }
    }

    @Test fun everyNewGoodHasAProducerAndARepeatableConsumer() {
        val consumed = Hamlet.defs.filter { it.cat == Cat.SHOP }.flatMap { HamletIndustry.recipe(it.id, 1).cost.resources.keys }.toSet() +
            HamletIndustry.expeditionOutfit.resources.keys + HamletIndustry.artisanCaravan.resources.keys
        for (r in AdventureResource.entries.filter { it.ordinal >= FRUIT.ordinal }) {
            assertTrue("No producer: $r", HamletIndustry.sources(r).isNotEmpty())
            assertTrue("No outlet: $r", r in consumed)
        }
    }

    @Test fun sheepToWeaverToTailorConsumesGoodsOnceAndSavesThem() {
        val g = game(); val sheep = install(g, "sheepmeadow")
        fund(g, HamletIndustry.feed(sheep)); assertTrue(g.feed(sheep))
        time += HamletLife.produce.getValue(sheep.t).seconds * 1000L
        assertTrue(g.collectProduct(sheep)); assertFalse(g.collectProduct(sheep)); assertEquals(6, g.adventure[WOOL])
        g.adventure.add(WATER, 4)
        deliver(g, install(g, "weaver")); assertEquals(0, g.adventure[WOOL]); assertEquals(4, g.adventure[CLOTH])
        deliver(g, install(g, "tailor")); assertEquals(2, g.adventure[CLOTH]); assertEquals(2, g.adventure[CRAFTS])
        assertTrue(HamletSave.fromJson(g, HamletSave.toJson(g)))
        assertEquals(2, g.adventure[CRAFTS]); assertEquals(2, g.adventure[CLOTH])
    }

    @Test fun wellProducesWaterWithBoundedOfflineStorageAndNoFreeFoodOrCoins() {
        val g = game(); val well = install(g, "well"); val coins = g.coins; val food = g.adventure[FOOD]
        time += 12 * 3_600_000L
        assertTrue(g.collectProduct(well)); assertEquals(36, g.adventure[WATER])
        assertEquals(coins, g.coins); assertEquals(food, g.adventure[FOOD]); assertFalse(g.collectProduct(well))
        time += 300_000; assertTrue(g.collectProduct(well)); assertEquals(48, g.adventure[WATER])
    }

    @Test fun missingMilkOrPurchaseCoinsNeverPartiallyChargesARecipe() {
        val g = game(); val cheese = install(g, "cheesemaker"); cheese.order = Order("Ada")
        g.adventure.add(WATER, 100); val before = g.adventure.bag.clone()
        assertFalse(g.acceptOrder(cheese)); assertArrayEquals(before, g.adventure.bag); assertEquals(0, cheese.order!!.t0)
        val fish = install(g, "fishmonger"); fish.order = Order("Ada"); g.coins = 39
        assertFalse(g.acceptOrder(fish)); assertEquals(39, g.coins); assertEquals(0, g.adventure[FISH])
        g.coins = 40; assertTrue(g.acceptOrder(fish)); assertEquals(0, g.coins)
    }

    @Test fun firstPassContractsKeepTheirOriginalOutputsAcrossMigration() {
        val g = game(); val tailor = install(g, "tailor"); tailor.order = Order("Ada", time, 1)
        val root = JSONObject(HamletSave.toJson(g)); val objs = root.getJSONArray("objs")
        for (i in 0 until objs.length()) objs.getJSONObject(i).optJSONObject("o")?.remove("recipeVersion")
        assertTrue(HamletSave.fromJson(g, root.toString()))
        time += tailor.def.job * 1000L
        assertTrue(g.deliver(g.objs.getValue(tailor.id))); assertEquals(0, g.adventure[CRAFTS])
        val bakery = install(g, "bakery"); bakery.order = Order("Ada", time, 2)
        time += bakery.def.job * 1000L; assertTrue(g.deliver(bakery)); assertEquals(8, g.adventure[BREAD])
    }

    @Test fun newContractsKeepTheirVersionAndPaidRankAcrossReload() {
        val g = game(); val herbalist = install(g, "herbalist"); fund(g, HamletIndustry.recipe(herbalist.t, 1).cost)
        herbalist.order = Order("Ada"); assertTrue(g.acceptOrder(herbalist)); herbalist.tier = 5
        assertTrue(HamletSave.fromJson(g, HamletSave.toJson(g)))
        val loaded = g.objs.getValue(herbalist.id); assertEquals(2, loaded.order!!.recipeVersion)
        time += loaded.def.job * 1000L; assertTrue(g.deliver(loaded)); assertEquals(2, g.adventure[MEDICINE])
    }

    @Test fun outfittingAndArtisanCaravansAreRepeatableAndAtomic() {
        val g = game(); fund(g, HamletIndustry.expeditionOutfit, 6)
        val before = g.adventure.bag.clone(); assertFalse(HamletIndustry.outfitExpedition(g)); assertArrayEquals(before, g.adventure.bag)
        install(g, "waypost")
        repeat(6) { assertTrue(HamletIndustry.outfitExpedition(g)) }
        assertEquals(120, g.journey.supplies); assertFalse(HamletIndustry.outfitExpedition(g))
        fund(g, HamletIndustry.artisanCaravan, 6); assertFalse(HamletIndustry.tradeCrafts(g))
        install(g, "adventureInn"); val tickets = g.adventure[TICKETS]
        repeat(6) { assertTrue(HamletIndustry.tradeCrafts(g)) }
        assertEquals(tickets + 6, g.adventure[TICKETS]); assertEquals(0, g.journey.supplies)
        val after = g.adventure.bag.clone(); assertFalse(HamletIndustry.tradeCrafts(g)); assertArrayEquals(after, g.adventure.bag)
    }
}
