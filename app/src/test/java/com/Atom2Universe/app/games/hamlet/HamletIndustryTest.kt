package com.Atom2Universe.app.games.hamlet

import com.Atom2Universe.app.games.hamlet.AdventureResource.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class HamletIndustryTest {
    private var time = 1_800_000_000_000L
    private fun game() = HamletGame({ time }, Random(12)).apply { startNew(false) }
    private fun location(g: HamletGame, id: String): Pair<Int, Int> {
        val from = g.realmOrigin()
        for (y in from until from + g.realmSize()) for (x in from until from + g.realmSize())
            if (g.canPlace(id, x, y, false)) return x to y
        error("No room for $id")
    }
    private fun install(g: HamletGame, id: String): Obj {
        val (x, y) = location(g, id)
        g.sandbox = true
        val o = g.buy(id, x, y, false)!!
        g.sandbox = false
        return o
    }
    private fun order(g: HamletGame, o: Obj) {
        o.order = Order("Ada")
        assertTrue(g.acceptOrder(o))
        time += o.def.job * 1000L
        assertTrue(g.deliver(o))
    }

    @Test fun constructionChargesWoodAndStoneAndUndoReturnsExactlyWhatWasPaid() {
        val g = game(); val before = g.adventure.bag.clone(); val coins = g.coins
        val (x, y) = location(g, "field")
        val cost = HamletIndustry.building(Hamlet.byId.getValue("field"))
        assertNotNull(g.buy("field", x, y, false))
        assertEquals(before[TIMBER.ordinal] - cost.resources.getValue(TIMBER), g.adventure[TIMBER])
        assertEquals(before[STONE.ordinal] - cost.resources.getValue(STONE), g.adventure[STONE])
        assertTrue(g.buildHistory.undo()); assertArrayEquals(before, g.adventure.bag); assertEquals(coins, g.coins)
        assertFalse(g.buildHistory.undo()); assertArrayEquals(before, g.adventure.bag)
    }

    @Test fun missingStoneNeverChargesCoinsOrWoodOrCreatesAnObject() {
        val g = game(); g.adventure.bag[STONE.ordinal] = 0
        val (x, y) = location(g, "field"); val before = g.adventure.bag.clone(); val coins = g.coins; val count = g.objs.size
        assertNull(g.buy("field", x, y, false))
        assertArrayEquals(before, g.adventure.bag); assertEquals(coins, g.coins); assertEquals(count, g.objs.size)
    }

    @Test fun bakeryConsumesWheatAndFuelOnceAndProducesBreadOnlyOnDelivery() {
        val g = game(); val bakery = install(g, "bakery"); bakery.order = Order("Ada")
        g.pantry["wheat"] = 2
        val wood = g.adventure[TIMBER]; val books = g.adventure[BOOKS]
        assertTrue(g.acceptOrder(bakery)); assertFalse(g.acceptOrder(bakery))
        assertEquals(0, g.stock("wheat")); assertEquals(wood - 2, g.adventure[TIMBER]); assertEquals(0, g.adventure[BREAD])
        assertFalse(g.deliver(bakery)); time += 300_000
        assertTrue(g.deliver(bakery)); assertEquals(4, g.adventure[BREAD]); assertEquals(books, g.adventure[BOOKS])
        assertFalse(g.deliver(bakery)); assertEquals(4, g.adventure[BREAD])
    }

    @Test fun missingWheatDoesNotConsumeFuelOrStartTheClock() {
        val g = game(); val bakery = install(g, "bakery"); bakery.order = Order("Ada")
        val before = g.adventure.bag.clone()
        assertFalse(g.acceptOrder(bakery)); assertEquals(0, bakery.order!!.t0); assertArrayEquals(before, g.adventure.bag)
    }

    @Test fun orderRemembersItsPaidRankAcrossSaveAndAnUpgrade() {
        val g = game(); val bakery = install(g, "bakery"); bakery.order = Order("Ada"); g.pantry["wheat"] = 2
        assertTrue(g.acceptOrder(bakery)); bakery.tier = 5
        val saved = HamletSave.toJson(g); assertTrue(HamletSave.fromJson(g, saved))
        time += 300_000; assertTrue(g.deliver(g.objs.getValue(bakery.id))); assertEquals(4, g.adventure[BREAD])
    }

    @Test fun oldAcceptedOrdersFinishWithoutInventingUnpaidManufacturedGoods() {
        val g = game(); val bakery = install(g, "bakery"); bakery.order = Order("Ada", time)
        val root = JSONObject(HamletSave.toJson(g))
        val objects = root.getJSONArray("objs")
        for (i in 0 until objects.length()) objects.getJSONObject(i).optJSONObject("o")?.remove("recipeRank")
        assertTrue(HamletSave.fromJson(g, root.toString()))
        time += 300_000; assertTrue(g.deliver(g.objs.getValue(bakery.id))); assertEquals(0, g.adventure[BREAD])
    }

    @Test fun sawmillAndQuarryProduceTheirOwnMaterialsWithoutBooks() {
        val g = game(); g.adventure.workers.fill(0); val wood = g.adventure[TIMBER]; val stone = g.adventure[STONE]; val books = g.adventure[BOOKS]
        order(g, install(g, "lumbermill")); order(g, install(g, "quarry"))
        assertEquals(wood + 45, g.adventure[TIMBER]); assertEquals(stone + 45, g.adventure[STONE]); assertEquals(books, g.adventure[BOOKS])
        assertEquals(3, Hamlet.byId.getValue("lumbermill").lvl); assertEquals(3, Hamlet.byId.getValue("quarry").lvl)
    }

    @Test fun forgeConsumesOreAndTimberToMakeToolsForVillageConstruction() {
        val g = game(); g.adventure.workers.fill(0); val forge = install(g, "adventureForge")
        val wood = g.adventure[TIMBER]; val ore = g.adventure[ORE]
        order(g, forge)
        assertEquals(wood - 8, g.adventure[TIMBER]); assertEquals(ore - 4, g.adventure[ORE])
        assertEquals(2, g.adventure[TOOLS]); assertFalse(g.deliver(forge)); assertEquals(2, g.adventure[TOOLS])
        assertFalse(HamletIndustry.building(Hamlet.byId.getValue("adventureForge")).resources.containsKey(TOOLS))
        assertFalse(HamletIndustry.building(Hamlet.byId.getValue("blacksmith")).resources.containsKey(TOOLS))
        assertTrue(HamletIndustry.building(Hamlet.byId.getValue("library")).resources.getValue(TOOLS) > 0)
        assertTrue(HamletSave.fromJson(g, HamletSave.toJson(g))); assertEquals(2, g.adventure[TOOLS])
    }

    @Test fun animalFoodComesFromCornAndNeverCreatesOre() {
        val g = game(); val pen = install(g, "henyard"); val ore = g.adventure[ORE]; val food = g.adventure[FOOD]
        assertFalse(g.feed(pen)); g.pantry["corn"] = 1
        assertTrue(g.feed(pen)); assertEquals(0, g.stock("corn")); assertFalse(g.feed(pen))
        time += HamletLife.produce.getValue(pen.t).seconds * 1000L
        assertTrue(g.collectProduct(pen)); assertEquals(ore, g.adventure[ORE]); assertEquals(food + 12, g.adventure[FOOD])
    }

    @Test fun breadFeedsAdventuresAndBuildersWithNoPartialPaymentAtCapacity() {
        val g = game(); g.adventure.bag[TOOLS.ordinal] = 2; g.adventure.bag[BREAD.ordinal] = 10; g.adventure.bag[TIMBER.ordinal] = 120; g.adventure.bag[STONE.ordinal] = 120
        val food = g.adventure[FOOD]; assertTrue(HamletIndustry.prepareRations(g)); assertEquals(food + 40, g.adventure[FOOD])
        g.workSeconds = 3601; val before = g.adventure.bag.clone()
        assertFalse(HamletIndustry.supplyBuilders(g)); assertArrayEquals(before, g.adventure.bag)
        g.workSeconds = 0; assertTrue(HamletIndustry.supplyBuilders(g)); assertEquals(3600, g.workSeconds)
        assertEquals(0, g.adventure[BREAD]); assertEquals(0, g.adventure[TIMBER]); assertEquals(0, g.adventure[STONE])
    }

    @Test fun caravanTradesCanBeRepeatedBeyondDailyInvitationBonuses() {
        val g = game(); install(g, "adventureInn")
        HamletIndustry.caravan.resources.forEach { (r, n) -> g.adventure.bag[r.ordinal] = n * 5 }
        g.journey.supplies = 200; g.adventure.workTicketsToday = HamletAdventure.WORK_TICKETS_PER_DAY
        val tickets = g.adventure[TICKETS]
        repeat(5) { assertTrue(HamletIndustry.welcomeCaravan(g)) }
        assertEquals(tickets + 5, g.adventure[TICKETS]); assertEquals(0, g.journey.supplies)
        assertFalse(HamletIndustry.welcomeCaravan(g))
    }

    @Test fun fullWorkStorageNeverPreventsMaterialFarming() {
        val g = game(); g.workSeconds = 7200; g.beginWork(); val wood = g.adventure[TIMBER]
        repeat(36) { assertTrue(g.answerWork(g.workTool)) }
        assertEquals(wood + 24, g.adventure[TIMBER]); assertEquals(7200, g.workSeconds)
    }

    @Test fun infrastructureBonusesUseTheBestRankAndCannotBeMultipliedByDuplicates() {
        val g = game(); val first = install(g, "adventureStore"); first.tier = 3
        val rates = g.adventure.productionPerMinute()
        install(g, "adventureStore")
        assertEquals(3, g.adventure.facility("adventureStore"))
        assertArrayEquals(rates, g.adventure.productionPerMinute(), .0001)
    }

    @Test fun undoCannotRefundAnInfrastructureThatAlreadyProducedResources() {
        val g = game(); g.xp = 1000; g.coins = 2000
        HamletIndustry.building(Hamlet.byId.getValue("adventureForge")).resources.forEach { (r, n) -> g.adventure.add(r, n) }
        val (x, y) = location(g, "adventureForge"); assertNotNull(g.buy("adventureForge", x, y, false))
        val ore = g.adventure[ORE]; time += 180_000
        assertFalse(g.buildHistory.undo()); assertEquals(ore + 1, g.adventure[ORE])
    }

    @Test fun twoHoursOfActiveWorkCanRemoveADayFromAnExistingExpedition() {
        val g = game()
        // Isolate an already unlocked late expedition; this does not bypass progression in the game.
        g.journey.expedition = 5; g.journey.departure = time; g.journey.arrival = time + 36 * 3_600_000
        val initialArrival = g.journey.arrival; val began = time; g.beginWork()
        while (initialArrival - g.journey.arrival < Hamlet.DAY_MS && time - began < 2 * 3_600_000) {
            repeat(12) { assertTrue(g.answerWork(g.workTool)); time += 1400 }
            assertTrue(g.workOnExpedition()); time += 1400
        }
        assertTrue(initialArrival - g.journey.arrival >= Hamlet.DAY_MS)
        assertTrue(time - began < 2 * 3_600_000)
        assertEquals(0, g.adventure.frontier); assertEquals(0, g.journey.chapter)
        assertEquals(0, g.adventure.ownedCount()) // Farmable time does not grant progression milestones.
    }

    @Test fun storageNeverShrinksWhenHarvestingOnAnotherIsland() {
        val g = game(); g.hall = 8; g.addStock("wheat", 200); g.sandbox = true
        assertTrue(g.realms.found()); g.sandbox = false
        assertEquals(1, g.hall); assertEquals(200, g.pantryCap()); g.addStock("wheat", 4); assertEquals(200, g.stock("wheat"))
        assertTrue(HamletSave.fromJson(g, HamletSave.toJson(g))); assertEquals(200, g.stock("wheat"))
    }

    @Test fun facilitiesAndHeroLimitsBelongToTheWholeDomain() {
        val g = game(); val forge = install(g, "adventureForge"); forge.tier = 3; g.hall = 8
        val stars = g.adventure.starLimit(); g.sandbox = true; assertTrue(g.realms.found()); g.sandbox = false
        assertEquals(3, g.armory.forgeRank()); assertEquals(stars, g.adventure.starLimit())
        assertTrue(HamletSave.fromJson(g, HamletSave.toJson(g))); assertEquals(3, g.armory.forgeRank())
    }

    @Test fun surplusWorkersRestWithoutLosingTheirAssignmentsOnReload() {
        val g = game(); g.adventure.workers.fill(0); g.adventure.workers[1] = 24
        assertEquals(6.0, g.adventure.productionPerMinute()[1], .001)
        assertTrue(HamletSave.fromJson(g, HamletSave.toJson(g)))
        assertEquals(24, g.adventure.workers[1]); assertEquals(6.0, g.adventure.productionPerMinute()[1], .001)
    }

    @Test fun offlineProductionStartsAtActualCompletionIncludingFractionalMinutes() {
        fun production(frequent: Boolean): Long {
            time = 1_800_000_000_000L
            val g = game(); val forge = install(g, "adventureForge"); forge.b0 = time + 30_000
            g.adventure.workers.fill(0); val ore = g.adventure[ORE]
            if (frequent) repeat(40) { time += 15_000; g.tick() } else { time += 600_000; g.tick() }
            return g.adventure[ORE] - ore
        }
        assertEquals(7, production(false)); assertEquals(7, production(true))
    }

    @Test fun houseUpgradeAndHallCostsGrowWithoutIntroducingAnUnobtainableIngredient() {
        val g = game(); val cottage = g.objs.values.first { it.t == "cottage" }
        var last = 0L
        for (tier in 1..4) {
            cottage.tier = tier
            val cost = HamletIndustry.upgrade(cottage)
            assertTrue(cost.resources.getValue(TIMBER) > last); last = cost.resources.getValue(TIMBER)
            cost.resources.forEach { (r, n) -> assertTrue(n >= 0); assertTrue(r in setOf(TIMBER, STONE, ORE, TOOLS)) }
        }
        g.hall = 1; val first = HamletIndustry.hall(g); g.hall = 7; val lastHall = HamletIndustry.hall(g)
        assertTrue(lastHall.resources.getValue(STONE) > first.resources.getValue(STONE) * 300)
    }

    @Test fun aFreshVillageCanReachBreadAndRawMaterialProducersWithoutInjectedCurrency() {
        val g = game(); val field = g.objs.values.first { it.t == "field" }
        repeat(3) { assertTrue(g.plant(field, "corn")); time += 45_000; g.tick(); assertTrue(g.harvest(field)) }
        assertTrue(g.claimQuest()); assertTrue(g.claimQuest()); assertTrue(g.adventure.summon().isNotEmpty())
        // Neighbor work is the existing repeatable source of coins when the purse is low.
        assertTrue(g.neighbors.visit(0))
        var jobs = 0
        while ((g.coins < 2800 || g.level() < 3) && jobs++ < 160) {
            assertTrue(g.neighbors.placeOrder(0, HamletNeighbors.FIRST_SHOP))
            while (g.neighbors.left(0, HamletNeighbors.FIRST_SHOP) > 0) {
                assertTrue(g.neighbors.help(0, HamletNeighbors.FIRST_SHOP, g.neighbors.helpTool(0, HamletNeighbors.FIRST_SHOP)))
                time += 2000
            }
            assertNotNull(g.neighbors.collect(0, HamletNeighbors.FIRST_SHOP)); g.tick()
        }
        assertTrue(g.coins >= 2800); assertTrue(g.level() >= 3)
        fun work() { g.beginWork(); repeat(12) { assertTrue(g.answerWork(g.workTool)); time += 2000 }; g.tick() }
        val producers = listOf("bakery", "lumbermill", "quarry").map { id ->
            val cost = HamletIndustry.building(Hamlet.byId.getValue(id))
            var attempts = 0
            while (!cost.canPay(g) && attempts++ < 30) work()
            val (x, y) = location(g, id)
            val o = g.buy(id, x, y, false)!!
            while (g.buildLeft(o) > 0) { if (!g.applyWork(o)) work() }
            o
        }
        assertTrue(g.plant(field, "wheat"))
        while (g.cropLeft(field) > 0) { if (!g.applyWork(field)) work() }
        assertTrue(g.harvest(field)); val bakery = producers.first(); bakery.order = Order("Ada")
        assertTrue(g.acceptOrder(bakery))
        while (g.jobLeft(bakery) > 0) { if (!g.applyWork(bakery)) work() }
        assertTrue(g.deliver(bakery)); assertEquals(4, g.adventure[BREAD]); assertTrue(HamletIndustry.prepareRations(g))
        assertFalse(g.sandbox)
    }
}
