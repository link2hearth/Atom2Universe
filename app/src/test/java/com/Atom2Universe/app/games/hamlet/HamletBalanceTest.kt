package com.Atom2Universe.app.games.hamlet

import com.Atom2Universe.app.games.hamlet.AdventureResource.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class HamletBalanceTest {
    private var time = 1_800_000_000_000L
    private fun game() = HamletGame({ time }, Random(23)).apply { startNew(false) }.established()
    private fun rate(id: String, resource: AdventureResource) =
        (HamletIndustry.recipe(id, 1).output[resource] ?: 0) * 3600.0 / Hamlet.byId.getValue(id).job

    @Test fun longWorkshopsKeepAUsefulHourlyOutput() {
        assertTrue(rate("apothecary", MEDICINE) >= rate("herbalist", MEDICINE))
        assertTrue(rate("greenhouse", MEDICINE) >= rate("herbalist", MEDICINE))
        assertTrue(rate("conservatory", MEDICINE) > rate("herbalist", MEDICINE))
        assertTrue(rate("skygarden", MEDICINE) > rate("conservatory", MEDICINE))
        val hen = HamletIndustry.products("henyard", 1).getValue(EGGS) * 3600.0 / HamletLife.produce.getValue("henyard").seconds
        assertTrue(rate("eggfarm", EGGS) >= hen)
        assertTrue(rate("fishing", FISH) > rate("fishmonger", FISH))
        assertTrue(rate("glassworks", CRAFTS) > rate("florist", CRAFTS))
        assertTrue(rate("jeweler", CRAFTS) > rate("glassworks", CRAFTS))
        assertTrue(rate("prismforge", TOOLS) > rate("adventureForge", TOOLS))
        assertTrue(rate("barracks", FOOD) > rate("adventureInn", FOOD))
        assertTrue(rate("butcher", FOOD) >= 240)
    }

    @Test fun artisanRouteRewardsProcessingInsteadOfChargingTheFullRawRouteAgain() {
        val raw = HamletIndustry.caravan
        val artisan = HamletIndustry.artisanCaravan
        for (r in listOf(BREAD, BOOKS, TOOLS)) assertTrue(artisan.resources.getValue(r) < raw.resources.getValue(r))
        assertTrue(artisan.supplies < raw.supplies)
        assertTrue(artisan.resources.getValue(CRAFTS) > 0)
        val balloon = HamletIndustry.recipe("balloon", 1)
        assertEquals(balloon.output.getValue(CRAFTS) * 2, balloon.cost.resources.getValue(TIMBER))
        assertEquals(balloon.output.getValue(CRAFTS) / 4, balloon.cost.resources.getValue(CLOTH))
    }

    @Test fun instantRationsRemainUsefulWithoutMakingTheInnInferior() {
        val g = game(); g.adventure.add(BREAD, 4)
        val before = g.adventure[FOOD]
        assertTrue(HamletIndustry.prepareRations(g)); assertTrue(HamletIndustry.prepareRations(g))
        assertEquals(before + 48, g.adventure[FOOD]); assertFalse(HamletIndustry.prepareRations(g))
        val inn = HamletIndustry.recipe("adventureInn", 1)
        assertEquals(HamletIndustry.rations.resources[BREAD], inn.cost.resources[BREAD])
        assertTrue(inn.output.getValue(FOOD) > HamletIndustry.RATION_FOOD)
    }

    @Test fun longContractMigrationNeverAwardsUnpaidVersionThreeOutputs() {
        val g = game(); val start = g.realmOrigin()
        g.sandbox = true
        val spot = (start until start + g.realmSize()).flatMap { y -> (start until start + g.realmSize()).map { x -> x to y } }
            .first { (x,y) -> g.canPlace("apothecary", x, y, false) }
        val shop = g.buy("apothecary", spot.first, spot.second, false)!!
        g.sandbox = false
        shop.order = Order("Ada", time, 1, 2)
        assertTrue(HamletSave.fromJson(g, HamletSave.toJson(g)))
        time += shop.def.job * 1000L
        assertTrue(g.deliver(g.objs.getValue(shop.id)))
        assertEquals(12, g.adventure[MEDICINE])
        assertEquals(96, HamletIndustry.recipe("apothecary", 1).output.getValue(MEDICINE))
    }

    @Test fun freeWorkCanRemoveADayOfVigilInUnderTwoActiveHoursWithoutUnlockingAnythingEarly() {
        val g = game(); val before = time
        assertFalse(g.legends.applyWork(-1)); assertFalse(g.legends.applyWork(0))
        // An already paid and unlocked vigil; the test measures the same 12-choice activity as the UI.
        g.legends.until[0] = time + Hamlet.DAY_MS
        g.beginWork()
        while (g.legends.timeLeft(0) > 0) {
            repeat(12) { assertTrue(g.answerWork(g.workTool)); time += 1400 }
            assertTrue(g.legends.applyWork(0)); time += 1400
        }
        assertTrue(time - before < 2 * 3_600_000L)
        assertFalse(g.adventure.heroes[HamletLegends.defs[0].hero].owned)
        assertTrue(g.legends.canFinish(0)); assertNotNull(g.legends.finish(0)); assertNull(g.legends.finish(0))
        assertFalse(g.legends.canBegin(0)) // Work never bypasses chapters, ingredients or the circle.
    }

    @Test fun vigilSpendsOnlyRemainingWorkAndPersistsTheReduction() {
        val g = game(); g.legends.until[0] = time + 120_000; g.workSeconds = 300
        assertTrue(g.legends.applyWork(0)); assertEquals(180, g.workSeconds)
        assertFalse(g.legends.applyWork(0)); assertEquals(180, g.workSeconds)
        assertTrue(HamletSave.fromJson(g, HamletSave.toJson(g)))
        assertTrue(g.legends.canFinish(0)); assertEquals(180, g.workSeconds)
    }
}
