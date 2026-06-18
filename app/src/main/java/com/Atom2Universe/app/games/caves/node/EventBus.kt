package com.Atom2Universe.app.games.caves.node

internal sealed class GameEvent {

    data class MobDied(
        val mobId: Int,
        val x: Double,
        val y: Double,
        val z: Double,
        val level: Int,
        val isBoss: Boolean,
        val mobDefId: String,
        val mobMaxHp: Int
    ) : GameEvent()

    data class BossSpawned(val mobId: Int) : GameEvent()

    /** [dirX]/[dirZ] : direction horizontale normalisée du recul (de l'attaquant vers le joueur). */
    data class PlayerHit(val damage: Int, val dirX: Float = 0f, val dirZ: Float = 0f) : GameEvent()

    data class MobHit(val isBoss: Boolean) : GameEvent()

    data class MobNearby(val isBoss: Boolean) : GameEvent()
}

internal class EventBus {
    private val listeners = ArrayList<(GameEvent) -> Unit>(4)

    fun publish(event: GameEvent) {
        for (i in listeners.indices) listeners[i](event)
    }

    fun subscribe(listener: (GameEvent) -> Unit) {
        listeners.add(listener)
    }
}
