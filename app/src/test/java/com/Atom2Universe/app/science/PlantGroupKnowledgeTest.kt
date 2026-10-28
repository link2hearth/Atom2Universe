package com.Atom2Universe.app.science

import com.Atom2Universe.app.R
import com.Atom2Universe.app.science.botany.BotanyCatalog
import com.Atom2Universe.app.science.parentes.LifeArchive
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],manifest=Config.NONE)
class PlantGroupKnowledgeTest {
    private fun strings(locale: String): Map<String, String> {
        val nodes=DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File("src/main/res/$locale/strings_botany_groups.xml")).getElementsByTagName("string")
        return (0 until nodes.length).associate { i ->
            val node=nodes.item(i)
            node.attributes.getNamedItem("name").nodeValue to node.textContent
        }
    }

    @Test fun toutesLesFamillesPartagentLaDescriptionEtUneSourcePrecise() {
        // Green algae have no APweb page: only the Kew search, never a borrowed page.
        val kewOnly=setOf("Chlamydomonadaceae","Cladophoraceae","Volvocaceae")
        for(family in BotanyCatalog.families) {
            assertTrue(family.id,PlantGroupKnowledge.hasArticle(family.id))
            // One resource ID means edits to the atlas diagnosis immediately reach the tree.
            assertEquals(family.id,family.description,PlantGroupKnowledge.descriptionResource(family.id))
            val links=PlantGroupKnowledge.sources(family.id)
            assertEquals(family.id,if(family.id in kewOnly)1 else 2,links.size)
            if(family.id !in kewOnly) {
                assertTrue(family.id,links.first().second.substringAfter('#',"").isNotEmpty())
                assertFalse(family.id,links.first().second.contains("amborellalesweb2"))
            }
            assertTrue(family.id,links.last().second.startsWith("https://powo.science.kew.org/results?q="))
        }
        // These orders were missing and crashed the family page.
        assertTrue(PlantGroupKnowledge.sources("Boraginaceae").first().second.endsWith("boraginalesweb.htm#Boraginaceae"))
        assertTrue(PlantGroupKnowledge.sources("Aristolochiaceae").first().second.endsWith("piperalesweb2.htm#Aristolochiaceae"))
        assertTrue(PlantGroupKnowledge.sources("Myrtaceae").first().second.endsWith("myrtalesweb2.htm#Myrtaceae"))
        assertTrue(PlantGroupKnowledge.sources("Retziaceae").first().second.endsWith("lamialesweb.htm#Stilbaceae"))
        assertTrue(PlantGroupKnowledge.sources("Sphagnaceae").first().second.endsWith("sporing.html#Sphagnopsida"))
        assertTrue(PlantGroupKnowledge.sources("Equisetaceae").first().second.endsWith("sporing.html#Equisetaceae"))
        assertTrue(PlantGroupKnowledge.sources("Cupressaceae").first().second.endsWith("cupressalesweb.html#Cupressaceae"))
        // These real filenames break the tempting order-name + web.htm rule.
        assertTrue(PlantGroupKnowledge.sources("Aquifoliaceae").first().second.contains("aquifolialesweb2.htm"))
        assertTrue(PlantGroupKnowledge.sources("Lythraceae").first().second.contains("myrtalesweb2.htm"))
        assertTrue(PlantGroupKnowledge.sources("Celastraceae").first().second.contains("celastralesweb.html"))
        assertTrue(PlantGroupKnowledge.sources("Pinaceae").first().second.contains("conifers.html"))
    }

    @Test fun nomsDeCladesCompatiblesAvecArchiveReelle() {
        val archive=File("src/main/assets/parentes/runtime.bin").inputStream().use { LifeArchive.read(it) }
        fun ancestors(name: String): List<String> {
            var node=archive.nodes.values.single { it.scientific==name }
            return buildList {
                while(true) {
                    add(node.scientific)
                    node=archive.nodes[node.parent] ?: break
                }
            }
        }
        val banana=ancestors("Musa acuminata")
        val pear=ancestors("Pyrus communis")
        assertTrue("Archive uses Magnoliopsida for both lineages", "Magnoliopsida" in banana && "Magnoliopsida" in pear)
        assertEquals(R.string.plant_group_flowers,PlantGroupKnowledge.descriptionResource("Magnoliopsida"))
        for(name in listOf("Chloroplastida","Streptophyta","Embryophyta","Tracheophyta","Spermatophyta","Magnoliopsida","Mesangiospermae","Magnoliidae","Bryophyta","Marchantiophyta")) {
            assertTrue(name,archive.nodes.values.any { it.scientific==name })
            assertTrue(name,PlantGroupKnowledge.hasArticle(name))
        }
        assertFalse("An unknown subgroup must not receive invented traits",PlantGroupKnowledge.hasArticle("unrecorded_subgroup"))
    }

    @Test fun ressourcesPartageesTraduitesEtFormatsIdentiques() {
        val en=strings("values");val fr=strings("values-fr")
        assertEquals(en.keys,fr.keys)
        val formats=Regex("%[0-9]+\\$[dsf]")
        for((key,value) in en) {
            assertTrue(key,value.isNotBlank() && fr.getValue(key).isNotBlank())
            assertEquals(key,formats.findAll(value).map { it.value }.toList(),formats.findAll(fr.getValue(key)).map { it.value }.toList())
        }
        val referenced=Regex("R\\.string\\.(plant_group_[A-Za-z0-9_]+)")
            .findAll(File("src/main/java/com/Atom2Universe/app/science/PlantGroupKnowledge.kt").readText()).map { it.groupValues[1] }
        referenced.forEach { assertTrue(it,it in en) }
    }

    @Test fun nouvellesFichesDeSousGroupesCorrespondentAuxNoeudsReelsEtRestentSourcees() {
        val archive=File("src/main/assets/parentes/runtime.bin").inputStream().use { LifeArchive.read(it) }
        val targets=listOf("Euphyllophyta","Core_Lamiales","Asphodeloideae","Amygdaleae","Apiineae","Cicereae",
            "Oleeae","Phoeniceae","Plantagineae","Saliceae","Ranunculoideae","Agrimoniinae","Sanguisorbeae",
            "Cercidoideae","Fragariinae","Kerrieae","Cinchonoideae","Rubieae","Lycieae","Cactoideae",
            "Opuntioideae","Attaleinae","Saniculoideae","Equisetidae","Polypodiidae","Ginkgoidae")
        for(name in targets) {
            val node=archive.nodes.values.single { it.scientific==name }
            assertFalse("A subgroup article must not masquerade as a species: $name",node.species)
            var ancestor=node
            var green=false
            while(true) {
                if(ancestor.scientific=="Chloroplastida")green=true
                ancestor=ancestor.parent?.let { archive.nodes[it] } ?: break
            }
            assertTrue("Not a green-plant subgroup: $name",green)
            assertNotNull(name,PlantGroupKnowledge.descriptionResource(name))
            val links=PlantGroupKnowledge.sources(name)
            assertTrue(name,links.first().second.startsWith("https://www.mobot.org/MOBOT/research/APweb/orders/"))
            assertFalse("A subgroup needs its own appropriate source page: $name",links.first().second.contains("amborellalesweb2"))
            assertTrue(name,links.last().second.endsWith(name))
        }
        // These new plates must have their missing intermediate ancestry explained, not only their family.
        val expected=mapOf("Agrimonia eupatoria" to setOf("Agrimoniinae","Sanguisorbeae"),
            "Pulsatilla pratensis" to setOf("Ranunculoideae"),
            "Antirrhinum majus" to setOf("Core_Lamiales"),
            "Aloe vera" to setOf("Asphodeloideae"),
            "Phoenix dactylifera" to setOf("Phoeniceae"))
        for((species,groups) in expected) {
            var node=archive.nodes.values.single { it.scientific==species }
            val lineage=mutableSetOf<String>()
            while(true) {
                lineage+=node.scientific
                node=node.parent?.let { archive.nodes[it] } ?: break
            }
            assertTrue(species,lineage.containsAll(groups))
            groups.forEach { assertTrue(it,PlantGroupKnowledge.hasArticle(it)) }
        }
    }
}
