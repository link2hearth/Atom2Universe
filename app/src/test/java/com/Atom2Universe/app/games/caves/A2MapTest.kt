package com.Atom2Universe.app.games.caves

import com.Atom2Universe.app.games.caves.world.A2Map
import com.Atom2Universe.app.games.caves.world.AIR
import com.Atom2Universe.app.games.caves.world.Chunk
import com.Atom2Universe.app.games.caves.world.MapPoint
import com.Atom2Universe.app.games.caves.world.MapSource
import com.Atom2Universe.app.games.caves.world.SPAWN_MARKER_A
import com.Atom2Universe.app.games.caves.world.SPAWN_MARKER_B
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

class A2MapTest {

    private val stone: Short = 2000
    private val plank: Short = 1010

    /** Petite arène : sol de pierre, un mur de planches (orientées au milieu), deux balises. */
    private fun arena(): A2Map = A2Map.capture(
        "arene", 20, 6, 12,
        blockAt = { x, y, z ->
            when {
                y == 0 -> stone
                x == 10 && y in 1..3 -> plank
                x == 2 && y == 1 && z == 2 -> SPAWN_MARKER_A
                x == 17 && y == 1 && z == 9 -> SPAWN_MARKER_B
                else -> AIR
            }
        },
        metaAt = { x, y, _ -> if (x == 10 && y == 2) 1 else 0 },
    )

    private fun roundTrip(map: A2Map): A2Map {
        val out = ByteArrayOutputStream()
        map.write(out)
        return A2Map.read(ByteArrayInputStream(out.toByteArray()))
    }

    @Test
    fun `la capture transforme les balises en points d apparition`() {
        val map = arena()
        assertEquals(listOf(MapPoint(2, 1, 2)), map.spawnsA)
        assertEquals(listOf(MapPoint(17, 1, 9)), map.spawnsB)
        assertEquals(AIR, map.blockAt(2, 1, 2))
        assertEquals(AIR, map.blockAt(17, 1, 9))
        assertEquals(plank, map.blockAt(10, 2, 5))
        assertEquals(1.toByte(), map.metaAt(10, 2, 5))
    }

    @Test
    fun `une carte relue est identique`() {
        val map = arena()
        val copy = roundTrip(map)
        assertEquals(map.name, copy.name)
        assertEquals(map.sizeX, copy.sizeX)
        assertEquals(map.sizeY, copy.sizeY)
        assertEquals(map.sizeZ, copy.sizeZ)
        assertArrayEquals(map.blocks, copy.blocks)
        assertArrayEquals(map.meta, copy.meta)
        assertEquals(map.spawnsA, copy.spawnsA)
        assertEquals(map.spawnsB, copy.spawnsB)
    }

    @Test
    fun `les grands aplats prennent tres peu de place`() {
        // 128 × 32 × 128 = un demi-million de blocs : un sol et du vide.
        val map = A2Map.capture("plaine", 128, 32, 128,
            blockAt = { _, y, _ -> if (y == 0) stone else AIR },
            metaAt = { _, _, _ -> 0 })
        val out = ByteArrayOutputStream()
        map.write(out)
        assertTrue("taille : ${out.size()} octets", out.size() < 1_000)
        assertArrayEquals(map.blocks, roundTrip(map).blocks)
    }

    @Test(expected = IOException::class)
    fun `un fichier qui n est pas une carte est refuse`() {
        A2Map.read(ByteArrayInputStream("pas une carte".toByteArray()))
    }

    @Test
    fun `la source pose la carte a sa place dans les chunks`() {
        val source = MapSource(arena(), originX = -5, originY = 64, originZ = 3)

        // Chunk (-1, 4, 0) : x monde -16..-1, y 64..79, z 0..15. La carte commence en x = -5.
        val west = Chunk(-1, 4, 0)
        source.fill(west)
        assertEquals(stone, west.blockAt(11, 0, 3))   // coin (0, 0, 0) de la carte
        assertEquals(AIR, west.blockAt(10, 0, 3))     // x = -6 : hors carte
        assertEquals(AIR, west.blockAt(11, 0, 2))     // z = 2 : hors carte

        // Chunk (0, 4, 0) : le mur de planches est en x carte 10, soit x monde 5.
        val east = Chunk(0, 4, 0)
        source.fill(east)
        assertEquals(plank, east.blockAt(5, 2, 3))
        assertEquals(1.toByte(), east.metaAt(5, 2, 3))
        assertEquals(0.toByte(), east.metaAt(5, 1, 3))

        // Chunk entièrement sous la carte : reste vide.
        val below = Chunk(0, 3, 0)
        source.fill(below)
        assertTrue(below.blocks.all { it == AIR })
    }

