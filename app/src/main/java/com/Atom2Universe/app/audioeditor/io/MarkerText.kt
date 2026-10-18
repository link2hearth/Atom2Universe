package com.Atom2Universe.app.audioeditor.io

import com.Atom2Universe.app.audioeditor.core.Marker
import java.util.Locale

/**
 * Les repères en texte, au format des étiquettes d'Audacity : une ligne par repère, `début<TAB>fin<TAB>nom`, les
 * temps en secondes avec six décimales et un point. Un repère simple a la même valeur de début et de fin. Un
 * fichier d'étiquettes d'Audacity s'importe donc tel quel, et inversement.
 */
object MarkerText {

    /** Une étiquette lue : temps en secondes, pas encore en trames. */
    class Label(val startSec: Double, val endSec: Double, val name: String)

    fun format(markers: List<Marker>, sampleRate: Int): String {
        val sb = StringBuilder()
        for (m in markers.sortedBy { it.pos }) {
            sb.append(seconds(m.pos, sampleRate)).append('\t')
                .append(seconds(maxOf(m.end, m.pos), sampleRate)).append('\t')
                .append(m.name.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')).append('\n')
        }
        return sb.toString()
    }

    private fun seconds(frames: Long, rate: Int) = String.format(Locale.ROOT, "%.6f", frames.toDouble() / rate)

    /**
     * Lit des étiquettes. Les lignes vides, celles qui commencent par `\` (les plages de fréquences d'Audacity) et
     * celles qu'on ne comprend pas sont ignorées ; une fin avant le début est ramenée au début.
     */
    fun parse(text: String): List<Label> {
        val out = ArrayList<Label>()
        for (raw in text.lineSequence()) {
            val line = raw.trimEnd('\r')
            if (line.isBlank() || line.startsWith("\\")) continue
            val parts = line.split('\t')
            if (parts.size < 3) continue
            val a = number(parts[0]) ?: continue
            val b = number(parts[1]) ?: continue
            if (a < 0 || b < 0) continue
            out += Label(a, maxOf(a, b), parts.drop(2).joinToString(" ").trim())
        }
        return out
    }

    private fun number(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }
}
