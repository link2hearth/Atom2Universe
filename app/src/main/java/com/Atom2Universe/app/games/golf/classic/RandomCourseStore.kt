package com.Atom2Universe.app.games.golf.classic

import android.content.Context
import com.Atom2Universe.app.games.golf.classic.core.RandomGolfSpec

/** The generated courses the player kept, newest first. Only the spec is stored: the holes are rebuilt from it. */
internal class RandomCourseStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("classic_golf_random_courses", Context.MODE_PRIVATE)

    /** Newest first, as created. */
    private fun created(): List<RandomGolfSpec> = prefs.getString("ids", "").orEmpty().split(',').mapNotNull { RandomGolfSpec.parse(it) }
    private fun favourites(): Set<String> = prefs.getString("favourites", "").orEmpty().split(',').filter { it.isNotEmpty() }.toSet()

    /** As shown in the menu: the favourites on top, then the others, each group newest first. */
    fun all(): List<RandomGolfSpec> = order(created(), favourites())

    fun isFavourite(spec: RandomGolfSpec) = spec.id in favourites()

    fun toggleFavourite(spec: RandomGolfSpec) {
        val now = favourites().let { if (spec.id in it) it - spec.id else it + spec.id }
        prefs.edit().putString("favourites", now.joinToString(",")).apply()
    }

    /** The last seed, count and theme used, to prefill the creation dialog. */
    var last: RandomGolfSpec?
        get() = RandomGolfSpec.parse(prefs.getString("last", null))
        set(value) { prefs.edit().putString("last", value?.id).apply() }

    fun add(spec: RandomGolfSpec) {
        var kept = listOf(spec) + created().filter { it != spec }
        val favourites = favourites()
        // Past the limit the oldest course that is not a favourite goes first.
        while (kept.size > MAX) {
            val victim = kept.lastOrNull { it.id !in favourites } ?: break
            forget(victim)
            kept = kept - victim
        }
        write(kept)
    }

    fun remove(spec: RandomGolfSpec) {
        forget(spec)
        write(created().filter { it != spec })
        prefs.edit().putString("favourites", (favourites() - spec.id).joinToString(",")).apply()
    }

    private fun write(list: List<RandomGolfSpec>) = prefs.edit().putString("ids", list.joinToString(",") { it.id }).apply()

    /** A deleted course takes its saved round and records with it. */
    private fun forget(spec: RandomGolfSpec) {
        context.getSharedPreferences(ClassicRoundLength.WHOLE.progressName(spec.progressName), Context.MODE_PRIVATE).edit().clear().apply()
    }

    companion object {
        private const val MAX = 24
        fun order(created: List<RandomGolfSpec>, favourites: Set<String>) =
            created.filter { it.id in favourites } + created.filter { it.id !in favourites }
    }
}
