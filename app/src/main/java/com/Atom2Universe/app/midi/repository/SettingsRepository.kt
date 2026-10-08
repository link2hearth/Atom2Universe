package com.Atom2Universe.app.midi.repository

import android.content.Context
import com.Atom2Universe.app.midi.data.AppSettings
import com.Atom2Universe.app.midi.data.MidiDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Repository pour gérer les paramètres de l'application
 * Stocke: SoundFont path, dossier MIDI URI, préférences utilisateur
 */
class SettingsRepository(private val context: Context) {

    private val database = MidiDatabase.getInstance(context)
    private val settingsDao = database.settingsDao()

    companion object {
        // Clés des paramètres
        const val KEY_SOUNDFONT_PATH = "soundfont_path"
        const val KEY_SOUNDFONT_LABEL = "soundfont_label"
        const val KEY_MIDI_FOLDER_URI = "midi_folder_uri"
        const val KEY_MIDI_FOLDER_LABEL = "midi_folder_label"
        const val KEY_CURRENT_TRACK_ID = "current_track_id"
        const val KEY_PLAYBACK_POSITION = "playback_position"
        const val KEY_REPEAT_MODE = "repeat_mode"
        const val KEY_SHUFFLE_MODE = "shuffle_mode"
        const val KEY_LAST_PLAYLIST_ID = "last_playlist_id"

        // Audio mixer settings
        const val KEY_MIXER_PRESET = "mixer_preset"
        const val KEY_MASTER_GAIN = "master_gain"
        const val KEY_REVERB_PRESET = "reverb_preset"

        // SF2 Engine settings
        const val KEY_SF2_VELOCITY_CURVE = "sf2_velocity_curve"

        // Hybrid mode settings
        const val KEY_HYBRID_MODE_ENABLED = "hybrid_mode_enabled"
        const val KEY_HYBRID_SF2_PROGRAMS = "hybrid_sf2_programs"  // CSV: "0,48,49,50"
        const val KEY_HYBRID_USE_SF2_FOR_DRUMS = "hybrid_use_sf2_for_drums"
        const val KEY_HYBRID_BASE_ENGINE = "hybrid_base_engine"  // "sf2" or "fluidsynth"

        // Synthesizer mode: "sonivox", "sf2", "hybrid", "fluidsynth"
        const val KEY_SYNTH_MODE = "synth_mode"
        const val SYNTH_MODE_SONIVOX = "sonivox"
        const val SYNTH_MODE_SF2 = "sf2"
        const val SYNTH_MODE_HYBRID = "hybrid"
        const val SYNTH_MODE_FLUIDSYNTH = "fluidsynth"

        // MIDI EQ settings
        const val KEY_MIDI_EQ_ENABLED = "midi_eq_enabled"
        // Bandes: "midi_eq_band_0" .. "midi_eq_band_9"  (valeurs en millibels)
    }

    /**
     * Récupère tous les paramètres (pour debug/export)
     */
    val allSettings: Flow<List<AppSettings>> = settingsDao.getAllSettings()

    // === SoundFont ===

    /**
     * Récupère le chemin du SoundFont
     */
    suspend fun getSoundFontPath(): String? {
        return withContext(Dispatchers.IO) {
            settingsDao.getSetting(KEY_SOUNDFONT_PATH)
        }
    }

    /**
     * Sauvegarde le chemin du SoundFont
     */
    suspend fun saveSoundFontPath(path: String) {
        withContext(Dispatchers.IO) {
            settingsDao.saveSetting(AppSettings(KEY_SOUNDFONT_PATH, path))
        }
    }

    /**
     * Récupère le label/nom du SoundFont (pour affichage)
     */
    suspend fun getSoundFontLabel(): String? {
        return withContext(Dispatchers.IO) {
            settingsDao.getSetting(KEY_SOUNDFONT_LABEL)
        }
    }

    /**
     * Sauvegarde le label du SoundFont
     */
    suspend fun saveSoundFontLabel(label: String) {
        withContext(Dispatchers.IO) {
            settingsDao.saveSetting(AppSettings(KEY_SOUNDFONT_LABEL, label))
        }
    }

    // === Dossier MIDI ===

    /**
     * Récupère l'URI du dossier MIDI
     */
    suspend fun getMidiFolderUri(): String? {
        return withContext(Dispatchers.IO) {
            settingsDao.getSetting(KEY_MIDI_FOLDER_URI)
        }
    }

