package com.Atom2Universe.app.games.caves.world

import com.Atom2Universe.app.games.caves.node.BlockRegistry
import com.Atom2Universe.app.games.caves.node.BlockDef
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class WaterActivationTest {
    // Initialiser uniquement les deux types d'eau : pas d'AssetManager Android en test JVM.
    private lateinit var waterTable: BooleanArray
    private var previousSource = false
    private var previousFlow = false

    @Before fun registerWater() {
        val field = BlockRegistry::class.java.getDeclaredField("waterTable").apply { isAccessible = true }
        waterTable = field.get(BlockRegistry) as BooleanArray
        previousSource = waterTable[WATER.toInt()]
        previousFlow = waterTable[WATER_FLOW.toInt()]
        waterTable[WATER.toInt()] = true
        waterTable[WATER_FLOW.toInt()] = true
    }

    @After fun restoreRegistry() {
        waterTable[WATER.toInt()] = previousSource
        waterTable[WATER_FLOW.toInt()] = previousFlow
    }

    /** Publication normale d'un chunk préparé, sans bruit de terrain ni API Android. */
    @Suppress("UNCHECKED_CAST")
    private fun load(world: World, cx: Int, cy: Int, cz: Int, fill: (Chunk) -> Unit): Chunk {
        val chunks = World::class.java.getDeclaredField("chunks").apply { isAccessible = true }
            .get(world) as java.util.concurrent.ConcurrentHashMap<Long, Chunk>
        val chunk = Chunk(cx, cy, cz)
        chunk.blocks.fill(STONE)
        fill(chunk)
        chunks[world.chunkKey(cx, cy, cz)] = chunk
        world.markGenerated(chunk)
        return chunk
    }

    private fun settle(world: World) { repeat(300) { world.tickWater() } }

    @Test fun savedWaterfallResumesWithoutEditingABlock() {
        val world = World()
        val chunk = load(world, 0, 5, 0) {
            for (y in 1..13) it.setBlock(8, y, 8, AIR)
            it.setBlock(8, 14, 8, WATER_FLOW)
            it.setBlock(8, 15, 8, WATER)
        }
        settle(world)
        assertEquals(WATER_FLOW, chunk.blockAt(8, 1, 8))
        assertEquals(0, world.waterFlowLevel(8, 81, 8))
    }

    @Test fun savedFlowWithoutSupplyDrainsOnLoad() {
        val world = World()
        val chunk = load(world, 0, 5, 0) { it.setBlock(8, 8, 8, WATER_FLOW) }
        settle(world)
        assertEquals(AIR, chunk.blockAt(8, 8, 8))
    }

    @Test fun closingTheFeedDrainsTheWaterfallAndItsLowerStream() {
        val world = World()
        val chunk = load(world, 0, 5, 0) {
            it.setBlock(7, 10, 8, WATER)
            for (y in 2..10) it.setBlock(8, y, 8, AIR)
            for (x in 9..12) it.setBlock(x, 2, 8, AIR)
        }
        settle(world)
        assertEquals(WATER_FLOW, chunk.blockAt(8, 2, 8))
        assertEquals(WATER_FLOW, chunk.blockAt(11, 2, 8))
        // Like the reported sluice: keep the upstream water, place a solid block across its outlet.
        world.setBlock(8, 90, 8, CLAY)
        settle(world)
        assertEquals(WATER, chunk.blockAt(7, 10, 8))
        for (y in 2..9) assertEquals("Hanging waterfall at $y", AIR, chunk.blockAt(8, y, 8))
        for (x in 9..12) assertEquals("Disconnected stream at $x", AIR, chunk.blockAt(x, 2, 8))
        world.setBlock(8, 90, 8, AIR)
        settle(world)
        assertEquals("Reopening the sluice resumes the waterfall", WATER_FLOW, chunk.blockAt(8, 2, 8))
    }

    @Test fun meetingSourcesRefillSurfaceWithoutManufacturingPermanentWater() {
        val world = World()
        val chunk = load(world, 0, 5, 0) {
            it.setBlock(7, 8, 8, WATER)
            it.setBlock(8, 8, 8, AIR)
            it.setBlock(9, 8, 8, WATER)
        }
        settle(world)
        assertEquals(WATER_FLOW, chunk.blockAt(8, 8, 8))
        assertEquals(0, world.waterFlowLevel(8, 88, 8))
        world.setBlock(7, 88, 8, STONE)
        world.setBlock(9, 88, 8, STONE)
        settle(world)
        assertEquals("Refilled water must drain when both feeds are blocked", AIR, chunk.blockAt(8, 8, 8))
    }

    @Test fun cutWaterfallDrainsAcrossVerticalChunkBoundary() {
        val world = World()
        val lower = load(world, 0, 4, 0) { for (y in 2..15) it.setBlock(8, y, 8, AIR) }
        val upper = load(world, 0, 5, 0) {
            for (y in 0..8) it.setBlock(8, y, 8, AIR)
            it.setBlock(7, 8, 8, WATER)
        }
        settle(world)
        assertEquals(WATER_FLOW, lower.blockAt(8, 2, 8))
        world.setBlock(8, 88, 8, CLAY)
        settle(world)
        for (y in 0..7) assertEquals(AIR, upper.blockAt(8, y, 8))
        for (y in 2..15) assertEquals(AIR, lower.blockAt(8, y, 8))
    }

    @Test fun newAirChunkWakesRiverAcrossHorizontalBoundaryInEitherOrder() {
        for (waterFirst in listOf(true, false)) {
            val world = World()
            fun river() = load(world, 0, 5, 0) { it.setBlock(15, 8, 8, WATER) }
            fun ravine() = load(world, 1, 5, 0) {
                for (y in 1..8) it.setBlock(0, y, 8, AIR)
            }
            val ravine = if (waterFirst) {
                river(); settle(world); ravine()
            } else {
                val c = ravine(); settle(world); river(); c
            }
            settle(world)
            assertEquals(WATER_FLOW, ravine.blockAt(0, 1, 8))
        }
    }

    @Test fun savedFlowWaitsForSupplyAboveThenContinuesDown() {
        val world = World()
        val lower = load(world, 0, 5, 0) {
            for (y in 1..14) it.setBlock(8, y, 8, AIR)
            it.setBlock(8, 15, 8, WATER_FLOW)
        }
        settle(world)
        assertEquals(WATER_FLOW, lower.blockAt(8, 15, 8))
        load(world, 0, 6, 0) { it.setBlock(8, 0, 8, WATER) }
        settle(world)
        assertEquals(WATER_FLOW, lower.blockAt(8, 1, 8))
    }

    @Suppress("UNCHECKED_CAST")
    @Test fun replaceableGrassDoesNotSealTheEdgeOfARiver() {
        val defs = BlockRegistry::class.java.getDeclaredField("defs").apply { isAccessible = true }
            .get(BlockRegistry) as MutableMap<Short, BlockDef>
        val decorations = BlockRegistry::class.java.getDeclaredField("decorationTable").apply { isAccessible = true }
            .get(BlockRegistry) as BooleanArray
        val id = GRASS_TUFT_SHORT
        val previous = defs[id]
        val wasDecoration = decorations[id.toInt()]
        defs[id] = BlockDef(id = id, name = "test_grass", textureTop = "", textureSide = "", textureBottom = "",
            textureFront = null, textureSideGrass = null, textureSideSand = null, textureSideSnow = null,
            color = 0, hardness = .1f, decoration = true, transparent = false, water = false,
            falling = false, waterlogged = false, lightEmission = 0, drop = "", creativeTab = "",
            spriteMargin = 0f, spriteHeight = .5f, replaceable = true)
        decorations[id.toInt()] = true
        try {
            val world = World()
            val chunk = load(world, 0, 5, 0) {
                it.setBlock(8, 8, 8, WATER)
                it.setBlock(9, 8, 8, id)
                for (y in 1..7) it.setBlock(9, y, 8, AIR)
            }
            chunk.meshDirty = false
            settle(world)
            assertEquals(WATER_FLOW, chunk.blockAt(9, 8, 8))
            assertEquals(WATER_FLOW, chunk.blockAt(9, 1, 8))
            assertTrue("Le sprite de l'herbe doit aussi disparaître", chunk.meshDirty)
        } finally {
            if (previous == null) defs.remove(id) else defs[id] = previous
            decorations[id.toInt()] = wasDecoration
        }
    }
}
