package com.Atom2Universe.app.audioeditor.core

import kotlin.math.PI
import kotlin.math.sin

/*
 * Le modèle d'un projet audio. Tout est **immuable** : une édition (couper, coller, déplacer un
 * clip…) fabrique un nouveau [Project] qui partage avec l'ancien tout ce qu'elle n'a pas touché.
 * L'historique n'est donc qu'une pile de projets (voir [EditSession]) et le fil audio peut lire
 * « son » instantané sans verrou.
 *
 * Les positions et les durées sont en **trames** (une trame = un échantillon par canal) à la
 * fréquence d'échantillonnage du projet. Les clips ne contiennent jamais d'audio : ils pointent
 * dans une [Source] (un fichier PCM sur disque) ; couper, déplacer ou rogner ne touche donc pas
 * aux fichiers et ne coûte rien.
 */

/** Forme d'une rampe de fondu : `x` va de 0 (silence) à 1 (plein niveau). */
enum class FadeShape {
    LINEAR, EXPONENTIAL, LOGARITHMIC, S_CURVE, EQUAL_POWER;

    fun curve(x: Float): Float {
        val c = x.coerceIn(0f, 1f)
        return when (this) {
            LINEAR -> c
            EXPONENTIAL -> c * c
            LOGARITHMIC -> kotlin.math.sqrt(c)
            S_CURVE -> c * c * (3f - 2f * c)
            EQUAL_POWER -> sin(c * PI.toFloat() / 2f)
        }
    }
}

/**
 * Une rampe de gain posée au début (fondu d'entrée) ou à la fin (fondu de sortie) d'un clip.
 *
 * [from] et [to] sont les positions de départ et d'arrivée **sur la courbe** : un fondu complet
 * va de 0 à 1 (entrée) ou de 1 à 0 (sortie). Quand on coupe un clip au milieu d'un fondu, chaque
 * morceau garde la portion de courbe qui lui revient (voir [slice]) : le fondu reste continu,
 * sans marche au point de coupe.
 */
data class Fade(
    val len: Long = 0,
    val from: Float = 0f,
    val to: Float = 1f,
    val shape: FadeShape = FadeShape.LINEAR,
) {
    val isNone get() = len <= 0

    /** Gain à la trame [offset] de la rampe (0 ≤ offset < [len]). */
    fun gainAt(offset: Long): Float {
        if (len <= 0) return 1f
        val p = from + (to - from) * (offset.toFloat() / len)
        return shape.curve(p)
    }

    /** La portion [[a], [b]) de la rampe, recalée pour commencer à 0. */
    fun slice(a: Long, b: Long): Fade {
        if (len <= 0 || b <= a) return NONE
        val fa = from + (to - from) * (a.toFloat() / len)
        val fb = from + (to - from) * (b.toFloat() / len)
        return Fade(b - a, fa, fb, shape)
    }

    companion object {
        val NONE = Fade()
        fun fadeIn(len: Long, shape: FadeShape = FadeShape.LINEAR) = if (len <= 0) NONE else Fade(len, 0f, 1f, shape)
        fun fadeOut(len: Long, shape: FadeShape = FadeShape.LINEAR) = if (len <= 0) NONE else Fade(len, 1f, 0f, shape)
    }
}

/** Un fichier PCM du projet (`sources/<id>.wav`). [float] : trames en flottants 32 bits, sinon entiers 16 bits. */
data class Source(
    val id: String,
    val file: String,
    val channels: Int,
    val frames: Long,
    val sampleRate: Int,
    val float: Boolean = false,
)

/**
 * Un morceau de [Source] posé sur une piste.
 *
 * Le clip lit [length] trames de la source à partir de [srcStart] et les joue à partir de [start]
 * sur la ligne de temps. Si [reversed], elles sont lues à l'envers (le dernier échantillon de la
 * portion passe en premier).
 */
