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
    data class EnemyFired(val weaponType: String) : GameEvent()

    data class WeaponFired(val weaponType: String) : GameEvent()
    /** [weaponType] : l'arme rechargée (« gun », « smg »…), pour jouer le bon geste. */
    data class WeaponReload(val complete: Boolean, val weaponType: String = "") : GameEvent()
    /**
     * Une balle vient de frapper un bloc (mode Assaut). [material] : « concrete », « wood » ou
     * « metal ». Volume et panoramique sont déjà calculés depuis la position du joueur.
     */
    data class BulletImpact(val material: String, val volume: Float, val pan: Float) : GameEvent()
    /** Début d'une manche du mode Assaut. */
    object RoundStarted : GameEvent()
    /** Fin d'une manche : [won] si toute la garnison est tombée. */
    data class RoundEnded(val won: Boolean) : GameEvent()
    /** Boucle d'ambiance à jouer (nom de fichier sans extension), ou null pour le silence. */
    data class Ambience(val track: String?) : GameEvent()
    data class Footstep(val surface: String = "stone", val running: Boolean = false,
                        val moving: Boolean = false, val interval: Float = .46f) : GameEvent()
    data class AnimalCall(val species: String, val volume: Float, val pan: Float) : GameEvent()

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
