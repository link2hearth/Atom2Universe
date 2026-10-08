package com.Atom2Universe.app.games.caves

import com.Atom2Universe.app.games.caves.entity.*
import com.Atom2Universe.app.games.caves.node.MobDef
import com.Atom2Universe.app.games.caves.node.PlayerNode
import com.Atom2Universe.app.games.caves.render.EnemyModels
import com.Atom2Universe.app.games.caves.world.*
import org.junit.Assert.*
import org.junit.Test

class EnemyCombatTest {
    @Test fun bossFocusRequiresTheBodyBeforeTheFirstWall() {
        val boss=enemy("ogre").apply { isBoss=true }
        fun focus(x: Double=10.0,limit: Double=24.0)=EnemyFocus.mob(listOf(boss),x,2.0,0.0,0.0,0.0,1.0,limit)
        assertNotNull(focus())
        assertNull(focus(limit=9.0))
        assertNull(focus(x=13.0))
        boss.hp-=5
        assertEquals(boss.hp,focus()!!.hp)
        boss.hp=0
        assertNull(focus())
    }
    @Test fun nearestMobSuppliesItsOwnInfo() {
        val boss=enemy("ogre").apply { isBoss=true }
        val front=enemy("zombie").apply { z=5.0 }
        assertEquals("zombie",EnemyFocus.mob(listOf(boss,front),10.0,2.0,0.0,0.0,0.0,1.0,24.0)!!.model)
        front.hp=0
        assertNotNull(EnemyFocus.mob(listOf(boss,front),10.0,2.0,0.0,0.0,0.0,1.0,24.0))
    }
    private fun enemy(kind: String) = Enemy(0,MobDef(kind,4,1,0f,2.0,16.0,1.2f,.5f,1f,
        1.0,1.0,0,0f,listOf("any"),kind,0,1f,"aggressive",true,1),10.0,1.0,10.0).apply {
        exploration=true; hp=maxHp; onGround=true
    }
    private class Target(override var x: Double=10.0,override var y: Double=2.62,override var z: Double=11.8):EnemyTarget {
        override val eyeDrop=0.0
        override val node=PlayerNode()
        override var hitCooldown=0f
    }
    private fun manager(e: Enemy,t: Target,wallAtZ: Int?=null): EnemyManager {
        val blocks=ShortArray(32*16*32)
        for(i in 0 until 32*32) blocks[i]=STONE
        if(wallAtZ!=null) for(x in 0 until 32) for(y in 1..5) blocks[x+32*(wallAtZ+32*y)]=STONE
        val map=A2Map("test",32,16,32,blocks,ByteArray(blocks.size),emptyList(),emptyList())
        val world=World(source=MapSource(map,originY=0))
        for(x in 0..1) for(z in 0..1) world.pregenerateChunk(x,0,z)
        return EnemyManager(world).apply {
            targets=listOf(t);spawnManager.enabled=false;explorationCombat=true
            clearSight={ _,_,_,_,_,_ -> true };enemies.add(e)
        }
    }
    private fun prime(e: Enemy,shape: AttackShape,followUps: Int=0) {
        e.attack=EnemyAttack(shape,.1f,.28f,3.0,followUps=followUps).apply {
            x=e.x;y=e.y;z=e.z;targetX=10.0;targetY=2.0;targetZ=15.0
        }
        e.attackWindup=.1f
    }

