package com.Atom2Universe.app.games.hamlet

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class HamletCombatDepthTest {
    private fun fighter(index: Int = 0, enemy: Boolean = false, slot: Int = 0,
        role: HeroRole = HeroRole.DUELIST, hp: Double = 10_000.0, attack: Double = 100.0,
        armor: Double = 0.0, healingBonus: Double = 0.0, lifesteal: Double = 0.0,
        armorPierce: Double = 0.0, openingShield: Double = 0.0, boss: Boolean = false,
        ability: HeroAbility = HeroAbility.NONE) =
        BattleUnit(index, if (enemy) 3 else 7, enemy, HeroFaction.GROVE, role, slot,
            hp, attack, armor, 1f, 1000f, 1000f, crit = 0.0,
            healingBonus = healingBonus, lifesteal = lifesteal, armorPierce = armorPierce,
            openingShield = openingShield, boss = boss, ability = ability)

    private fun battle(vararg units: BattleUnit, tactic: BattleTactic = BattleTactic.BALANCED, region: Int = 0) =
        HamletBattle(BattleMode.CAMPAIGN, 9, region, units.toMutableList(), Random(42), autoUltimates = false, tactic = tactic)

    @Test fun `assaut et defense modifient reellement les echanges`() {
        val balanced = battle(fighter(), fighter(100, true))
        val assault = battle(fighter(), fighter(100, true), tactic = BattleTactic.ASSAULT)
        val defensive = battle(fighter(), fighter(100, true), tactic = BattleTactic.DEFENSIVE)
        listOf(balanced, assault, defensive).forEach { it.advance(5f) }
        assertTrue(assault.damage > balanced.damage)
        assertTrue(assault.units[0].hp < balanced.units[0].hp)
        assertTrue(defensive.units[0].hp > balanced.units[0].hp)
        assertTrue(defensive.damage < balanced.damage)
        assertTrue(defensive.report()[0].protection > 0)
    }

    @Test fun `controle cible les casters plutot que leur garde`() {
        fun encounter(tactic: BattleTactic): HamletBattle {
            val ranger = fighter(role = HeroRole.RANGER, slot = 2)
            val guard = fighter(100, true, role = HeroRole.GUARDIAN)
            val mage = fighter(101, true, slot = 2, role = HeroRole.MAGE)
            guard.attackLeft = 100f; mage.attackLeft = 100f
            return battle(ranger, guard, mage, tactic = tactic).also { it.advance(.7f) }
        }
        val balanced = encounter(BattleTactic.BALANCED)
        val control = encounter(BattleTactic.CONTROL)
        assertTrue(balanced.units[1].hp < balanced.units[1].maxHp)
        assertEquals(balanced.units[2].maxHp, balanced.units[2].hp, .0001)
        assertEquals(control.units[1].maxHp, control.units[1].hp, .0001)
        assertTrue(control.units[2].hp < control.units[2].maxHp)
    }

    @Test fun `controle recharge plus vite et prolonge les nouveaux etourdissements`() {
        fun encounter(tactic: BattleTactic): HamletBattle {
            val guard = fighter(role = HeroRole.GUARDIAN).also { it.skillLeft = 1f; it.attackLeft = 100f }
            val foe = fighter(100, true).also { it.attackLeft = 100f }
            return battle(guard, foe, tactic = tactic).also { it.advance(.9f) }
        }
        val balanced = encounter(BattleTactic.BALANCED)
        val control = encounter(BattleTactic.CONTROL)
        assertEquals(0.0, balanced.damage, .0001)
        assertTrue(control.damage > 0)
        assertTrue(control.units[1].stun > .7f)
    }

    @Test fun `equipement soins vol de vie penetration et ouverture agissent`() {
        fun healing(bonus: Double): Double {
            val healer = fighter(role = HeroRole.HEALER, healingBonus = bonus)
            healer.hp = 2000.0; healer.skillLeft = 0f; healer.attackLeft = 100f
            val foe = fighter(100, true).also { it.attackLeft = 100f }
            return battle(healer, foe).also { it.advance(.05f) }.report()[0].healing
        }
        assertEquals(healing(0.0) * 2, healing(1.0), .0001)
        val vampire = fighter(lifesteal = .5).also { it.hp = 5000.0 }
        val foe = fighter(100, true, armor = 100.0).also { it.attackLeft = 100f }
        val b = battle(vampire, foe); b.advance(.4f)
        assertEquals(5025.0, vampire.hp, .0001)
        assertEquals(25.0, b.report()[0].healing, .0001)
        val pierced = battle(fighter(armorPierce = .7), fighter(100, true, armor = 100.0))
        pierced.advance(.4f); assertTrue(pierced.damage > b.damage)
        val ward = fighter(hp = 2000.0, openingShield = .1)
        val brute = fighter(100, true, attack = 1000.0)
        val wardBattle = battle(ward, brute); wardBattle.advance(.4f)
        assertEquals(200.0, wardBattle.report()[0].protection, .0001)
        assertEquals(1200.0, ward.hp, .0001)
    }

    @Test fun `rapport exclut surdegats et comptabilise un KO unique`() {
        val b = battle(fighter(attack = 1000.0), fighter(100, true, hp = 50.0))
        b.advance(2f)
        assertEquals(BattleOutcome.VICTORY, b.outcome)
        assertEquals(50.0, b.report()[0].damage, .0001)
        assertEquals(1, b.report()[0].kills)
        assertEquals(b.damage, b.report().sumOf { it.damage }, .0001)
    }

    @Test fun `boss annonce les cibles et controle retarde le choc sans supprimer sa charge`() {
        val hero = fighter().also { it.attackLeft = 100f }
        val boss = fighter(100, true, boss = true).also { it.skillLeft = 0f; it.attackLeft = 100f }
        val b = battle(hero, boss)
        b.advance(.05f)
        val warning = b.warnings().single()
        assertEquals(BossPattern.CLEAVE, warning.pattern)
        assertEquals(listOf(hero.index), warning.targets)
        assertEquals(hero.maxHp, hero.hp, .0001)
        boss.stun = 1f
        b.advance(.8f)
        assertEquals(1.5f, boss.telegraphLeft, .0001f)
        b.advance(2f)
        assertTrue(hero.hp < hero.maxHp)
        assertTrue(b.warnings().isEmpty())
    }

    @Test fun `trois patterns boss annoncent leurs vrais rangs cibles`() {
        for (sprite in 3..5) {
            val front = fighter().also { it.attackLeft = 100f }
            val rear = fighter(1, slot = 2).also { it.attackLeft = 100f }
            val boss = HamletBattle.enemy(sprite, 100, 0, 1.0, true).also { it.skillLeft = 0f; it.attackLeft = 100f }
            val b = battle(front, rear, boss); b.advance(.05f)
            val expected = when (boss.bossPattern) {
                BossPattern.CLEAVE -> listOf(front.index)
                BossPattern.DEVASTATION -> listOf(front.index, rear.index)
                BossPattern.DREAD -> listOf(rear.index)
            }
            assertEquals(expected, b.warnings().single().targets)
            b.advance(1.6f)
            assertEquals(front.index in expected, front.hp < front.maxHp)
            assertEquals(rear.index in expected, rear.hp < rear.maxHp)
            if (boss.bossPattern == BossPattern.DREAD) assertTrue(rear.weakened > 0)
        }
    }

    @Test fun `defense attend les blessures pour ultime automatique puis soigne`() {
        val healer = fighter(role = HeroRole.HEALER, slot = 2).also { it.ultimateLeft = 0f; it.attackLeft = 100f }
        val foe = fighter(100, true).also { it.attackLeft = 100f }
        val b = battle(healer, foe, tactic = BattleTactic.DEFENSIVE).also { it.auto = true }
        b.advance(.05f); assertEquals(0.0, b.healing, .0001)
        assertTrue(healer.ultimateLeft <= 0)
        healer.hp = 4000.0
        b.advance(.05f); assertTrue(b.healing > 0); assertTrue(healer.ultimateLeft > 0)
    }

    @Test fun `pas fixes gardent le meme rapport pour toutes tactiques et cadences`() {
        for (tactic in BattleTactic.entries) {
            fun encounter() = battle(fighter(role = HeroRole.GUARDIAN),
                fighter(1, role = HeroRole.HEALER, slot = 2),
                fighter(100, true, boss = true), tactic = tactic)
            val a = encounter(); val b = encounter()
            a.advance(40f); repeat(800) { b.advance(.05f) }
            assertEquals(a.time, b.time, .0001f)
            assertEquals(a.outcome, b.outcome)
            assertEquals(a.units.map { it.hp }, b.units.map { it.hp })
            assertEquals(a.report(), b.report())
        }
    }

    @Test fun `prairie neutre preserve le combat de depart`() {
        val b = battle(fighter(), fighter(100, true))
        b.advance(.4f)
        assertEquals(BattleField.MEADOW, b.field)
        assertEquals(100.0, b.damage, .0001)
        assertEquals(9900.0, b.units[0].hp, .0001)
        assertEquals(0.0, b.units[0].shield, .0001)
        assertEquals(0f, b.units[0].venom, .0001f)
    }

    @Test fun `carrieres encouragent les casters et la penetration sans ajouter de PV`() {
        fun encounter(role: HeroRole, region: Int, pierce: Double = 0.0) =
            battle(fighter(role = role, armorPierce = pierce), fighter(100, true), region = region).also { it.advance(.4f) }
        val meadow = encounter(HeroRole.RANGER, 0)
        val quarry = encounter(HeroRole.RANGER, 3)
        val mage = encounter(HeroRole.MAGE, 3)
        val pierced = encounter(HeroRole.RANGER, 3, .7)
        assertTrue(quarry.damage < meadow.damage)
        assertEquals(meadow.damage, mage.damage, .0001)
        assertTrue(pierced.damage > quarry.damage)
        assertEquals(meadow.units[1].maxHp, quarry.units[1].maxHp, .0001)
    }

    @Test fun `brume donne les boucliers aux soutiens actifs des deux camps`() {
        val guard = fighter(role = HeroRole.GUARDIAN).also { it.attackLeft = 100f }
        val ally = fighter(1, slot = 2).also { it.hp = 5000.0; it.attackLeft = 100f }
        val enemyGuard = fighter(100, true, role = HeroRole.GUARDIAN).also { it.attackLeft = 100f }
        val b = battle(guard, ally, enemyGuard, region = 1)
        b.advance(8.1f)
        assertEquals(150.0, ally.shield, .0001)
        assertEquals(150.0, enemyGuard.shield, .0001)
        assertEquals(0.0, guard.shield, .0001)
    }

    @Test fun `marais applique un venin borne et purification le retire`() {
        val healer = fighter(role = HeroRole.HEALER, ability = HeroAbility.PURIFY).also { it.attackLeft = 100f }
        val foe = fighter(100, true).also { it.attackLeft = 100f }
        val b = battle(healer, foe, region = 4)
        b.advance(10.1f)
        assertTrue(healer.venom > 0)
        assertTrue(foe.venom > 0)
        assertTrue(healer.hp > healer.maxHp * .99)
        healer.skillLeft = 0f
        b.advance(.05f)
        assertEquals(0f, healer.venom, .0001f)
        assertTrue(foe.venom > 0)
        b.advance(4f)
        assertTrue(foe.hp > foe.maxHp * .97)
        assertTrue(b.damage < 100.0)
    }

    @Test fun `gel ralentit les attaques mais ne penalise pas les competences`() {
        // At five seconds both cadences still land five attacks: frost's fifth lands near 4.9 s.
        // Six seconds separates the schedules, before frost's sixth attack near 6.05 s.
        fun encounter(region: Int) = battle(fighter(), fighter(100, true), region = region).also { it.advance(6f) }
        val meadow = encounter(0); val frost = encounter(5)
        assertEquals(600.0, meadow.damage, .0001)
        assertEquals(500.0, frost.damage, .0001)
        assertTrue(frost.damage < meadow.damage)
        assertTrue(frost.units[0].hp > meadow.units[0].hp)
        assertEquals(meadow.units[0].skillLeft, frost.units[0].skillLeft, .0001f)
        assertEquals(meadow.units[0].ultimateLeft, frost.units[0].ultimateLeft, .0001f)
    }

    @Test fun `braises plafonnent la pression et restent neutres les vingt premieres secondes`() {
        fun encounter(region: Int) = battle(fighter(), fighter(100, true), region = region)
        val meadow = encounter(0); val embers = encounter(2)
        meadow.advance(20f); embers.advance(20f)
        assertEquals(meadow.damage, embers.damage, .0001)
        val initial = embers.damage
        val baseInitial = meadow.damage
        meadow.advance(20f); embers.advance(20f)
        assertTrue(embers.damage - initial > meadow.damage - baseInitial)
        assertTrue(embers.damage - initial <= (meadow.damage - baseInitial) * 1.151)
    }

    @Test fun `tous les terrains restent deterministes quelle que soit la cadence de dessin`() {
        for (region in 0..5) {
            fun encounter() = battle(fighter(role = HeroRole.GUARDIAN),
                fighter(1, role = HeroRole.HEALER, slot = 2, ability = HeroAbility.PURIFY),
                fighter(100, true, boss = true), region = region)
            val a = encounter(); val b = encounter()
            a.advance(40f); repeat(800) { b.advance(.05f) }
            assertEquals(a.field, BattleField.entries[region])
            assertEquals(a.outcome, b.outcome)
            assertEquals(a.units.map { it.hp }, b.units.map { it.hp })
            assertEquals(a.units.map { it.shield }, b.units.map { it.shield })
            assertEquals(a.report(), b.report())
        }
    }
}
