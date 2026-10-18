package com.Atom2Universe.app.games.toyboxracers.menu

import android.content.Context
import com.Atom2Universe.app.games.toyboxracers.game.RaceDifficulty
import com.Atom2Universe.app.games.toyboxracers.track.SceneChoice

/** Résultats locaux : chaque tracé et chaque difficulté gardent leur propre palmarès. */
internal class ToyboxRaceProgress(context: Context) {
    data class Results(val races: Int = 0, val wins: Int = 0, val podiums: Int = 0, val bestPosition: Int = 0)

    private val prefs = context.getSharedPreferences("toybox_racers_progress", Context.MODE_PRIVATE)

    fun results(scene: SceneChoice, difficulty: RaceDifficulty): Results = read(key(scene, difficulty))
    fun total(): Results = read("total")

    fun record(scene: SceneChoice, difficulty: RaceDifficulty, position: Int) {
        if (position !in 1..6) return
        val edit = prefs.edit()
        for (prefix in listOf(key(scene, difficulty), "total")) {
            val previous = read(prefix)
            edit.putInt("${prefix}_races", previous.races + 1)
                .putInt("${prefix}_wins", previous.wins + if (position == 1) 1 else 0)
                .putInt("${prefix}_podiums", previous.podiums + if (position <= 3) 1 else 0)
                .putInt("${prefix}_position", if (previous.bestPosition == 0) position else minOf(position, previous.bestPosition))
        }
        edit.apply()
    }

    private fun read(prefix: String) = Results(
        prefs.getInt("${prefix}_races", 0), prefs.getInt("${prefix}_wins", 0),
        prefs.getInt("${prefix}_podiums", 0), prefs.getInt("${prefix}_position", 0)
    )

    private fun key(scene: SceneChoice, difficulty: RaceDifficulty) =
        "${scene.room.name}_${scene.circuit.name}_${difficulty.name}"
}
