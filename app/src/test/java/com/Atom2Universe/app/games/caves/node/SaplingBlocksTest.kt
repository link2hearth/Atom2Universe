package com.Atom2Universe.app.games.caves.node

import com.Atom2Universe.app.games.caves.world.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.DataOutputStream

class SaplingBlocksTest {
    private fun template(id: Short, tags: Set<String> = emptySet()) = BlockDef(
        id,"test","test","test","test",null,null,null,null,0,1f,
        false,false,false,false,false,0,"","nature",.1f,.9f,tags=tags)

    @Suppress("UNCHECKED_CAST")
    @Test fun everySaplingIsInCreativeAndObeysPlantSupportRules() {
        val defs = BlockRegistry::class.java.getDeclaredField("defs").apply { isAccessible = true }
            .get(BlockRegistry) as MutableMap<Short,BlockDef>
        val previous = defs.toMap()
        try {
            defs[DIRT] = template(DIRT,setOf("soil"))
            defs[GRASS] = template(GRASS,setOf("soil"))
            defs[STONE] = template(STONE)
            val saplings = SaplingBlocks.definitions(template(WHEAT1))
            for (def in saplings) {
                assertFalse("ID collision ${def.id}",defs.containsKey(def.id))
                defs[def.id] = def
            }
            for (def in saplings) {
                assertTrue(def.placeable && def.decoration)
                assertEquals(def.name,def.drop)
                assertTrue(BlockRegistry.creativeList().contains(def.id))
                for (ground in listOf(DIRT,GRASS,STONE,AIR,WATER,SAND)) {
                    assertEquals("${def.name} on $ground",ground == DIRT || ground == GRASS,
                        BlockPlacement.supported(def.id,0,1,0) { _,y,_ -> if (y == 0) ground else AIR })
                }
                for (locale in listOf("values","values-fr")) {
                    val strings = File("src/main/res/$locale/strings_caves.xml").readText()
                    assertTrue("Missing $locale/${def.name}",strings.contains("name=\"cave_block_${def.name}\""))
                }
            }
            val assetIds = File("src/main/assets/caves/blocks").listFiles()!!.filter { it.extension == "json" }
                .mapNotNull { Regex("\"id\"\\s*:\\s*(\\d+)").find(it.readText())?.groupValues?.get(1)?.toInt() }.toSet()
            assertFalse(saplings.any { it.id.toInt() in assetIds })
            assertTrue(TreeSpecies.types.containsAll(ShowcaseMap.treeTypes))
            assertTrue(TreeSpecies.types.containsAll(RegionalBiomes.variants.map { it.tree }.filter { it != "none" }))
        } finally { defs.clear(); defs.putAll(previous) }
    }

    @Test fun transparentSpritesAreDistinct() {
        val hashes = hashSetOf<Int>()
        for ((i,type) in TreeSpecies.types.withIndex()) {
            val pixels = SaplingBlocks.pixels(i)
            assertEquals(1024,pixels.size)
            assertTrue(type,pixels.count { it != 0 } in 50..600)
            assertTrue(type,hashes.add(pixels.contentHashCode()))
            val output = File("build/reports/saplings/$type.argb").apply { requireNotNull(parentFile).mkdirs() }
            DataOutputStream(output.outputStream()).use { data -> pixels.forEach(data::writeInt) }
        }
    }
}
