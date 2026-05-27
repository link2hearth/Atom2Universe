package com.Atom2Universe.app.midi.service

import java.util.Locale

/**
 * Gestionnaire de mixage audio MIDI
 *
 * Fonctionnalités:
 * - Normalisation des velocities pour éviter la saturation
 * - Volume par canal avec ajustement automatique
 * - Master gain pour réduction globale
 * - Compression douce des pics de volume
 *
 * Le MIDI utilise des velocities de 0-127, mais certains fichiers
 * ont des velocities très élevées qui causent de la saturation
 * quand plusieurs canaux jouent ensemble.
 */
object MidiAudioMixer {

    /**
     * Presets de normalisation
     */
    enum class NormalizationPreset(
        val label: String,
        val masterGain: Float,      // Réduction globale (0.0-1.0)
        val velocityCap: Int,       // Velocity max autorisée
        val compressionThreshold: Int, // Seuil de compression
        val compressionRatio: Float    // Ratio de compression au-dessus du seuil
    ) {
        OFF("Désactivé", 1.0f, 127, 127, 1.0f),
        LIGHT("Léger", 0.85f, 120, 100, 0.8f),
        MEDIUM("Moyen", 0.75f, 110, 90, 0.6f),
        STRONG("Fort", 0.65f, 100, 80, 0.5f),
        AGGRESSIVE("Agressif", 0.55f, 90, 70, 0.4f)
    }

    // Paramètres actuels
    private var currentPreset = NormalizationPreset.MEDIUM
    private var masterGain = 0.75f
    private var velocityCap = 110
    private var compressionThreshold = 90
    private var compressionRatio = 0.6f

    // Volume par canal (0.0 à 1.0)
    private val channelVolumes = FloatArray(16) { 1.0f }

    private val channelBoost = FloatArray(16) { if (it == 9) 1.15f else 1.0f }

    // Statistiques (pour debug/ajustement)
    private var processedEvents = 0L
    private var clippedEvents = 0L

    /**
     * Applique le preset de normalisation
     */
    fun setPreset(preset: NormalizationPreset) {
        currentPreset = preset
        masterGain = preset.masterGain
        velocityCap = preset.velocityCap
        compressionThreshold = preset.compressionThreshold
        compressionRatio = preset.compressionRatio
    }

    /**
     * Récupère le preset actuel
     */
    fun getCurrentPreset(): NormalizationPreset = currentPreset

    /**
     * Définit le master gain manuellement (0.0 à 1.0)
     */
    fun setMasterGain(gain: Float) {
        masterGain = gain.coerceIn(0.0f, 1.0f)
    }

    /**
     * Récupère le master gain
     */
    fun getMasterGain(): Float = masterGain

    /**
     * Définit le volume d'un canal spécifique (0.0 à 1.0)
     */
    fun setChannelVolume(channel: Int, volume: Float) {
        if (channel in 0..15) {
            channelVolumes[channel] = volume.coerceIn(0.0f, 1.0f)
        }
    }

    /**
     * Récupère le volume d'un canal
     */
    fun getChannelVolume(channel: Int): Float {
        return if (channel in 0..15) channelVolumes[channel] else 1.0f
    }

    /**
     * Définit le boost/réduction d'un canal (pour équilibrage)
     */
    fun setChannelBoost(channel: Int, boost: Float) {
        if (channel in 0..15) {
            channelBoost[channel] = boost.coerceIn(0.0f, 2.0f)
        }
    }

    /**
     * Récupère le boost d'un canal
     */
    fun getChannelBoost(channel: Int): Float {
        return if (channel in 0..15) channelBoost[channel] else 1.0f
    }

    /**
     * Réinitialise tous les paramètres aux valeurs par défaut
     */
    fun reset() {
        setPreset(NormalizationPreset.MEDIUM)
        for (i in 0..15) {
            channelVolumes[i] = 1.0f
            channelBoost[i] = if (i == 9) 1.15f else 1.0f
        }
        processedEvents = 0
        clippedEvents = 0
    }

    /**
     * Traite un événement MIDI et retourne les bytes ajustés
     *
     * @param midiBytes Les bytes MIDI originaux
     * @return Les bytes MIDI avec velocity ajustée, ou null si l'événement doit être ignoré
     */
    fun processMidiEvent(midiBytes: ByteArray): ByteArray {
        if (midiBytes.isEmpty()) return midiBytes

        val status = midiBytes[0].toInt() and 0xFF
        val type = status and 0xF0

        // Traiter seulement les Note On (0x90) avec velocity > 0
        if (type == 0x90 && midiBytes.size >= 3) {
            val channel = status and 0x0F
            midiBytes[1].toInt() and 0x7F
            val originalVelocity = midiBytes[2].toInt() and 0x7F

            // Si velocity = 0, c'est un Note Off déguisé, ne pas traiter
            if (originalVelocity == 0) {
                return midiBytes
            }

            // Calculer la nouvelle velocity
            val adjustedVelocity = calculateAdjustedVelocity(channel, originalVelocity)

            processedEvents++
            if (adjustedVelocity < originalVelocity) {
                clippedEvents++
            }

            // Créer les nouveaux bytes avec velocity ajustée
            return byteArrayOf(
                midiBytes[0],
                midiBytes[1],
                adjustedVelocity.toByte()
            )
        }

        // Pour les autres événements, retourner tel quel
        return midiBytes
    }

