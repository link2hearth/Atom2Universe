package com.Atom2Universe.app.games.farm

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class FarmCookingTest {
    private val now = 1_700_000_000_000L
    @Test fun `tous les plats ont un ou deux gestes et les creations restent hors cuisine`() {
        FarmRecipe.entries.forEach { recipe ->
            val session = FarmCookingSession(recipe)
            if (recipe.food) assertTrue(session.steps.size in 1..2)
            else assertTrue(session.steps.isEmpty())
        }
        assertEquals(listOf(FarmCookingStep.ASSEMBLE), FarmCookingSession(FarmRecipe.EGG_SANDWICH).steps)
        assertEquals(listOf(FarmCookingStep.CHOP, FarmCookingStep.STIR), FarmCookingSession(FarmRecipe.SOUP).steps)
    }
    @Test fun `decoupe valide chaque cible une fois et ignore geste court horizontal ou hors planche`() {
        val session = FarmCookingSession(FarmRecipe.SOUP)
        assertFalse(session.drag(.2f, .5f, .2f, .65f))
        assertFalse(session.drag(.1f, .5f, .9f, .5f))
        assertFalse(session.drag(Float.NaN, .1f, .2f, .9f))
        assertFalse(session.drag(-.2f, .1f, .2f, .9f))
        assertTrue(session.drag(.2f, .2f, .2f, .8f))
        assertFalse(session.drag(.2f, .2f, .2f, .8f))
        assertEquals(.25f, session.progress, .00001f)
        assertFalse(session.next())
        for (x in listOf(.4f, .6f, .8f)) assertTrue(session.drag(x, .2f, x, .8f))
        assertTrue(session.next())
        assertEquals(FarmCookingStep.STIR, session.step)
        assertEquals(0f, session.progress, 0f)
    }
    @Test fun `deux tours dans chaque sens melangent sans chrono`() {
        for (direction in listOf(-1, 1)) {
            val session = FarmCookingSession(FarmRecipe.CHEESE)
            for (i in 0..160) {
                val angle = i * PI * 4 / 160 * direction
                session.stir((.5 + cos(angle) * .25).toFloat(), (.53 + sin(angle) * .25).toFloat())
                if (i == 80) assertEquals(.5f, session.progress, .0001f)
            }
            assertTrue(session.next()); assertTrue(session.complete)
        }
    }
    @Test fun `saut au travers du bol et sortie ne simulent pas un tour`() {
        val session = FarmCookingSession(FarmRecipe.CHEESE)
        session.stir(.75f, .53f)
        assertFalse(session.stir(.25f, .53f))
        assertFalse(session.stir(.5f, .53f))
        assertFalse(session.stir(.5f, .99f))
        assertEquals(0f, session.progress, 0f)
        assertFalse(session.next())
    }
    @Test fun `sandwich demande quatre couches ordonnees et un depot sur assiette`() {
        val session = FarmCookingSession(FarmRecipe.EGG_SANDWICH)
        assertFalse(session.drag(.38f, .24f, .5f, .73f))
        assertFalse(session.drag(.14f, .24f, .14f, .8f))
        assertTrue(session.drag(.14f, .24f, .5f, .73f))
        assertFalse(session.drag(.14f, .24f, .5f, .73f))
        for (i in 1..3) assertTrue(session.drag(.14f + i * .24f, .24f, .5f, .73f))
        assertTrue(session.next()); assertTrue(session.complete)
        assertFalse(session.drag(.14f, .24f, .5f, .73f))
        assertFalse(session.assist()); assertFalse(session.next())
    }
    @Test fun `alternative par bouton termine chaque recette en seize actions au plus`() {
        FarmRecipe.entries.filter { it.food }.forEach { recipe ->
            val session = FarmCookingSession(recipe)
            var actions = 0
            while (!session.complete && actions < 16) {
                assertTrue(session.assist()); actions++
                if (session.progress >= .99999f) assertTrue(session.next())
            }
            assertTrue(recipe.name, session.complete)
        }
    }
    @Test fun `nouvelle session apres abandon commence vide et ne touche aucun stock`() {
        val workshop = FarmWorkshopState().apply { discover(1, emptySet()) }
        val before = workshop.toJson().toString()
        FarmCookingSession(FarmRecipe.PICKLES).apply { assist(); assist() }
        assertEquals(0f, FarmCookingSession(FarmRecipe.PICKLES).progress, 0f)
        assertEquals(before, workshop.toJson().toString())
    }
    @Test fun `bonus manuel seulement sur temps normal et livre seulement a collecte`() {
        val workshop = FarmWorkshopState().apply { discover(1, emptySet()) }
        assertTrue(workshop.start(FarmRecipe.PICKLES, now, workshop.revision, true))
        assertEquals(now + 60_000, workshop.jobs.single().readyAt)
        assertEquals(0, workshop.distinctDishes)
        assertEquals(1, workshop.collect(now + 60_000))
        assertEquals(1, workshop.cooked(FarmRecipe.PICKLES))
        assertEquals(1, workshop.practiced(FarmRecipe.PICKLES))
        assertEquals(0, workshop.collect(now + 60_000))
        assertTrue(workshop.start(FarmRecipe.PICKLES, now + 60_000, workshop.revision, true))
        assertEquals(now + 60_000 + FarmRecipe.PICKLES.durationMillis * 80 / 100, workshop.jobs.single().readyAt)
        assertEquals(1, workshop.distinctDishes)
        assertTrue(workshop.start(FarmRecipe.PICKLES, now + 60_000, workshop.revision, false))
        assertEquals(now + 60_000 + FarmRecipe.PICKLES.durationMillis, workshop.jobs.last().readyAt)
    }
    @Test fun `plats repetes et creations non alimentaires ne debloquent pas toutes les pages`() {
        val workshop = FarmWorkshopState().apply { discover(23, FarmAnimalProduct.entries.toSet(), flowers = FarmFlower.entries.toSet()) }
        repeat(3) { workshop.start(FarmRecipe.PICKLES, now, workshop.revision) }
        workshop.collect(now + LivestockState.DAY)
        workshop.start(FarmRecipe.SCARF, now + LivestockState.DAY, workshop.revision)
        workshop.start(FarmRecipe.GARDEN_BOUQUET, now + LivestockState.DAY, workshop.revision)
        workshop.collect(now + 2 * LivestockState.DAY)
        workshop.discover(23, FarmAnimalProduct.entries.toSet())
        assertEquals(1, workshop.distinctDishes)
        assertEquals(3, workshop.totalDishes)
        assertFalse(workshop.knows(FarmRecipe.CORNBREAD))
    }
    @Test fun `stock plein retient aussi experience du livre et ne recompte pas un plat`() {
        val workshop = FarmWorkshopState().apply { discover(1, emptySet()); start(FarmRecipe.PICKLES, now, revision, true) }
        val json = workshop.toJson().put("stock", JSONObject().put("PICKLES", FarmWorkshopState.STOCK_CAPACITY))
            .put("cooked", JSONObject().put("PICKLES", FarmWorkshopState.STOCK_CAPACITY))
        val restored = FarmWorkshopState.fromJson(json)
        assertEquals(0, restored.collect(now + 60_000))
        assertEquals(0, restored.practiced(FarmRecipe.PICKLES))
        restored.take(FarmRecipe.PICKLES, 1)
        assertEquals(1, restored.collect(now + 60_000))
        assertEquals(1, restored.practiced(FarmRecipe.PICKLES))
        assertEquals(0, restored.collect(now + 60_000))
    }
    @Test fun `pages nouvelles exigent experience ingredients et recettes de base puis restent connues`() {
        val stock = JSONObject()
        FarmRecipe.entries.filter { it.food && it.dishesNeeded == 0 }.take(8).forEach { stock.put(it.name, 1) }
        val workshop = FarmWorkshopState.fromJson(JSONObject().put("stock", stock))
        workshop.discover(1, emptySet())
        assertFalse(workshop.knows(FarmRecipe.CORNBREAD))
        workshop.discover(23, FarmAnimalProduct.entries.toSet(), FarmCrop.trees.toSet(), FarmFlower.entries.toSet(), FarmMeat.entries.toSet())
        assertEquals(FarmRecipe.entries, workshop.recipes)
        assertTrue(workshop.toggleFavorite(FarmRecipe.EGG_SANDWICH))
        assertFalse(workshop.toggleFavorite(FarmRecipe.SCARF))
        workshop.discover(1, emptySet())
        val restored = FarmWorkshopState.fromJson(workshop.toJson())
        assertEquals(FarmRecipe.entries, restored.recipes)
        assertTrue(restored.favorite(FarmRecipe.EGG_SANDWICH))
        assertEquals(8, restored.distinctDishes)
        assertTrue(restored.toggleFavorite(FarmRecipe.EGG_SANDWICH))
        assertFalse(restored.favorite(FarmRecipe.EGG_SANDWICH))
    }
    @Test fun `donnees du livre abimees sont bornees et anciens plats conserves`() {
        val workshop = FarmWorkshopState.fromJson(JSONObject().put("stock", JSONObject().put("PICKLES", 2))
            .put("cooked", JSONObject().put("SOUP", -10).put("SCARF", 99))
            .put("practiced", JSONObject().put("PICKLES", 99))
            .put("favorites", JSONObject().put("UNKNOWN", true).put("EGG_SANDWICH", true)))
        assertEquals(2, workshop.cooked(FarmRecipe.PICKLES))
        assertEquals(2, workshop.practiced(FarmRecipe.PICKLES))
        assertEquals(0, workshop.cooked(FarmRecipe.SCARF))
        assertFalse(workshop.favorite(FarmRecipe.EGG_SANDWICH))
        val recovered = FarmWorkshopState.fromJson(JSONObject().put("cooked", JSONObject().put("SOUP", 1))
            .put("favorites", JSONObject().put("SOUP", true)))
        assertTrue(recovered.knows(FarmRecipe.SOUP))
        assertTrue(recovered.favorite(FarmRecipe.SOUP))
        FarmRecipe.entries.forEach { recipe ->
            assertTrue(recipe.preparations.all { it.recipe.ordinal < recipe.ordinal })
        }
    }
}
