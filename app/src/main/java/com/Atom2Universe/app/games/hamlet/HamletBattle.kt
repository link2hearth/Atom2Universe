package com.Atom2Universe.app.games.hamlet

import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

enum class BattleMode { CAMPAIGN, TRIAL, BOUNTY, RAID, ARENA }
enum class BattleOutcome { RUNNING, VICTORY, DEFEAT }
enum class BattleEventKind { HIT, HEAL, SHIELD, SKILL, SUMMON, DODGE }

data class BattleEvent(val source: Int, val target: Int, val value: Int, val kind: BattleEventKind, val time: Float)

class BattleUnit(val index: Int, val sprite: Int, val enemy: Boolean, val faction: HeroFaction,
    val role: HeroRole, val slot: Int, val maxHp: Double, val attack: Double, val armor: Double,
    val interval: Float, val skillPeriod: Float, val ultimatePeriod: Float,
    val crit: Double = 0.03, val skillBoost: Double = 1.0, val reduction: Double = 0.0,
    val heroVisual: Boolean = !enemy, val summoned: Boolean = false) {
    var hp = maxHp
    var shield = 0.0
    var attackLeft = interval * (0.3f + slot * 0.09f)
    var skillLeft = skillPeriod * 0.5f
    var ultimateLeft = ultimatePeriod * 0.75f
    var stun = 0f
    var poison = 0f
    var poisonAttack = 0.0
    var buff = 0f
    var weakened = 0f
    var flash = 0f
    var lunge = 0f
    var summonLeft = 0f
    val alive get() = hp > 0
    val front get() = slot < 2
}

/** Combat à pas fixes, identique en simulation et sur écran. Pas de tirage d'issue par "puissance".
 * Les soins, boucliers, rangées, contre-factions, contrôles et invocations sont réellement joués. */
