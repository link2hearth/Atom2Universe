package com.Atom2Universe.app.games.hamlet

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.recyclerview.widget.RecyclerView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/**
 * La boutique de construction doit rester légère quel que soit le nombre d'objets : on compte les vues réellement
 * créées pour la catégorie la plus longue, on vérifie les onglets, la recherche et les outils, puis que chaque dessin
 * du catalogue se prépare bien sur le fil d'arrière-plan.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = android.app.Application::class)
class HamletCatalogTest {
    private fun ouvrir(): Pair<HamletActivity, RecyclerView> {
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        app.getSharedPreferences("atom2univers_prefs", 0).edit().putString("app_language", "fr").commit()
        app.getSharedPreferences("hamlet_ui", 0).edit().putBoolean("introduced", true).putBoolean("effects", false).commit()
        val activity = Robolectric.buildActivity(HamletActivity::class.java).setup().get()
        idle()
        HamletCatalogDialog(activity).show()
        idle()
        val decor = ShadowDialog.getLatestDialog().window!!.decorView
        val recycler = vues(decor).filterIsInstance<RecyclerView>().single()
        return activity to recycler
    }
    private fun idle() = shadowOf(android.os.Looper.getMainLooper()).idle()
    private fun vues(v: View, out: MutableList<View> = ArrayList()): List<View> { out.add(v); if (v is ViewGroup) for (i in 0 until v.childCount) vues(v.getChildAt(i), out); return out }
    private fun poser(recycler: RecyclerView) {
        recycler.measure(View.MeasureSpec.makeMeasureSpec(1500, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1800, View.MeasureSpec.EXACTLY))
        recycler.layout(0, 0, 1500, 1800)
    }
    private fun onglet(recycler: RecyclerView, debut: String): Button {
        val racine = generateSequence(recycler as View) { it.parent as? View }.last()
        return vues(racine).filterIsInstance<Button>().first { it.text.toString().startsWith(debut) }
    }

    @Test fun lesDecorationsNeCreentQueLesCartesVisibles() {
        val (_, recycler) = ouvrir()
        poser(recycler)
        assertEquals("champs : deux cartes", 2, recycler.adapter!!.itemCount.coerceAtMost(2))
        onglet(recycler, "Décors").performClick(); idle(); poser(recycler)
        val total = recycler.adapter!!.itemCount
        assertTrue("la catégorie compte plus de 300 objets : $total", total > 300)
        assertTrue("seules les cartes visibles existent : ${recycler.childCount} vues pour $total objets", recycler.childCount in 1..60)
        recycler.scrollToPosition(total - 1); poser(recycler)
        assertTrue("le bas de la liste se construit aussi : ${recycler.childCount}", recycler.childCount in 1..60)
    }

    @Test fun chaqueOngletSAffiche() {
        val (_, recycler) = ouvrir()
        for (nom in listOf("Champs", "Maisons", "Ateliers", "Animaux", "Jardins", "Décors", "Sols")) {
            onglet(recycler, nom).performClick(); idle(); poser(recycler)
            assertTrue("$nom : au moins une carte", recycler.adapter!!.itemCount >= 1)
            assertTrue("$nom : la liste se pose", recycler.childCount >= 1)
        }
        onglet(recycler, "Outils").performClick(); idle(); poser(recycler)
        assertTrue("les outils : retour, copie, refonte, démolition, terrain", recycler.adapter!!.itemCount >= 8)
    }

    @Test fun laRechercheFiltreApresUnCourtDelai() {
        val (activity, recycler) = ouvrir()
        onglet(recycler, "Décors").performClick(); idle(); poser(recycler)
        val avant = recycler.adapter!!.itemCount
        val champ = generateSequence(recycler as View) { it.parent as? View }.last().let { vues(it) }.filterIsInstance<EditText>().first()
        val voulu = activity.itemName("well").take(4)
        champ.setText(voulu)
        shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(400)); poser(recycler)
        val apres = recycler.adapter!!.itemCount
        assertTrue("la recherche « $voulu » réduit la liste ($avant -> $apres)", apres in 1 until avant)
        champ.setText("zzzzzz")
        shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(400)); poser(recycler)
        assertEquals("aucun résultat : le message seul", 1, recycler.adapter!!.itemCount)
    }

    @Test fun tousLesDessinsDuCatalogueSePreparentEnArrierePlan() {
        val thumbs = HamletCatalogThumbs(cacheBytes = 512 * 1024 * 1024)
        try {
            val defs = Hamlet.defs.filter { it.cat != Cat.HALL }
            for (d in defs) thumbs.request(d, 160, 84, HamletCatalogThumbs.key(d, 160, 84)) { true }
            val fin = System.currentTimeMillis() + 120_000
            fun restants() = defs.filter { thumbs.peek(HamletCatalogThumbs.key(it, 160, 84)) == null }
            while (restants().isNotEmpty() && System.currentTimeMillis() < fin) Thread.sleep(50)
            val manquants = restants()
            assertTrue("vignettes manquantes : ${manquants.take(10).map { it.id }} (${manquants.size})", manquants.isEmpty())
        } finally { thumbs.close() }
    }
}
