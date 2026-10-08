package com.Atom2Universe.app.games.roguelike

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class DungeonGeneratorTest {
    @Test fun mazeIsConnectedAtEveryFormatBoundary() {
        for(size in listOf(17,23,25,39,45,69,71,99)) repeat(12) { seed ->
            val layout=DungeonGenerator.generate(size,size,Random(seed),outdoor=seed%2==0)
            val distances=DungeonGenerator.distances(layout.tiles,layout.start)
            for(y in 0 until size)for(x in 0 until size) {
                if(x==0||y==0||x==size-1||y==size-1)assertEquals(TileType.WALL,layout.tiles[y][x])
                else if(layout.tiles[y][x]!=TileType.WALL)assertTrue(distances[y][x]>=0)
            }
            assertTrue(distances[layout.stairs.y][layout.stairs.x]>0)
            assertEquals(1,layout.tiles.sumOf { row->row.count { it==TileType.STAIRS_DOWN } })
        }
    }
}
