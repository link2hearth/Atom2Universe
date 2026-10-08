package com.Atom2Universe.app.games.caves

import com.Atom2Universe.app.games.caves.entity.Enemy
import com.Atom2Universe.app.games.caves.entity.Harassment
import com.Atom2Universe.app.games.caves.node.MobDef
import org.junit.Assert.*
import org.junit.Test

/** Le tir à distance agace, il n'achève pas (voir CAVE_WORLD_ARMES_DE_JET.md). */
class HarassmentTest {
    private fun slime(behavior: String = "aggressive") = Enemy(1, MobDef(
        id = "slime", hpBase = 4, damageBase = 1, speed = 1.8f, attackRange = 1.6, detectRange = 11.0,
        eyeHeight = .8f, radius = .5f, spriteScale = .7f, hpScalePerLevel = 1.0, hpScaleCap = 1.0,
        damageScalePer3Lvl = 0, speedScalePerLevel = 0f, biomes = listOf("any"), model = "slime",
        spawnZoneMin = 1, spawnWeight = 1f, behavior = behavior, bossEligible = false, xpBase = 1),
        0.0, 0.0, 0.0).apply { exploration = true; level = 1; hp = maxHp }

    /** Un tir à distance, tel que le jeu l'applique : plafonné, puis compté. */
    private fun shoot(e: Enemy, damage: Int) {
        val dealt = Harassment.cap(e, damage)
        e.hp = (e.hp - dealt).coerceAtLeast(0)
        Harassment.ranged(e, dealt)
    }

    @Test fun arrowsAloneNeverKill() {
        val e = slime()
        assertEquals(40, e.maxHp)
        shoot(e, 14); shoot(e, 14)                        // la 2e flèche est rognée à 6 : repli
        assertEquals(20, e.hp)
        assertTrue(Harassment.shielded(e))
        shoot(e, 14)                                      // rebondit
        assertEquals(20, e.hp)
        Harassment.update(e, Harassment.RETREAT_TIME)
        assertEquals("the retreat regains every ranged point", 40, e.hp)
        assertFalse(Harassment.shielded(e))
        repeat(50) { if (!Harassment.shielded(e)) shoot(e, 24) else Harassment.update(e, 1f) }
        assertTrue(e.hp > 0)
    }

    @Test fun aBigShotIsCutAtTheThreshold() {
        val e = slime()
        shoot(e, 60)                                      // arbalète critique contre 40 PV
        assertEquals(20, e.hp)
        assertTrue(Harassment.shielded(e))
    }

    @Test fun meleeMakesRangedDamageStickAndBreaksTheRetreat() {
        val e = slime()
        shoot(e, 14)
        Harassment.melee(e); e.hp -= 13                   // coup d'épée : jauge vidée
        assertEquals(13, e.hp)
        shoot(e, 14)                                      // sur un monstre entamé, la flèche achève
        assertEquals(0, e.hp)

        val f = slime()
        shoot(f, 14); shoot(f, 14)
        Harassment.update(f, Harassment.RETREAT_TIME / 2) // moitié du chemin : 20 + 10
        assertEquals(30, f.hp)
        Harassment.melee(f)
        assertFalse(Harassment.shielded(f))
        assertEquals(Harassment.BREAK_STUN, f.staggerTimer, 0f)
        Harassment.update(f, 1f)
        assertEquals("a broken retreat stops healing", 30, f.hp)
    }

    @Test fun animalsAndAssaultAreExempt() {
        val animal = slime("passive")
        assertEquals(30, Harassment.cap(animal, 30))
        val soldier = slime().apply { exploration = false }
        shoot(soldier, 39)
        assertEquals(1, soldier.hp)
        assertFalse(Harassment.shielded(soldier))
    }
}
