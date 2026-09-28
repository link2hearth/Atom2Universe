package com.Atom2Universe.app.games.caves.entity

import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Le tir à distance agace, il n'achève pas. Les dégâts des projectiles du joueur remplissent une
 * jauge que le corps à corps vide. Un projectile ne la fait jamais dépasser la moitié des PV max ;
 * quand elle l'atteint, le monstre se replie sur place (cocon ou bouclier, voir [MobDef.retreat]) :
 * insensible aux projectiles, il regagne ce qu'on lui a pris de loin. Un coup au corps à corps
 * casse le repli et le laisse sonné. Seuls les monstres de la Survie sont concernés.
 */
internal object Harassment {
    const val SHARE = .5f
    const val RETREAT_TIME = 6f
    const val BREAK_STUN = 1.5f
    const val BOSS_BREAK_STUN = .75f

    fun applies(e: Enemy) = e.exploration && e.def.behavior != "passive"
    fun limit(e: Enemy) = ceil(e.maxHp * SHARE).toInt().coerceAtLeast(1)
    /** En repli : les projectiles rebondissent sur lui. */
    fun shielded(e: Enemy) = applies(e) && e.retreat > 0f

    /** Dégâts qu'un projectile peut réellement infliger : jamais au-delà du seuil. */
    fun cap(e: Enemy, damage: Int): Int = when {
        !applies(e) -> damage
        e.retreat > 0f -> 0
        else -> damage.coerceAtMost(limit(e) - e.harassment).coerceAtLeast(0)
    }

    /** Enregistre [dealt] dégâts à distance ; vrai si le monstre vient de se replier. */
    fun ranged(e: Enemy, dealt: Int): Boolean {
        if (!applies(e) || dealt <= 0 || e.hp <= 0) return false
        e.harassment += dealt
        if (e.harassment < limit(e)) return false
        e.retreat = RETREAT_TIME
        e.retreatFrom = e.hp
        e.retreatHeal = e.harassment
        e.harassment = 0
        // La coque protège de tout ce qui a été posé à distance.
        e.freezeTimer = 0f; e.confusionTimer = 0f
        e.poisonTimer = 0f; e.poisonDamage = 0; e.fireTimer = 0f; e.fireDamage = 0
        e.staggerTimer = 0f; e.attackWindup = 0f; e.attack?.lungeRemaining = 0f
        return true
    }

    /** Un coup au corps à corps : la jauge se vide ; un repli casse et le monstre reste sonné. */
    fun melee(e: Enemy) {
        if (!applies(e)) return
        e.harassment = 0
        if (e.retreat <= 0f) return
        e.retreat = 0f
        e.staggerTimer = if (e.isBoss) BOSS_BREAK_STUN else BREAK_STUN
        e.attackWindup = 0f
    }

    /** Le repli regagne ses PV à rythme constant, puis le monstre repart, jauge vide. */
    fun update(e: Enemy, dt: Float) {
        if (e.retreat <= 0f) return
        e.retreat = (e.retreat - dt).coerceAtLeast(0f)
        val healed = (e.retreatHeal * (1f - e.retreat / RETREAT_TIME)).roundToInt()
        e.hp = maxOf(e.hp, (e.retreatFrom + healed).coerceAtMost(e.maxHp))
        if (e.retreat == 0f) { e.harassment = 0; e.state = EnemyState.CHASE }
    }

    /** Montée de la coque, de 0 à 1 en un quart de seconde. */
    fun growth(e: Enemy) = ((RETREAT_TIME - e.retreat) / .25f).coerceIn(0f, 1f)
}
