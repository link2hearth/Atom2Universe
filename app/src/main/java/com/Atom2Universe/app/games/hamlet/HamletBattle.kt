package com.Atom2Universe.app.games.hamlet

import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

enum class BattleMode { CAMPAIGN, TRIAL, BOUNTY, RAID, ARENA }
enum class BattleOutcome { RUNNING, VICTORY, DEFEAT }
enum class BattleTactic { BALANCED, ASSAULT, DEFENSIVE, CONTROL }
enum class BattleField {
    MEADOW, MIST, EMBER, QUARRY, MARSH, FROST;
    companion object {
        fun fromRegion(region: Int) = entries[Math.floorMod(region, entries.size)]
    }
}
enum class BossPattern { CLEAVE, DEVASTATION, DREAD }
enum class BattleEventKind { HIT, HEAL, SHIELD, SKILL, SUMMON, DODGE, TELEGRAPH }

data class BattleHeroReport(val index: Int, val sprite: Int, val damage: Double, val healing: Double,
    val protection: Double, val kills: Int)
data class BattleWarning(val source: Int, val pattern: BossPattern, val left: Float, val targets: List<Int>)

/** [critical] only changes how the hit is shown. */
data class BattleEvent(val source: Int, val target: Int, val value: Int, val kind: BattleEventKind, val time: Float,
    val critical: Boolean = false)

class BattleUnit(val index: Int, val sprite: Int, val enemy: Boolean, val faction: HeroFaction,
    val role: HeroRole, val slot: Int, val maxHp: Double, val attack: Double, val armor: Double,
    val interval: Float, val skillPeriod: Float, val ultimatePeriod: Float,
    val crit: Double = 0.03, val skillBoost: Double = 1.0, val reduction: Double = 0.0,
    val heroVisual: Boolean = !enemy, val summoned: Boolean = false, val ability: HeroAbility = HeroAbility.NONE,
    val healingBonus: Double = 0.0, val lifesteal: Double = 0.0, val armorPierce: Double = 0.0,
    val openingShield: Double = 0.0, val boss: Boolean = false) {
    var hp = maxHp
    var shield = 0.0
    var attackLeft = interval * (0.3f + slot * 0.09f)
    var skillLeft = skillPeriod * 0.5f
    var ultimateLeft = ultimatePeriod * 0.75f
    var stun = 0f
    var poison = 0f
    var poisonAttack = 0.0
    var poisonSource = -1
    /** Environmental venom stays separate from a rogue's poison, so purging clears both fairly. */
    var venom = 0f
        internal set
    var buff = 0f
    var weakened = 0f
    var flash = 0f
    var lunge = 0f
    var summonLeft = 0f
    var telegraphLeft = 0f
        internal set
    val bossPattern = BossPattern.entries[Math.floorMod(sprite, BossPattern.entries.size)]
    internal var chargedUltimate = false
    internal val shieldOwners = LinkedHashMap<Int, Double>()
    internal var summonerIndex = -1
    val alive get() = hp > 0
    val front get() = slot < 2
}

/** Combat à pas fixes, identique en simulation et sur écran. Pas de tirage d'issue par "puissance".
 * Les soins, boucliers, rangées, contre-factions, contrôles et invocations sont réellement joués. */