    /**
     * Sauvegarde l'URI du dossier MIDI
     */
    suspend fun saveMidiFolderUri(uri: String) {
        withContext(Dispatchers.IO) {
            settingsDao.saveSetting(AppSettings(KEY_MIDI_FOLDER_URI, uri))
        }
    }

    /**
     * Récupère le label du dossier MIDI (path relatif pour affichage)
     */
    suspend fun getMidiFolderLabel(): String? {
        return withContext(Dispatchers.IO) {
            settingsDao.getSetting(KEY_MIDI_FOLDER_LABEL)
        }
    }

    /**
     * Sauvegarde le label du dossier MIDI
     */
    suspend fun saveMidiFolderLabel(label: String) {
        withContext(Dispatchers.IO) {
            settingsDao.saveSetting(AppSettings(KEY_MIDI_FOLDER_LABEL, label))
        }
    }

    // === Playback State ===

    /**
     * Récupère l'ID du track en cours de lecture
     */
    suspend fun getCurrentTrackId(): Long? {
        return withContext(Dispatchers.IO) {
            settingsDao.getSetting(KEY_CURRENT_TRACK_ID)?.toLongOrNull()
        }
    }

    /**
     * Sauvegarde l'ID du track en cours
     */
    suspend fun saveCurrentTrackId(trackId: Long) {
        withContext(Dispatchers.IO) {
            settingsDao.saveSetting(AppSettings(KEY_CURRENT_TRACK_ID, trackId.toString()))
        }
    }

    /**
     * Récupère la position de lecture (millisecondes)
     */
    suspend fun getPlaybackPosition(): Long {
        return withContext(Dispatchers.IO) {
            settingsDao.getSetting(KEY_PLAYBACK_POSITION)?.toLongOrNull() ?: 0L
        }
    }

    /**
     * Sauvegarde la position de lecture
     */
    suspend fun savePlaybackPosition(position: Long) {
        withContext(Dispatchers.IO) {
            settingsDao.saveSetting(AppSettings(KEY_PLAYBACK_POSITION, position.toString()))
        }
    }

    /**
     * Récupère le mode repeat (0=none, 1=one, 2=all)
     */
    suspend fun getRepeatMode(): Int {
        return withContext(Dispatchers.IO) {
            settingsDao.getSetting(KEY_REPEAT_MODE)?.toIntOrNull() ?: 0
        }
    }

    /**
     * Sauvegarde le mode repeat
     */
    suspend fun saveRepeatMode(mode: Int) {
        withContext(Dispatchers.IO) {
            settingsDao.saveSetting(AppSettings(KEY_REPEAT_MODE, mode.toString()))
        }
    }

    /**
     * Récupère le mode shuffle (true/false)
     */
    suspend fun getShuffleMode(): Boolean {
        return withContext(Dispatchers.IO) {
            settingsDao.getSetting(KEY_SHUFFLE_MODE)?.toBoolean() ?: false
        }
    }

    /**
     * Sauvegarde le mode shuffle
     */
    suspend fun saveShuffleMode(enabled: Boolean) {
        withContext(Dispatchers.IO) {
            settingsDao.saveSetting(AppSettings(KEY_SHUFFLE_MODE, enabled.toString()))
        }
    }

    /**
     * Récupère l'ID de la dernière playlist ouverte
     */
    suspend fun getLastPlaylistId(): Long? {
        return withContext(Dispatchers.IO) {
            settingsDao.getSetting(KEY_LAST_PLAYLIST_ID)?.toLongOrNull()
        }
    }

    /**
     * Sauvegarde l'ID de la playlist en cours
     */
    suspend fun saveLastPlaylistId(playlistId: Long) {
        withContext(Dispatchers.IO) {
            settingsDao.saveSetting(AppSettings(KEY_LAST_PLAYLIST_ID, playlistId.toString()))
        }
    }

    // === Audio Mixer Settings ===

    /**
     * Récupère le preset de mixage (index de NormalizationPreset)
     */
    suspend fun getMixerPreset(): Int {
        return withContext(Dispatchers.IO) {
            settingsDao.getSetting(KEY_MIXER_PRESET)?.toIntOrNull() ?: 2 // Default: MEDIUM
        }
    }

    /**
     * Sauvegarde le preset de mixage
     */
    suspend fun saveMixerPreset(presetIndex: Int) {
        withContext(Dispatchers.IO) {
            settingsDao.saveSetting(AppSettings(KEY_MIXER_PRESET, presetIndex.toString()))
        }
    }

