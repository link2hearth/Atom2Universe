package com.Atom2Universe.app.games.particules

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

data class Relic(val id: RelicId, val name: String, val desc: String, val rarity: Rarity)

object RelicCatalog {
    val ALL = listOf(
        Relic(RelicId.PADDLE_ZEPHYR,    "Zéphyr",          "Raquette +15%",                Rarity.COMMON),
        Relic(RelicId.HEAVY_BALL,       "Balle lourde",    "25% chance de double dégât",   Rarity.COMMON),
        Relic(RelicId.STONE_HEART,      "Cœur de pierre",  "+1 vie maximum",               Rarity.RARE),
        Relic(RelicId.EXPLOSIVE_FUSE,   "Fusible",         "Explosions +50% de rayon",     Rarity.COMMON),
        Relic(RelicId.MAGNET_START,     "Aimant discret",  "4s d'aimant à chaque niveau",  Rarity.COMMON),
        Relic(RelicId.COLLECTOR,        "Collecteur",      "+1 pièce par brique",          Rarity.COMMON),
        Relic(RelicId.PRESSURE,         "Sous pression",   "Vitesse de balle -5%",         Rarity.COMMON),
        Relic(RelicId.LUCKY,            "Chance",          "10% de drop sur briques simples", Rarity.RARE),
        Relic(RelicId.PIERCE_START,     "Percée initiale", "3s de perçage à chaque niveau",Rarity.RARE),
        Relic(RelicId.SHIELD_START,     "Tortue",          "1 bouclier offert par niveau", Rarity.RARE),
        Relic(RelicId.GUARDIAN_LASER,   "Gardien laser",   "4s de laser par niveau",       Rarity.RARE),
        Relic(RelicId.RESONANCE,        "Résonance",       "Combo +50% de durée",          Rarity.COMMON),
        Relic(RelicId.MULTI_START,      "Multi-particules","2 balles au départ",           Rarity.EPIC),
        Relic(RelicId.FIRE_HEART,       "Cœur de feu",     "3s de feu à chaque niveau",    Rarity.RARE),
        Relic(RelicId.BOSS_SLAYER,      "Tueur de boss",   "×2 pièces sur les boss",       Rarity.COMMON),
        Relic(RelicId.COMBO_GREED,      "Avide",           "×2 pièces dès combo 5",        Rarity.COMMON),
        Relic(RelicId.PADDLE_GRAVITY,   "Gravité fine",    "Raquette doublement réactive", Rarity.COMMON),
        Relic(RelicId.PADDLE_SPRINT,    "Sprint",          "Raquette +50% de vitesse",     Rarity.COMMON),
        Relic(RelicId.SECOND_CHANCE,    "Seconde chance",  "Survit une fois à la défaite", Rarity.EPIC),
        Relic(RelicId.PANIC_TRAINER,    "Entraînement",    "Contact doigt activé en 30s", Rarity.RARE)
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
