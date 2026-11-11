package com.Atom2Universe.app.studio.ui

import android.graphics.Color
import androidx.core.graphics.ColorUtils

/** Une teinte par octave MIDI, partagée par le clavier de jeu et la colonne du piano roll. */
internal class StudioKeyColors(private val dark: Boolean) {
    private val white = IntArray(octaves.size) {
        ColorUtils.blendARGB(if (dark) 0xFFEBEBEF.toInt() else Color.WHITE, octaves[it], 0.75f)
    }
    private val black = IntArray(octaves.size) { ColorUtils.blendARGB(0xFF171920.toInt(), octaves[it], 0.12f) }
    private val mutedWhite = IntArray(octaves.size) {
        ColorUtils.blendARGB(if (dark) 0xFF58585F.toInt() else 0xFFB8B8BF.toInt(), octaves[it], 0.13f)
    }
    private val mutedBlack = if (dark) 0xFF28292F.toInt() else 0xFF44454B.toInt()

    fun face(pitch: Int, sharp: Boolean, enabled: Boolean, rhythm: Boolean, pressed: Boolean, accent: Int): Int {
        if (pressed) return accent
        val octave = pitch / 12
        val base = when {
            sharp -> if (enabled) black[octave] else mutedBlack
            enabled -> white[octave]
            else -> mutedWhite[octave]
        }
        return if (rhythm) ColorUtils.blendARGB(base, accent, 0.40f) else base
    }

    fun ink(face: Int, sharp: Boolean, enabled: Boolean, highlighted: Boolean): Int {
        if (highlighted) return if (ColorUtils.calculateLuminance(face) > 0.19) darkInk else Color.WHITE
        return if (sharp || dark && !enabled) Color.WHITE else darkInk
    }

    private companion object {
        val darkInk = 0xFF171B20.toInt()
        // Do−1 à Do9 : bleu froid, turquoise, sauge, blé, pêche et corail.
        val octaves = intArrayOf(
            0xFFC3CCEB.toInt(), 0xFFBDD4E9.toInt(), 0xFFB8DCDF.toInt(), 0xFFB9E0D2.toInt(),
            0xFFCBDFBF.toInt(), 0xFFE0E3BA.toInt(), 0xFFF0DAB6.toInt(), 0xFFF3C9AF.toInt(),
            0xFFEFBBA8.toInt(), 0xFFEBAFA5.toInt(), 0xFFE4A2A3.toInt(),
        )
    }
}
