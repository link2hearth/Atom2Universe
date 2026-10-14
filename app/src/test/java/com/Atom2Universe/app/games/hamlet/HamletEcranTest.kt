package com.Atom2Universe.app.games.hamlet

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * Parcours du vrai écran du jeu sous Robolectric, sans image : le début de partie sans héros, le premier appel,
 * la révélation, l'onglet Légendes, la grange et la fenêtre d'amélioration. Il attrape les chaînes manquantes,
 * les formats qui ne correspondent pas et les plantages d'interface, pas la beauté de l'écran.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = android.app.Application::class)
class HamletEcranTest {
    private fun ecran(): ActivityController<HamletActivity> {
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        app.getSharedPreferences("atom2univers_prefs", 0).edit().putString("app_language", "fr").commit()
        app.getSharedPreferences("hamlet_ui", 0).edit().putBoolean("introduced", true).putBoolean("effects", false).commit()
        return Robolectric.buildActivity(HamletActivity::class.java).setup()
    }
    private fun idle() = shadowOf(android.os.Looper.getMainLooper()).idle()
    private fun vues(v: View, out: MutableList<View> = ArrayList()): List<View> { out.add(v); if (v is ViewGroup) for (i in 0 until v.childCount) vues(v.getChildAt(i), out); return out }
    private fun textes(root: View) = vues(root).filterIsInstance<TextView>().map { it.text.toString() }
    private fun bouton(root: View, debut: String): Button? = vues(root).filterIsInstance<Button>().firstOrNull { it.text.toString().startsWith(debut) && it.isShown }
    private fun module(root: View, nom: String) = vues(root).firstOrNull { it.contentDescription?.toString()?.startsWith(nom) == true && it.javaClass.simpleName == "Item" }

    @Test fun lePremierAppelOuvreLesHerosPuisLaRevelationLesPresente() {
        val controller = ecran(); val activity = controller.get(); val decor = activity.window.decorView
        val game = activity.game
        idle()
        assertEquals(0, game.adventure.ownedCount())
        assertEquals("héros et aventure cachés au départ", View.GONE, module(decor, "Héros")?.visibility ?: View.GONE)
        assertEquals(View.GONE, module(decor, "Aventure")?.visibility ?: View.GONE)

        game.stats["planted"] = 3; game.stats["harvested"] = 3
        assertTrue(game.claimQuest()); assertTrue(game.claimQuest())
        activity.onChanged(); idle()
        assertEquals(1L, game.adventure[AdventureResource.TICKETS])
        assertEquals(View.VISIBLE, module(decor, "Héros")!!.visibility)
        assertEquals(View.VISIBLE, module(decor, "Aventure")!!.visibility)

        activity.open(HamletModule.HEROES); idle()
        val appel = bouton(decor, "Recruter ×1")!!
        assertTrue(appel.performClick()); idle()
        assertEquals(1, game.adventure.ownedCount())
        // la fenêtre de révélation est ouverte : son bouton ferme et rend la main à la page
        val continuer = vues(org.robolectric.shadows.ShadowDialog.getLatestDialog().window!!.decorView).filterIsInstance<Button>().first()
        assertTrue(continuer.performClick()); idle()
        assertTrue(textes(decor).any { it.startsWith("Tes héros (1 / 36)") })
        // des cartes fermées restent pour les héros qu'on ne connaît pas
        assertTrue(textes(decor).any { it == "?" })
        controller.pause().stop().destroy()
    }

    @Test fun laBanniereCommuneEtLesOngletsDuCarnetSontLa() {
        val controller = ecran(); val activity = controller.get(); val decor = activity.window.decorView
        val game = activity.game
        game.journey.chapter = 6; game.adventure.add(AdventureResource.TICKETS, 2)
        activity.onChanged(); idle()
        activity.open(HamletModule.JOURNAL, HamletJournalPage.LEGENDS); idle()
        assertTrue("la rumeur de la première légende", textes(decor).any { it.contains("lueur sur la colline") })
        // les ressources du camp restent dans la bannière sur le carnet
        assertTrue(vues(decor).any { it.contentDescription?.toString()?.startsWith("vivres") == true || it.contentDescription?.toString()?.contains("vivres") == true })
        activity.open(HamletModule.JOURNAL, HamletJournalPage.CHAPTERS); idle()
        assertTrue(textes(decor).any { it.contains("invitation") })
        activity.open(HamletModule.JOURNAL, HamletJournalPage.EXPEDITIONS); idle()
        activity.open(HamletModule.HEROES); idle()
        activity.open(HamletModule.ADVENTURE); idle()
        controller.pause().stop().destroy()
    }

