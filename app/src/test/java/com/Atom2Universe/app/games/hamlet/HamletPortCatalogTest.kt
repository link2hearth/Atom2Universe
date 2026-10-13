package com.Atom2Universe.app.games.hamlet

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class HamletPortCatalogTest {
    @Test fun `les 595 references du prototype existent dans le catalogue natif`() {
        assertEquals(595, HamletPortCatalog.sourceIds.size)
        val missing = HamletPortCatalog.sourceIds.filter { (HamletPortCatalog.aliases[it] ?: it) !in Hamlet.byId }
        assertTrue("Missing original entries: $missing", missing.isEmpty())
        assertEquals(Hamlet.defs.size, Hamlet.byId.size)
        assertEquals(setOf("castle", "farm", "magicfarm"), HamletPortCatalog.aliases.keys)
    }

    @Test fun `catalogue complet accessible sans boutique payante et chantiers au plus deux heures`() {
        for (def in Hamlet.defs) {
            assertTrue("level ${def.id}", def.lvl in 1..40)
            assertTrue("build ${def.id}", def.build in 0..7200)
            assertTrue("cost ${def.id}", def.cost >= 0 && def.gems >= 0)
            assertTrue("footprint ${def.id}", def.w in 1..6 && def.d in 1..6)
            if (def.cat == Cat.SHOP) assertTrue("production ${def.id}", def.job > 0 && def.pay > 0)
        }
        val game = HamletGame().apply { startNew(true) }
        for (id in HamletPortCatalog.sourceIds) {
            val definition = Hamlet.byId.getValue(HamletPortCatalog.aliases[id] ?: id)
            assertTrue(game.canAfford(definition)); assertTrue(game.ulvl() >= definition.lvl)
        }
    }

    @Test fun `les sols historiques conservent leurs codes et les ajouts ne les deplacent pas`() {
        assertEquals(1, Hamlet.pathCode["road"])
        assertEquals(2, Hamlet.pathCode["cobble"])
        assertEquals(3, Hamlet.pathCode["river"])
        assertEquals(4, Hamlet.pathCode["bridge"])
        assertEquals(5, Hamlet.pathCode["brickroad"])
        assertEquals(15, Hamlet.pathCode["sandpath"])
        for (id in listOf("lavaflow", "icepath", "sandpatch", "mosspath", "rosepath")) {
            assertTrue(id in Hamlet.pathCode)
            assertEquals(id, Hamlet.pathById[Hamlet.pathCode[id]])
        }
    }

    @Test fun `references maritime fortifications et animaux ont leurs familles graphiques dediees`() {
        for (id in listOf("wGalleon", "wWhale", "wKoi", "wHouseboat", "gatehouse", "keep", "portcullis", "ctowerBlue"))
            assertEquals(id, "coastal", Hamlet.byId.getValue(id).look.kind)
        for (id in HamletPortCatalog.sourceIds.filter { it.startsWith("an_") }) assertEquals("animal", Hamlet.byId.getValue(id).look.kind)
        assertEquals(24, HamletPortCatalog.sourceIds.count { it.startsWith("an_") })
        for (id in HamletPortCatalog.sourceIds.filter { it.matches(Regex("w[A-Z].*")) }) assertTrue(id, Hamlet.byId.getValue(id).onWater)
        assertTrue(Hamlet.byId.getValue("castlewall").link)
        assertNotEquals(Hamlet.byId.getValue("statueGolden").look.a, Hamlet.byId.getValue("statueJade").look.a)
        assertNotEquals(Hamlet.byId.getValue("hv0").look.kind, Hamlet.byId.getValue("hv1").look.kind)
    }

    @Test fun `tous les noms et produits sont traduits en anglais et en francais`() {
        fun strings(locale: String): Set<String> {
            val folder = listOf(File("src/main/res/$locale"), File("app/src/main/res/$locale")).first { it.isDirectory }
            val names = HashSet<String>()
            val parser = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            folder.listFiles()!!.filter { it.name.startsWith("strings_hamlet") && it.extension == "xml" }.forEach { file ->
                val nodes = parser.parse(file).getElementsByTagName("string")
                for (i in 0 until nodes.length) names.add(nodes.item(i).attributes.getNamedItem("name").nodeValue)
            }
            return names
        }
        val en = strings("values"); val fr = strings("values-fr")
        for (def in Hamlet.defs) {
            val name = "hamlet_n_${def.id}"
            assertTrue("EN $name", name in en); assertTrue("FR $name", name in fr)
            if (def.cat == Cat.SHOP) {
                val product = "hamlet_n_p_${def.id}"
                assertTrue("EN $product", product in en); assertTrue("FR $product", product in fr)
            }
        }
    }
}
