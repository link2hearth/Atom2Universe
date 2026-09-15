package com.Atom2Universe.app.games.caves.mode

import com.Atom2Universe.app.games.caves.node.PlayerNode

/** Récupération propre à l'Assaut ; le bouclier ne se régénère jamais tout seul. */
internal class AssaultRecovery(private val player: PlayerNode) {
    private var quietSeconds = 0f
    private var healCredit = 0f

    fun reset() {
        quietSeconds = 0f
        healCredit = 0f
        player.setMaxHp(AssaultMode.PLAYER_MAX_HP, AssaultMode.PLAYER_MAX_HP)
        player.setMaxShield(MAX_SHIELD, MAX_SHIELD)
    }

    fun hit(damage: Int) {
        if (damage <= 0 || !player.isAlive) return
        quietSeconds = 0f
        healCredit = 0f
        // PlayerNode absorbe d'abord avec le bouclier puis applique le reliquat aux PV.
        player.applyDamage(damage)
    }

    fun update(dt: Float) {
        if (!player.isAlive || dt <= 0f) return
        val before = (quietSeconds - HEAL_DELAY).coerceAtLeast(0f)
        quietSeconds += dt
        if (player.hp >= player.maxHp) { healCredit = 0f; return }
        val healingSeconds = (quietSeconds - HEAL_DELAY).coerceAtLeast(0f) - before
        healCredit += healingSeconds * HEAL_PER_SECOND
        val amount = healCredit.toInt()
        if (amount > 0) {
            healCredit -= amount
            player.applyHeal(amount)
        }
    }

    fun recharge(available: Int): Int {
        if (!player.isAlive || available <= 0) return 0
        val amount = minOf(available, (player.maxShield - player.shield).coerceAtLeast(0))
        if (amount > 0) player.setMaxShield(MAX_SHIELD, amount)
        return amount
    }

    companion object {
        const val MAX_SHIELD = 100
        const val HEAL_DELAY = 6f
        const val HEAL_PER_SECOND = 10f
        const val PICKUP_CHARGE = 35
        const val DROP_CHANCE = 1f / 3f
    }
}

/** Coordonnées monde ; le reliquat reste disponible si le joueur n'a besoin que de quelques points. */
internal class ShieldPickup(val x: Double, val y: Double, val z: Double,
                            var charge: Int = AssaultRecovery.PICKUP_CHARGE)
