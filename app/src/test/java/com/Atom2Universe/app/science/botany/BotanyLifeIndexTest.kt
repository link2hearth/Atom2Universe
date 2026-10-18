package com.Atom2Universe.app.science.botany

import com.Atom2Universe.app.science.parentes.LifeArchive
import com.Atom2Universe.app.science.parentes.LifeTree
import com.Atom2Universe.app.R
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class BotanyLifeIndexTest {
    private val archive=LifeArchive.read(File("src/main/assets/parentes/runtime.bin").inputStream())
    private val tree=LifeTree(archive.nodes,archive.root,emptySet())
    private fun index(names:Map<String,String> = emptyMap())=BotanyLifeIndex(tree,names)

    @Test fun chaquePlanteDuSousArbrePossedeUneReferenceUnique() {
        val index=index()
        assertTrue(index.entries.isNotEmpty())
        assertEquals(tree.descendants(BotanyLifeIndex.GREEN_ROOT).map{it.id}.toSet(),index.entries.map{it.nodeId}.toSet())
        assertEquals(index.entries.size,index.entries.map{it.nodeId}.distinct().size)
        assertTrue(index.entries.any{"ott56610" in it.lineage})
        assertTrue(index.entries.any{"ott56610" !in it.lineage})
        for(entry in index.entries) {
            assertTrue(tree.nodes.getValue(entry.nodeId).species)
            assertEquals(entry,index.forNode(entry.nodeId))
            assertTrue(entry.lineage.all{it in tree.nodes})
            assertTrue(entry.scientific.isNotBlank())
        }
        assertTrue(index.entries.any{it.scientific=="Marchantia polymorpha"})
        assertTrue(index.entries.any{it.scientific=="Pteridium aquilinum"})
        assertTrue(index.entries.any{it.scientific=="Chlamydomonas reinhardtii"})
        assertFalse(index.entries.any{it.scientific=="Homo sapiens" || it.scientific=="Saccharomyces cerevisiae"})
    }

    @Test fun correspondancesExactesEtNavigationDansLesDeuxSens() {
        val index=index(mapOf("robur" to "Quercus robur","other_oak" to "Quercus petraea","absent_oak" to "Quercus fictitia","helianthus" to "Helianthus annuus"))
        assertEquals("robur",index.forNode("ott239659")?.illustratedId)
        assertEquals("ott239659",index.forIllustrated("robur")?.nodeId)
        assertEquals("ott515712",index.forIllustrated("helianthus")?.nodeId)
        val otherOak=requireNotNull(index.forIllustrated("other_oak"))
        assertNotEquals("ott239659",otherOak.nodeId)
        assertEquals("Quercus petraea",otherOak.scientific)
        assertEquals("other_oak",index.forNode(otherOak.nodeId)?.illustratedId)
        assertNull(index.forIllustrated("absent_oak"))
        assertNull(index.forNode("unknown"))
        assertNull(index.forNode(tree.root))
        assertNull(index.forNode("ott309263")?.illustratedId)
        assertFalse(index.isPlant("unknown"))
        // Ambiguous duplicate scientific names must not silently choose a profile.
        assertNull(index(mapOf("one" to "Quercus robur","two" to "Quercus robur")).forNode("ott239659")?.illustratedId)
    }

    @Test fun lesNoticesEtNomsExistentDansLesDeuxLangues() {
        val index=index()
        for(locale in listOf("values","values-fr")) {
            val nodes=DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(File("src/main/res/$locale/strings_parentes.xml")).getElementsByTagName("string")
            val keys=(0 until nodes.length).map{nodes.item(it).attributes.getNamedItem("name").nodeValue}.toSet()
            for(entry in index.entries) {
                val label=tree.nodes.getValue(entry.nodeId).labelKey
                if(label.isNotBlank())assertTrue("$locale/$label",label in keys)
                archive.notes[entry.nodeId]?.let{assertTrue("$locale/${it.key}",it.key in keys)}
            }
        }
    }

    @Test fun toutesLesCorrespondancesDuCatalogueReelSontReversibles() {
        val texts=File("src/main/res/values").listFiles { file -> file.name.startsWith("strings_botany") && file.extension=="xml" }
            .orEmpty().flatMap { file ->
                val nodes=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("string")
                (0 until nodes.length).map { nodes.item(it).let { n -> n.attributes.getNamedItem("name").nodeValue to n.textContent } }
            }.toMap()
        val resourceNames=R.string::class.java.fields.associate{it.getInt(null) to it.name}
        val names=BotanyCatalog.species.associate{it.id to texts.getValue(resourceNames.getValue(it.latin))}
        val index=index(names)
        val matches=index.entries.filter{it.illustratedId!=null}
        assertEquals(matches.size,matches.map{it.illustratedId}.distinct().size)
        val unambiguousTerminalProfiles=names.entries.groupBy{BotanyLifeIndex.treeScientificName(it.value)}
            .filter{(scientific,profiles)->profiles.size==1 && index.entries.count{entry->
                tree.nodes.getValue(entry.nodeId).species && BotanyLifeIndex.treeScientificName(entry.scientific)==scientific
            }==1}.values.flatten().map{it.key}.toSet()
        assertEquals(unambiguousTerminalProfiles,matches.map{it.illustratedId!!}.toSet())
        for(entry in matches) {
            val atlasName=names.getValue(entry.illustratedId!!)
            assertEquals(BotanyLifeIndex.treeScientificName(entry.scientific),BotanyLifeIndex.treeScientificName(atlasName))
            assertEquals(entry,index.forIllustrated(entry.illustratedId))
        }
        for(species in PriorityTreeCatalogHerbariumLot38.species) {
            val entry=requireNotNull(index.forIllustrated(species.id)) { "Missing living-tree link: ${species.id}" }
            assertEquals(species.id,index.forNode(entry.nodeId)?.illustratedId)
        }
        for(species in PriorityTreeCatalogHerbariumLot39.species + PriorityTreeCatalogHerbariumLot40.species + PriorityTreeCatalogHerbariumLot41.species + PriorityTreeCatalogHerbariumLot42.species + PriorityTreeCatalogHerbariumLot43.species + PriorityTreeCatalogHerbariumLot44.species + PriorityTreeCatalogHerbariumLot45.species + PriorityTreeCatalogHerbariumLot46.species) {
            val entry=requireNotNull(index.forIllustrated(species.id)) { "Missing living-tree link: ${species.id}" }
            assertEquals(species.id,index.forNode(entry.nodeId)?.illustratedId)
        }
    }
}
