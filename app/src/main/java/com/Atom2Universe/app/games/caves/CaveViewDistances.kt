package com.Atom2Universe.app.games.caves

import android.content.Context

/** Pause menu distances, in chunks: [detail] full meshes around the player, [view] with the far LOD,
 * [simulation] how far enemies, animals, residents and mechanical parts stay active. */
internal data class CaveViewDistances(val detail: Int, val view: Int, val simulation: Int) {
    fun normalized(): CaveViewDistances {
        val detail = detail.coerceIn(MIN_DETAIL, MAX_DETAIL)
        // Nothing is simulated where the world is not drawn in detail.
        return CaveViewDistances(detail, view.coerceIn(detail, MAX_VIEW),
            simulation.coerceIn(MIN_SIMULATION, minOf(MAX_SIMULATION, detail)))
    }

    fun save(context: Context) {
        val value = normalized()
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
            .putInt("detail", value.detail).putInt("view", value.view).putInt("simulation", value.simulation).apply()
    }

    companion object {
        const val MIN_DETAIL = 6
        const val MAX_DETAIL = 32
        const val MAX_VIEW = 64
        const val MIN_SIMULATION = 4
        const val MAX_SIMULATION = 12
        private const val PREFERENCES = "cave_view_distances"

        fun load(context: Context): CaveViewDistances {
            val prefs = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            return CaveViewDistances(prefs.getInt("detail", 8), prefs.getInt("view", 16), prefs.getInt("simulation", 4)).normalized()
        }
    }
}
