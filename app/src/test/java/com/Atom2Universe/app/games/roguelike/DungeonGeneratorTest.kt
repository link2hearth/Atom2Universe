package com.Atom2Universe.app.games.roguelike

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/**
 * Le générateur doit toujours produire un étage entièrement relié, avec l'escalier loin
 * du départ. Écrit aussi quelques plans en texte dans build/dungeon-maps.txt pour les voir.
 */
class DungeonGeneratorTest {

    private val w = RoguelikeGame.MAP_W
    private val h = RoguelikeGame.MAP_H

    @Test
    fun everyFloorIsConnected() {
        val stats = StringBuilder()
        var totalRooms = 0; var totalDeadEnds = 0; var totalStairs = 0; var roomCells = 0; var floorCells = 0
        val runs = 500
        repeat(runs) { seed ->
            val l = DungeonGenerator.generate(w, h, Random(seed))
            val dist = DungeonGenerator.distances(l.tiles, l.start)
            for (y in 0 until h) for (x in 0 until w) if (l.tiles[y][x] != TileType.WALL) {
                floorCells++
                if (l.rooms.any { it.contains(Pos(x, y)) }) roomCells++
                assertTrue("graine $seed : case ($x,$y) inatteignable", dist[y][x] >= 0)
            }
            for (x in 0 until w) { assertEquals(TileType.WALL, l.tiles[0][x]); assertEquals(TileType.WALL, l.tiles[h - 1][x]) }
            for (y in 0 until h) { assertEquals(TileType.WALL, l.tiles[y][0]); assertEquals(TileType.WALL, l.tiles[y][w - 1]) }
            totalRooms += l.rooms.size; totalDeadEnds += l.deadEnds.size; totalStairs += dist[l.stairs.y][l.stairs.x]
        }
        stats.appendLine("Moyennes sur $runs étages : ${totalRooms / runs} salles, ${totalDeadEnds / runs} culs-de-sac, " +
            "escalier à ${totalStairs / runs} pas, ${100 * roomCells / floorCells} % du sol dans les salles")

        val maps = StringBuilder(stats)
        for (seed in listOf(1, 2, 3, 4)) {
            val l = DungeonGenerator.generate(w, h, Random(seed))
            maps.appendLine().appendLine("── graine $seed ──")
            for (y in 0 until h) {
                for (x in 0 until w) maps.append(when {
                    Pos(x, y) == l.start -> '@'
                    l.tiles[y][x] == TileType.STAIRS_DOWN -> '>'
                    l.tiles[y][x] == TileType.WALL -> '#'
                    else -> '.'
                })
                maps.appendLine()
            }
        }
        File("build/dungeon-maps.txt").writeText(maps.toString())
        println(maps)
    }
}
