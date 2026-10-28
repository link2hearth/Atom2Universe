package com.Atom2Universe.app.midi.visualizer

import android.os.Handler
import android.os.Looper
import android.view.Choreographer

/**
 * Une seule horloge d'images pour tous les claviers et indicateurs : elle ne tourne que tant
 * qu'au moins une vue est branchée ET que quelque chose bouge (note qui change, fondu en cours).
 * Quand tout est immobile (musique arrêtée), elle s'endort et c'est le prochain changement de
 * [MidiLiveState] qui la réveille, depuis le fil audio.
 * Les vues se débranchent quand leur fenêtre est cachée (application en arrière-plan).
 * À utiliser depuis le fil principal uniquement.
 */
object MidiFrameClock {

    fun interface Tickable {
        /**
         * @param frameTimeNanos date de l'image, en nanosecondes
         * @return true tant que la vue anime quelque chose sans nouvel événement (fondu…)
         */
        fun onFrame(frameTimeNanos: Long): Boolean
    }

    private val tickables = ArrayList<Tickable>()
    // Abonnés servis à chaque image mais qui ne suffisent pas à faire tourner l'horloge
    private val passengers = HashSet<Tickable>()
    private var running = false
    // Copie réutilisée : une vue peut se débrancher pendant son propre onFrame
    private var snapshot = arrayOfNulls<Tickable>(8)

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val wakeOnMain = Runnable { requestFrame() }
    // Posé dans MidiLiveState pendant le sommeil ; appelé depuis le fil audio
    private val wakeFromAudio = Runnable { mainHandler.post(wakeOnMain) }

    private val callback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!hasDriver()) {
                running = false
                return
            }
            val versionBefore = MidiLiveState.changeVersion()
            val count = tickables.size
            if (snapshot.size < count) snapshot = arrayOfNulls(count * 2)
            for (i in 0 until count) snapshot[i] = tickables[i]
            var busy = false
            for (i in 0 until count) {
                val t = snapshot[i] ?: continue
                snapshot[i] = null
                if (t.onFrame(frameTimeNanos)) busy = true
            }
            if (busy) {
                Choreographer.getInstance().postFrameCallback(this)
                return
            }
            // Tout est immobile : dormir jusqu'au prochain changement d'état
            MidiLiveState.setWakeListener(wakeFromAudio)
            if (MidiLiveState.changeVersion() != versionBefore &&
                MidiLiveState.clearWakeListener(wakeFromAudio)
            ) {
                // Changement arrivé pendant l'image : on reste éveillé
                Choreographer.getInstance().postFrameCallback(this)
                return
            }
            // Sinon, soit on dort, soit le fil audio a déjà posté le réveil
            running = false
        }
    }

    /**
     * @param keepsClockRunning false pour un abonné qui n'a de sens que si une vue visible
     * tourne aussi (ex. l'adaptateur des canaux) : seul, il ne réveille pas l'horloge.
     */
    fun register(tickable: Tickable, keepsClockRunning: Boolean = true) {
        if (!tickables.contains(tickable)) tickables.add(tickable)
        if (keepsClockRunning) passengers.remove(tickable) else passengers.add(tickable)
        requestFrame()
    }

    fun unregister(tickable: Tickable) {
        tickables.remove(tickable)
        passengers.remove(tickable)
    }

    /** Demande au moins une image (vue rebranchée, redevenue visible, réglée sur un autre canal). */
    fun requestFrame() {
        if (running || !hasDriver()) return
        running = true
        MidiLiveState.clearWakeListener(wakeFromAudio)
        Choreographer.getInstance().postFrameCallback(callback)
    }

    private fun hasDriver(): Boolean = tickables.size > passengers.size
}
