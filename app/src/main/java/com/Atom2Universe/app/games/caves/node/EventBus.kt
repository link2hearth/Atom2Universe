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

    /** Un ennemi vient de tirer (soldat du mode Assaut). */
    object EnemyFired : GameEvent()

    /**
     * Un soldat du mode Assaut vient de tomber. À ne pas confondre avec [MobDied], qui veut dire
     * « un monstre de la survie est mort » : ce dernier déclenche le butin et l'XP, et le butin
     * cherche la fiche du monstre dans MobRegistry, où les soldats n'existent pas.
     */
    object SoldierDown : GameEvent()
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
