package com.Atom2Universe.app.games.caves.node

import com.Atom2Universe.app.games.caves.entity.Enemy

internal object CombatNode {

    fun enemyAttacksPlayer(enemy: Enemy, player: PlayerNode, bus: EventBus, dirX: Float = 0f, dirZ: Float = 0f) {
        val damage = enemy.scaledDamage
        player.applyDamage(damage)
        bus.publish(GameEvent.PlayerHit(damage, dirX, dirZ))
    }

    fun damageEnemy(enemy: Enemy, damage: Int) {
        enemy.hp = (enemy.hp - damage).coerceAtLeast(0)
        enemy.hitFlash = 0.18f
    }
}
