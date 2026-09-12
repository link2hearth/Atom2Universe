package com.Atom2Universe.app.games.caves.mode

import com.Atom2Universe.app.games.caves.CaveRenderer
import com.Atom2Universe.app.games.caves.world.MapSource

/**
 * Le mode Assaut : une carte préparée, jouée au fusil (voir CAVE_WORLD_ASSAUT.md).
 *
 * Phase 1 : on peut seulement s'y promener armé. Pas de monstres, pas de sauvegarde, et la
 * carte ne se creuse pas. Les manches, le score et les bots viendront dans les phases suivantes.
 */
internal class AssaultMode(
    private val r: CaveRenderer,
    private val source: MapSource,
) : GameMode {

    override val allowsWorldEdits: Boolean get() = false

    override fun spawnPoint(): FloatArray = source.spawnPoint()

    override fun onSurfaceCreated(savedState: CaveRenderer.SavedState?) {
        // Pas encore d'équipement de match (phase 2) : on prête toutes les armes à distance.
        r.giveWeaponTestKit()
        r.loadoutChangedCallback?.invoke()
    }

    override fun onPlayerPlaced(x: Double, y: Double, z: Double) = Unit

    override fun update(dt: Float) {
        // Tombé hors de la carte : retour au point d'apparition.
        if (r.camera.playerY < source.originY - FALL_LIMIT) respawn()
    }

    private fun respawn() {
        val spawn = source.spawnPoint()
        val camera = r.camera
        camera.playerX = spawn[0].toDouble()
        camera.playerY = spawn[1].toDouble()
        camera.playerZ = spawn[2].toDouble()
        r.physics.reset()
    }

    private companion object {
        /** Blocs de chute sous la carte avant d'être ramené. */
        const val FALL_LIMIT = 16
    }
}
