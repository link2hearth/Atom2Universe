package com.Atom2Universe.app.science.mycology

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Garde-fous de l'atlas des champignons : le catalogue se tient, chaque texte existe en anglais et en
 * français, et chaque espèce a au moins une source dans « À propos ».
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MycologyCatalogTest {

    private class Names(val strings: Set<String>, val arrays: Set<String>, val plurals: Set<String>, val untranslatable: Set<String>)

    private fun names(file: String): Names {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(file))
        val strings = HashSet<String>()
        val untranslatable = HashSet<String>()
        val arrays = HashSet<String>()
        val s = doc.getElementsByTagName("string")
        for (i in 0 until s.length) {
            val attrs = s.item(i).attributes
            val name = attrs.getNamedItem("name").nodeValue
            if (attrs.getNamedItem("translatable")?.nodeValue == "false") untranslatable += name else strings += name
        }
        val a = doc.getElementsByTagName("string-array")
        for (i in 0 until a.length) arrays += a.item(i).attributes.getNamedItem("name").nodeValue
        val plurals = HashSet<String>()
        val p = doc.getElementsByTagName("plurals")
        for (i in 0 until p.length) plurals += p.item(i).attributes.getNamedItem("name").nodeValue
        return Names(strings, arrays, plurals, untranslatable)
    }

    private val en by lazy { names("src/main/res/values/strings_mycology.xml") }
    private val fr by lazy { names("src/main/res/values-fr/strings_mycology.xml") }

    @Test
    fun chaqueTexteExisteEnAnglaisEtEnFrancais() {
        assertEquals("chaînes absentes en français", en.strings - fr.strings, emptySet<String>())
        assertEquals("chaînes absentes en anglais", fr.strings - en.strings, emptySet<String>())
        assertEquals("listes absentes en français", en.arrays - fr.arrays, emptySet<String>())
        assertEquals("listes absentes en anglais", fr.arrays - en.arrays, emptySet<String>())
        assertEquals("pluriels absents en français", en.plurals - fr.plurals, emptySet<String>())
        assertEquals("pluriels absents en anglais", fr.plurals - en.plurals, emptySet<String>())
        assertTrue("les noms latins ne se traduisent pas", fr.untranslatable.isEmpty())
    }

    @Test
    fun chaqueRessourceDeLAtlasEstDefinie() {
        val all = en.strings + en.untranslatable
        for (f in Class.forName("com.Atom2Universe.app.R\$string").fields) {
            if (f.name.startsWith("myco_")) assertTrue("string ${f.name} non définie", f.name in all)
        }
        for (f in Class.forName("com.Atom2Universe.app.R\$plurals").fields) {
            if (f.name.startsWith("myco_")) assertTrue("plurals ${f.name} non définie", f.name in en.plurals)
        }
        for (f in Class.forName("com.Atom2Universe.app.R\$array").fields) {
            if (f.name.startsWith("myco_")) assertTrue("array ${f.name} non définie", f.name in en.arrays)
        }
        // et le contraire : aucune chaîne orpheline dans le fichier
        val used = Class.forName("com.Atom2Universe.app.R\$string").fields.map { it.name }.toSet()
        for (name in all) assertTrue("string $name n'est pas dans R", name in used)
    }

    @Test
    fun leCatalogueSeTient() {
        val ids = FungusCatalog.species.map { it.id }
        assertEquals("identifiants en double", ids.size, ids.toSet().size)
        for (group in FungusCatalog.groups) {
            assertTrue("groupe ${group.id} trop petit", group.members.size >= 2)
            for (id in group.members) assertTrue("$id absent du catalogue (groupe ${group.id})", FungusCatalog.get(id) != null)
        }
        for (change in FungusCatalog.changes) assertTrue(FungusCatalog.get(change.speciesId) != null)
        // Un statut mortel ou toxique doit dire pourquoi : une note et au moins quatre caractères d'identification.
        for (sp in FungusCatalog.species) assertTrue("${sp.id} sans repère", sp.look.marksSide.isNotEmpty() && sp.look.marksUnder.isNotEmpty())
    }

    @Test
    fun chaqueEspeceADesSources() {
        val json = JSONObject(File("src/main/assets/science/mycology/sources.json").readText())
        val sources = json.getJSONArray("sources")
        val covered = HashSet<String>()
        var general = 0
        for (i in 0 until sources.length()) {
            val o = sources.getJSONObject(i)
            assertTrue(o.getString("url").startsWith("https://"))
            assertTrue(o.getString("citation").isNotBlank())
            val species = o.getJSONArray("species")
            for (j in 0 until species.length()) {
                val id = species.getString(j)
                if (id == "*") general++ else {
                    assertTrue("espèce inconnue « $id » dans les sources", FungusCatalog.get(id) != null)
                    covered += id
                }
            }
        }
        assertTrue(general > 0)
        val missing = FungusCatalog.species.map { it.id }.filter { it !in covered }
        assertTrue("espèces sans source propre : $missing", missing.isEmpty())
    }
}
