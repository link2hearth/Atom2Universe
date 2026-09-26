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
    private fun load(world: World, cx: Int, cy: Int, cz: Int, fill: (Chunk) -> Unit): Chunk {
        var result: Chunk? = null
        world.updateAroundPlayer(cx, cy, cz, maxNewChunks = 1) { chunk ->
            assertEquals(cx, chunk.cx); assertEquals(cy, chunk.cy); assertEquals(cz, chunk.cz)
            chunk.blocks.fill(STONE)
            fill(chunk)
            world.markGenerated(chunk)
            result = chunk
        }
        return checkNotNull(result)
    }

    private fun settle(world: World) { repeat(300) { world.tickWater() } }

    @Test fun savedWaterfallResumesWithoutEditingABlock() {
        val world = World(terrainVersion = 1)
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
        val world = World(terrainVersion = 1)
        val chunk = load(world, 0, 5, 0) { it.setBlock(8, 8, 8, WATER_FLOW) }
        settle(world)
        assertEquals(AIR, chunk.blockAt(8, 8, 8))
    }

    @Test fun newAirChunkWakesRiverAcrossHorizontalBoundaryInEitherOrder() {
        for (waterFirst in listOf(true, false)) {
            val world = World(terrainVersion = 1)
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
        val world = World(terrainVersion = 1)
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
            val world = World(terrainVersion = 1)
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
