package com.Atom2Universe.app.midi.visualizer

import android.view.Choreographer

/**
 * Une seule horloge d'images pour tous les claviers et indicateurs : elle ne tourne que tant
 * qu'au moins une vue est branchée, et s'arrête toute seule ensuite (zéro coût à l'arrêt).
 * À utiliser depuis le fil principal uniquement.
 */
object MidiFrameClock {

    fun interface Tickable {
        /** @param frameTimeNanos date de l'image, en nanosecondes */
        fun onFrame(frameTimeNanos: Long)
    }

    private val tickables = ArrayList<Tickable>()
    private var running = false

    private val callback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (tickables.isEmpty()) {
                running = false
                return
            }
            // Copie : une vue peut se débrancher pendant son propre onFrame
            val snapshot = tickables.toTypedArray()
            for (t in snapshot) t.onFrame(frameTimeNanos)
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun register(tickable: Tickable) {
        if (!tickables.contains(tickable)) tickables.add(tickable)
        if (!running) {
            running = true
            Choreographer.getInstance().postFrameCallback(callback)
        }
    }

    fun unregister(tickable: Tickable) {
        tickables.remove(tickable)
    }
}
