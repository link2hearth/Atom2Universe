package com.Atom2Universe.app.games.caves.mode

import com.Atom2Universe.app.games.caves.CaveRenderer
import com.Atom2Universe.app.games.caves.entity.Enemy

/**
 * Les règles d'une partie de Cave World.
 *
 * Le renderer s'occupe de ce qui est commun à tous les modes (monde, caméra, armes, rendu) et
 * délègue au mode ce qui lui est propre : la survie a ses monstres, son XP et son butin ; le
 * futur mode Assaut aura ses manches et ses bots. Voir CAVE_WORLD_ASSAUT.md à la racine.
 *
 * Toutes les méthodes sont appelées sur le thread GL.
 */
internal interface GameMode {

    /**
     * Branche les règles du mode (événements, butin…) et restaure sa progression si la partie
     * reprend une sauvegarde. Appelé une fois, avant que le joueur soit placé dans le monde.
     */
    fun onSurfaceCreated(savedState: CaveRenderer.SavedState?)

    /** Le joueur vient d'être placé dans le monde, autour du point d'apparition [x], [y], [z]. */
    fun onPlayerPlaced(x: Double, y: Double, z: Double)

    /** Une image de jeu. N'est pas appelé quand la partie est en pause. */
    fun update(dt: Float)

    /**
     * Point d'apparition d'une nouvelle partie (x, y des yeux, z).
     * Null = celui que le monde trouve lui-même ([World.findSpawnPoint]).
     */
    fun spawnPoint(): FloatArray? = null

    /** Le joueur peut-il creuser et poser des blocs ? */
    val allowsWorldEdits: Boolean get() = true

    /** Les tirs ne consomment pas de munitions de réserve (le chargeur se recharge quand même). */
    val infiniteAmmo: Boolean get() = false

    /** Heure de jeu figée (en ms de cycle, voir CaveRenderer.gameTimeMs) ; null = le jour et la nuit tournent. */
    val fixedTimeOfDayMs: Long? get() = null

    /** Multiplicateur de dégâts d'un tir qui touche la tête d'un ennemi. */
    val headshotMultiplier: Float get() = 1f

    /** Un projectile du joueur ou d'un allié vient de toucher [enemy], à la tête si [headshot]. */
    fun onEnemyHit(enemy: Enemy, headshot: Boolean) = Unit
}
