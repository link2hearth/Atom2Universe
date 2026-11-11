package com.Atom2Universe.app.studio

import com.Atom2Universe.app.R
import com.Atom2Universe.app.studio.data.PadPlayMode
import com.Atom2Universe.app.studio.data.Track
import com.Atom2Universe.app.studio.engine.Inst
import com.Atom2Universe.app.studio.engine.TrackKind

/** Les noms et les couleurs que l'écran du Studio et la bibliothèque partagent. */
object StudioNames {

    fun padMode(mode: PadPlayMode): Int = when (mode) {
        PadPlayMode.DRIVE -> R.string.studio_pad_mode_drive
        PadPlayMode.FILTER -> R.string.studio_pad_mode_filter
        PadPlayMode.CLEAN -> R.string.studio_pad_mode_clean
        PadPlayMode.THEREMIN -> R.string.studio_pad_mode_theremin
    }

    /** Le texte du nom d'un style de départ. */
    fun style(id: String): Int = when (id) {
        "techno" -> R.string.studio_style_techno
        "hiphop" -> R.string.studio_style_hiphop
        "lofi" -> R.string.studio_style_lofi
        "electro" -> R.string.studio_style_electro
        "ambient" -> R.string.studio_style_ambient
        "trap" -> R.string.studio_style_trap
        else -> R.string.studio_style_house
    }
}

/** Les couleurs des pistes : une par son de batterie, et des teintes pour les synthés et instruments ajoutés. */
object StudioColors {
    /** Grosse caisse verte, caisse claire rouge, clap orange, charleston jaune, etc. */
    val LANE = intArrayOf(
        0xFF2ECC71.toInt(), 0xFFE74C3C.toInt(), 0xFFF39C12.toInt(), 0xFFF1C40F.toInt(),
        0xFF3498DB.toInt(), 0xFFE91E8C.toInt(), 0xFF9B59B6.toInt(), 0xFF1ABC9C.toInt(),
    )

    val EXTRA = intArrayOf(
        0xFF8E44AD.toInt(), 0xFFD35400.toInt(), 0xFF16A085.toInt(), 0xFF2980B9.toInt(),
        0xFFC0392B.toInt(), 0xFF27AE60.toInt(), 0xFFB7950B.toInt(), 0xFF7F8C8D.toInt(),
    )

    fun of(t: Track): Int = when {
        t.kind == TrackKind.DRUM -> LANE[t.voice.coerceIn(0, Inst.DRUM_COUNT - 1)]
        t.id == Inst.SYNTH -> LANE[Inst.SYNTH]
        else -> EXTRA[t.id % EXTRA.size]
    }
}