class HamletBattle(val mode: BattleMode, val stage: Int, val region: Int, val units: MutableList<BattleUnit>,
    val rng: Random, val relic: Int = -1, val relicLevel: Int = 0, val pet: Int = -1, val petLevel: Int = 0,
    val autoUltimates: Boolean = true) {
    var time = 0f
        private set
    var outcome = BattleOutcome.RUNNING
        private set
    var settled = false
    var auto = autoUltimates
    val events = ArrayList<BattleEvent>()
    var damage = 0.0
        private set
    var healing = 0.0
        private set
    private var accumulator = 0.0
    private var supportLeft = 5f
    private var nextIndex = (units.maxOfOrNull { it.index } ?: 0) + 1

    fun advance(seconds: Float) {
        if (outcome != BattleOutcome.RUNNING || !seconds.isFinite() || seconds <= 0) return
        accumulator += seconds.coerceAtMost(120f)
        while (accumulator >= STEP && outcome == BattleOutcome.RUNNING) {
            step(); accumulator -= STEP
        }
    }
    private fun step() {
        time += STEP
        events.removeAll { time - it.time > 1.4f }
        for (u in units.toList()) {
            if (!u.alive) continue
            u.flash = max(0f, u.flash - STEP); u.lunge = max(0f, u.lunge - STEP)
            u.buff = max(0f, u.buff - STEP)
            u.weakened = max(0f, u.weakened - STEP)
            if (u.summonLeft > 0) { u.summonLeft -= STEP; if (u.summonLeft <= 0) u.hp = 0.0 }
            if (u.poison > 0) {
                u.poison -= STEP
                u.hp = max(0.0, u.hp - u.poisonAttack * STEP)
                if (!u.alive) continue
            }
            if (u.stun > 0) { u.stun -= STEP; continue }
            u.attackLeft -= STEP; u.skillLeft -= STEP; u.ultimateLeft -= STEP
            if (u.attackLeft <= 0) {
                target(u)?.let { hit(u, it, 1.0) }
                u.attackLeft += u.interval
            }
            if (u.skillLeft <= 0) { cast(u, false); u.skillLeft += u.skillPeriod }
            if ((auto || u.enemy) && u.ultimateLeft <= 0) { cast(u, true); u.ultimateLeft += u.ultimatePeriod }
        }
        supportLeft -= STEP
        if (supportLeft <= 0) { support(); supportLeft += 9f }
        val allies = units.any { !it.enemy && it.alive && !it.summoned }
        val foes = units.any { it.enemy && it.alive }
        outcome = when { !allies -> BattleOutcome.DEFEAT; !foes -> BattleOutcome.VICTORY; time >= 90f -> BattleOutcome.DEFEAT; else -> BattleOutcome.RUNNING }
    }
    fun ultimate(index: Int): Boolean {
        val u = units.firstOrNull { it.index == index } ?: return false
        if (outcome != BattleOutcome.RUNNING || u.enemy || u.summoned || !u.alive || u.stun > 0 || u.ultimateLeft > 0) return false
        cast(u, true); u.ultimateLeft = u.ultimatePeriod; return true
    }
    fun readyUltimates() = units.filter { !it.enemy && !it.summoned && it.alive && it.stun <= 0 && it.ultimateLeft <= 0 }.map { it.index }
    private fun allies(u: BattleUnit) = units.filter { it.enemy == u.enemy && it.alive }
    private fun foes(u: BattleUnit) = units.filter { it.enemy != u.enemy && it.alive }
    private fun target(u: BattleUnit): BattleUnit? {
        val targets = foes(u)
        if (u.role == HeroRole.ROGUE) return targets.filter { !it.front }.minByOrNull { it.hp } ?: targets.minByOrNull { it.hp }
        return targets.filter { it.front }.minByOrNull { it.slot } ?: targets.minByOrNull { it.hp }
    }
    private fun hit(u: BattleUnit, victim: BattleUnit, coefficient: Double, area: Boolean = false) {
        if (!victim.alive) return
        val critical = if (rng.nextDouble() < u.crit) 1.5 else 1.0
        val faction = HamletHeroes.counter(u.faction, victim.faction)
        val defense = 100.0 / (100 + victim.armor)
        val rage = 1.0 + max(0f, time - 45f) * 0.018
        var value = u.attack * coefficient * faction * defense * critical * (if (u.buff > 0) 1.2 else 1.0) * (if (u.weakened > 0) 0.8 else 1.0) * (1 - victim.reduction) * rage
        if (victim.shield > 0) { val absorbed = min(victim.shield, value); victim.shield -= absorbed; value -= absorbed }
        value = max(0.0, value)
        victim.hp = max(0.0, victim.hp - value)
        victim.flash = 0.16f; if (!area) u.lunge = 0.22f
        if (!u.enemy) damage += value
        events.add(BattleEvent(u.index, victim.index, value.toInt(), BattleEventKind.HIT, time))
    }
    private fun heal(u: BattleUnit, target: BattleUnit, coefficient: Double) {
        val amount = min(target.maxHp - target.hp, u.attack * coefficient * u.skillBoost)
        target.hp = min(target.maxHp, target.hp + amount)
        if (!u.enemy) healing += amount
        events.add(BattleEvent(u.index, target.index, amount.toInt(), BattleEventKind.HEAL, time))
    }
    private fun shield(u: BattleUnit, target: BattleUnit, amount: Double) {
        target.shield = min(target.maxHp * 0.6, target.shield + amount)
        events.add(BattleEvent(u.index, target.index, amount.toInt(), BattleEventKind.SHIELD, time))
    }
    private fun cast(u: BattleUnit, ultimate: Boolean) {
        val allies = allies(u)
        val foes = foes(u)
        if (foes.isEmpty()) return
        events.add(BattleEvent(u.index, u.index, if (ultimate) 1 else 0, BattleEventKind.SKILL, time))
        val boost = u.skillBoost * if (ultimate) 1.6 else 1.0
        when (u.role) {
            HeroRole.GUARDIAN -> {
                (if (ultimate) allies else listOf(u)).forEach { shield(u, it, u.attack * 3.5 * boost) }
                target(u)?.let { hit(u, it, 1.5 * boost); it.stun = 0.7f }
            }
            HeroRole.HEALER -> {
                (if (ultimate) allies else listOfNotNull(allies.minByOrNull { it.hp / it.maxHp })).forEach { heal(u, it, 4.2 * boost) }
                if (u.heroVisual && u.sprite == 8) allies.forEach { shield(u, it, u.attack * boost) }
            }
            HeroRole.BARD -> {
                allies.forEach { it.buff = if (ultimate) 8f else 5f }
                allies.minByOrNull { it.hp / it.maxHp }?.let { heal(u, it, 2.0 * boost) }
                target(u)?.let { hit(u, it, 1.1 * boost) }
            }
            HeroRole.MAGE -> {
                foes.take(if (ultimate) 6 else 3).forEach { hit(u, it, 1.75 * boost, true) }
                if (u.heroVisual && u.sprite == 10 || !u.heroVisual && u.sprite == 1) foes.take(2).forEach { it.stun = 1.0f }
            }
            HeroRole.SUMMONER -> {
                if (allies.count { it.summonLeft > 0 } < 2) {
                    val summon = BattleUnit(nextIndex++, 0, u.enemy, u.faction, HeroRole.DUELIST, 1,
                        u.maxHp * 0.4 * boost, u.attack * 0.55 * boost, u.armor, 1.2f, 60f, 100f, heroVisual = false, summoned = true)
                    summon.summonLeft = 12f; units.add(summon)
                    events.add(BattleEvent(u.index, summon.index, 0, BattleEventKind.SUMMON, time))
                }
                target(u)?.let { hit(u, it, 1.4 * boost) }
            }
            HeroRole.ENGINEER -> {
                foes.take(if (ultimate) 5 else 2).forEach { hit(u, it, 1.6 * boost, true); it.stun = 0.5f }
                allies.filter { it.front }.forEach { shield(u, it, u.attack * 1.5 * boost) }
            }
            HeroRole.ROGUE -> {
                val target = foes.filter { !it.front }.minByOrNull { it.hp } ?: foes.minByOrNull { it.hp }
                target?.let { hit(u, it, 3.1 * boost); it.poison = 5f; it.poisonAttack = u.attack * 0.2 * boost }
            }
            HeroRole.RANGER -> (if (ultimate) foes.take(3) else listOfNotNull(target(u))).forEach { hit(u, it, 2.3 * boost, ultimate) }
            HeroRole.DUELIST -> target(u)?.let { hit(u, it, 2.6 * boost); if (ultimate) heal(u, u, 2.0) }
        }
    }
    private fun support() {
        val team = units.filter { !it.enemy && it.alive }
        val target = team.minByOrNull { it.hp / it.maxHp } ?: return
        if (pet >= 0 && petLevel > 0) {
            if (pet == 0) { target.hp = min(target.maxHp, target.hp + target.maxHp * 0.025 * min(8, petLevel)); events.add(BattleEvent(-1, target.index, 0, BattleEventKind.HEAL, time)) }
            else if (pet == 1) team.forEach { it.buff = 4f }
            else foes(target).forEach { it.weakened = 4f + min(8, petLevel) * 0.25f }
        }
        if (relic >= 0 && relicLevel > 0) {
            val strength = 1 + relicLevel * 0.08
            when (relic) {
                0 -> team.forEach { shield(target, it, it.maxHp * 0.04 * strength) }
                1 -> team.forEach { heal(target, it, 1.2 * strength) }
                else -> foes(target).take(3).forEach { hit(target, it, 0.8 * strength, true) }
            }
        }
    }
    fun survivors() = units.count { !it.enemy && it.alive && !it.summoned }
    companion object {
        const val STEP = 0.05f
        fun enemy(sprite: Int, index: Int, slot: Int, scale: Double, boss: Boolean): BattleUnit {
            val faction = HeroFaction.entries[(sprite + 1) % 3]
            val role = when (sprite) { 1, 6 -> HeroRole.MAGE; 2 -> HeroRole.ROGUE; 3, 4 -> HeroRole.GUARDIAN; 7 -> HeroRole.SUMMONER; else -> HeroRole.DUELIST }
            val hp = if (boss) 1800.0 else when (sprite) { 3, 4 -> 450.0; 1 -> 220.0; else -> 310.0 }
            val attack = if (boss) 62.0 else when (sprite) { 1, 2 -> 36.0; 3, 4 -> 26.0; else -> 30.0 }
            return BattleUnit(index, sprite, true, faction, role, slot, hp * scale, attack * scale,
                (if (boss) 32.0 else 12.0) + scale * 2, if (sprite == 2) 1.25f else 1.7f,
                if (boss) 8f else 12f, if (boss) 19f else 30f)
        }
    }
}
