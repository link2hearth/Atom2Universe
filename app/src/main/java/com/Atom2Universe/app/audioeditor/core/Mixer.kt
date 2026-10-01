package com.Atom2Universe.app.audioeditor.core

/** Donne au [Mixer] les échantillons d'une source, en flottants planaires (un tableau par canal de la source). */
interface SampleProvider {
    /** Remplit `dst[c][off until off + count]` avec les trames `frame until frame + count` ; du silence hors des bornes. */
    fun read(source: Source, frame: Long, count: Int, dst: Array<FloatArray>, off: Int)
}

/** Les crêtes (valeur absolue maximale, avant écrêtage) d'un bloc mixé, pour les vumètres. */
class Levels {
    var peakL = 0f
    var peakR = 0f
}

/**
 * Mélange un [Project] en stéréo, bloc par bloc.
 *
 * C'est le même code pour la lecture, l'export, les aperçus et les vignettes : aucune de ces
 * fonctions n'a sa propre idée de ce que « jouer le projet » veut dire. Un mixeur n'est pas partagé
 * entre fils (il garde des tampons de travail) ; le [Project], lui, est immuable.
 *
 * Par échantillon : `source × gain du clip (fondus compris) × enveloppe de la piste × volume de la
 * piste × panoramique`, puis le volume général. Le panoramique est celui d'Audacity : au centre les
 * deux côtés restent à pleine échelle, et le côté opposé s'atténue quand on pousse d'un côté. Seules
 * les pistes qui s'entendent (solo / sourdine) sont mélangées.
 */
class Mixer(private val provider: SampleProvider) {

    private var tmp = arrayOf(FloatArray(0), FloatArray(0))
    // Les vues passées au fournisseur sont préallouées : aucune allocation dans la boucle de rendu.
    private var monoView = arrayOf(tmp[0])
    private var stereoView = arrayOf(tmp[0], tmp[1])

    /**
     * Remplit `outL` / `outR` (au moins [frames] valeurs) avec les trames `start until start + frames`.
     * Rien n'est écrêté : c'est à l'appelant de borner (lecture) ou de choisir (export en flottant).
     */
    fun render(project: Project, start: Long, frames: Int, outL: FloatArray, outR: FloatArray, levels: Levels? = null) {
        java.util.Arrays.fill(outL, 0, frames, 0f)
        java.util.Arrays.fill(outR, 0, frames, 0f)
        if (tmp[0].size < frames) {
            tmp = arrayOf(FloatArray(frames), FloatArray(frames))
            monoView = arrayOf(tmp[0])
            stereoView = arrayOf(tmp[0], tmp[1])
        }
        val blockEnd = start + frames

        for (track in project.audibleTracks()) {
            val panL = if (track.pan > 0f) 1f - track.pan else 1f
            val panR = if (track.pan < 0f) 1f + track.pan else 1f
            val hasEnv = track.envelope.isNotEmpty()
            for (clip in track.clips) {
                if (clip.end <= start || clip.start >= blockEnd) continue
                val o0 = maxOf(start, clip.start)
                val o1 = minOf(blockEnd, clip.end)
                val n = (o1 - o0).toInt()
                if (n <= 0) continue
                val src = project.sources[clip.sourceId] ?: continue
                val t0 = o0 - clip.start
                val dst = (o0 - start).toInt()

                // Les clips retournés lisent le bloc correspondant de la source puis le renversent.
                val srcFrame = if (clip.reversed) clip.srcStart + clip.length - (t0 + n) else clip.srcStart + t0
                val stereo = src.channels >= 2
                val chans = if (stereo) stereoView else monoView
                provider.read(src, srcFrame, n, chans, 0)
                if (clip.reversed) for (c in chans) c.reverse(0, n)

                val fadeInLen = clip.fadeIn.len
                val outStart = clip.length - clip.fadeOut.len
                val left = tmp[0]
                val right = if (stereo) tmp[1] else tmp[0]
                val trackGain = track.volume
                for (i in 0 until n) {
                    val t = t0 + i
                    var g = if (t < fadeInLen || t >= outStart) clip.gainAt(t) else clip.gain
                    g *= trackGain
                    if (hasEnv) g *= track.envelopeGain(o0 + i)
                    outL[dst + i] += left[i] * g * panL
                    outR[dst + i] += right[i] * g * panR
                }
            }
        }

        val master = project.master
        var pl = 0f
        var pr = 0f
        for (i in 0 until frames) {
            val l = outL[i] * master
            val r = outR[i] * master
            outL[i] = l
            outR[i] = r
            val al = if (l < 0f) -l else l
            val ar = if (r < 0f) -r else r
            if (al > pl) pl = al
            if (ar > pr) pr = ar
        }
        if (levels != null) {
            levels.peakL = pl
            levels.peakR = pr
        }
    }
}
