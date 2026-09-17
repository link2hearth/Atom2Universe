package com.Atom2Universe.app.games.roguelike

import androidx.annotation.StringRes
import com.Atom2Universe.app.R
import kotlin.math.roundToInt
import kotlin.random.Random

// ─── Monstres ───────────────────────────────────────────────────────────────────

/**
 * Stats de base à l'étage 1. Elles grimpent avec l'étage, jamais avec le joueur :
 * c'est ce qui permet à l'équipement de compter.
 *
 * [cadence] : le monstre frappe tous les N tours. Un rat frappe à chaque tour,
 * une grosse brute prend son élan.
 */
enum class MonsterType(
    @StringRes override val labelRes: Int,
    val baseHp: Int, val baseDamage: Int, val cadence: Int,
    val minFloor: Int, val goldMin: Int, val goldMax: Int,
) : Labeled {
    RAT     (R.string.roguelike_monster_rat,      16,  3, 1, 1, 1,  3),
    GOBLIN  (R.string.roguelike_monster_goblin,   24,  4, 1, 1, 2,  5),
    SKELETON(R.string.roguelike_monster_skeleton, 34,  6, 2, 2, 3,  7),
    ORC     (R.string.roguelike_monster_orc,      50, 10, 2, 3, 5, 10),
    DEMON   (R.string.roguelike_monster_demon,    70, 14, 3, 5, 8, 15),
}

class Enemy(
    val type: MonsterType,
    val maxHp: Int,
    val damage: Int,
    val cadence: Int,
    /** Tours restants avant sa prochaine attaque (affiché au-dessus de lui). */
    var countdown: Int,
) {
    var hp = maxHp
    var burnTurns  = 0
    var burnDamage = 0
    val alive get() = hp > 0
}

object Encounters {

    fun hpMult(floor: Int)     = 1f + 0.22f * (floor - 1)
    fun damageMult(floor: Int) = 1f + 0.15f * (floor - 1)

    /** Taille du groupe : seul au début, jusqu'à 3 à partir de l'étage 5. */
    fun groupSize(floor: Int, rng: Random): Int {
        val r = rng.nextFloat()
        return when {
            floor <= 2 -> 1
            floor <= 4 -> if (r < 0.70f) 1 else 2
            else       -> when { r < 0.50f -> 1; r < 0.85f -> 2; else -> 3 }
        }
    }

    fun roll(floor: Int, rng: Random): List<MonsterType> {
        val eligible = MonsterType.entries.filter { it.minFloor <= floor }
        return List(groupSize(floor, rng)) { eligible.random(rng) }
    }

    /**
     * En groupe, chacun frappe moins souvent (cadence + taille − 1) et les attaques sont
     * décalées : trois rats frappent à tour de rôle, pas tous ensemble.
     */
    fun build(types: List<MonsterType>, floor: Int): List<Enemy> = types.mapIndexed { i, t ->
        val cadence = t.cadence + types.size - 1
        Enemy(
            type      = t,
            maxHp     = (t.baseHp * hpMult(floor)).roundToInt(),
            damage    = (t.baseDamage * damageMult(floor)).roundToInt(),
            cadence   = cadence,
            countdown = cadence - (i % cadence),
        )
    }
}

// ─── Reliques ───────────────────────────────────────────────────────────────────

enum class Relic(
    @StringRes override val labelRes: Int,
    val minDamage: Int, val maxDamage: Int, val cooldown: Int,
    val burnTurns: Int, val burnShare: Float,
) : Labeled {
    FIREBALL(R.string.roguelike_relic_fireball, 6, 9, 3, 2, 0.25f),
}

// ─── Combat ─────────────────────────────────────────────────────────────────────

/** Qualité d'un geste en rythme : parade ou frappe. */
enum class Timing { MISS, GOOD, PERFECT }

enum class CombatPhase { PLAYER_TURN, ENEMY_TURN, VICTORY, DEFEAT }

data class HitResult(val target: Int, val damage: Int, val crit: Boolean, val killed: Boolean)
data class BurnTick(val enemy: Int, val damage: Int, val killed: Boolean)
data class EnemyStrike(val enemy: Int, val damage: Int, val parry: Timing)
data class CombatRewards(val gold: Int, val potions: Int, val equipment: List<Equipment>)

/**
 * Un combat au tour par tour. Le moteur ne connaît pas le temps : l'écran mesure le
 * geste du joueur (parade, swipe) et le lui transmet sous forme de [Timing].
 *
 * Déroulé d'un tour :
 *   tour du joueur : [attack], [castRelic] ou [drinkPotion]
 *   tour ennemi    : [startEnemyTurn] (brûlures, liste des attaquants),
 *                    puis [resolveStrike] pour chacun, puis [endEnemyTurn]
 */
