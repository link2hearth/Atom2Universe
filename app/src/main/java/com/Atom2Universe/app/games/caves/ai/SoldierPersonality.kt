package com.Atom2Universe.app.games.caves.ai

import kotlin.random.Random

/**
 * Variante individuelle d'un [SoldierTuning], tirée d'un seul soldat à l'autre pour qu'une
 * garnison entière ne se comporte pas comme un seul homme répété soixante fois.
 *
 * Deux facteurs seulement, jamais un bruit indépendant par champ : un tireur plus adroit doit
 * rester cohérent (`aimErrorMin` < `aimErrorMax`), pas gagner une précision qui contredit sa
 * réaction. [skill] resserre réaction et visée ensemble ; [aggression] le rend plus mobile et
 * plus insistant en rafale, sans toucher à ce qui n'a pas de sens à varier (portée, rechargement).
 */
internal fun SoldierTuning.withPersonality(rng: Random): SoldierTuning {
    val skill = SKILL_MIN + rng.nextFloat() * (SKILL_MAX - SKILL_MIN)
    val aggression = AGGRESSION_MIN + rng.nextFloat() * (AGGRESSION_MAX - AGGRESSION_MIN)
    // skill > 1 : plus adroit, donc des facteurs plus PETITS (moins de temps, moins d'erreur).
    val precision = 1f / skill
    val newBurstMin = (burstMin * aggression).toInt().coerceAtLeast(1)
    val newBurstMax = (burstMax * aggression).toInt().coerceAtLeast(newBurstMin + 1)
    return copy(
        reactionMin = reactionMin * precision,
        reactionMax = reactionMax * precision,
        aimErrorStartDeg = aimErrorStartDeg * precision,
        aimErrorMinDeg = aimErrorMinDeg * precision,
        aimErrorMaxDeg = aimErrorMaxDeg * precision,
        repositionSeconds = repositionSeconds / aggression,
        burstMin = newBurstMin,
        burstMax = newBurstMax,
    )
}

private const val SKILL_MIN = 0.8f
private const val SKILL_MAX = 1.2f
private const val AGGRESSION_MIN = 0.8f
private const val AGGRESSION_MAX = 1.3f
