package com.Atom2Universe.app.games.roguelike

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Ce que fait chaque élément, vérifié sur un ennemi seul qui frappe à chaque tour. Le d20
 * des jets de sauvegarde est truqué : 1, le monstre rate toujours son jet (l'effet prend) ;
 * 20, il le réussit toujours.
 */
class RelicTest {

    private fun fightWith(relic: Relic, type: MonsterType = MonsterType.GOBLIN, d20: Int = 1): Combat {
        val hero = Hero.starter().apply { addRelic(relic) }
        // Très solide : il survit au sort, on peut regarder l'effet
        val enemy = Enemy(type, maxHp = 1000, damage = 3, cadence = 1, countdown = 1)
        return Combat(hero, 1, listOf(enemy), ambush = false, rng = Random(1), d20 = { d20 })
    }

    /** Un tour ennemi complet : les attaquants frappent (parade parfaite), puis on revient au joueur. */
    private fun enemyTurn(c: Combat): EnemyTurnStart {
        val t = c.startEnemyTurn()
        t.attackers.forEach { c.resolveStrike(it, Timing.PERFECT) }
        c.endEnemyTurn()
        return t
    }

    @Test
    fun leHerosNeufNaPasDeRelique() {
        val hero = Hero.starter()
        assertTrue(hero.relics.isEmpty())
        assertTrue(hero.relicSlots.all { it == null })
    }

    // ── Glace ───────────────────────────────────────────────────────────────────

    @Test
    fun laGlaceFigeSiLeJetEstRate() {
        val c = fightWith(Relic.ICE_SHARD, d20 = 1)
        val hit = c.castRelic(Relic.ICE_SHARD, 0, Timing.MISS).main!!
        assertFalse(hit.save!!.saved)
        val turn = enemyTurn(c)
        assertTrue(turn.attackers.isEmpty())
        assertEquals(Element.ICE, turn.stopped.single().element)
        assertEquals("un délai : le compteur n'a pas bougé", 1, c.enemies[0].countdown)
        c.attack(0, Timing.MISS)
        assertEquals("dégelé, il frappe au tour suivant", listOf(0), c.startEnemyTurn().attackers)
    }

    @Test
    fun laGlaceNeFigePasSiLeJetEstReussi() {
        val c = fightWith(Relic.ICE_SHARD, d20 = 20)
        assertTrue(c.castRelic(Relic.ICE_SHARD, 0, Timing.MISS).main!!.save!!.saved)
        assertEquals(listOf(0), c.startEnemyTurn().attackers)
    }

    @Test
    fun unSwipeParfaitImposeLeDesavantage() {
        // Les dés sortent 20 puis 1 : avec désavantage, le monstre garde le 1
        val dice = ArrayDeque(listOf(20, 1))
        val hero = Hero.starter().apply { addRelic(Relic.ICE_SHARD) }
        val c = Combat(hero, 1, listOf(Enemy(MonsterType.RAT, 1000, 3, 1, 1)), ambush = false,
            rng = Random(1), d20 = { dice.removeFirst() })
        val save = c.castRelic(Relic.ICE_SHARD, 0, Timing.PERFECT).main!!.save!!
        assertTrue(save.disadvantage)
        assertEquals(1, save.roll)
        assertFalse(save.saved)
    }

    @Test
    fun unSwipeBienMonteLeDD() {
        val normal = fightWith(Relic.ICE_SHARD, d20 = 10).castRelic(Relic.ICE_SHARD, 0, Timing.MISS).main!!.save!!
        val good = fightWith(Relic.ICE_SHARD, d20 = 10).castRelic(Relic.ICE_SHARD, 0, Timing.GOOD).main!!.save!!
        assertEquals(normal.dc + SpellSave.GOOD_STRIKE_DC, good.dc)
        assertFalse(good.disadvantage)
    }

    @Test
    fun leGesteDeLaFoudreCompteAChaqueJet() {
        val hero = Hero.starter().apply { addRelic(Relic.LIGHTNING) }
        var rolls = 0
        val c = Combat(hero, 1, listOf(Enemy(MonsterType.RAT, 1000, 3, 1, 1)), ambush = false,
            rng = Random(1), d20 = { rolls++; 20 })
        c.castRelic(Relic.LIGHTNING, 0, Timing.PERFECT)
        val save = c.startEnemyTurn().saves.single().save
        assertTrue("le désavantage suit la paralysie", save.disadvantage)
        assertEquals(2, rolls)
    }

    // ── Foudre ──────────────────────────────────────────────────────────────────

    @Test
    fun laFoudreFaitPerdreLAttaqueSurUnJetRate() {
        val c = fightWith(Relic.LIGHTNING, d20 = 1)
        c.castRelic(Relic.LIGHTNING, 0, Timing.MISS)
        val turn = enemyTurn(c)
        assertTrue(turn.attackers.isEmpty())
        assertEquals(Element.LIGHTNING, turn.stopped.single().element)
        assertFalse(turn.saves.single().save.saved)
    }

    @Test
    fun laFoudreLaissePasserSurUnJetReussi() {
        val c = fightWith(Relic.LIGHTNING, d20 = 20)
        c.castRelic(Relic.LIGHTNING, 0, Timing.MISS)
        val turn = c.startEnemyTurn()
        assertEquals(listOf(0), turn.attackers)
        assertTrue(turn.saves.single().save.saved)
    }

    // ── Rage ────────────────────────────────────────────────────────────────────

    @Test
    fun deuxControlesDAffileeFontEnrager() {
        val c = fightWith(Relic.LIGHTNING, d20 = 1)
        c.castRelic(Relic.LIGHTNING, 0, Timing.MISS)
        val t1 = enemyTurn(c)                                  // 1er coup perdu
        assertTrue(t1.enraged.isEmpty())
        c.attack(0, Timing.MISS)
        val t2 = enemyTurn(c)                                  // 2e coup perdu : il enrage
        assertEquals(listOf(0), t2.enraged)
        val e = c.enemies[0]
        assertTrue(e.enraged)
        assertEquals("la rage efface la paralysie", 0, e.paralyzedTurns)
        c.attack(0, Timing.MISS)
        assertEquals("enragé, il frappe", listOf(0), c.startEnemyTurn().attackers)
    }

    @Test
    fun onNeControlePasUnEnnemiEnrage() {
        val c = fightWith(Relic.ICE_SHARD, d20 = 1)
        c.castRelic(Relic.ICE_SHARD, 0, Timing.MISS)
        enemyTurn(c)
        c.relicCooldowns.clear()                               // comme si la SAG avait tout rechargé
        val second = c.castRelic(Relic.ICE_SHARD, 0, Timing.MISS).main!!
        assertTrue("2e gel d'affilée : il enrage", second.enraged)
        enemyTurn(c)
        c.relicCooldowns.clear()
        val third = c.castRelic(Relic.ICE_SHARD, 0, Timing.MISS).main!!
        assertEquals(SaveReason.RAGE, third.save!!.reason)
        assertTrue(third.save!!.saved)
    }

    @Test
    fun frapperRemetLaSerieAZero() {
        val c = fightWith(Relic.ICE_SHARD, d20 = 1)
        c.castRelic(Relic.ICE_SHARD, 0, Timing.MISS)
        enemyTurn(c)                                           // figé
        c.attack(0, Timing.MISS)
        enemyTurn(c)                                           // il frappe : la série repart de zéro
        assertEquals(0, c.enemies[0].controlStreak)
        c.relicCooldowns.clear()
        assertFalse(c.castRelic(Relic.ICE_SHARD, 0, Timing.MISS).main!!.enraged)
    }

    @Test
    fun enrageIlCompteDeuxFoisPlusVite() {
        val hero = Hero.starter()
        val brute = Enemy(MonsterType.ORC, 1000, 3, cadence = 4, countdown = 4).apply { rageTurns = 3 }
        val c = Combat(hero, 1, listOf(brute), ambush = false, rng = Random(1), d20 = { 1 })
        c.attack(0, Timing.MISS)
        c.startEnemyTurn()
        assertEquals(2, brute.countdown)
    }

    // ── Affinités ───────────────────────────────────────────────────────────────

    @Test
    fun unImmuniseNePrendNiDegatsNiEffet() {
        val c = fightWith(Relic.FIREBALL, type = MonsterType.DEMON)
        val hit = c.castRelic(Relic.FIREBALL, 0, Timing.MISS).main!!
        assertEquals(Affinity.IMMUNE, hit.affinity)
        assertEquals(0, hit.damage)
        assertEquals(0, c.enemies[0].burnTurns)
    }

    @Test
    fun unVulnerablePrendDouble() {
        val c = fightWith(Relic.FIREBALL, type = MonsterType.RAT)
        val (lo, _) = c.hero.relicDamage(Relic.FIREBALL)
        val hit = c.castRelic(Relic.FIREBALL, 0, Timing.MISS).main!!
        assertEquals(Affinity.VULNERABLE, hit.affinity)
        assertTrue(hit.damage >= 2 * lo)
    }

    @Test
    fun lesAffinitesDeplacentLeJet() {
        val dc = Hero.starter().spellDc(Relic.ICE_SHARD)
        val normal = SpellSave.landChance(dc, SpellSave.monsterProficiency(1))
        assertEquals("équipement de départ contre l'étage 1 : une fois sur deux", 0.5f, normal, 1e-4f)
        assertEquals(0.75f, SpellSave.landChance(dc, SpellSave.monsterProficiency(1) + Affinity.VULNERABLE.saveBonus), 1e-4f)
        assertEquals(0.25f, SpellSave.landChance(dc, SpellSave.monsterProficiency(1) + Affinity.RESISTANT.saveBonus), 1e-4f)
    }

    @Test
    fun lImmuniteDispenseDuJet() {
        val c = fightWith(Relic.VENOM, type = MonsterType.SKELETON)
        c.castRelic(Relic.VENOM, 0, Timing.MISS)
        assertEquals(0, c.enemies[0].poisonDoses)
    }

    // ── Feu, poison ─────────────────────────────────────────────────────────────

    @Test
    fun lePoisonSEmpileEtSEteint() {
        val c = fightWith(Relic.VENOM)
        val enemy = c.enemies[0]
        // Deux doses : on lance, on attend la recharge en attaquant, on relance
        c.castRelic(Relic.VENOM, 0, Timing.MISS)
        while (!c.canCast(Relic.VENOM)) {
            if (c.phase == CombatPhase.ENEMY_TURN) enemyTurn(c) else c.attack(0, Timing.MISS)
        }
        c.castRelic(Relic.VENOM, 0, Timing.MISS)
        assertEquals(2, enemy.poisonDoses)
        val tick = c.startEnemyTurn().ticks.single()
        assertEquals(Element.POISON, tick.element)
        // Le gobelin est vulnérable au poison : chaque dose ronge double
        assertEquals(2 * 2 * c.hero.poisonDose(Relic.VENOM), tick.damage)
        c.endEnemyTurn()
        repeat(Relic.VENOM.effectTurns) {
            if (c.phase == CombatPhase.PLAYER_TURN) c.attack(0, Timing.MISS)
            enemyTurn(c)
        }
        assertEquals(0, enemy.poisonDoses)
    }

    @Test
    fun leFeuBrule() {
        val c = fightWith(Relic.FIREBALL)
        c.castRelic(Relic.FIREBALL, 0, Timing.MISS)
        assertEquals(Relic.FIREBALL.effectTurns, c.enemies[0].burnTurns)
        assertEquals(Element.FIRE, c.startEnemyTurn().ticks.single().element)
    }

    // ── Porter, recharger ───────────────────────────────────────────────────────

    @Test
    fun uneReliqueRangeeNeSeLancePas() {
        val c = fightWith(Relic.FIREBALL)
        c.hero.toggleRelic(Relic.FIREBALL)
        assertFalse(c.canCast(Relic.FIREBALL))
    }

    @Test
    fun lesEmplacementsPleinsRefusent() {
        val hero = Hero.starter()
        assertTrue(hero.addRelic(Relic.FIREBALL))
        assertTrue(hero.addRelic(Relic.ICE_SHARD))
        assertFalse("troisième relique : trouvée mais pas portée", hero.addRelic(Relic.VENOM))
        assertEquals(Hero.RelicToggle.SLOTS_FULL, hero.toggleRelic(Relic.VENOM))
        assertEquals(Hero.RelicToggle.REMOVED, hero.toggleRelic(Relic.FIREBALL))
        assertEquals(Hero.RelicToggle.EQUIPPED, hero.toggleRelic(Relic.VENOM))
    }

    @Test
    fun leSortSuitLaPuissanceDeLArme() {
        val hero = Hero.starter()
        val (lo1, hi1) = hero.relicDamage(Relic.FIREBALL)
        assertTrue(lo1 < hi1)
        val dc1 = hero.spellDc(Relic.ICE_SHARD)
        hero.equipped[EquipSlot.WEAPON] = LootSystem.create(ItemBase.SWORD, Material.IRON, 1, Rarity.NORMAL, 0, Random(0))
        assertTrue(hero.relicDamage(Relic.FIREBALL).first > hi1)
        assertTrue("la maîtrise suit l'arme", hero.spellDc(Relic.ICE_SHARD) > dc1)
    }

    /** Un héros avec un anneau qui donne [points] dans [attr]. */
    private fun heroWith(attr: StatType, points: Int) = Hero.starter().apply {
        equipped[EquipSlot.RING] = LootSystem.create(ItemBase.RING, Material.LEATHER, 1, Rarity.NORMAL, 0, Random(0))
            .copy(implicits = listOf(StatRoll(attr, points.toFloat())), affixes = emptyList())
    }

    @Test
    fun leVeninSuitLaDexLesElementsLInt() {
        val base = Hero.starter()
        val dex = heroWith(StatType.DEX, 10)
        val int = heroWith(StatType.INT, 10)
        assertTrue(dex.relicPower(Relic.VENOM) > base.relicPower(Relic.VENOM))
        assertEquals(base.relicPower(Relic.VENOM), int.relicPower(Relic.VENOM), 1e-4f)
        assertTrue(int.relicPower(Relic.FIREBALL) > base.relicPower(Relic.FIREBALL))
        assertEquals(base.relicPower(Relic.FIREBALL), dex.relicPower(Relic.FIREBALL), 1e-4f)
    }

    @Test
    fun leDdSuitLaCaracDeLaRelique() {
        val int = heroWith(StatType.INT, 6)
        val dex = heroWith(StatType.DEX, 6)
        assertEquals(Hero.starter().spellDc(Relic.ICE_SHARD) + 3, int.spellDc(Relic.ICE_SHARD))
        assertEquals(Hero.starter().spellDc(Relic.ICE_SHARD), dex.spellDc(Relic.ICE_SHARD))
        assertEquals(Hero.starter().spellDc(Relic.VENOM) + 3, dex.spellDc(Relic.VENOM))
    }

    @Test
    fun laRechargeSeGardeDUnCombatALAutre() {
        val c = fightWith(Relic.FIREBALL)
        c.castRelic(Relic.FIREBALL, 0, Timing.MISS)
        val hero = c.hero
        val left = hero.relicCooldown(Relic.FIREBALL)
        assertTrue(left > 0)
        // Nouveau combat tout de suite (enchaîné) : la relique n'est pas prête
        val next = Combat(hero, 1, listOf(Enemy(MonsterType.GOBLIN, 1000, 3, 1, 1)), ambush = false, rng = Random(2))
        assertFalse(next.canCast(Relic.FIREBALL))
        // Marcher recharge lentement, se reposer vite
        repeat(Hero.RELIC_WALK_STEPS - 1) { hero.walkRelics() }
        assertEquals(left, hero.relicCooldown(Relic.FIREBALL))
        hero.walkRelics()
        assertEquals(left - 1, hero.relicCooldown(Relic.FIREBALL))
        hero.tickRelics(left - 1)
        assertTrue(next.canCast(Relic.FIREBALL))
    }

    @Test
    fun seReposerRechargeLesReliques() {
        val g = RoguelikeGame(rng = Random(5))
        g.hero.addRelic(Relic.VENOM)
        g.hero.relicCooldowns[Relic.VENOM] = 2
        assertTrue("PV pleins, mais une relique à recharger : on peut se reposer", g.canRest())
        g.rest()
        assertEquals(1, g.hero.relicCooldown(Relic.VENOM))
    }

    @Test
    fun chaqueSortVautSonBudget() {
        for (r in Relic.entries) {
            val hit = RelicBudget.hitCoef(r)
            val targets = RelicBudget.targets(r)
            // Ce que vaut l'effet sur une cible, recompté ici à partir de ce que le combat fait vraiment
            val effect = when (r.effect) {
                RelicEffect.NONE      -> 0f
                RelicEffect.BURN      -> hit * Relic.BURN_SHARE * r.effectTurns
                RelicEffect.FREEZE    -> RelicBudget.FREEZE_TURN_VALUE * r.effectTurns * RelicBudget.REF_LAND_CHANCE
                RelicEffect.PARALYZE  -> RelicBudget.PARALYSIS_TURN_VALUE * r.effectTurns * RelicBudget.REF_LAND_CHANCE
                RelicEffect.POISON    -> r.doseCoef * r.effectTurns
                RelicEffect.SOAK      -> RelicBudget.SOAK_TURN_VALUE * r.effectTurns
                RelicEffect.FRACTURE  -> RelicBudget.FRACTURE_TURN_VALUE * r.effectTurns
                RelicEffect.MARK      -> RelicBudget.MARK_VALUE
                // L'affaiblissement de chaque ennemi, plus les coups renforcés du héros, partagés
                RelicEffect.WARCRY    -> RelicBudget.WEAKEN_TURN_VALUE * r.effectTurns +
                    RelicBudget.empowerBonus(r) * Relic.WARCRY_ATTACKS / targets
                RelicEffect.ENCHANT_POISON -> r.doseCoef * RelicBudget.enchantDoseTicks(r)
                RelicEffect.BLEED     -> RelicBudget.bleedCoef(r) * r.effectTurns * RelicBudget.REF_ATTACKS_PER_TURN
                RelicEffect.BLEED_ON_CRIT -> RelicBudget.REF_CRIT_CHANCE *
                    RelicBudget.bleedCoef(r) * Relic.FAN_BLEED_TURNS * RelicBudget.REF_ATTACKS_PER_TURN
                RelicEffect.ACID      -> RelicBudget.FRACTURE_TURN_VALUE * r.effectTurns + r.doseCoef * Relic.ENCHANT_DOSE_TURNS
                // Le surplus du coup contre un figé, à la fréquence où on en trouve un
                RelicEffect.CRYSTALLIZE -> RelicBudget.share(r) - hit
                // Des prix payés : le coup en vaut plus que la part
                RelicEffect.DELAYED   -> -hit * RelicBudget.DELAY_PREMIUM / (1f + RelicBudget.DELAY_PREMIUM)
                RelicEffect.BLIND     -> RelicBudget.BLIND_TURN_VALUE * r.effectTurns
                // Comptés en PV ou en contrôle, faute d'échange PV ↔ épée : toute la part
                RelicEffect.SMOKE, RelicEffect.BARRIER, RelicEffect.REGEN,
                RelicEffect.STONESKIN, RelicEffect.CHARM -> RelicBudget.share(r)
            }
            assertEquals("$r : (coup + effet) × cibles = 1 épée + la prime",
                1f + RelicBudget.SHARE_PER_TURN * r.cooldown, (hit + effect) * targets, 1e-4f)
            // Un sort qui ne frappe presque plus n'est pas un sort : l'effet coûte trop cher
            if (r.hits) assertTrue("$r frappe encore ($hit)", hit >= 0.3f) else assertEquals(0f, hit)
        }
    }

    @Test
    fun leJetEstBienUnD20() {
        val hero = Hero.starter().apply { addRelic(Relic.ICE_SHARD) }
        val c = Combat(hero, 1, listOf(Enemy(MonsterType.GOBLIN, 1000, 3, 1, 1)), ambush = false, rng = Random(9))
        val save = c.castRelic(Relic.ICE_SHARD, 0, Timing.MISS).main!!.save
        assertNotNull(save)
        assertTrue(save!!.roll in 1..20)
        assertEquals(save.roll + SpellSave.monsterProficiency(1) + MonsterType.GOBLIN.affinity(Element.ICE).saveBonus, save.total)
    }
}