data class Clip(
    val id: Int,
    val sourceId: String,
    val srcStart: Long,
    val length: Long,
    val start: Long,
    val gain: Float = 1f,
    val fadeIn: Fade = Fade.NONE,
    val fadeOut: Fade = Fade.NONE,
    val reversed: Boolean = false,
    val name: String = "",
) {
    val end get() = start + length

    /** Trame de la source jouée au décalage [t] (0 ≤ t < [length]) du clip. */
    fun sourceFrame(t: Long): Long = if (reversed) srcStart + length - 1 - t else srcStart + t

    /** Gain du clip au décalage [t] : son gain propre multiplié par les fondus. */
    fun gainAt(t: Long): Float {
        var g = gain
        if (t < fadeIn.len) g *= fadeIn.gainAt(t)
        val outStart = length - fadeOut.len
        if (fadeOut.len > 0 && t >= outStart) g *= fadeOut.gainAt(t - outStart)
        return g
    }

    /**
     * Le sous-clip qui couvre les décalages [[s], [e]) de ce clip, resté à sa place sur la ligne de
     * temps (`start + s`). Les fondus sont découpés avec lui.
     */
    fun slice(s: Long, e: Long, newId: Int): Clip {
        val a = s.coerceIn(0, length)
        val b = e.coerceIn(a, length)
        val newSrc = if (reversed) srcStart + (length - b) else srcStart + a
        val inEnd = minOf(b, fadeIn.len)
        val newIn = if (a < inEnd) fadeIn.slice(a, inEnd) else Fade.NONE
        val outBegin = length - fadeOut.len
        val outA = maxOf(a, outBegin)
        val newOut = if (fadeOut.len > 0 && outA < b) fadeOut.slice(outA - outBegin, b - outBegin) else Fade.NONE
        return copy(id = newId, srcStart = newSrc, length = b - a, start = start + a, fadeIn = newIn, fadeOut = newOut)
    }
}

/** Un point de l'enveloppe de volume d'une piste : le gain vaut [gain] à la trame [frame] (interpolé entre deux points). */
data class EnvPoint(val frame: Long, val gain: Float)

data class Track(
    val id: Int,
    val name: String,
    val color: Int = 0,
    val volume: Float = 1f,
    /** −1 (tout à gauche) … +1 (tout à droite). */
    val pan: Float = 0f,
    val mute: Boolean = false,
    val solo: Boolean = false,
    val locked: Boolean = false,
    val clips: List<Clip> = emptyList(),
    val envelope: List<EnvPoint> = emptyList(),
) {
    val end: Long get() = clips.maxOfOrNull { it.end } ?: 0L

    fun withClips(list: List<Clip>) = copy(clips = list.sortedWith(compareBy({ it.start }, { it.id })))

    /** Gain de l'enveloppe à la trame [frame] (1 s'il n'y a pas d'enveloppe). */
    fun envelopeGain(frame: Long): Float {
        val e = envelope
        if (e.isEmpty()) return 1f
        if (frame <= e.first().frame) return e.first().gain
        if (frame >= e.last().frame) return e.last().gain
        var lo = 0
        var hi = e.size - 1
        while (hi - lo > 1) {
            val mid = (lo + hi) ushr 1
            if (e[mid].frame <= frame) lo = mid else hi = mid
        }
        val p = e[lo]
        val q = e[hi]
        val span = (q.frame - p.frame).toFloat()
        return if (span <= 0f) q.gain else p.gain + (q.gain - p.gain) * ((frame - p.frame) / span)
    }
}

/** Un repère sur la ligne de temps ; [end] > [pos] en fait une zone. */
data class Marker(val id: Int, val pos: Long, val end: Long = pos, val name: String = "") {
    val isRegion get() = end > pos
}

data class Project(
    val sampleRate: Int = 44100,
    val tracks: List<Track> = emptyList(),
    val markers: List<Marker> = emptyList(),
    val sources: Map<String, Source> = emptyMap(),
    /** Volume général, appliqué au mixage final. */
    val master: Float = 1f,
    /** Prochain identifiant libre pour un clip, une piste ou un repère. */
    val nextId: Int = 1,
) {
    /** Fin du dernier clip : la durée du projet. */
    val length: Long get() = tracks.maxOfOrNull { it.end } ?: 0L

    /** Début du premier clip : tout ce qui précède est du vide (un bord gauche rogné ou un clip glissé en laisse). */
    val start: Long get() = tracks.flatMap { it.clips }.minOfOrNull { it.start } ?: 0L

    fun track(id: Int): Track? = tracks.firstOrNull { it.id == id }

    fun indexOfTrack(id: Int): Int = tracks.indexOfFirst { it.id == id }

    fun findClip(clipId: Int): Pair<Track, Clip>? {
        for (t in tracks) for (c in t.clips) if (c.id == clipId) return t to c
        return null
    }

    /** Remplace la piste [id] par le résultat de [f]. */
    fun mapTrack(id: Int, f: (Track) -> Track): Project =
        copy(tracks = tracks.map { if (it.id == id) f(it) else it })

    /** Les sources encore utilisées par un clip : le reste peut être effacé du disque. */
    fun usedSourceIds(): Set<String> {
        val out = HashSet<String>()
        for (t in tracks) for (c in t.clips) out.add(c.sourceId)
        return out
    }

    /** Quelles pistes s'entendent : s'il y a un solo, seules les pistes en solo ; sinon toutes celles qui ne sont pas en sourdine. */
    fun audibleTracks(): List<Track> {
        val anySolo = tracks.any { it.solo }
        return tracks.filter { if (anySolo) it.solo else !it.mute }
    }
}
