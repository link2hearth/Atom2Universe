package com.Atom2Universe.app.games.particules

import androidx.annotation.StringRes
import com.Atom2Universe.app.R

/**
 * Système de reliques (bonus passifs) collectées pendant un run rogue-like.
 * Chaque relique modifie le comportement du jeu pour le reste du run.
 */
enum class Rarity { COMMON, RARE, EPIC }

enum class RelicId {
    PADDLE_ZEPHYR,     // +15% largeur paddle
    HEAVY_BALL,        // 25% chance d'infliger 2 dégâts
    STONE_HEART,       // +1 vie maximum
    EXPLOSIVE_FUSE,    // rayon des explosives +50%
    MAGNET_START,      // 4s d'aimant au début de chaque niveau
    COLLECTOR,         // +1 pièce par brique
    PRESSURE,          // vitesse de balle -5%
    LUCKY,             // 10% de chance de drop sur briques simples
    PIERCE_START,      // 3s de pierce au début de chaque niveau
    SHIELD_START,      // 1 bouclier offert au début de chaque niveau
    GUARDIAN_LASER,    // 4s de laser au début de chaque niveau
    RESONANCE,         // +50% durée du combo
    MULTI_START,       // 2 balles au début de chaque niveau
    FIRE_HEART,        // 3s de feu au début de chaque niveau
    BOSS_SLAYER,       // ×2 pièces sur niveaux boss
    COMBO_GREED,       // ×2 pièces dès combo 5
    PADDLE_GRAVITY,    // paddle plus réactif (spin ×2)
    PADDLE_SPRINT,     // paddle +50% de vitesse de déplacement
    SECOND_CHANCE,     // revient avec 1 vie en cas de game over, une seule fois
    PANIC_TRAINER      // mode contact s'active au bout de 30s (au lieu de 60s)
}

data class Relic(val id: RelicId, @StringRes val nameRes: Int, @StringRes val descRes: Int, val rarity: Rarity)

object RelicCatalog {
    val ALL = listOf(
        Relic(RelicId.PADDLE_ZEPHYR,    R.string.particules_relic_paddle_zephyr_name,   R.string.particules_relic_paddle_zephyr_desc,   Rarity.COMMON),
        Relic(RelicId.HEAVY_BALL,       R.string.particules_relic_heavy_ball_name,      R.string.particules_relic_heavy_ball_desc,      Rarity.COMMON),
        Relic(RelicId.STONE_HEART,      R.string.particules_relic_stone_heart_name,     R.string.particules_relic_stone_heart_desc,     Rarity.RARE),
        Relic(RelicId.EXPLOSIVE_FUSE,   R.string.particules_relic_explosive_fuse_name,  R.string.particules_relic_explosive_fuse_desc,  Rarity.COMMON),
        Relic(RelicId.MAGNET_START,     R.string.particules_relic_magnet_start_name,    R.string.particules_relic_magnet_start_desc,    Rarity.COMMON),
        Relic(RelicId.COLLECTOR,        R.string.particules_relic_collector_name,       R.string.particules_relic_collector_desc,       Rarity.COMMON),
        Relic(RelicId.PRESSURE,         R.string.particules_relic_pressure_name,        R.string.particules_relic_pressure_desc,        Rarity.COMMON),
        Relic(RelicId.LUCKY,            R.string.particules_relic_lucky_name,           R.string.particules_relic_lucky_desc,           Rarity.RARE),
        Relic(RelicId.PIERCE_START,     R.string.particules_relic_pierce_start_name,    R.string.particules_relic_pierce_start_desc,    Rarity.RARE),
        Relic(RelicId.SHIELD_START,     R.string.particules_relic_shield_start_name,    R.string.particules_relic_shield_start_desc,    Rarity.RARE),
        Relic(RelicId.GUARDIAN_LASER,   R.string.particules_relic_guardian_laser_name,  R.string.particules_relic_guardian_laser_desc,  Rarity.RARE),
        Relic(RelicId.RESONANCE,        R.string.particules_relic_resonance_name,       R.string.particules_relic_resonance_desc,       Rarity.COMMON),
        Relic(RelicId.MULTI_START,      R.string.particules_relic_multi_start_name,     R.string.particules_relic_multi_start_desc,     Rarity.EPIC),
        Relic(RelicId.FIRE_HEART,       R.string.particules_relic_fire_heart_name,      R.string.particules_relic_fire_heart_desc,      Rarity.RARE),
        Relic(RelicId.BOSS_SLAYER,      R.string.particules_relic_boss_slayer_name,     R.string.particules_relic_boss_slayer_desc,     Rarity.COMMON),
        Relic(RelicId.COMBO_GREED,      R.string.particules_relic_combo_greed_name,     R.string.particules_relic_combo_greed_desc,     Rarity.COMMON),
        Relic(RelicId.PADDLE_GRAVITY,   R.string.particules_relic_paddle_gravity_name,  R.string.particules_relic_paddle_gravity_desc,  Rarity.COMMON),
        Relic(RelicId.PADDLE_SPRINT,    R.string.particules_relic_paddle_sprint_name,   R.string.particules_relic_paddle_sprint_desc,   Rarity.COMMON),
        Relic(RelicId.SECOND_CHANCE,    R.string.particules_relic_second_chance_name,   R.string.particules_relic_second_chance_desc,   Rarity.EPIC),
        Relic(RelicId.PANIC_TRAINER,    R.string.particules_relic_panic_trainer_name,   R.string.particules_relic_panic_trainer_desc,   Rarity.RARE)
    )

    fun byId(id: RelicId): Relic = ALL.first { it.id == id }

    /** Tire n reliques distinctes, biaisées par la rareté. */
    fun roll(owned: Set<RelicId>, n: Int, rareBoost: Boolean = false): List<Relic> {
        val pool = ALL.filter { it.id !in owned }
        if (pool.isEmpty()) return emptyList()
        val weighted = pool.flatMap { r ->
            val w = when (r.rarity) {
                Rarity.COMMON -> if (rareBoost) 2 else 5
                Rarity.RARE   -> if (rareBoost) 5 else 2
                Rarity.EPIC   -> if (rareBoost) 3 else 1
            }
            List(w) { r }
        }.toMutableList()
        val picks = mutableListOf<Relic>()
        repeat(minOf(n, pool.size)) {
            if (weighted.isEmpty()) return@repeat
            val pick = weighted.random()
            picks.add(pick)
            weighted.removeAll { it.id == pick.id }
        }
        return picks
    }
}
