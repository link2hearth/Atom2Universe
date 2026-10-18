package com.Atom2Universe.app.midi.visualizer

import android.content.Context
import com.Atom2Universe.app.R

/**
 * Table des 128 instruments General MIDI (GM) standard
 *
 * Les programmes MIDI 0-127 correspondent aux instruments ci-dessous.
 * Le canal 10 (index 9) est réservé à la batterie.
 */
object GeneralMidiInstruments {

    /**
     * Mapping des notes de batterie vers les IDs de ressources strings
     */
    private val drumResourceIds = mapOf(
        35 to R.string.gm_drum_35,
        36 to R.string.gm_drum_36,
        37 to R.string.gm_drum_37,
        38 to R.string.gm_drum_38,
        39 to R.string.gm_drum_39,
        40 to R.string.gm_drum_40,
        41 to R.string.gm_drum_41,
        42 to R.string.gm_drum_42,
        43 to R.string.gm_drum_43,
        44 to R.string.gm_drum_44,
        45 to R.string.gm_drum_45,
        46 to R.string.gm_drum_46,
        47 to R.string.gm_drum_47,
        48 to R.string.gm_drum_48,
        49 to R.string.gm_drum_49,
        50 to R.string.gm_drum_50,
        51 to R.string.gm_drum_51,
        52 to R.string.gm_drum_52,
        53 to R.string.gm_drum_53,
        54 to R.string.gm_drum_54,
        55 to R.string.gm_drum_55,
        56 to R.string.gm_drum_56,
        57 to R.string.gm_drum_57,
        58 to R.string.gm_drum_58,
        59 to R.string.gm_drum_59,
        60 to R.string.gm_drum_60,
        61 to R.string.gm_drum_61,
        62 to R.string.gm_drum_62,
        63 to R.string.gm_drum_63,
        64 to R.string.gm_drum_64,
        65 to R.string.gm_drum_65,
        66 to R.string.gm_drum_66,
        67 to R.string.gm_drum_67,
        68 to R.string.gm_drum_68,
        69 to R.string.gm_drum_69,
        70 to R.string.gm_drum_70,
        71 to R.string.gm_drum_71,
        72 to R.string.gm_drum_72,
        73 to R.string.gm_drum_73,
        74 to R.string.gm_drum_74,
        75 to R.string.gm_drum_75,
        76 to R.string.gm_drum_76,
        77 to R.string.gm_drum_77,
        78 to R.string.gm_drum_78,
        79 to R.string.gm_drum_79,
        80 to R.string.gm_drum_80,
        81 to R.string.gm_drum_81
    )

    /**
     * Catégories d'instruments avec leurs IDs de ressources
     */
    enum class Category(val stringResId: Int, val range: IntRange) {
        PIANO(R.string.gm_category_piano, 0..7),
        CHROMATIC_PERCUSSION(R.string.gm_category_chromatic_percussion, 8..15),
        ORGAN(R.string.gm_category_organ, 16..23),
        GUITAR(R.string.gm_category_guitar, 24..31),
        BASS(R.string.gm_category_bass, 32..39),
        STRINGS(R.string.gm_category_strings, 40..47),
        ENSEMBLE(R.string.gm_category_ensemble, 48..55),
        BRASS(R.string.gm_category_brass, 56..63),
        REED(R.string.gm_category_reed, 64..71),
        PIPE(R.string.gm_category_pipe, 72..79),
        SYNTH_LEAD(R.string.gm_category_synth_lead, 80..87),
        SYNTH_PAD(R.string.gm_category_synth_pad, 88..95),
        SYNTH_FX(R.string.gm_category_synth_fx, 96..103),
        ETHNIC(R.string.gm_category_ethnic, 104..111),
        PERCUSSIVE(R.string.gm_category_percussive, 112..119),
        SOUND_FX(R.string.gm_category_sound_fx, 120..127);

        fun getDisplayName(context: Context): String {
            return context.getString(stringResId)
        }
    }

    /**
     * Retourne le nom de l'instrument pour un programme donné (localisé)
     */
    fun getName(context: Context, program: Int): String {
        return if (program in 0..127) {
            val instruments = context.resources.getStringArray(R.array.gm_instruments)
            instruments.getOrElse(program) { context.getString(R.string.gm_instrument_unknown) }
        } else {
            context.getString(R.string.gm_instrument_unknown)
        }
    }

    /**
     * Retourne le nom de la percussion pour une note donnée (canal 10) (localisé)
     */
    fun getDrumName(context: Context, note: Int): String {
        val resId = drumResourceIds[note]
        return if (resId != null) {
            context.getString(resId)
        } else {
            context.getString(R.string.gm_percussion_format, note)
        }
    }

    /**
     * Retourne la catégorie d'un instrument
     */
    fun getCategory(program: Int): Category? {
        return Category.values().find { program in it.range }
    }

    /**
     * Retourne le nom de la catégorie pour un programme (localisé)
     */
    fun getCategoryName(context: Context, program: Int): String {
        return getCategory(program)?.getDisplayName(context)
            ?: context.getString(R.string.gm_category_other)
    }

    /**
     * Retourne une icône emoji pour la catégorie
     */
    fun getCategoryEmoji(program: Int): String {
        return when (getCategory(program)) {
            Category.PIANO -> "🎹"
            Category.CHROMATIC_PERCUSSION -> "🔔"
            Category.ORGAN -> "🎛️"
            Category.GUITAR -> "🎸"
            Category.BASS -> "🎸"
            Category.STRINGS -> "🎻"
            Category.ENSEMBLE -> "🎼"
            Category.BRASS -> "🎺"
            Category.REED -> "🎷"
            Category.PIPE -> "🪈"
            Category.SYNTH_LEAD, Category.SYNTH_PAD, Category.SYNTH_FX -> "🎛️"
            Category.ETHNIC -> "🪕"
            Category.PERCUSSIVE -> "🥁"
            Category.SOUND_FX -> "🔊"
            null -> "🎵"
        }
    }

    /**
     * Retourne une couleur représentative pour un canal
     */
    fun getChannelColor(channel: Int): Int {
        // Couleurs distinctes pour les 16 canaux
        return CHANNEL_COLORS[channel % CHANNEL_COLORS.size]
    }

    /**
     * Couleurs pour chaque canal MIDI (format ARGB)
     */
    private val CHANNEL_COLORS = intArrayOf(
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
}
