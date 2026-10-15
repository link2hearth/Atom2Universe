package com.Atom2Universe.app.games.hamlet

import com.Atom2Universe.app.games.hamlet.AdventureResource.*
import org.junit.Assert.*
import org.junit.Test

/** Les échelles de bâtiments : un bâtiment plus tardif n'est jamais plus faible que celui qu'il prolonge. */
class HamletLadderTest {
    private fun rate(id: String, resource: AdventureResource) =
        (HamletIndustry.recipe(id, 1).output[resource] ?: 0) * 3600.0 / Hamlet.byId.getValue(id).job

    /** Unités produites par unité d'ingrédient (matières et cultures). */
    private fun yieldPerUnit(id: String, resource: AdventureResource): Double {
        val recipe = HamletIndustry.recipe(id, 1)
        val inputs = recipe.cost.resources.values.sum() + recipe.cost.crops.values.sum()
        return (recipe.output[resource] ?: 0).toDouble() / inputs.coerceAtLeast(1)
    }

    @Test fun everySmithyMakesAtLeastAsManyToolsAsTheAdventureForge() {
        for (id in listOf("blacksmith", "locksmith", "bellfoundry", "shieldmaker", "armory", "fletcher", "foundry", "weaponsmith")) {
            assertTrue("$id rate", rate(id, TOOLS) >= rate("adventureForge", TOOLS))
            assertTrue("$id yield", yieldPerUnit(id, TOOLS) >= yieldPerUnit("adventureForge", TOOLS))
        }
        assertTrue(rate("prismforge", TOOLS) > rate("blacksmith", TOOLS))
    }

    @Test fun everyBookWorkshopMakesAtLeastAsManyBooksAsTheWaypost() {
        for (id in listOf("adventureAcademy", "school", "library", "cartographers", "cartographer", "scribe", "academy", "grandacademy", "adventureGate"))
            assertTrue("$id rate", rate(id, BOOKS) >= rate("waypost", BOOKS))
    }

    @Test fun theWineCellarSitsInTheSameSlotOfTheLadderAsTheEggFarm() {
        val wine = Hamlet.byId.getValue("winecellar"); val eggs = Hamlet.byId.getValue("eggfarm")
        assertEquals(eggs.lvl, wine.lvl)
        assertEquals(eggs.cost, wine.cost); assertEquals(eggs.xp, wine.xp)
        assertEquals(eggs.pay, wine.pay); assertEquals(eggs.payXp, wine.payXp); assertEquals(eggs.build, wine.build)
    }

    @Test fun everyOrdinaryCropIsAskedByAWorkshopOrALegend() {
        val inWorkshops = Hamlet.defs.filter { it.cat == Cat.SHOP }.flatMap { HamletIndustry.recipe(it.id, 1).cost.crops.keys }.toSet()
        val inLegends = HamletLegends.defs.flatMap { l -> l.phases.flatMap { p -> p.needs.map { it.crop } } }.toSet()
        for (crop in Hamlet.crops.filter { !it.magic })
            assertTrue("${crop.id} ne sert à aucun atelier ni aucune légende", crop.id in inWorkshops || crop.id in inLegends)
    }

    @Test fun noHouseBoughtWithCoinsIsBeatenByAnotherOne() {
        val homes = Hamlet.defs.filter { it.cat == Cat.HOME && it.gems == 0 }
        for (x in homes) {
            val better = homes.filter { y ->
                y !== x && y.lvl <= x.lvl && y.cost <= x.cost && y.pop >= x.pop && y.w * y.d <= x.w * x.d &&
                    y.tax >= x.tax && y.tax.toLong() * x.period >= x.tax.toLong() * y.period &&
                    (y.lvl < x.lvl || y.cost < x.cost || y.pop > x.pop || y.w * y.d < x.w * x.d || y.tax > x.tax ||
                        y.tax.toLong() * x.period > x.tax.toLong() * y.period)
            }
            assertTrue("${x.id} est battue par ${better.map { it.id }}", better.isEmpty())
        }
    }
}