class Combat(
    val hero: Hero,
    val floor: Int,
    val enemies: List<Enemy>,
    ambush: Boolean,
    private val rng: Random = Random,
) {
    companion object {
        const val STRIKE_GOOD      = 0.25f
        const val STRIKE_PERFECT   = 0.60f
        const val PARRY_GOOD_MULT  = 0.5f
        const val PARRY_PERFECT_MULT = 0.2f
        const val POTION_DROP      = 0.08f
    }

    /** Pris en embuscade : les monstres frappent avant qu'on puisse agir. */
    var phase = if (ambush) CombatPhase.ENEMY_TURN else CombatPhase.PLAYER_TURN
        private set

    val relicCooldowns = mutableMapOf<Relic, Int>()
    var rewards: CombatRewards? = null
        private set

    fun aliveIndices() = enemies.indices.filter { enemies[it].alive }
    fun canCast(relic: Relic) = phase == CombatPhase.PLAYER_TURN && (relicCooldowns[relic] ?: 0) == 0
    fun canDrinkPotion() = phase == CombatPhase.PLAYER_TURN && hero.potions > 0 && hero.hp < hero.maxHp

    // ── Tour du joueur ──────────────────────────────────────────────────────────

    fun attack(target: Int, timing: Timing): HitResult {
        check(phase == CombatPhase.PLAYER_TURN)
        val raw = rng.nextInt(hero.weaponMin, hero.weaponMax.coerceAtLeast(hero.weaponMin) + 1).toFloat()
        val result = hit(target, raw, timing)
        // Vol de vie : seulement à l'arme
        if (hero.lifeSteal > 0f) hero.heal((result.damage * hero.lifeSteal).roundToInt())
        afterPlayerAction()
        return result
    }

    fun castRelic(relic: Relic, target: Int, timing: Timing): HitResult {
        check(canCast(relic))
        val raw = rng.nextInt(relic.minDamage, relic.maxDamage + 1) * hero.spellMult
        val result = hit(target, raw, timing)
        val e = enemies[target]
        if (e.alive && relic.burnTurns > 0) {
            e.burnTurns  = relic.burnTurns
            e.burnDamage = (result.damage * relic.burnShare).roundToInt().coerceAtLeast(1)
        }
        relicCooldowns[relic] = hero.spellCooldown(relic.cooldown)
        afterPlayerAction()
        return result
    }

    fun drinkPotion(): Int {
        check(canDrinkPotion())
        val before = hero.hp
        hero.potions--
        hero.heal((hero.maxHp * Hero.POTION_HEAL).roundToInt())
        afterPlayerAction()
        return hero.hp - before
    }

    private fun hit(target: Int, raw: Float, timing: Timing): HitResult {
        val e = enemies[target]
        require(e.alive)
        val bonus = when (timing) { Timing.MISS -> 0f; Timing.GOOD -> STRIKE_GOOD; Timing.PERFECT -> STRIKE_PERFECT }
        val crit  = rng.nextFloat() < (hero.critChance + bonus).coerceAtMost(0.95f)
        val dmg   = (if (crit) raw * hero.critMult else raw).roundToInt().coerceAtLeast(1)
        e.hp = (e.hp - dmg).coerceAtLeast(0)
        return HitResult(target, dmg, crit, !e.alive)
    }

    private fun afterPlayerAction() {
        phase = if (aliveIndices().isEmpty()) win() else CombatPhase.ENEMY_TURN
    }

    // ── Tour des ennemis ────────────────────────────────────────────────────────

    /** Applique les brûlures et renvoie la liste des ennemis qui frappent ce tour. */
    fun startEnemyTurn(): Pair<List<BurnTick>, List<Int>> {
        check(phase == CombatPhase.ENEMY_TURN)
        val burns = mutableListOf<BurnTick>()
        for (i in aliveIndices()) {
            val e = enemies[i]
            if (e.burnTurns > 0) {
                e.hp = (e.hp - e.burnDamage).coerceAtLeast(0)
                e.burnTurns--
                burns += BurnTick(i, e.burnDamage, !e.alive)
            }
        }
        if (aliveIndices().isEmpty()) { phase = win(); return burns to emptyList() }

        val attackers = aliveIndices().filter { i ->
            val e = enemies[i]
            e.countdown--
            if (e.countdown <= 0) { e.countdown = e.cadence; true } else false
        }
        return burns to attackers
    }

    fun resolveStrike(enemyIndex: Int, parry: Timing): EnemyStrike {
        check(phase == CombatPhase.ENEMY_TURN)
        val e = enemies[enemyIndex]
        val parryMult = when (parry) { Timing.MISS -> 1f; Timing.GOOD -> PARRY_GOOD_MULT; Timing.PERFECT -> PARRY_PERFECT_MULT }
        val spread = 0.85f + rng.nextFloat() * 0.30f
        val dmg = hero.mitigate(e.damage * spread * parryMult, floor).roundToInt().coerceAtLeast(1)
        hero.hp = (hero.hp - dmg).coerceAtLeast(0)
        if (hero.hp == 0) phase = CombatPhase.DEFEAT
        return EnemyStrike(enemyIndex, dmg, parry)
    }

    fun endEnemyTurn() {
        if (phase != CombatPhase.ENEMY_TURN) return
        for (r in relicCooldowns.keys) relicCooldowns[r] = (relicCooldowns[r]!! - 1).coerceAtLeast(0)
        phase = CombatPhase.PLAYER_TURN
    }

    // ── Victoire ────────────────────────────────────────────────────────────────

    private fun win(): CombatPhase {
        var gold = 0; var potions = 0
        val loot = mutableListOf<Equipment>()
        val floorGold = 1f + 0.10f * (floor - 1)
        for (e in enemies) {
            gold += (rng.nextInt(e.type.goldMin, e.type.goldMax + 1) * floorGold * hero.goldMult).roundToInt()
            if (rng.nextFloat() < POTION_DROP) potions++
            LootSystem.tryDrop(floor, hero.nextLootId, rng)?.let { loot += it; hero.nextLootId++ }
        }
        rewards = CombatRewards(gold, potions, loot)
        return CombatPhase.VICTORY
    }
}
