package com.Atom2Universe.app.games.survivor

/** Paramètres communs aux apparitions et aux formations. */
internal object SurvivorBalance {
    const val VERSION = 3
    const val WORLD_WIDTH = 960f
    const val MAX_SPAWN_BOOST = 1.65f
    const val DAMAGE_PER_RANK = 0.20f
    const val XP_FACTOR = 0.65f

    private val minutes = floatArrayOf(0f, 1f, 2f, 5f, 10f, 15f, 20f)
    private val rates = floatArrayOf(2.6f, 3f, 4.6f, 5.8f, 7f, 9f, 11f)
    private val limits = floatArrayOf(48f, 56f, 64f, 100f, 150f, 180f, 200f)

    val upgradeCaps = mapOf(
        "speed" to 8, "lifeSteal" to 5, "regen" to 10, "crit_mult" to 6,
        "explosion_on_kill" to 6, "proj_count" to 6, "proj_pen" to 3,
        "proj_fork" to 1, "orbital_frag" to 1, "orbital_count" to 8,
        "orbital_multi" to 2, "aura_radius" to 10, "aura_slow" to 5,
        "bomb_multi" to 2, "bomb_residue_duration" to 5
    )

    fun hpScale(wave: Int): Float {
        val m = (wave - 1).coerceAtLeast(0) / 2f
        return 1f + 0.16f * m + 0.012f * m * m
    }

    fun damageScale(wave: Int) = 1f + ((wave - 1).coerceAtLeast(0) * 0.02f).coerceAtMost(1f)
    // +0,25 % par minute, au plus +5 % : la poursuite reste esquivable.
    fun speedScale(wave: Int) = 1f + ((wave - 1).coerceAtLeast(0) * 0.00125f).coerceAtMost(0.05f)
    fun spawnRate(seconds: Float) = interpolate(seconds / 60f, rates)
    fun populationLimit(seconds: Float) = interpolate(seconds / 60f, limits).toInt()

    private fun interpolate(m: Float, values: FloatArray): Float {
        for (i in 1 until minutes.size) {
            if (m <= minutes[i]) {
                val fraction = ((m - minutes[i - 1]) / (minutes[i] - minutes[i - 1])).coerceIn(0f, 1f)
                return values[i - 1] + fraction * (values[i] - values[i - 1])
            }
        }
        return values.last()
    }

    data class EnemyStats(val hp: Float, val speed: Float, val damage: Float, val xp: Float, val radius: Float)

    fun enemy(type: EnemyType, wave: Int): EnemyStats {
        val base = when (type) {
            EnemyType.ZOMBIE -> EnemyStats(10f, 85f, 10f, 5f, 14f)
            EnemyType.FAST -> EnemyStats(6f, 150f, 5f, 3f, 11f)
            EnemyType.ERRATIC -> EnemyStats(8f, 115f, 8f, 4f, 13f)
            EnemyType.ORBITER -> EnemyStats(20f, 105f, 15f, 10f, 18f)
            EnemyType.SHOOTER -> EnemyStats(12f, 50f, 5f, 8f, 20f)
            EnemyType.MINI_BOSS -> EnemyStats(260f, 70f, 25f, 200f, 32f)
        }
        return base.copy(hp = base.hp * hpScale(wave), speed = base.speed * speedScale(wave),
            damage = base.damage * damageScale(wave),
            xp = if (type == EnemyType.MINI_BOSS) base.xp else base.xp * XP_FACTOR)
    }
}