    /**
     * Récupère le master gain (0.0 à 1.0)
     */
    suspend fun getMasterGain(): Float {
        return withContext(Dispatchers.IO) {
            settingsDao.getSetting(KEY_MASTER_GAIN)?.toFloatOrNull() ?: 0.75f
        }
    }

    /**
     * Sauvegarde le master gain
     */
    suspend fun saveMasterGain(gain: Float) {
        withContext(Dispatchers.IO) {
            settingsDao.saveSetting(AppSettings(KEY_MASTER_GAIN, gain.toString()))
        }
    }

    /**
     * Récupère le preset de reverb (-1 à 3)
     */
    suspend fun getReverbPreset(): Int {
        return withContext(Dispatchers.IO) {
            settingsDao.getSetting(KEY_REVERB_PRESET)?.toIntOrNull() ?: -1 // Default: Off
        }
    }

    /**
     * Sauvegarde le preset de reverb
     */
    suspend fun saveReverbPreset(preset: Int) {
        withContext(Dispatchers.IO) {
            settingsDao.saveSetting(AppSettings(KEY_REVERB_PRESET, preset.toString()))
        }
    }

    // === SF2 Engine Settings ===

    /**
     * Récupère la courbe de vélocité SF2 (0=Linear, 1=Concave, 2=Soft, 3=Hard)
     */
    suspend fun getSf2VelocityCurve(): Int {
        return withContext(Dispatchers.IO) {
            settingsDao.getSetting(KEY_SF2_VELOCITY_CURVE)?.toIntOrNull() ?: 1 // Default: Concave
        }
    }

    /**
     * Sauvegarde la courbe de vélocité SF2
     */
    suspend fun saveSf2VelocityCurve(curve: Int) {
        withContext(Dispatchers.IO) {
            settingsDao.saveSetting(AppSettings(KEY_SF2_VELOCITY_CURVE, curve.toString()))
        }
    }

    // === MIDI EQ ===

    suspend fun getMidiEqEnabled(): Boolean {
        return withContext(Dispatchers.IO) {
            settingsDao.getSetting(KEY_MIDI_EQ_ENABLED)?.toBooleanStrictOrNull() ?: false
        }
    }

    suspend fun setMidiEqEnabled(enabled: Boolean) {
        withContext(Dispatchers.IO) {
            settingsDao.saveSetting(AppSettings(KEY_MIDI_EQ_ENABLED, enabled.toString()))
        }
    }

    suspend fun getMidiEqBandLevel(bandIndex: Int): Int {
        return withContext(Dispatchers.IO) {
            settingsDao.getSetting("midi_eq_band_$bandIndex")?.toIntOrNull() ?: 0
        }
    }

    suspend fun setMidiEqBandLevel(bandIndex: Int, millibels: Int) {
        withContext(Dispatchers.IO) {
            settingsDao.saveSetting(AppSettings("midi_eq_band_$bandIndex", millibels.toString()))
        }
    }

    // === Generic operations ===

    /**
     * Récupère un paramètre générique par clé
     */
    suspend fun getString(key: String, defaultValue: String? = null): String? {
        return withContext(Dispatchers.IO) {
            settingsDao.getSetting(key) ?: defaultValue
        }
    }

    /**
     * Sauvegarde un paramètre générique
     */
    suspend fun putString(key: String, value: String) {
        withContext(Dispatchers.IO) {
            settingsDao.saveSetting(AppSettings(key, value))
        }
    }

    /**
     * Supprime un paramètre
     */
    suspend fun remove(key: String) {
        withContext(Dispatchers.IO) {
            settingsDao.deleteSetting(key)
        }
    }

    /**
     * Supprime tous les paramètres
     */
    suspend fun clearAll() {
        withContext(Dispatchers.IO) {
            settingsDao.deleteAllSettings()
        }
    }

    // === Hybrid Mode Settings ===

    /**
     * Vérifie si le mode hybride est activé
     */
    suspend fun isHybridModeEnabled(): Boolean {
        return withContext(Dispatchers.IO) {
            settingsDao.getSetting(KEY_HYBRID_MODE_ENABLED)?.toBoolean() ?: false
        }
    }

