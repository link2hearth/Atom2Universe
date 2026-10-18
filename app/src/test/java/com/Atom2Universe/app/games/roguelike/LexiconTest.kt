package com.Atom2Universe.app.games.roguelike

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Le lexique doit couvrir tout ce que le jeu affiche, et ses liens ne doivent mener nulle part
 * dans le vide : ajouter une relique, un état, une stat sans sa fiche fait échouer ce test.
 */
class LexiconTest {

    private fun ids() = Lexicon.entries.map { it.id }.toSet()

    @Test
    fun lesIdentifiantsSontUniques() {
        val all = Lexicon.entries.map { it.id }
        assertEquals(all.toSet().size, all.size)
    }

    @Test
    fun toutCeQueLeJeuAffichePossedeUneFiche() {
        val ids = ids()
        val missing = buildList {
            StatType.entries.forEach { if (Lexicon.idOf(it) !in ids) add(Lexicon.idOf(it)) }
            Element.entries.forEach { if (Lexicon.idOf(it) !in ids) add(Lexicon.idOf(it)) }
            Relic.entries.forEach { if (Lexicon.idOf(it) !in ids) add(Lexicon.idOf(it)) }
            Reaction.entries.forEach { if (Lexicon.idOf(it) !in ids) add(Lexicon.idOf(it)) }
            Resonance.entries.forEach { if (Lexicon.idOf(it) !in ids) add(Lexicon.idOf(it)) }
            ArmorWeight.entries.forEach { if (Lexicon.idOf(it) !in ids) add(Lexicon.idOf(it)) }
            Archetype.entries.forEach {
                if (Lexicon.idOf(it) !in ids) add(Lexicon.idOf(it))
                if (Lexicon.specialId(it) !in ids) add(Lexicon.specialId(it))
            }
            ItemBase.entries.forEach { if (Lexicon.idOf(it) !in ids) add(Lexicon.idOf(it)) }
            Rarity.entries.forEach { if (Lexicon.idOf(it) !in ids) add(Lexicon.idOf(it)) }
            MonsterType.entries.forEach { if (Lexicon.idOf(it) !in ids) add(Lexicon.idOf(it)) }
            (0 until Grade.ELEMENTS).forEach { if (Lexicon.materialId(it) !in ids) add(Lexicon.materialId(it)) }
        }
        assertTrue("Fiches manquantes : $missing", missing.isEmpty())
    }

    @Test
    fun chaqueCategorieAuMoinsUneFicheToujoursVisible() {
        for (cat in LexiconCategory.entries)
            assertTrue("Catégorie vide : $cat", Lexicon.visibleIn(cat, Hero.starter()).isNotEmpty())
    }

    @Test
    fun lesPaliersSeFabriquentALaDemande() {
        assertNotNull(Lexicon.find(Lexicon.tierId(StatType.CON, 4)))
        assertNotNull(Lexicon.find(Lexicon.tierId(StatType.CRIT_CHANCE, 30)))
        assertNull(Lexicon.find("tier:NOPE:4"))
        assertNull(Lexicon.find("tier:CON:0"))
        assertNull(Lexicon.find("n_importe_quoi"))
    }

    @Test
    fun cequOnNaPasVuResteCache() {
        val hero = Hero.starter()
        val secret = Lexicon.entries.filter { it.secret }
        assertTrue(secret.isNotEmpty())
        assertTrue("Rien ne doit être connu au départ", secret.none { it.visible(hero) })

        hero.discover(MonsterType.RAT, Element.FIRE, listOf(Reaction.THERMAL_SHOCK))
        hero.relics += Relic.FIREBALL
        hero.knownResonances += Resonance.ALCHEMY
        assertTrue(Lexicon.find(Lexicon.idOf(Reaction.THERMAL_SHOCK))!!.visible(hero))
        assertFalse(Lexicon.find(Lexicon.idOf(Reaction.SHATTER))!!.visible(hero))
        assertTrue(Lexicon.find(Lexicon.idOf(Relic.FIREBALL))!!.visible(hero))
        assertFalse(Lexicon.find(Lexicon.idOf(Relic.VENOM))!!.visible(hero))
        assertTrue(Lexicon.find(Lexicon.idOf(Resonance.ALCHEMY))!!.visible(hero))
        assertTrue(Hero.affinityKey(MonsterType.RAT, Element.FIRE) in hero.knownAffinities)
    }

    @Test
    fun lesMarqueursDesTextesVisentDesFichesQuiExistent() {
        val res = File("src/main/res")
        val files = listOf("values", "values-fr").flatMap { dir ->
            listOf("strings_roguelike.xml", "strings_roguelike_lexicon.xml").map { File(res, "$dir/$it") }
        }
        val marker = Regex("""\[\[([^|\]]+)\|""")
        val broken = mutableListOf<String>()
        for (f in files) {
            assertTrue("Introuvable : $f", f.exists())
            for (m in marker.findAll(f.readText())) {
                val id = m.groupValues[1]
                // Les ids composés au moment de l'affichage (paliers) portent un %s
                if ('%' in id) continue
                val ok = if (id.startsWith("cat:")) LexiconCategory.entries.any { it.name == id.removePrefix("cat:") } else Lexicon.find(id) != null
                if (!ok) broken += "${f.parentFile?.name}: $id"
            }
        }
        assertTrue("Liens vers des fiches absentes : $broken", broken.isEmpty())
    }

    @Test
    fun lesMarqueursSeRetirentPourLeCanvas() {
        assertEquals("Léger, +5 % d'esquive", LexiconText.strip("[[weight_light|Léger]], +5 % d'[[stat_dodge|esquive]]"))
        assertEquals("sans lien", LexiconText.strip("sans lien"))
    }
}
