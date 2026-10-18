package com.Atom2Universe.app.midi.visualizer

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicIntegerArray

/**
 * État « en direct » du morceau : quelles notes sonnent, sur quel canal, avec quelle vélocité,
 * et quel instrument est actif sur chaque canal.
 *
 * C'est l'unique source de vérité des claviers animés. Le moteur audio l'écrit (depuis son
 * propre fil, sans verrou ni allocation) et les vues la LISENT à chaque image. Une vue qui
 * apparaît en retard (nouveau fichier, retour sur l'onglet, ligne recyclée) retrouve donc
 * immédiatement le bon état : il n'y a plus d'événement à rater, donc plus de désynchronisation.
 */
object MidiLiveState {

    const val CHANNELS = 16
    const val NOTES = 128

    // Vélocité par (canal, note) ; 0 = la note ne sonne pas
    private val velocities = AtomicIntegerArray(CHANNELS * NOTES)

    // Programme (instrument) courant par canal ; -1 = inconnu, la ligne garde celui de l'analyse
    private val programs = AtomicIntegerArray(CHANNELS).also { for (i in 0 until CHANNELS) it.set(i, -1) }

    // Compteurs de changement : les vues ne redessinent que si le leur est dépassé
    private val notesVersion = AtomicInteger(0)
    private val programsVersion = AtomicInteger(0)

    fun noteOn(channel: Int, note: Int, velocity: Int) {
        if (!valid(channel, note)) return
        velocities.set(channel * NOTES + note, velocity.coerceIn(1, 127))
        notesVersion.incrementAndGet()
    }

    fun noteOff(channel: Int, note: Int) {
        if (!valid(channel, note)) return
        if (velocities.getAndSet(channel * NOTES + note, 0) != 0) notesVersion.incrementAndGet()
    }

    fun setProgram(channel: Int, program: Int) {
        if (channel !in 0 until CHANNELS) return
        if (programs.getAndSet(channel, program) != program) programsVersion.incrementAndGet()
    }

    /** Éteint toutes les notes ; les instruments restent (on est toujours dans le même fichier). */
    fun clearNotes() {
        var changed = false
        for (i in 0 until velocities.length()) {
            if (velocities.getAndSet(i, 0) != 0) changed = true
        }
        if (changed) notesVersion.incrementAndGet()
    }

    /** Nouveau fichier : notes ET instruments repartent de zéro. */
    fun reset() {
        clearNotes()
        for (i in 0 until CHANNELS) programs.set(i, -1)
        programsVersion.incrementAndGet()
    }

    fun velocity(channel: Int, note: Int): Int =
        if (valid(channel, note)) velocities.get(channel * NOTES + note) else 0

    /** Programme courant du canal, ou -1 s'il n'a pas encore changé dans ce fichier. */
    fun program(channel: Int): Int =
        if (channel in 0 until CHANNELS) programs.get(channel) else -1

    fun notesVersion(): Int = notesVersion.get()

    fun programsVersion(): Int = programsVersion.get()

    /** Notes qui sonnent sur le canal, pour réémettre l'état vers un écouteur. */
    fun activeNotes(channel: Int): List<Int> {
        if (channel !in 0 until CHANNELS) return emptyList()
        val result = ArrayList<Int>()
        for (note in 0 until NOTES) {
            if (velocities.get(channel * NOTES + note) > 0) result.add(note)
        }
        return result
    }

    private fun valid(channel: Int, note: Int) = channel in 0 until CHANNELS && note in 0 until NOTES
}
