package com.Atom2Universe.app.midi.practice

/**
 * Interface abstraite pour la synthèse audio en mode pratique.
 *
 * Permet d'utiliser différents moteurs de synthèse (Sonivox, SF2, etc.)
 * pour le playback et l'interaction avec le piano virtuel.
 */
interface PracticeSynthesizer {

    /**
     * Initialise le synthétiseur.
     * @return true si l'initialisation a réussi
     */
    fun initialize(): Boolean

    /**
     * Libère les ressources du synthétiseur.
     */
    fun release()

    /**
     * Joue une note.
     * @param channel Canal MIDI (0-15)
     * @param note Numéro de note MIDI (0-127)
     * @param velocity Vélocité (0-127, 0 = note off)
     */
    fun noteOn(channel: Int, note: Int, velocity: Int)

    /**
     * Arrête une note.
     * @param channel Canal MIDI (0-15)
     * @param note Numéro de note MIDI (0-127)
     */
    fun noteOff(channel: Int, note: Int)

    /**
     * Change le programme (instrument) sur un canal.
     * @param channel Canal MIDI (0-15)
     * @param program Numéro de programme (0-127)
     */
    fun programChange(channel: Int, program: Int)

    /**
     * Envoie un Control Change.
     * @param channel Canal MIDI (0-15)
     * @param controller Numéro du contrôleur (0-127)
     * @param value Valeur (0-127)
     */
    fun controlChange(channel: Int, controller: Int, value: Int)

    /**
     * Coupe toutes les notes sur tous les canaux.
     */
    fun allNotesOff()

    /**
     * Coupe immédiatement tout son sur tous les canaux.
     */
    fun allSoundOff()

    /**
     * Vérifie si le synthétiseur est prêt.
     */
    fun isReady(): Boolean

    /**
     * Retourne le nom du synthétiseur pour l'affichage.
     */
    fun getName(): String
}
