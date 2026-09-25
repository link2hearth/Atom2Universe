package com.Atom2Universe.app.games.caves

import android.content.Context

internal data class CaveViewDistances(val simulation: Int, val view: Int) {
    fun normalized(): CaveViewDistances {
        val simulation = simulation.coerceIn(MIN_SIMULATION, MAX_SIMULATION)
        return CaveViewDistances(simulation, view.coerceIn(simulation, MAX_VIEW))
    }

    fun save(context: Context) {
        val value = normalized()
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
            .putInt("simulation", value.simulation).putInt("view", value.view).apply()
    }

    companion object {
        const val MIN_SIMULATION = 6
        const val MAX_SIMULATION = 32
        const val MAX_VIEW = 64
        private const val PREFERENCES = "cave_view_distances"

        fun load(context: Context): CaveViewDistances {
            val prefs = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            return CaveViewDistances(prefs.getInt("simulation", 8), prefs.getInt("view", 32)).normalized()
        }
    }
}
