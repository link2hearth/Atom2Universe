package com.Atom2Universe.app.midi.input

/** Décode les notes et contrôleurs d'un seul port MIDI 1.0, indépendamment du découpage des paquets. */
internal class MidiStreamDecoder(private val onMessage: (ByteArray) -> Unit) {
    private var runningStatus = 0
    private var firstData = -1
    private var closed = false

    @Synchronized
    fun receive(bytes: ByteArray, offset: Int, count: Int) {
        if (closed) return
        for (index in offset until offset + count) {
            val value = bytes[index].toInt() and 0xFF
            when {
                // L'horloge et les autres messages temps réel peuvent s'intercaler entre deux octets.
                value >= 0xF8 -> continue
                value >= 0x80 -> {
                    // SysEx, System Common ou un autre type de canal mettent fin au message en cours.
                    // Leurs données restent ignorées jusqu'au prochain statut de note ou de contrôleur.
                    runningStatus = when (value and 0xF0) {
                        0x80, 0x90, 0xB0 -> value
                        else -> 0
                    }
                    firstData = -1
                }
                runningStatus == 0 -> continue
                firstData < 0 -> firstData = value
                else -> {
                    val status = if ((runningStatus and 0xF0) == 0x90 && value == 0) {
                        0x80 or (runningStatus and 0x0F)
                    } else runningStatus
                    val message = byteArrayOf(status.toByte(), firstData.toByte(), value.toByte())
                    firstData = -1
                    onMessage(message)
                }
            }
        }
    }

    /** Après déconnexion, même un dernier paquet déjà en vol ne doit plus jouer de note. */
    @Synchronized
    fun close() {
        closed = true
    }
}
