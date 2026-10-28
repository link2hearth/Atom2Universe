package com.Atom2Universe.app.music.equalizer

import android.content.Context
import com.Atom2Universe.app.R
import com.Atom2Universe.app.music.equalizer.data.EqPreset

/**
 * Nom affiché d'un préréglage d'égaliseur.
 *
 * Le nom en base reste la clé anglaise (« Flat », « Custom », « ~custom_track_12 ») : la
 * synchro cloud et la recherche par nom s'en servent. Seul l'affichage est traduit ; un
 * préréglage nommé par l'utilisateur garde son nom tel quel.
 */
object EqPresetNames {

    private val SYSTEM_NAMES = mapOf(
        "Flat" to R.string.music_eq_preset_flat,
        "Rock" to R.string.music_eq_preset_rock,
        "Pop" to R.string.music_eq_preset_pop,
        "Jazz" to R.string.music_eq_preset_jazz,
        "Classical" to R.string.music_eq_preset_classical,
        "Electronic" to R.string.music_eq_preset_electronic,
        "Bass Boost" to R.string.music_eq_preset_bass_boost,
        "Treble Boost" to R.string.music_eq_preset_treble_boost,
        "Vocal" to R.string.music_eq_preset_vocal,
        "Late Night" to R.string.music_eq_preset_late_night
    )

    // Préfixes des préréglages propres à un morceau, un album ou un artiste (EqualizerFragment)
    private const val TRACK_PREFIX = "~custom_track_"
    private const val ALBUM_PREFIX = "~custom_album_"
    private const val ARTIST_PREFIX = "~custom_artist_"

    fun displayName(context: Context, preset: EqPreset): String =
        displayName(context, preset.name, preset.isSystemPreset)

    fun displayName(context: Context, name: String, isSystemPreset: Boolean): String {
        val res = if (isSystemPreset) {
            SYSTEM_NAMES[name]
        } else when {
            name == MusicEqualizerManager.CUSTOM_PRESET_NAME -> R.string.music_eq_preset_custom
            name.startsWith(TRACK_PREFIX) -> R.string.music_eq_preset_custom_track
            name.startsWith(ALBUM_PREFIX) -> R.string.music_eq_preset_custom_album
            name.startsWith(ARTIST_PREFIX) -> R.string.music_eq_preset_custom_artist
            else -> null
        }
        return res?.let { context.getString(it) } ?: name
    }
}
