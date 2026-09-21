package com.Atom2Universe.app.games.roguelike

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class DungeonDistrictsTest {
    @Test fun formatsProtectRoutesFireQuietSitesAndCombatIdentity() {
        for(format in DungeonFormat.entries) repeat(40) { seed ->
            val rng=Random(seed)
            val spec=DungeonFormats.roll(rng,format)
            val prepared=DungeonLevelFactory.create(if(seed%2==0)1 else 100,rng,spec)
            val lv=prepared.level
            val population=prepared.population
            val distance=DungeonPaths.distances(lv,lv.start)
            val exitDistance=DungeonPaths.distances(lv,prepared.layout.stairs)
            assertEquals(spec.packs,population.spawns.size)
            assertEquals(SceneryKind.BONFIRE,lv.scenery[lv.start]?.kind)
            assertTrue(lv.walkable(lv.start.x,lv.start.y))
            assertFalse(population.quietCells.isEmpty())
            assertEquals(1,population.spawns.count { it.role==PackRole.GUARD })
            for(y in 0 until lv.h)for(x in 0 until lv.w) {
                if(x==0||y==0||x==lv.w-1||y==lv.h-1)assertEquals(TileType.WALL,lv.tiles[y][x])
                if(lv.walkable(x,y))assertTrue(distance[y][x]>=0)
            }
            assertEquals(1,lv.tiles.sumOf { row->row.count { it==TileType.STAIRS_DOWN } })
            for((i,spawn) in population.spawns.withIndex()) {
                val p=spawn.pos
                assertTrue(distance[p.y][p.x]>=if(format==DungeonFormat.MICRO)8 else 12)
                assertTrue(exitDistance[p.y][p.x]>=if(spawn.role==PackRole.GUARD)3 else 7)
                assertFalse(p in population.quietCells)
                val from=DungeonPaths.distances(lv,p)
                for(other in population.spawns.take(i))assertTrue(from[other.pos.y][other.pos.x]>=3)
                assertTrue(lv.backdropAt(p) in lv.themeAt(p.x,p.y).backdrops)
            }
            if(format==DungeonFormat.MICRO) {
                val allowed=spec.regions.toSet()+if(spec.regions.single()==DungeonTheme.PIRATE)setOf(DungeonTheme.PIRATE_CABIN)else emptySet()
                assertTrue(lv.themes.all { row->row.all { it in allowed } })
            }
        }
    }
    @Test fun depthCannotChooseSizeOrGeography() {
        repeat(40) { seed ->
            val a=Random(seed);val b=Random(seed)
            val specA=DungeonFormats.roll(a);val specB=DungeonFormats.roll(b)
            assertEquals(specA,specB)
            val first=DungeonDistricts.generate(specA,a)
            val deep=DungeonDistricts.generate(specB,b)
            assertEquals(first.layout.start,deep.layout.start)
            assertEquals(first.layout.stairs,deep.layout.stairs)
            for(y in first.themes.indices) {
                assertArrayEquals(first.themes[y],deep.themes[y])
                assertArrayEquals(first.layout.tiles[y],deep.layout.tiles[y])
            }
        }
    }
    @Test fun restRequiresStandingOnStartingFire() {
        val g=RoguelikeGame(rng=Random(7))
        g.level.packs.clear()
        val start=g.playerPos
        val step=listOf(Pos(1,0),Pos(-1,0),Pos(0,1),Pos(0,-1)).first { g.level.canStep(start,it.x,it.y) }
        g.hero.hp=g.hero.maxHp/2
        g.tryMove(step.x,step.y)
        assertFalse(g.canRest());assertFalse(g.rest())
        g.tryMove(-step.x,-step.y)
        assertTrue(g.onCampTile());assertTrue(g.rest())
        assertEquals(g.hero.maxHp,g.hero.hp)
        assertFalse(g.canRest())
    }
}