    @Test fun eachUndergroundSpeciesHasThreeDifferentMeleeAttacks() {
        for(kind in UndergroundSites.Kind.entries) {
            val e=enemy(kind.mob)
            val attacks=(0..2).map { e.attackSequence=it;EnemyAttack.forEnemy(e,1.5) }
            assertEquals(kind.mob,3,attacks.map { it.shape }.toSet().size)
            assertTrue(attacks.all { it.windup in .15f.. .7f && it.recovery<=.5f })
        }
    }
    @Test fun doubleStrikeDealsTwoSeparateHitsAndCanBeInterrupted() {
        for(interrupt in listOf(false,true)) {
            val e=enemy("goblin");val target=Target();val manager=manager(e,target)
            var hits=0
            manager.meleeImpact={ _,_,_,_ -> hits++ }
            prime(e,AttackShape.DOUBLE,1)
            repeat(3) { manager.update(.05f) }
            assertEquals(1,hits)
            if(interrupt) e.staggerTimer=.8f
            repeat(8) { manager.update(.05f) }
            assertEquals(if(interrupt) 1 else 2,hits)
        }
    }
    @Test fun bowVolleyEmitsNothingDuringPreparationAndTwoShotsAfterRelease() {
        val e=enemy("skeleton");val t=Target(z=16.0);val m=manager(e,t)
        var shots=0;m.rangedImpact={ _,_,_,_ -> shots++ }
        prime(e,AttackShape.ARROW,1)
        m.update(.04f);assertEquals(0,shots)
        m.update(.07f);assertEquals(1,shots)
        repeat(8) { m.update(.05f) }
        assertEquals(2,shots)
    }
    @Test fun touchingSlimeHurtsWithoutAnAnnouncedAttackAndDoesNotTickEveryFrame() {
        val e=enemy("slime");val t=Target(z=10.4);val m=manager(e,t)
        e.attackCooldown=100f
        var hits=0;m.meleeImpact={ _,_,_,_ -> hits++ }
        m.update(.01f);assertEquals(1,hits)
        repeat(20) { m.update(.01f) }
        assertEquals(1,hits)
    }
    @Test fun chargeMovesItsBodyAndStopsAtSolidWalls() {
        for(wall in listOf(false,true)) {
            val e=enemy("ogre");val t=Target(z=12.0);val m=manager(e,t,if(wall) 11 else null)
            m.clearSight={ _,_,_,_,_,_ -> !wall }
            var hits=0;m.meleeImpact={ _,_,_,_ -> hits++ }
            prime(e,AttackShape.CHARGE)
            repeat(15) { m.update(.05f) }
            if(wall) { assertTrue(e.z<10.51);assertEquals(0,hits) }
            else { assertTrue(e.z>11.5);assertTrue(hits>=1) }
        }
    }
    @Test fun oneSpinCannotDamageThePlayerOnEveryFrame() {
        val e=enemy("dwarf");val t=Target(z=8.2);val m=manager(e,t)
        var hits=0;m.meleeImpact={ _,_,_,_ -> hits++ }
        prime(e,AttackShape.SPIN)
        repeat(12) { m.update(.05f) }
        assertEquals(1,hits)
    }
    @Test fun contactRateIsIndependentOfFrameRateAndWallsBlockIt() {
        fun hits(fps: Int,wall: Boolean):Int {
            val e=enemy("zombie");val t=Target(z=10.4);val m=manager(e,t)
            e.attackCooldown=100f
            m.clearSight={ _,_,_,_,_,_ -> !wall }
            var count=0;m.meleeImpact={ _,_,_,_ -> count++ }
            repeat(fps*2) { m.update(1f/fps) }
            return count
        }
        assertEquals(3,hits(30,false));assertEquals(3,hits(120,false));assertEquals(0,hits(60,true))
    }
    @Test fun contactCannotHitAcrossFloorsOrFromAStunnedOrDeadMob() {
        val e=enemy("slime");val t=Target(z=10.4)
        assertTrue(EnemyRig.touching(e,t))
        t.y=6.0;assertFalse(EnemyRig.touching(e,t));t.y=2.62
        e.staggerTimer=.1f;assertFalse(EnemyRig.touching(e,t));e.staggerTimer=0f
        e.hp=0;assertFalse(EnemyRig.touching(e,t))
    }
    @Test fun spinningHitsBehindButSweepingDoesNotAndSlamsCanBeJumped() {
        assertTrue(EnemyAttack(AttackShape.SPIN,.4f,.3f,3.0).hits(0.0,1.62,-2.0))
        assertFalse(EnemyAttack(AttackShape.SWEEP,.3f,.2f,3.0).hits(0.0,1.62,-2.0))
        assertFalse(EnemyAttack(AttackShape.SLAM,.5f,.3f,3.0).hits(0.0,2.5,1.0))
    }
    @Test fun bowSocketRotatesWithTheWeaponAndScalesWithBoss() {
        val e=enemy("skeleton")
        val front=EnemyRig.muzzle(e)
        assertTrue(front.y>e.y+1.4)
        assertTrue(front.z>e.z+.6)
        e.windupYaw=90f
        val side=EnemyRig.muzzle(e)
        assertEquals(front.z-e.z,side.x-e.x,1e-5)
        assertEquals(-(front.x-e.x),side.z-e.z,1e-5)
        e.isBoss=true
        val boss=EnemyRig.muzzle(e)
        assertEquals((side.y-e.y)*Enemy.BOSS_SPRITE_SCALE,boss.y-e.y,1e-5)
    }
    @Test fun feintWithdrawsBeforeCommittingAndModelsStayInsideMeshBudget() {
        assertTrue(EnemyAttackPose.preparation(AttackShape.FEINT,.3f)>EnemyAttackPose.preparation(AttackShape.FEINT,.6f))
        assertEquals(1f,EnemyAttackPose.preparation(AttackShape.FEINT,1f),.001f)
        for((name,model) in EnemyModels.all) {
            assertTrue(name,model.parts.size<=48)
            assertTrue(name,model.parts.all { it.w>0 && it.h>0 && it.d>0 })
        }
    }
    @Test fun doubleStrikePreparesTheOtherArmBeforeItsSecondImpact() {
        val right=EnemyAttackPose.doubleArm(1,0,true,1f,0f)
        val left=EnemyAttackPose.doubleArm(-1,0,true,1f,0f)
        assertTrue(left>right)
        assertTrue(EnemyAttackPose.doubleArm(-1,1,false,0f,1f)>EnemyAttackPose.doubleArm(1,1,false,0f,1f))
    }
}