    @Test
    fun `le ciel est ouvert au dessus de chaque colonne et partout autour`() {
        val source = MapSource(arena(), originX = 0, originY = 64, originZ = 0)
        assertEquals(64 + 1, source.skyTopY(0, 0))    // sol seul
        assertEquals(64 + 4, source.skyTopY(10, 5))   // mur de planches jusqu'à y = 3
        assertEquals(Int.MIN_VALUE, source.skyTopY(-1, 0))
        assertEquals(Int.MIN_VALUE, source.skyTopY(0, 12))
    }

    @Test
    fun `l arene d essai a son sol, ses murs et un spawn dans chaque coin`() {
        val map = com.Atom2Universe.app.games.caves.world.BuiltinMaps.arena()
        assertEquals(100, map.sizeX)
        assertEquals(100, map.sizeZ)
        assertEquals(com.Atom2Universe.app.games.caves.world.GRASS, map.blockAt(50, 0, 50))
        assertEquals(com.Atom2Universe.app.games.caves.world.STONE, map.blockAt(0, 5, 40))
        assertEquals(com.Atom2Universe.app.games.caves.world.STONE, map.blockAt(99, 1, 99))
        assertEquals(com.Atom2Universe.app.games.caves.world.STONE, map.blockAt(50, 3, 50))   // pilier central
        assertEquals(AIR, map.blockAt(50, 4, 50))
        // Obstacles recopiés en miroir : le muret long existe dans les quatre quarts.
        assertEquals(com.Atom2Universe.app.games.caves.world.STONE, map.blockAt(20, 2, 15))
        assertEquals(com.Atom2Universe.app.games.caves.world.STONE, map.blockAt(99 - 20, 2, 15))
        assertEquals(com.Atom2Universe.app.games.caves.world.STONE, map.blockAt(20, 2, 99 - 15))
        assertEquals(com.Atom2Universe.app.games.caves.world.STONE, map.blockAt(99 - 20, 2, 99 - 15))
        assertEquals(AIR, map.blockAt(50, 1, 5))
        assertEquals(listOf(MapPoint(2, 1, 2)), map.spawnsA)
        assertEquals(listOf(MapPoint(97, 1, 97)), map.spawnsB)
        assertEquals(AIR, map.blockAt(2, 1, 2))
    }

    @Test
    fun `toute l arene est chargee, meme loin du joueur, et rien n est decharge`() {
        val source = MapSource(com.Atom2Universe.app.games.caves.world.BuiltinMaps.arena())
        // 100 blocs = chunks 0 à 6 en x et z ; hauteur 64..69 = chunk 4 seulement.
        assertEquals(com.Atom2Universe.app.games.caves.world.ChunkBounds(0, 6, 4, 4, 0, 6), source.chunkBounds())

        val world = com.Atom2Universe.app.games.caves.world.World(source = source)
        val loaded = ArrayList<Chunk>()
        // Joueur dans le coin (0, 4, 0) : le coin opposé (6, 4, 6) serait hors d'un rayon de vue réduit.
        world.updateAroundPlayer(0, 4, 0) { loaded += it }
        assertEquals(49, loaded.size)
        assertTrue(loaded.any { it.cx == 6 && it.cz == 6 })

        // Le joueur s'éloigne très loin : aucun chunk n'est déchargé, aucun nouveau n'est demandé.
        loaded.forEach { world.markGenerated(it) }
        world.updateAroundPlayer(500, 4, 500) { loaded += it }
        assertEquals(49, loaded.size)
        assertEquals(49, world.allChunks().size)
    }

    @Test
    fun `on apparait les pieds sur la case de la balise`() {
        val source = MapSource(arena(), originX = 100, originY = 64, originZ = -50)
        assertArrayEquals(floatArrayOf(102.5f, 65 + 1.62f, -47.5f), source.spawnPoint(0), 1e-4f)
        assertArrayEquals(floatArrayOf(117.5f, 65 + 1.62f, -40.5f), source.spawnPoint(1), 1e-4f)
    }
}
