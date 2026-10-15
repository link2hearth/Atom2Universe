package com.Atom2Universe.app.games.hamlet

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Le bouton « Y aller » des objectifs de chapitre : il n'apparaît que pour un objectif à faire et mène au bon endroit. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = android.app.Application::class)
class HamletGoalTest {
    private fun ouvrir(): HamletActivity {
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        app.getSharedPreferences("atom2univers_prefs", 0).edit().putString("app_language", "fr").commit()
        app.getSharedPreferences("hamlet_ui", 0).edit().putBoolean("introduced", true).putBoolean("effects", false).commit()
        val activity = Robolectric.buildActivity(HamletActivity::class.java).setup().get()
        idle()
        return activity
    }
    private fun idle() = shadowOf(android.os.Looper.getMainLooper()).idle()
    private fun vues(v: View, out: MutableList<View> = ArrayList()): List<View> { out.add(v); if (v is ViewGroup) for (i in 0 until v.childCount) vues(v.getChildAt(i), out); return out }
    private fun allers(activity: HamletActivity) = vues(activity.window.decorView).filterIsInstance<ImageButton>().filter { it.contentDescription?.toString() == "Y aller" && it.isShown }
    private fun chapitres(activity: HamletActivity) { activity.open(HamletModule.JOURNAL, HamletJournalPage.CHAPTERS); idle() }

    @Test fun unObjectifAFaireABoutonEtUnObjectifFiniNon() {
        val activity = ouvrir(); val g = activity.game
        g.xp = 200 // niveau 3 : le banc est ouvert
        chapitres(activity)
        // chapitre 1 : récolter 8 fois, poser un banc
        assertEquals("deux objectifs à faire, deux boutons", 2, allers(activity).size)
        g.stats["harvested"] = 8
        activity.onChanged(); chapitres(activity)
        assertEquals("la récolte est faite : plus qu'un bouton", 1, allers(activity).size)
    }

    @Test fun unBatimentPasEncoreOuvertNAPasDeBouton() {
        val activity = ouvrir()
        // niveau 1 : le banc n'est pas ouvert, seule la récolte mène quelque part
        chapitres(activity)
        assertEquals(1, allers(activity).size)
    }

    @Test fun lObjectifDePoseOuvreLaPoseDeLObjet() {
        val activity = ouvrir(); val g = activity.game
        g.xp = 200; g.stats["harvested"] = 8
        activity.onChanged(); chapitres(activity)
        val bouton = allers(activity).single()
        bouton.performClick(); idle()
        assertEquals("bench", activity.village.renderer.ghostDef?.id)
    }

    @Test fun lObjectifDExpeditionOuvreLesExpeditions() {
        val activity = ouvrir(); val g = activity.game
        g.xp = 1_000; g.journey.chapter = 1 // chapitre 2 : population, livraisons, relais, expéditions
        activity.onChanged(); chapitres(activity)
        val avant = allers(activity).size
        assertTrue("au moins la population, les livraisons et l'expédition : $avant", avant >= 3)
        val departs = { vues(activity.window.decorView).filterIsInstance<Button>().count { it.text.toString() == "Partir" && it.isShown } }
        assertEquals("les expéditions ne sont pas ouvertes", 0, departs())
        // le dernier objectif du chapitre est l'expédition vers la première région
        allers(activity).last().performClick(); idle()
        assertTrue("l'onglet des expéditions s'affiche", departs() >= 1)
    }

    @Test fun lObjectifDePopulationOuvreLesMaisons() {
        val activity = ouvrir(); val g = activity.game
        g.xp = 1_000; g.journey.chapter = 1
        activity.onChanged(); chapitres(activity)
        activity.guideToGoal("population"); idle()
        val boutique = org.robolectric.shadows.ShadowDialog.getLatestDialog().window!!.decorView
        val choisi = vues(boutique).filterIsInstance<Button>().single { it.isSelected }
        assertEquals("la boutique s'ouvre sur les maisons", "Maisons", choisi.text.toString())
    }
}
