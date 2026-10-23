package com.Atom2Universe.app.games

import android.content.SharedPreferences

/** Insère la nouvelle tuile près d'Accrétion sans perdre le rangement du joueur. */
internal object GamesHubMigration {
    fun separateRicochet(prefs: SharedPreferences) {
        if (prefs.getBoolean("ricochet_tile_v1", false)) return
        val assignments = prefs.getString("tile_panes", "").orEmpty().split(';').mapNotNull {
            val parts = it.split(':')
            val pane = parts.getOrNull(1)?.toIntOrNull()
            if (parts.size == 2 && pane != null) parts[0] to pane else null
        }.toMap().toMutableMap()
        val pane = (assignments["bigger"] ?: 0).coerceIn(0, 1)
        assignments["ricochet"] = pane
        val editor = prefs.edit().putBoolean("ricochet_tile_v1", true)
            .putString("tile_panes", assignments.entries.joinToString(";") { "${it.key}:${it.value}" })
        for (index in 0..1) {
            val key = if (index == 0) "tile_order" else "tile_order_1"
            val order = prefs.getString(key, "").orEmpty().split(',').filter { it.isNotEmpty() && it != "ricochet" }.toMutableList()
            if (index == pane && order.isNotEmpty()) {
                // Accrétion peut être absente d'un ancien ordre : elle était alors ajoutée à la fin.
                if ("bigger" !in order) order.add("bigger")
                order.add(order.indexOf("bigger") + 1, "ricochet")
            }
            if (order.isNotEmpty()) editor.putString(key, order.joinToString(","))
        }
        editor.apply()
    }
}
