package com.Atom2Universe.app.games.caves

import com.Atom2Universe.app.games.caves.entity.RangedProfile
import com.Atom2Universe.app.games.caves.node.CreativeTiers
import com.Atom2Universe.app.games.caves.node.FarmItems
import com.Atom2Universe.app.games.caves.node.ForgedEquipment
import com.Atom2Universe.app.games.caves.node.KitchenItems
import com.Atom2Universe.app.games.caves.node.MineralItems
import com.Atom2Universe.app.games.caves.world.MineralProgression as P
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * Tout ce qui se fabrique en Survie doit exister dans le catalogue créatif, et les armes à feu
 * restent au mode Assaut. Les tests JVM n'ont pas org.json : les fichiers sont lus tels quels.
 */
class CreativeCatalogTest {
    private val blocks = File("src/main/assets/caves/blocks").listFiles { f -> f.extension == "json" }!!
        .associate { f ->
            val text = f.readText()
            Regex("\"id\"\\s*:\\s*(\\d+)").find(text)!!.groupValues[1].toShort() to text
        }
    private val crafts = File("src/main/assets/caves/crafts").listFiles { f -> f.extension == "json" }!!
        .associate { it.name to it.readText() }

    @Test fun everyCraftResultIsAnOrdinaryCatalogItem() {
        for ((name, text) in crafts) {
            assertFalse("$name still rolls a random weapon", "result_item" in text)
            val result = requireNotNull(Regex("\"result\"\\s*:\\s*(\\d+)").find(text)?.groupValues?.get(1)?.toShort()) {
                "$name has no result"
            }
            val block = blocks[result]
            // Items built in code (minerals, armor, farm and kitchen) are all listed by creativeList().
            if (block == null) {
                assertTrue("$name makes unknown item $result", MineralItems.variant(result) != null ||
                    ForgedEquipment.template(result) != null || FarmItems.isItem(result) || KitchenItems.isItem(result))
                continue
            }
            assertFalse("$name makes a retired item", Regex("\"retired\"").containsMatchIn(block))
            assertNotEquals("$name makes a firearm", true, RangedProfile.of(result)?.firearm)
        }
    }

    @Test fun rangedWeaponsAreFixedItems() {
        for (weapon in RangedProfile.all.values) {
            assertNotNull("${weapon.type} needs a block definition", blocks[weapon.item])
            val crafted = crafts.values.any { Regex("\"result\"\\s*:\\s*${weapon.item}\\b").containsMatchIn(it) }
            assertEquals("${weapon.type}: only the non-firearms are crafted", !weapon.firearm, crafted)
        }
        assertEquals(listOf("sling", "bow", "crossbow"), RangedProfile.all.values.filter { !it.firearm }.map { it.type })
        assertEquals(12, RangedProfile.all.getValue("sling").damage)
        assertEquals(14, RangedProfile.all.getValue("bow").damage)
        assertEquals(24, RangedProfile.all.getValue("crossbow").damage)
        assertFalse("the sling carries no bonus", RangedProfile.all.getValue("sling").gear)
    }

    @Test fun tiersFoldUnderTheirLowestTier() {
        val silver = (0..P.LAST_STAGE).filter { P.metal(it) == 1 } // metal 1 = silver
            .map { MineralItems.id(it, MineralItems.Form.ARMOR) }
        assertEquals(P.TIERS, silver.size)
        val ingot = MineralItems.id(0, MineralItems.Form.INGOT)
        // A higher tier sorted first still gives its place to the whole family, headed by tier 1.
        val ordered = listOf(silver[2], ingot, silver[0], silver[5], 2020.toShort()) + silver.filter { it !in setOf(silver[0], silver[2], silver[5]) }
        val (closed, heads) = CreativeTiers.group(ordered, { it }, null)
        assertEquals(listOf(silver[0], ingot, 2020.toShort()), closed)
        assertEquals(mapOf(silver[0] to P.TIERS), heads)
        val (open, _) = CreativeTiers.group(ordered, { it }, CreativeTiers.family(silver[0]))
        assertEquals(silver + listOf(ingot, 2020.toShort()), open)
    }
}
