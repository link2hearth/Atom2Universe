package com.Atom2Universe.app.studio.dsp

/**
 * Un synthé extérieur au Studio qui sait rendre du son stéréo à la demande : en pratique le lecteur SF2 maison de
 * l'appli. Cette interface garde le moteur du Studio sans aucune dépendance Android (les tests utilisent un faux).
 */
interface ExternalSynth {
    fun noteOn(note: Int, velocity: Int)
    fun noteOff(note: Int)
    fun allOff()

    /** Choisit l'instrument du soundfont (banque et programme GM). */
    fun setProgram(bank: Int, program: Int)

    /** Écrit le son rendu dans [left] et [right] (déjà remis à zéro) sur [count] trames. */
    fun render(left: FloatArray, right: FloatArray, count: Int)

    val activeVoices: Int
}

/**
 * Une piste « soundfont » : le mixeur du Studio (volume, panoramique, envois, effets) s'applique au son d'un
 * [ExternalSynth]. Sans source (soundfont absent), la piste est simplement silencieuse.
 */
class Sf2Instrument(sampleRate: Int, private val source: ExternalSynth?) : Instrument(sampleRate) {

    private val left = FloatArray(MAX_CHUNK)
    private val right = FloatArray(MAX_CHUNK)

    fun setProgram(bank: Int, program: Int) {
        source?.setProgram(bank, program)
    }

    override fun noteOn(note: Int, velocity: Float) {
        source?.noteOn(note, (velocity * 127f).toInt().coerceIn(1, 127))
    }

    override fun noteOff(note: Int) {
        source?.noteOff(note)
    }

    override fun allOff() {
        source?.allOff()
    }

    override val activeVoices: Int get() = source?.activeVoices ?: 0

    override fun renderMono(out: FloatArray, count: Int): Boolean {
        val s = source ?: return false
        if (s.activeVoices == 0) return false
        java.util.Arrays.fill(left, 0, count, 0f)
        java.util.Arrays.fill(right, 0, count, 0f)
        s.render(left, right, count)
        // Le mixeur du Studio place chaque piste dans le stéréo : on garde le milieu du son du soundfont.
        for (i in 0 until count) out[i] = (left[i] + right[i]) * 0.5f
        return true
    }
}