    /**
     * Calcule la velocity ajustée pour un canal donné
     * Méthode publique pour permettre aux moteurs de synthèse d'utiliser la normalisation
     *
     * @param channel Le canal MIDI (0-15)
     * @param originalVelocity La velocity originale (1-127)
     * @param applyChannelVolume Si true, applique le volume du canal (pour les moteurs qui gèrent le volume séparément)
     * @return La velocity normalisée (1-127)
     */
    fun calculateAdjustedVelocity(channel: Int, originalVelocity: Int, applyChannelVolume: Boolean = true): Int {
        if (originalVelocity <= 0) return 0

        var velocity = originalVelocity.toFloat()

        // 1. Appliquer la compression si au-dessus du seuil
        if (velocity > compressionThreshold) {
            val excess = velocity - compressionThreshold
            velocity = compressionThreshold + (excess * compressionRatio)
        }

        // 2. Appliquer le cap de velocity
        velocity = velocity.coerceAtMost(velocityCap.toFloat())

        // 3. Appliquer le volume du canal (optionnel - certains moteurs gèrent ça via CC7)
        if (applyChannelVolume) {
            velocity *= channelVolumes[channel]
        }

        // 4. Appliquer le boost du canal (toujours appliqué pour l'équilibrage)
        velocity *= channelBoost[channel]

        // 5. Appliquer le master gain
        velocity *= masterGain

        // Convertir en int et clamper à 1-127 (0 = Note Off)
        return velocity.toInt().coerceIn(1, 127)
    }

    /**
     * Applique le boost d'instrument basé sur le program change
     * À appeler lors d'un program change pour ajuster le boost du canal
     *
     * @param channel Le canal MIDI
     * @param program Le numéro de programme (0-127)
     */
    fun applyInstrumentBoost(channel: Int, program: Int) {
        if (channel in 0..15) {
            // Canal 9 est toujours drums, garder le boost drums
            if (channel == 9) return

            val multiplier = InstrumentBalancing.getInstrumentMultiplier(program)
            channelBoost[channel] = multiplier
        }
    }

    /**
     * Récupère les statistiques de traitement
     */
    fun getStats(): String {
        val clippingPercent = if (processedEvents > 0) {
            (clippedEvents * 100.0 / processedEvents)
        } else 0.0
        return "Processed: $processedEvents, Clipped: $clippedEvents (${String.format(Locale.ROOT, "%.1f", clippingPercent)}%)"
    }

    /**
     * Log les statistiques actuelles
     */
    fun logStats() {
        // No-op - logging removed
    }

    /**
     * Configuration pour instruments spécifiques (Program Changes)
     * Certains instruments sont naturellement plus forts
     */
    object InstrumentBalancing {
        // Instruments qui ont tendance à saturer (réduction)
        private val loudInstruments = mapOf(
            // Brass
            56 to 0.8f,  // Trumpet
            57 to 0.8f,  // Trombone
            58 to 0.8f,  // Tuba
            59 to 0.85f, // Muted Trumpet
            60 to 0.8f,  // French Horn
            61 to 0.75f, // Brass Section
            62 to 0.75f, // Synth Brass 1
            63 to 0.75f, // Synth Brass 2

            // Synth Lead (souvent trop brillants)
            80 to 0.8f,  // Lead 1 (square)
            81 to 0.8f,  // Lead 2 (sawtooth)
            82 to 0.85f, // Lead 3 (calliope)
            83 to 0.85f, // Lead 4 (chiff)
            84 to 0.8f,  // Lead 5 (charang)

            // Organ
            16 to 0.85f, // Drawbar Organ
            17 to 0.85f, // Percussive Organ
            18 to 0.85f, // Rock Organ
            19 to 0.9f,  // Church Organ

            // Guitar (distorted)
            29 to 0.85f, // Overdriven Guitar
            30 to 0.8f,  // Distortion Guitar
        )

        // Instruments qui sont souvent trop faibles (boost)
        private val softInstruments = mapOf(
            // Strings
            40 to 1.1f,  // Violin
            41 to 1.1f,  // Viola
            42 to 1.1f,  // Cello
            43 to 1.05f, // Contrabass
            48 to 1.1f,  // String Ensemble 1

            // Flute/Wind
            73 to 1.15f, // Flute
            74 to 1.1f,  // Recorder
            75 to 1.1f,  // Pan Flute
            76 to 1.1f,  // Blown Bottle

            // Piano (un peu de boost pour équilibrer)
            0 to 1.05f,  // Acoustic Grand
            1 to 1.05f,  // Bright Acoustic
            2 to 1.0f,   // Electric Grand
            4 to 1.1f,   // Electric Piano 1
            5 to 1.1f,   // Electric Piano 2
        )

        /**
         * Récupère le multiplicateur de volume pour un instrument
         */
        fun getInstrumentMultiplier(program: Int): Float {
            return loudInstruments[program]
                ?: softInstruments[program]
                ?: 1.0f
        }
    }
}