    /**
     * Active/désactive le mode hybride
     */
    suspend fun setHybridModeEnabled(enabled: Boolean) {
        withContext(Dispatchers.IO) {
            settingsDao.saveSetting(AppSettings(KEY_HYBRID_MODE_ENABLED, enabled.toString()))
        }
    }

    /**
     * Récupère les programs MIDI (0-127) qui utilisent SF2 en mode hybride
     * @return Set des numéros de programs utilisant SF2
     */
    suspend fun getHybridSf2Programs(): Set<Int> {
        return withContext(Dispatchers.IO) {
            val csv = settingsDao.getSetting(KEY_HYBRID_SF2_PROGRAMS) ?: ""
            if (csv.isBlank()) {
                emptySet()
            } else {
                csv.split(",")
                    .mapNotNull { it.trim().toIntOrNull() }
                    .filter { it in 0..127 }
                    .toSet()
            }
        }
    }

    /**
     * Sauvegarde les programs MIDI qui utilisent SF2 en mode hybride
     * @param programs Set des numéros de programs (0-127)
     */
    suspend fun saveHybridSf2Programs(programs: Set<Int>) {
        withContext(Dispatchers.IO) {
            val csv = programs.filter { it in 0..127 }.sorted().joinToString(",")
            settingsDao.saveSetting(AppSettings(KEY_HYBRID_SF2_PROGRAMS, csv))
        }
    }

    /**
     * Vérifie si les percussions (channel 9) utilisent SF2 en mode hybride
     */
    suspend fun isHybridUseSf2ForDrums(): Boolean {
        return withContext(Dispatchers.IO) {
            settingsDao.getSetting(KEY_HYBRID_USE_SF2_FOR_DRUMS)?.toBoolean() ?: false
        }
    }

    /**
     * Définit si les percussions utilisent SF2 en mode hybride
     */
    suspend fun setHybridUseSf2ForDrums(useSf2: Boolean) {
        withContext(Dispatchers.IO) {
            settingsDao.saveSetting(AppSettings(KEY_HYBRID_USE_SF2_FOR_DRUMS, useSf2.toString()))
        }
    }

    /**
     * Gets the SF2 engine used as base for hybrid mode ("sf2" or "fluidsynth").
     * Defaults to "sf2" (A2U engine).
     */
    suspend fun getHybridBaseEngine(): String {
        return withContext(Dispatchers.IO) {
            settingsDao.getSetting(KEY_HYBRID_BASE_ENGINE) ?: SYNTH_MODE_SF2
        }
    }

    /**
     * Sets the SF2 engine used as base for hybrid mode.
     * @param engine SYNTH_MODE_SF2 or SYNTH_MODE_FLUIDSYNTH
     */
    suspend fun setHybridBaseEngine(engine: String) {
        withContext(Dispatchers.IO) {
            settingsDao.saveSetting(AppSettings(KEY_HYBRID_BASE_ENGINE, engine))
        }
    }

    // === Synthesizer Mode ===

    /**
     * Gets the current synthesizer mode.
     * @return "sonivox", "sf2", or "hybrid"
     */
    suspend fun getSynthMode(): String {
        return withContext(Dispatchers.IO) {
            settingsDao.getSetting(KEY_SYNTH_MODE) ?: SYNTH_MODE_SONIVOX
        }
    }

    /**
     * Sets the synthesizer mode.
     * @param mode One of SYNTH_MODE_SONIVOX, SYNTH_MODE_SF2, or SYNTH_MODE_HYBRID
     */
    suspend fun setSynthMode(mode: String) {
        withContext(Dispatchers.IO) {
            settingsDao.saveSetting(AppSettings(KEY_SYNTH_MODE, mode))
        }
    }

    /**
     * Checks if the current mode is Sonivox only.
     */
    suspend fun isSonivoxMode(): Boolean {
        return getSynthMode() == SYNTH_MODE_SONIVOX
    }

    /**
     * Checks if the current mode is SF2 only.
     */
    suspend fun isSf2Mode(): Boolean {
        return getSynthMode() == SYNTH_MODE_SF2
    }

    /**
     * Checks if the current mode is hybrid.
     */
    suspend fun isHybridMode(): Boolean {
        return getSynthMode() == SYNTH_MODE_HYBRID
    }

    /**
     * Checks if the current mode is FluidSynth.
     */
    suspend fun isFluidSynthMode(): Boolean {
        return getSynthMode() == SYNTH_MODE_FLUIDSYNTH
    }
}
