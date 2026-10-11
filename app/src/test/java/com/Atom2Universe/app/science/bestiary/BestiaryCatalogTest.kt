package com.Atom2Universe.app.science.bestiary

import com.Atom2Universe.app.R
import com.Atom2Universe.app.science.parentes.LifeArchive
import com.Atom2Universe.app.science.parentes.LifeTree
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.net.URI
import javax.xml.parsers.DocumentBuilderFactory

class BestiaryCatalogTest {
    private val archive = LifeArchive.read(File("src/main/assets/parentes/runtime.bin").inputStream())
    private val tree = LifeTree(archive.nodes, archive.root, emptySet())
    private val catalog = AnimalCatalog.species
    private fun texts(locale: String): Map<String, String> {
        val files = File("src/main/res/$locale").listFiles()!!.filter { it.name.startsWith("strings_bestiary") }
        return files.flatMap { file ->
            val strings = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("string")
            (0 until strings.length).map { strings.item(it).let { node -> node.attributes.getNamedItem("name").nodeValue to node.textContent } }
        }.toMap()
    }

    @Test fun centFichesCompletesTraduitesEtLieesSansAmbiguite() {
        assertTrue("At least 100 complete animal profiles", catalog.size >= 100)
        assertEquals(catalog.size, catalog.map { it.id }.distinct().size)
        assertEquals(catalog.size, catalog.map { it.scientific }.distinct().size)
        val names = R.string::class.java.fields.associate { it.getInt(null) to it.name }
        val en = texts("values"); val fr = texts("values-fr")
        assertEquals(en.keys, fr.keys)
        val index = BestiaryLifeIndex(tree, catalog.associate { it.id to it.scientific })
        for (animal in catalog) {
            val node = requireNotNull(index.forSpecies(animal.id)) { "Missing tree link: ${animal.scientific}" }
            assertEquals(animal.scientific, tree.nodes.getValue(node).scientific)
            assertEquals(animal.id, index.forNode(node))
            assertTrue(animal.group in setOf("mammals", "birds", "reptiles", "amphibians", "fish", "invertebrates"))
            for (resource in listOf(animal.name, animal.latin, animal.description, animal.identification, animal.habitat, animal.diet, animal.behavior, animal.sourceTitle)) {
                val key = names.getValue(resource)
                assertFalse("EN/$key", en.getValue(key).isBlank())
                assertFalse("FR/$key", fr.getValue(key).isBlank())
            }
            val latinKey = names.getValue(animal.latin)
            assertEquals(animal.scientific, en.getValue(latinKey))
            assertEquals(animal.scientific, fr.getValue(latinKey))
            val source = URI(animal.sourceUrl)
            assertEquals("https", source.scheme)
            assertFalse(source.host.isNullOrBlank())
            assertTrue("Species-specific source: ${animal.id}", source.path.length > 2)
        }
        val lion = catalog.single { it.scientific == "Panthera leo" }
        assertEquals(lion.id, index.forNode(tree.species.single { it.scientific == "Panthera leo" }.id))
    }

    @Test fun unGenreOuUneCorrespondanceAmbigueNeDonnePasDeFiche() {
        val lion = tree.species.single { it.scientific == "Panthera leo" }
        val index = BestiaryLifeIndex(tree, mapOf("lion" to "Panthera leo", "tiger" to "Panthera tigris", "genus" to "Panthera"))
        assertEquals("lion", index.forNode(lion.id))
        assertNull(index.forSpecies("genus"))
        assertNull(index.forNode("missing"))
        assertNull(index.forNode(tree.root))
        assertNull(BestiaryLifeIndex(tree, mapOf("a" to "Panthera leo", "b" to "Panthera leo")).forNode(lion.id))
    }
}
