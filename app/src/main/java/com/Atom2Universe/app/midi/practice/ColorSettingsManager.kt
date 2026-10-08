package com.Atom2Universe.app.midi.practice

import android.content.Context
import android.util.Log
import com.Atom2Universe.app.midi.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Gestionnaire centralisé des couleurs pour le mode Practice MIDI
 *
 * Hiérarchie des couleurs (priorité):
 * 1. Couleur instrument (si définie pour le program 0-127)
 * 2. Couleur canal (personnalisée ou par défaut, 0-15)
 */
object ColorSettingsManager {
    private const val TAG = "ColorSettingsManager"

    private var settingsRepository: SettingsRepository? = null
    private var isInitialized = false

    // Cache pour éviter les accès DB répétés
    private val channelColors = mutableMapOf<Int, Int?>()
    private val instrumentColors = mutableMapOf<Int, Int?>()

    // Clés pour le stockage
    const val KEY_CHANNEL_COLOR_PREFIX = "channel_color_"
    const val KEY_INSTRUMENT_COLOR_PREFIX = "instrument_color_"

    /**
     * Couleurs par défaut des 16 canaux MIDI (format ARGB)
     */
    val DEFAULT_CHANNEL_COLORS = intArrayOf(
        0xFF2196F3.toInt(),  // 0 - Bleu
        0xFFF44336.toInt(),  // 1 - Rouge
        0xFF4CAF50.toInt(),  // 2 - Vert
        0xFFFF9800.toInt(),  // 3 - Orange
        0xFF9C27B0.toInt(),  // 4 - Violet
        0xFF00BCD4.toInt(),  // 5 - Cyan
        0xFFE91E63.toInt(),  // 6 - Rose
        0xFF8BC34A.toInt(),  // 7 - Vert clair
        0xFF3F51B5.toInt(),  // 8 - Indigo
        0xFFFFEB3B.toInt(),  // 9 - Jaune (Drums)
        0xFF009688.toInt(),  // 10 - Teal
        0xFFFF5722.toInt(),  // 11 - Orange foncé
        0xFF673AB7.toInt(),  // 12 - Violet foncé
        0xFF03A9F4.toInt(),  // 13 - Bleu clair
        0xFFCDDC39.toInt(),  // 14 - Lime
        0xFF795548.toInt()   // 15 - Marron
    )

    /**
     * Initialise le gestionnaire avec le contexte de l'application
     */
    fun init(context: Context) {
        if (!isInitialized) {
            settingsRepository = SettingsRepository(context.applicationContext)
            isInitialized = true
        }
    }

    /**
     * Charge toutes les couleurs depuis la base de données vers le cache
     */
    suspend fun loadAllColors() {
        val repo = settingsRepository ?: return

        withContext(Dispatchers.IO) {
            // Charger les couleurs des canaux
            for (channel in 0..15) {
                val colorStr = repo.getString("$KEY_CHANNEL_COLOR_PREFIX$channel")
                channelColors[channel] = colorStr?.toIntOrNull()
            }

            // Charger les couleurs des instruments
            for (program in 0..127) {
                val colorStr = repo.getString("$KEY_INSTRUMENT_COLOR_PREFIX$program")
                instrumentColors[program] = colorStr?.toIntOrNull()
            }
        }
    }

    /**
     * Retourne la couleur pour une note selon son canal et programme
     * Priorité: couleur instrument > couleur canal custom > couleur canal par défaut
     */
    fun getNoteColor(channel: Int, program: Int): Int {
        // 1. Vérifier couleur instrument (si définie)
        instrumentColors[program]?.let { return it }

        // 2. Vérifier couleur canal custom
        channelColors[channel]?.let { return it }

        // 3. Couleur par défaut
        return DEFAULT_CHANNEL_COLORS[channel % 16]
    }