class HamletBattle(val mode: BattleMode, val stage: Int, val region: Int, val units: MutableList<BattleUnit>,
    val rng: Random, val relic: Int = -1, val relicLevel: Int = 0, val pet: Int = -1, val petLevel: Int = 0,
    val autoUltimates: Boolean = true, val tactic: BattleTactic = BattleTactic.BALANCED) {
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
    val field = BattleField.fromRegion(region)
    private var accumulator = 0.0
    private var supportLeft = 5f
    private var fieldLeft = if (field == BattleField.MIST) 8f else 10f
    private var nextIndex = (units.maxOfOrNull { it.index } ?: 0) + 1
    private class Contribution(var damage: Double = 0.0, var healing: Double = 0.0,
        var protection: Double = 0.0, var kills: Int = 0)
    private val contributions = HashMap<Int, Contribution>()

    init {
        for (u in units) {
            contributions[u.index] = Contribution()
            val opening = u.openingShield.coerceIn(0.0, 0.6) +
                if (!u.enemy && tactic == BattleTactic.DEFENSIVE && u.front) 0.12 else 0.0
            if (opening > 0) shield(u, u, u.maxHp * opening)
        }
    }

    /** Actual health restored/lost and shield damage absorbed, excluding overkill and overheal. */
    fun report(): List<BattleHeroReport> = units.filter { !it.enemy && !it.summoned }.map { u ->
        val s = contributions[u.index] ?: Contribution()
        BattleHeroReport(u.index, u.sprite, s.damage, s.healing, s.protection, s.kills)
    }

    fun warnings(): List<BattleWarning> = units.filter { it.alive && it.boss && it.telegraphLeft > 0 }.map { u ->
        BattleWarning(u.index, u.bossPattern, u.telegraphLeft, bossTargets(u).map { it.index })
    }

    fun advance(seconds: Float) {
        if (outcome != BattleOutcome.RUNNING || !seconds.isFinite() || seconds <= 0) return
        accumulator += seconds.coerceAtMost(120f)
        while (accumulator + 1e-8 >= STEP_SECONDS && outcome == BattleOutcome.RUNNING) {
            step(); accumulator -= STEP_SECONDS
        }
    }
    private fun step() {
        time += STEP
        events.removeAll { time - it.time > 1.4f }
        // Newly summoned units start on the following tick, without allocating a copy each step.
        val unitCount = units.size
        for (index in 0 until unitCount) {
            val u = units[index]
            if (!u.alive) continue
            u.flash = max(0f, u.flash - STEP); u.lunge = max(0f, u.lunge - STEP)
            u.buff = max(0f, u.buff - STEP)
            u.weakened = max(0f, u.weakened - STEP)
            if (u.summonLeft > 0) {
                u.summonLeft -= STEP
                if (u.summonLeft <= 0) { u.hp = 0.0; continue }
            }
            if (u.venom > 0) {
                u.venom = max(0f, u.venom - STEP)
                damageHealth(FIELD_SOURCE, u, u.maxHp * .006 * STEP * tacticDamage(true, u.enemy), animate = false)
                if (!u.alive) continue
            }
            if (u.poison > 0) {
                u.poison -= STEP
                val sourceEnemy = units.firstOrNull { it.index == u.poisonSource }?.enemy ?: !u.enemy
                damageHealth(u.poisonSource, u, u.poisonAttack * STEP * tacticDamage(sourceEnemy, u.enemy), animate = false)
                if (!u.alive) continue
            }
            if (u.stun > 0) { u.stun -= STEP; continue }
            val attackSpeed = (if (!u.enemy && tactic == BattleTactic.ASSAULT) 1.08f else 1f) *
                if (field == BattleField.FROST) .88f else 1f
            val skillSpeed = if (!u.enemy && tactic == BattleTactic.CONTROL) 1.2f else 1f
            u.attackLeft -= STEP * attackSpeed; u.skillLeft -= STEP * skillSpeed; u.ultimateLeft -= STEP
            if (u.boss) {
                if (u.telegraphLeft > 0) {
                    u.telegraphLeft = max(0f, u.telegraphLeft - STEP)
                    if (u.telegraphLeft <= 0) bossStrike(u)
                    continue
                }
                if (u.skillLeft <= 0 || u.ultimateLeft <= 0) {
                    u.chargedUltimate = u.ultimateLeft <= 0
                    u.telegraphLeft = 1.5f
                    if (u.skillLeft <= 0) u.skillLeft += u.skillPeriod
                    if (u.chargedUltimate) u.ultimateLeft += u.ultimatePeriod
                    events.add(BattleEvent(u.index, u.index, u.bossPattern.ordinal, BattleEventKind.TELEGRAPH, time))
                    continue
                }
            }
            if (u.attackLeft <= 0) {
                target(u)?.let { hit(u, it, 1.0) }
                u.attackLeft += u.interval
            }
            if (!u.boss && u.skillLeft <= 0) { cast(u, false); u.skillLeft += u.skillPeriod }
            if (!u.boss && (auto || u.enemy) && u.ultimateLeft <= 0 && shouldAutoUltimate(u)) {
                cast(u, true); u.ultimateLeft += u.ultimatePeriod
            }
        }
        supportLeft -= STEP
        if (supportLeft <= 0) { support(); supportLeft += 9f }
        fieldLeft -= STEP
        if (fieldLeft <= 0) {
            fieldPulse(); fieldLeft += if (field == BattleField.MIST) 8f else 10f
        }
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
    private fun shouldAutoUltimate(u: BattleUnit): Boolean {
        if (u.enemy || tactic != BattleTactic.DEFENSIVE) return true
        return when (u.role) {
            HeroRole.GUARDIAN, HeroRole.HEALER, HeroRole.BARD ->
                allies(u).any { it.hp < it.maxHp * .8 } || units.any { it.enemy && it.alive && it.telegraphLeft > 0 }
            else -> true
        }
    }
    private fun target(u: BattleUnit): BattleUnit? {
        val targets = foes(u)
        if (u.role == HeroRole.ROGUE) return targets.filter { !it.front }.minByOrNull { it.hp } ?: targets.minByOrNull { it.hp }
        if (!u.enemy && tactic == BattleTactic.CONTROL && u.role in CONTROL_ROLES) {
            targets.filter { it.role in THREAT_ROLES }.minByOrNull { it.hp }?.let { return it }
        }
        if (!u.enemy && tactic == BattleTactic.ASSAULT) return targets.filter { it.front }.minByOrNull { it.hp } ?: targets.minByOrNull { it.hp }
        return targets.filter { it.front }.minByOrNull { it.slot } ?: targets.minByOrNull { it.hp }
    }
    private fun hit(u: BattleUnit, victim: BattleUnit, coefficient: Double, area: Boolean = false) {
        if (!victim.alive) return
        val critical = if (rng.nextDouble() < u.crit) 1.5 else 1.0
        val faction = HamletHeroes.counter(u.faction, victim.faction)
        // Quarry plating changes which attackers work best, rather than raising every HP bar.
        val plating = if (field == BattleField.QUARRY && victim.enemy && u.role !in SPELL_ROLES) 25.0 else 0.0
        val defense = 100.0 / (100 + (victim.armor + plating) * (1 - u.armorPierce.coerceIn(0.0, 0.7)))
        val rage = 1.0 + max(0f, time - 45f) * 0.018
        val pressure = if (field == BattleField.EMBER) 1.0 + ((time - 20f).coerceIn(0f, 15f) * .01) else 1.0
        var value = u.attack * coefficient * faction * defense * critical * (if (u.buff > 0) 1.2 else 1.0) * (if (u.weakened > 0) 0.8 else 1.0) * (1 - victim.reduction) * rage
        value *= pressure
        value *= tacticDamage(u.enemy, victim.enemy)
        if (victim.shield > 0) {
            val absorbed = min(victim.shield, value); victim.shield -= absorbed; value -= absorbed
            var left = absorbed
            val owners = victim.shieldOwners.iterator()
            while (owners.hasNext() && left > 0) {
                val owner = owners.next(); val part = min(left, owner.value)
                contributions.getOrPut(owner.key) { Contribution() }.protection += part
                owner.setValue(owner.value - part); left -= part
                if (owner.value <= 0.00001) owners.remove()
            }
        }
        value = max(0.0, value)
        val dealt = damageHealth(u.index, victim, value, critical = critical > 1)
        if (!area) u.lunge = 0.22f
        if (dealt > 0 && u.lifesteal > 0 && u.alive) restore(u, u, dealt * u.lifesteal.coerceIn(0.0, 0.5))
    }
    private fun tacticDamage(sourceEnemy: Boolean, targetEnemy: Boolean): Double {
        val dealt = if (sourceEnemy) 1.0 else when (tactic) {
            BattleTactic.ASSAULT -> 1.15; BattleTactic.DEFENSIVE -> .9; BattleTactic.CONTROL -> .95; else -> 1.0
        }
        val taken = if (targetEnemy) 1.0 else when (tactic) {
            BattleTactic.ASSAULT -> 1.1; BattleTactic.DEFENSIVE -> .85; else -> 1.0
        }
        return dealt * taken
    }
    private fun damageHealth(source: Int, victim: BattleUnit, value: Double, animate: Boolean = true, critical: Boolean = false): Double {
        val dealt = min(victim.hp.coerceAtLeast(0.0), value.coerceAtLeast(0.0))
        val wasAlive = victim.alive
        victim.hp = max(0.0, victim.hp - dealt)
        val sourceUnit = units.firstOrNull { it.index == source }
        val credited = sourceUnit?.summonerIndex?.takeIf { it >= 0 } ?: source
        val stats = contributions.getOrPut(credited) { Contribution() }
        stats.damage += dealt
        if (wasAlive && !victim.alive) stats.kills++
        if (sourceUnit?.enemy == false) damage += dealt
        if (animate) {
            victim.flash = 0.16f
            events.add(BattleEvent(source, victim.index, dealt.toInt(), BattleEventKind.HIT, time, critical))
        }
        return dealt
    }
    private fun heal(u: BattleUnit, target: BattleUnit, coefficient: Double) {
        val tacticBoost = if (!u.enemy && tactic == BattleTactic.DEFENSIVE) 1.15 else 1.0
        restore(u, target, u.attack * coefficient * u.skillBoost * tacticBoost * (1 + u.healingBonus.coerceIn(0.0, 2.0)))
    }
    private fun restore(u: BattleUnit, target: BattleUnit, requested: Double) {
        if (!target.alive) return
        val amount = min(target.maxHp - target.hp, requested).coerceAtLeast(0.0)
        target.hp = min(target.maxHp, target.hp + amount)
        if (!u.enemy) healing += amount
        contributions.getOrPut(u.index) { Contribution() }.healing += amount
        events.add(BattleEvent(u.index, target.index, amount.toInt(), BattleEventKind.HEAL, time))
    }
    private fun shield(u: BattleUnit, target: BattleUnit, amount: Double) {
        val previous = target.shield
        target.shield = min(target.maxHp * 0.6, target.shield + amount)
        val granted = max(0.0, target.shield - previous)
        target.shieldOwners[u.index] = (target.shieldOwners[u.index] ?: 0.0) + granted
        events.add(BattleEvent(u.index, target.index, granted.toInt(), BattleEventKind.SHIELD, time))
    }
    private fun cast(u: BattleUnit, ultimate: Boolean) {
        val allies = allies(u)
        val foes = foes(u)
        val weakest = allies.minByOrNull { it.hp / it.maxHp }
        if (foes.isEmpty()) return
        events.add(BattleEvent(u.index, u.index, if (ultimate) 1 else 0, BattleEventKind.SKILL, time))
        val boost = u.skillBoost * if (ultimate) 1.6 else 1.0
        when (u.role) {
            HeroRole.GUARDIAN -> {
                (if (ultimate) allies else listOf(u)).forEach { shield(u, it, u.attack * 3.5 * boost) }
                target(u)?.let { hit(u, it, 1.5 * boost); stun(u, it, 0.7f) }
            }
            HeroRole.HEALER -> {
                (if (ultimate) allies else listOfNotNull(weakest)).forEach { heal(u, it, 4.2 * boost) }
                if (u.heroVisual && u.sprite == 8) allies.forEach { shield(u, it, u.attack * boost) }
            }
            HeroRole.BARD -> {
                allies.forEach { it.buff = if (ultimate) 8f else 5f }
                allies.minByOrNull { it.hp / it.maxHp }?.let { heal(u, it, 2.0 * boost) }
                target(u)?.let { hit(u, it, 1.1 * boost) }
            }
            HeroRole.MAGE -> {
                foes.take(if (ultimate) 6 else 3).forEach { hit(u, it, 1.75 * boost, true) }
                if (u.heroVisual && u.sprite == 10 || !u.heroVisual && u.sprite == 1) foes.take(2).forEach { stun(u, it, 1.0f) }
            }
            HeroRole.SUMMONER -> {
                if (allies.count { it.summonLeft > 0 } < 2) {
                    val summon = BattleUnit(nextIndex++, 0, u.enemy, u.faction, HeroRole.DUELIST, 1,
                        u.maxHp * 0.4 * boost, u.attack * 0.55 * boost, u.armor, 1.2f, 60f, 100f, heroVisual = false, summoned = true)
                    summon.summonLeft = 12f; summon.summonerIndex = u.index; units.add(summon)
                    events.add(BattleEvent(u.index, summon.index, 0, BattleEventKind.SUMMON, time))
                }
                target(u)?.let { hit(u, it, 1.4 * boost) }
            }
            HeroRole.ENGINEER -> {
                foes.take(if (ultimate) 5 else 2).forEach { hit(u, it, 1.6 * boost, true); stun(u, it, 0.5f) }
                allies.filter { it.front }.forEach { shield(u, it, u.attack * 1.5 * boost) }
            }
            HeroRole.ROGUE -> {
                val target = foes.filter { !it.front }.minByOrNull { it.hp } ?: foes.minByOrNull { it.hp }
                target?.let { hit(u, it, 3.1 * boost); it.poison = 5f; it.poisonAttack = u.attack * 0.2 * boost; it.poisonSource = u.index }
            }
            HeroRole.RANGER -> (if (ultimate) foes.take(3) else listOfNotNull(target(u))).forEach { hit(u, it, 2.3 * boost, ultimate) }
            HeroRole.DUELIST -> target(u)?.let { hit(u, it, 2.6 * boost); if (ultimate) heal(u, u, 2.0) }
        }
        when (u.ability) {
            HeroAbility.NONE -> Unit
            HeroAbility.WARD -> allies.filter { it.front }.forEach { shield(u, it, u.attack * 0.8 * boost) }
            HeroAbility.PURIFY -> (if (ultimate) allies else listOfNotNull(weakest)).forEach {
                it.poison = 0f; it.poisonAttack = 0.0; it.venom = 0f; it.weakened = 0f; it.stun = 0f
            }
            HeroAbility.FROST -> foes.filter { it.alive }.take(if (ultimate) 3 else 1).forEach { stun(u, it, if (ultimate) 0.8f else 0.4f) }
            HeroAbility.EXPOSE -> foes.filter { it.alive }.take(if (ultimate) 3 else 1).forEach { it.weakened = max(it.weakened, if (ultimate) 5f else 3f) }
            HeroAbility.REJUVENATE -> allies.minByOrNull { it.hp / it.maxHp }?.let { heal(u, it, 1.0 * boost) }
            HeroAbility.EXECUTION -> foes.filter { it.alive && it.hp < it.maxHp * 0.35 }.minByOrNull { it.hp }?.let { hit(u, it, 1.0 * boost) }
        }
    }
    private fun stun(u: BattleUnit, target: BattleUnit, seconds: Float) {
        val duration = seconds * if (!u.enemy && tactic == BattleTactic.CONTROL) 1.35f else 1f
        target.stun = max(target.stun, duration)
    }
    private fun bossTargets(u: BattleUnit): List<BattleUnit> {
        val targets = foes(u)
        return when (u.bossPattern) {
            BossPattern.CLEAVE -> targets.filter { it.front }.ifEmpty { targets.take(2) }
            BossPattern.DEVASTATION -> targets
            BossPattern.DREAD -> targets.filter { !it.front }.ifEmpty { targets.takeLast(2) }
        }
    }
    private fun bossStrike(u: BattleUnit) {
        events.add(BattleEvent(u.index, u.index, if (u.chargedUltimate) 1 else 0, BattleEventKind.SKILL, time))
        val power = (if (u.chargedUltimate) 1.4 else 1.0) * u.skillBoost
        for (target in bossTargets(u)) {
            hit(u, target, (if (u.bossPattern == BossPattern.DEVASTATION) 1.7 else 2.5) * power, true)
            if (u.bossPattern == BossPattern.DREAD && target.alive) target.weakened = max(target.weakened, 4f)
        }
        u.chargedUltimate = false
        // Charging replaces ordinary attacks; it must never bank a burst of overdue strikes.
        u.attackLeft = max(u.attackLeft, u.interval * .5f)
    }
    private fun fieldPulse() {
        when (field) {
            BattleField.MIST -> {
                // Both camps follow the same support rule, including enemy guardians.
                for (u in units) if (u.alive && u.stun <= 0 && u.role in SUPPORT_ROLES) {
                    allies(u).minByOrNull { it.hp / it.maxHp }?.let { shield(u, it, u.attack * 1.5) }
                }
            }
            BattleField.MARSH -> units.filter { it.alive }.forEach { it.venom = 4f }
            else -> Unit
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
        private const val STEP_SECONDS = 0.05
        private const val FIELD_SOURCE = -2
        private val CONTROL_ROLES = setOf(HeroRole.RANGER, HeroRole.MAGE, HeroRole.ENGINEER)
        private val THREAT_ROLES = setOf(HeroRole.MAGE, HeroRole.HEALER, HeroRole.SUMMONER)
        private val SPELL_ROLES = setOf(HeroRole.MAGE, HeroRole.ENGINEER)
        private val SUPPORT_ROLES = setOf(HeroRole.GUARDIAN, HeroRole.HEALER, HeroRole.BARD, HeroRole.ENGINEER)
        fun enemy(sprite: Int, index: Int, slot: Int, scale: Double, boss: Boolean): BattleUnit {
            val faction = HeroFaction.entries[(sprite + 1) % 3]
            val role = when (sprite) { 1, 6 -> HeroRole.MAGE; 2 -> HeroRole.ROGUE; 3, 4 -> HeroRole.GUARDIAN; 7 -> HeroRole.SUMMONER; else -> HeroRole.DUELIST }
            val hp = if (boss) 1800.0 else when (sprite) { 3, 4 -> 450.0; 1 -> 220.0; else -> 310.0 }
            val attack = if (boss) 62.0 else when (sprite) { 1, 2 -> 36.0; 3, 4 -> 26.0; else -> 30.0 }
            return BattleUnit(index, sprite, true, faction, role, slot, hp * scale, attack * scale,
                (if (boss) 32.0 else 12.0) + scale * 2, if (sprite == 2) 1.25f else 1.7f,
                if (boss) 8f else 12f, if (boss) 19f else 30f, boss = boss)
        }
    }
}