    @Test fun laGrangeEtLaFenetreDAmeliorationMontrentLesRecoltesDemandees() {
        val controller = ecran(); val activity = controller.get()
        val game = activity.game
        game.coins = 100_000
        val champ = game.objs.values.first { it.def.cat == Cat.FIELD }
        game.addStock("wheat", 7)
        val granary = HamletActivity::class.java.getDeclaredMethod("showGranary").apply { isAccessible = true }
        granary.invoke(activity); idle()
        val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog()
        assertTrue(textes(dialog.window!!.decorView).any { it.contains("7 /") })
        dialog.dismiss()
        val upgrade = HamletActivity::class.java.getDeclaredMethod("showUpgrade", Obj::class.java).apply { isAccessible = true }
        val besoins = game.upgradeNeeds(champ)
        assertTrue(besoins.isNotEmpty())
        game.pantry.clear()
        upgrade.invoke(activity, champ); idle()
        val vide = org.robolectric.shadows.ShadowDialog.getLatestDialog().window!!.decorView
        assertTrue("les pièces et chaque récolte demandée", textes(vide).count { it.contains(" / ") || it.contains("pièces") } >= 1 + besoins.size)
        assertFalse("sans la récolte, l'amélioration reste grisée", bouton(vide, "Améliorer")?.isEnabled ?: false)
        org.robolectric.shadows.ShadowDialog.getLatestDialog().dismiss()
        for (n in besoins) game.pantry[n.crop] = n.units
        upgrade.invoke(activity, champ); idle()
        val plein = org.robolectric.shadows.ShadowDialog.getLatestDialog().window!!.decorView
        assertTrue("avec les récoltes, l'amélioration se lance", bouton(plein, "Améliorer")!!.let { it.isEnabled && it.performClick() })
        idle(); assertEquals(2, champ.tier)
        controller.pause().stop().destroy()
    }

    @Test fun lesPagesDeLAventureEtDesHerosSOuvrentAvecUneEquipeDeCinq() {
        val controller = ecran(); val activity = controller.get(); val decor = activity.window.decorView
        val game = activity.game; val a = game.adventure
        for (id in intArrayOf(7, 3, 0, 1, 2, 12, 20)) a.welcome(a.heroes[id])
        a.frontier = HamletAdventure.FIFTH_SLOT + 5; a.add(AdventureResource.TICKETS, 3); a.add(AdventureResource.FOOD, 5_000)
        game.journey.chapter = 7
        activity.onChanged(); idle()
        for (tab in listOf(HamletAdventurePage.EXPLORE, HamletAdventurePage.HEROES, HamletAdventurePage.TEAMS, HamletAdventurePage.FORGE,
            HamletAdventurePage.KINGDOM, HamletAdventurePage.CHALLENGES)) {
            activity.open(HamletAdventurePage.module(tab), tab); idle()
        }
        // la fiche d'un héros : niveau, étoiles, compétences
        activity.open(HamletModule.HEROES, HamletAdventurePage.HEROES); idle()
        val carte = vues(decor).first { it.contentDescription?.toString() == "Cyran" && it.isClickable }
        assertTrue(carte.performClick()); idle()
        val fiche = org.robolectric.shadows.ShadowDialog.getLatestDialog().window!!.decorView
        assertTrue(textes(fiche).any { it.contains("Niveau") })
        org.robolectric.shadows.ShadowDialog.getLatestDialog().dismiss()
        // un combat de campagne se lance et se règle
        activity.open(HamletModule.ADVENTURE, HamletAdventurePage.EXPLORE); idle()
        assertTrue(bouton(decor, "Combattre")!!.performClick()); idle()
        assertNotNull(a.activeBattle)
        controller.pause().stop().destroy()
    }

    @Test fun recommencerRepartDeZeroSansHerosNiAventure() {
        val controller = ecran(); val activity = controller.get(); val decor = activity.window.decorView
        val game = activity.game
        for (id in intArrayOf(7, 3, 0)) game.adventure.welcome(game.adventure.heroes[id])
        game.adventure.frontier = 30; game.journey.chapter = 5
        activity.onChanged(); idle()
        assertEquals(View.VISIBLE, module(decor, "Héros")!!.visibility)
        HamletActivity::class.java.getDeclaredMethod("restartGame").apply { isAccessible = true }.invoke(activity); idle()
        assertEquals(0, game.adventure.ownedCount()); assertEquals(0, game.adventure.frontier); assertEquals(0, game.journey.chapter)
        assertEquals(View.GONE, module(decor, "Héros")!!.visibility)
        assertEquals(0, game.quest)
        controller.pause().stop().destroy()
    }
}