    /**
     * Debug: log the current state of color caches
     */
    fun logCacheState() {
        val customChannels = channelColors.filter { it.value != null }
        val customInstruments = instrumentColors.filter { it.value != null }
        Log.d(TAG, "Cache state: ${customChannels.size} custom channel colors, ${customInstruments.size} custom instrument colors")
        customChannels.forEach { (ch, color) ->
            Log.d(TAG, "  Channel $ch: ${color?.let { String.format("#%08X", it) }}")
        }
        customInstruments.forEach { (prog, color) ->
            Log.d(TAG, "  Instrument $prog: ${color?.let { String.format("#%08X", it) }}")
        }
    }

    /**
     * Retourne la couleur d'un canal (custom ou par défaut)
     */
    fun getChannelColor(channel: Int): Int {
        return channelColors[channel] ?: DEFAULT_CHANNEL_COLORS[channel % 16]
    }

    /**
     * Retourne true si le canal a une couleur personnalisée
     */
    fun hasCustomChannelColor(channel: Int): Boolean {
        return channelColors[channel] != null
    }

    /**
     * Retourne la couleur d'un instrument (custom ou null)
     */
    fun getInstrumentColor(program: Int): Int? {
        return instrumentColors[program]
    }

    /**
     * Retourne true si l'instrument a une couleur personnalisée
     */
    fun hasCustomInstrumentColor(program: Int): Boolean {
        return instrumentColors[program] != null
    }

    /**
     * Définit la couleur d'un canal (null pour reset)
     */
    suspend fun setChannelColor(channel: Int, color: Int?) {
        val repo = settingsRepository ?: return
        channelColors[channel] = color

        withContext(Dispatchers.IO) {
            val key = "$KEY_CHANNEL_COLOR_PREFIX$channel"
            if (color != null) {
                repo.putString(key, color.toString())
            } else {
                repo.remove(key)
            }
        }
    }

    /**
     * Définit la couleur d'un instrument (null pour reset)
     */
    suspend fun setInstrumentColor(program: Int, color: Int?) {
        val repo = settingsRepository ?: return
        instrumentColors[program] = color

        withContext(Dispatchers.IO) {
            val key = "$KEY_INSTRUMENT_COLOR_PREFIX$program"
            if (color != null) {
                repo.putString(key, color.toString())
            } else {
                repo.remove(key)
            }
        }
    }

    /**
     * Réinitialise toutes les couleurs aux valeurs par défaut
     */
    suspend fun resetToDefaults() {
        val repo = settingsRepository ?: return

        withContext(Dispatchers.IO) {
            // Reset couleurs canaux
            for (channel in 0..15) {
                repo.remove("$KEY_CHANNEL_COLOR_PREFIX$channel")
            }

            // Reset couleurs instruments
            for (program in 0..127) {
                repo.remove("$KEY_INSTRUMENT_COLOR_PREFIX$program")
            }
        }

        // Vider le cache
        channelColors.clear()
        instrumentColors.clear()
    }

    /**
     * Réinitialise seulement les couleurs des canaux
     */
    suspend fun resetChannelColors() {
        val repo = settingsRepository ?: return

        withContext(Dispatchers.IO) {
            for (channel in 0..15) {
                repo.remove("$KEY_CHANNEL_COLOR_PREFIX$channel")
            }
        }
        channelColors.clear()
    }

    /**
     * Réinitialise seulement les couleurs des instruments
     */
    suspend fun resetInstrumentColors() {
        val repo = settingsRepository ?: return

        withContext(Dispatchers.IO) {
            for (program in 0..127) {
                repo.remove("$KEY_INSTRUMENT_COLOR_PREFIX$program")
            }
        }
        instrumentColors.clear()
    }

    /**
     * Retourne le nombre d'instruments avec une couleur personnalisée
     */
    fun getCustomInstrumentColorCount(): Int {
        return instrumentColors.count { it.value != null }
    }

    /**
     * Retourne le nombre de canaux avec une couleur personnalisée
     */
    fun getCustomChannelColorCount(): Int {
        return channelColors.count { it.value != null }
    }
}
