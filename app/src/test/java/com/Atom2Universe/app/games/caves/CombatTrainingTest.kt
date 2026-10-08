package com.Atom2Universe.app.games.caves

import com.Atom2Universe.app.games.caves.mode.TrainingHealth
import com.Atom2Universe.app.games.caves.mode.CombatTrainingMode
import com.Atom2Universe.app.games.caves.node.PlayerNode
import com.Atom2Universe.app.games.caves.node.MineralItems as M
import com.Atom2Universe.app.games.caves.world.*
import com.Atom2Universe.app.games.caves.ai.NavGrid
import com.Atom2Universe.app.games.caves.ai.SolidGrid
import org.junit.Assert.*
import org.junit.Test

class CombatTrainingTest {
    @Test fun lethalDamageOnlyKillsThePreviewAndItRecoversAfterThreeSeconds() {
        val player=PlayerNode()
        val health=TrainingHealth()
        player.damagePreview=health::damage
        assertFalse(player.applyDamage(12))
        assertEquals(38,health.hp)
        assertFalse(player.applyDamage(100))
        assertEquals(0,health.hp)
        assertEquals(1,health.defeats)
        assertTrue(player.isAlive)
        assertEquals(50,player.hp)
        player.applyDamage(100)
        assertEquals(1,health.defeats)
        health.tick(2.9f)
        assertEquals(0,health.hp)
        health.tick(.2f)
        assertEquals(50,health.hp)
        player.applyDamage(7)
        assertEquals(43,health.hp)
        assertEquals(7,health.lastDamage)
        health.reset()
        assertEquals(50,health.hp)
        assertEquals(0,health.defeats)
    }

    @Test fun ordinaryPlayersStillTakeShieldAndLethalDamage() {
        val player=PlayerNode(maxShield=10)
        player.shield=10
        assertFalse(player.applyDamage(12))
        assertEquals(48,player.hp)
        assertEquals(0,player.shield)
        assertTrue(player.applyDamage(100))
        assertFalse(player.isAlive)
    }

    @Test fun kitContainsTheThreeFirstTierMeleeWeaponsAndBothBaseRangedWeapons() {
        val weapons=CombatTrainingMode.weapons
        assertEquals(5,weapons.toSet().size)
        assertEquals(listOf(M.Form.SWORD,M.Form.HAMMER,M.Form.SPEAR),weapons.take(3).map { M.variant(it)!!.form })
        assertTrue(weapons.take(3).all { M.variant(it)!!.stage==0 })
        assertEquals(listOf<Short>(9907,9908),weapons.takeLast(2))
    }

    @Test fun allTwelveBlueprintsAreCopiedWithTheirFurnitureAndOrientation() {
        val map=CombatTrainingMap.create()
        assertEquals(UndergroundSites.Kind.entries.toList(),CombatTrainingMap.bays.map { it.kind })
        assertEquals(12,map.spawnsA.size)
        for (bay in CombatTrainingMap.bays) {
            assertTrue(bay.plan.spawnPoints.size>=bay.kind.population)
            for ((p,c) in bay.plan.blocks) {
                val x=bay.x+p.x; val y=CombatTrainingMap.FLOOR+p.y; val z=bay.z+p.z
                val actual=map.blockAt(x,y,z)
                if (c.id==AIR && actual==TORCH) continue
                assertEquals("${bay.kind}: $p",c.id,actual)
                assertEquals(c.meta,map.metaAt(x,y,z))
            }
        }
    }

    @Test fun everyEntranceSpawnAndBossIsReachableFromTheFirstGallery() {
        val map=CombatTrainingMap.create()
        // Temples have a one-block dais: use the real navigation clearance and step rules.
        val grid=NavGrid.build(map.sizeX,map.sizeY,map.sizeZ,SolidGrid { x,y,z ->
            map.blockAt(x,y,z) !in listOf(AIR,TORCH)
        })
        val seen=BooleanArray(grid.nodeCount)
        val queue=ArrayDeque<Int>()
        val start=map.spawnsA.first().let { grid.nodeAt(it.x,it.y,it.z) }
        assertTrue(start>=0)
        queue.add(start); seen[start]=true
        while(queue.isNotEmpty()) {
            val n=queue.removeFirst()
            for (edge in grid.edgeStart[n] until grid.edgeStart[n+1]) {
                val next=grid.edgeTarget[edge]
                if (!seen[next]) { seen[next]=true; queue.add(next) }
            }
        }
        fun reachable(x: Int,y: Int,z: Int) = grid.nodeAt(x,y,z).let { it>=0 && seen[it] }
        for (bay in CombatTrainingMap.bays) {
            assertTrue("Entrance ${bay.kind}",bay.entrance.let { reachable(it.x,it.y,it.z) })
            for (p in bay.plan.spawnPoints + bay.plan.boss)
                assertTrue("Unreachable ${bay.kind}: $p",reachable(bay.x+p.x,CombatTrainingMap.FLOOR+p.y,bay.z+p.z))
        }
    }
}
