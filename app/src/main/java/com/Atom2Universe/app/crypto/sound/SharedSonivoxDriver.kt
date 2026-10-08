package com.Atom2Universe.app.crypto.sound

import org.billthefarmer.mididriver.MidiDriver

/**
 * Pilote Sonivox EAS partagé entre le clicker et la fusion.
 *
 * Le moteur natif EAS est unique pour tout le process : [MidiDriver] est un
 * singleton et `start()`/`stop()` (init/shutdown natifs) reconstruisent ou
 * détruisent l'unique moteur. Quand le clicker et la fusion — lancée depuis le
 * clicker — géraient chacun `start()`/`stop()` sur leur propre cycle de vie, le
 * shutdown de l'un coupait le son de l'autre. En particulier
 * `FusionActivity.onDestroy()` s'exécute APRÈS `MainClickerActivity.onResume()`
 * et coupait le moteur que le clicker venait de redémarrer, laissant le clicker
 * muet bien que son flag interne le croie actif.
 *
 * Ce gestionnaire compte les références : le moteur n'est initialisé qu'au
 * premier [acquire] et arrêté qu'au dernier [release].
 */
object SharedSonivoxDriver {

    private val driver: MidiDriver = MidiDriver.getInstance()
    private var refCount = 0
    private val lock = Any()

    /**
     * Réserve le moteur. [onReady] est invoqué une fois le moteur natif vivant
     * (immédiatement s'il l'était déjà, l'init EAS étant synchrone) : c'est là
     * qu'un client (ré)applique ses program/control changes de canal.
     */
    fun acquire(onReady: () -> Unit) {
        synchronized(lock) {
            if (refCount == 0) {
                // On pilote la disponibilité manuellement via onReady ; on neutralise
                // le listener du singleton pour ne pas déclencher celui d'un autre moteur.
                driver.setOnMidiStartListener { }
                driver.start()
            }
            refCount++
        }
        onReady()
    }

    /** Libère le moteur. Le moteur natif n'est arrêté qu'au dernier release. */
    fun release() {
        synchronized(lock) {
            if (refCount <= 0) return
            refCount--
            if (refCount == 0) driver.stop()
        }
    }

    fun queueEvent(event: ByteArray) = driver.queueEvent(event)
}
