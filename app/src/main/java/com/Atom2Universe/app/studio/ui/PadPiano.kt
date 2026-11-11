package com.Atom2Universe.app.studio.ui

import com.Atom2Universe.app.studio.music.StudioRegister

/** Géométrie commune au dessin et au toucher, même si la gamme omet des notes ou commence sur une noire. */
internal class PadPiano(levels: IntArray) {
    class Key(val pitch: Int, val black: Boolean, val enabled: Boolean, val left: Float, val right: Float, val depth: Float)

    val keys: List<Key>

    init {
        val low = levels.first()
        val high = levels.last()
        var cursor = 0f
        val whites = mutableListOf<Key>()
        val blacks = mutableListOf<Key>()
        for (pitch in low..high) {
            val black = StudioRegister.whites.binarySearch(pitch) < 0
            val enabled = levels.binarySearch(pitch) >= 0
            if (!black) {
                whites += Key(pitch, false, enabled, cursor, cursor + 1f, 1f)
                cursor += 1f
            } else if (pitch == low || pitch == high) {
                // Une tonique noire au bord reste accessible sur toute la hauteur de la tuile.
                blacks += Key(pitch, true, enabled, cursor, cursor + BLACK_WIDTH, 1f)
                cursor += BLACK_WIDTH
            } else {
                blacks += Key(pitch, true, enabled, cursor - BLACK_WIDTH / 2, cursor + BLACK_WIDTH / 2, 0.60f)
            }
        }
        keys = (whites + blacks).map { Key(it.pitch, it.black, it.enabled, it.left / cursor, it.right / cursor, it.depth) }
    }

    fun pitchAt(x: Float, y: Float): Int? {
        // La touche du dessus intercepte le doigt même quand elle est hors gamme.
        val horizontal = x.coerceIn(0f, Math.nextDown(1f))
        val key = keys.lastOrNull { horizontal >= it.left && horizontal < it.right && y <= it.depth }
        return key?.takeIf { it.enabled }?.pitch
    }

    private companion object { const val BLACK_WIDTH = 0.62f }
}
