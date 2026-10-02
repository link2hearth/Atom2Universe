package com.Atom2Universe.app.audioeditor.core

/*
 * Les éditions de la ligne de temps. Chaque fonction prend un [Project] et en rend un autre :
 * rien n'est modifié sur place, rien ne touche aux fichiers audio. C'est ce qui rend couper,
 * coller, déplacer ou annuler instantanés, quelle que soit la longueur de l'enregistrement.
 *
 * Les pistes visées sont désignées par leurs identifiants ; une piste verrouillée n'est jamais
 * modifiée. Les bornes d'une plage sont [from, to) en trames.
 */

/** Ce que « Copier » garde : par piste, les morceaux de clips recalés pour commencer à 0. */
class Clipboard(val length: Long, val tracks: List<List<Clip>>) {
    val isEmpty get() = length <= 0 || tracks.all { it.isEmpty() }
}

private fun Project.editable(ids: Collection<Int>): Set<Int> =
    tracks.filter { it.id in ids && !it.locked }.map { it.id }.toSet()

private fun Project.coversAllTracks(ids: Set<Int>): Boolean = tracks.isNotEmpty() && tracks.all { it.id in ids }

// ---- Pistes et sources ---------------------------------------------------------------------

fun Project.addSource(source: Source): Project = copy(sources = sources + (source.id to source))

/** Oublie les sources qu'aucun clip n'utilise (à faire à la fermeture, quand l'historique n'a plus à les ressusciter). */
fun Project.withoutUnusedSources(): Project {
    val used = usedSourceIds()
    return if (sources.keys.all { it in used }) this else copy(sources = sources.filterKeys { it in used })
}

/** Ajoute une piste vide en position [at] ; rend le projet et l'identifiant de la piste. */
fun Project.addTrack(name: String, color: Int = 0, at: Int = tracks.size): Pair<Project, Int> {
    val t = Track(id = nextId, name = name, color = color)
    val list = tracks.toMutableList()
    list.add(at.coerceIn(0, list.size), t)
    return copy(tracks = list, nextId = nextId + 1) to t.id
}

fun Project.removeTrack(trackId: Int): Project = copy(tracks = tracks.filter { it.id != trackId })

fun Project.moveTrack(trackId: Int, toIndex: Int): Project {
    val from = indexOfTrack(trackId)
    if (from < 0) return this
    val list = tracks.toMutableList()
    val t = list.removeAt(from)
    list.add(toIndex.coerceIn(0, list.size), t)
    return copy(tracks = list)
}

/**
 * Copie la piste juste après elle (mêmes réglages, mêmes clips, qui partagent les mêmes sources : rien n'est
 * recopié sur le disque). Rend le projet et l'identifiant de la nouvelle piste.
 */
fun Project.duplicateTrack(trackId: Int, name: String): Pair<Project, Int?> {
    val i = indexOfTrack(trackId)
    if (i < 0) return this to null
    val src = tracks[i]
    var next = nextId
    val newId = next++
    val clips = src.clips.map { it.copy(id = next++) }
    val copy = src.copy(id = newId, name = name, clips = clips, solo = false)
    val list = tracks.toMutableList()
    list.add(i + 1, copy)
    return copy(tracks = list, nextId = next) to newId
}

/**
 * Pose un clip de [sourceId] sur la piste, à la trame [at]. Sans [length], tout ce qui reste de la
 * source après [srcStart] est posé. Rend le projet et l'identifiant du clip (ou `null` si la piste
 * ou la source n'existe pas).
 */
fun Project.addClip(
    trackId: Int,
    sourceId: String,
    at: Long,
    srcStart: Long = 0,
    length: Long? = null,
    name: String = "",
): Pair<Project, Int?> {
    val src = sources[sourceId] ?: return this to null
    if (track(trackId) == null) return this to null
    val len = (length ?: (src.frames - srcStart)).coerceAtMost(src.frames - srcStart)
    if (len <= 0) return this to null
    val clip = Clip(id = nextId, sourceId = sourceId, srcStart = srcStart, length = len, start = at.coerceAtLeast(0), name = name)
    val p = mapTrack(trackId) { it.withClips(it.clips + clip) }.copy(nextId = nextId + 1)
    return p to clip.id
}

// ---- Couper / insérer ----------------------------------------------------------------------

/** Coupe en deux tout clip des pistes visées qui contient strictement la trame [frame]. */
fun Project.splitAt(trackIds: Collection<Int>, frame: Long): Project {
    val ids = editable(trackIds)
    var next = nextId
    val out = tracks.map { t ->
        if (t.id !in ids) return@map t
        val list = ArrayList<Clip>()
        for (c in t.clips) {
            if (frame > c.start && frame < c.end) {
                list.add(c.slice(0, frame - c.start, c.id))
                list.add(c.slice(frame - c.start, c.length, next++))
            } else list.add(c)
        }
        if (list.size == t.clips.size) t else t.withClips(list)
    }
    return copy(tracks = out, nextId = next)
}

private fun shiftEnvelope(env: List<EnvPoint>, from: Long, to: Long, delta: Long): List<EnvPoint> =
    // Les points dans [from, to) disparaissent avec l'audio ; ceux d'après suivent le décalage.
    env.filter { it.frame < from || it.frame >= to }.map { if (it.frame >= to) it.copy(frame = it.frame + delta) else it }

private fun shiftMarkers(markers: List<Marker>, from: Long, to: Long, delta: Long): List<Marker> =
    markers.map { m ->
        fun f(x: Long) = when {
            x >= to -> x + delta
            x > from -> from
            else -> x
        }
        m.copy(pos = f(m.pos), end = f(m.end))
    }

/**
 * Supprime l'audio de [[from], [to]) sur les pistes visées. Avec [ripple], ce qui suit remonte pour
 * boucher le trou (couper / supprimer) ; sans, le trou reste, c'est-à-dire du silence.
 */
fun Project.deleteRange(trackIds: Collection<Int>, from: Long, to: Long, ripple: Boolean = true): Project {
    if (to <= from) return this
    val ids = editable(trackIds)
    if (ids.isEmpty()) return this
    val span = to - from
    var next = nextId
    val out = tracks.map { t ->
        if (t.id !in ids) return@map t
        val list = ArrayList<Clip>()
        for (c in t.clips) {
            when {
                c.end <= from -> list.add(c)
                c.start >= to -> list.add(if (ripple) c.copy(start = c.start - span) else c)
                else -> {
                    if (c.start < from) list.add(c.slice(0, from - c.start, c.id))
                    if (c.end > to) {
                        val right = c.slice(to - c.start, c.length, if (c.start < from) next++ else c.id)
                        list.add(if (ripple) right.copy(start = from) else right)
                    }
                }
            }
        }
        val env = if (ripple) shiftEnvelope(t.envelope, from, to, -span) else t.envelope
        t.withClips(list).copy(envelope = env)
    }
    val markers = if (ripple && coversAllTracks(ids)) shiftMarkers(markers, from, to, -span) else markers
    return copy(tracks = out, markers = markers, nextId = next)
}

/** Remplace l'audio de [[from], [to]) par du silence, sans rien décaler. */
fun Project.silenceRange(trackIds: Collection<Int>, from: Long, to: Long): Project =
    deleteRange(trackIds, from, to, ripple = false)

/** Ouvre un trou de [len] trames à la trame [at] sur les pistes visées (le reste glisse vers la droite). */
fun Project.insertSilence(trackIds: Collection<Int>, at: Long, len: Long): Project {
    if (len <= 0) return this
    val ids = editable(trackIds)
    if (ids.isEmpty()) return this
    var next = nextId
    val out = tracks.map { t ->
        if (t.id !in ids) return@map t
        val list = ArrayList<Clip>()
        for (c in t.clips) {
            when {
                c.end <= at -> list.add(c)
                c.start >= at -> list.add(c.copy(start = c.start + len))
                else -> {
                    list.add(c.slice(0, at - c.start, c.id))
                    list.add(c.slice(at - c.start, c.length, next++).let { it.copy(start = it.start + len) })
                }
            }
        }
        val env = t.envelope.map { if (it.frame >= at) it.copy(frame = it.frame + len) else it }
        t.withClips(list).copy(envelope = env)
    }
    val markers = if (coversAllTracks(ids)) markers.map { m ->
        m.copy(pos = if (m.pos >= at) m.pos + len else m.pos, end = if (m.end >= at) m.end + len else m.end)
    } else markers
    return copy(tracks = out, markers = markers, nextId = next)
}

// ---- Presse-papiers ------------------------------------------------------------------------

fun Project.copyRange(trackIds: Collection<Int>, from: Long, to: Long): Clipboard {
    val ids = trackIds.toSet()
    val picked = tracks.filter { it.id in ids }
    val out = picked.map { t ->
        t.clips.filter { it.end > from && it.start < to }.map { c ->
            val s = maxOf(from, c.start) - c.start
            val e = minOf(to, c.end) - c.start
            val piece = c.slice(s, e, 0)
            piece.copy(start = piece.start - from)
        }
    }
    return Clipboard(to - from, out)
}

/**
 * Colle [cb] à la trame [at]. Le morceau n° j du presse-papiers va sur la piste n° j parmi celles
 * visées ; un presse-papiers d'une seule piste est collé sur chacune. Avec [ripple] (par défaut),
 * le contenu qui suit s'écarte pour faire de la place ; sinon le collage se superpose.
 */
fun Project.pasteAt(cb: Clipboard, trackIds: Collection<Int>, at: Long, ripple: Boolean = true): Project {
    if (cb.isEmpty) return this
    val ids = editable(trackIds)
    val targets = tracks.filter { it.id in ids }
    if (targets.isEmpty()) return this
    var p = if (ripple) insertSilence(ids, at, cb.length) else this
    var next = p.nextId
    for ((j, target) in targets.withIndex()) {
        val pieces = if (cb.tracks.size == 1) cb.tracks[0] else cb.tracks.getOrNull(j).orEmpty()
        if (pieces.isEmpty()) continue
        val added = pieces.map { it.copy(id = next++, start = at + it.start) }
        p = p.mapTrack(target.id) { it.withClips(it.clips + added) }
    }
    return p.copy(nextId = next)
}

/** Garde seulement [[from], [to]) et recale le début du projet sur 0 (« Rogner »). */
fun Project.trimToRange(from: Long, to: Long): Project {
    if (to <= from) return this
    val all = tracks.map { it.id }
    var p = this
    if (length > to) p = p.deleteRange(all, to, length, ripple = false)
    if (from > 0) p = p.deleteRange(all, 0, from, ripple = true)
    return p
}

/** Répète [times] fois, à la suite, ce qui se trouve dans [[from], [to]) sur les pistes visées. */
fun Project.repeatRange(trackIds: Collection<Int>, from: Long, to: Long, times: Int): Project {
    if (to <= from || times <= 0) return this
    val cb = copyRange(trackIds, from, to)
    var p = this
    for (k in 0 until times) p = p.pasteAt(cb, trackIds, to + k * (to - from))
    return p
}

// ---- Clips ---------------------------------------------------------------------------------

/** Déplace le clip à [newStart], éventuellement sur une autre piste. */
fun Project.moveClip(clipId: Int, newStart: Long, toTrackId: Int? = null): Project {
    val (from, clip) = findClip(clipId) ?: return this
    val dest = toTrackId?.let { track(it) } ?: from
    if (from.locked || dest.locked) return this
    val moved = clip.copy(start = newStart.coerceAtLeast(0))
    if (dest.id == from.id) return mapTrack(from.id) { it.withClips(it.clips.map { c -> if (c.id == clipId) moved else c }) }
    return mapTrack(from.id) { it.withClips(it.clips.filter { c -> c.id != clipId }) }
        .mapTrack(dest.id) { it.withClips(it.clips + moved) }
}

/** Déplace le bord gauche du clip à [newStart] (rogner ou, s'il reste de la source, rallonger). */
fun Project.trimClipLeft(clipId: Int, newStart: Long): Project {
    val (track, c) = findClip(clipId) ?: return this
    if (track.locked) return this
    val src = sources[c.sourceId] ?: return this
    val lowest = maxOf(-c.start, if (c.reversed) c.srcStart + c.length - src.frames else -c.srcStart)
    val d = (newStart - c.start).coerceIn(lowest, c.length - 1)
    if (d == 0L) return this
    val trimmed = when {
        d > 0 -> c.slice(d, c.length, c.id)
        else -> c.copy(start = c.start + d, length = c.length - d, srcStart = if (c.reversed) c.srcStart else c.srcStart + d)
    }
    return mapTrack(track.id) { it.withClips(it.clips.map { x -> if (x.id == clipId) trimmed else x }) }
}

/** Déplace le bord droit du clip à [newEnd]. */
fun Project.trimClipRight(clipId: Int, newEnd: Long): Project {
    val (track, c) = findClip(clipId) ?: return this
    if (track.locked) return this
    val src = sources[c.sourceId] ?: return this
    val highest = if (c.reversed) c.srcStart else src.frames - c.srcStart - c.length
    val d = (newEnd - c.end).coerceIn(1 - c.length, highest)
    if (d == 0L) return this
    val trimmed = when {
        d < 0 -> c.slice(0, c.length + d, c.id)
        else -> c.copy(length = c.length + d, srcStart = if (c.reversed) c.srcStart - d else c.srcStart)
    }
    return mapTrack(track.id) { it.withClips(it.clips.map { x -> if (x.id == clipId) trimmed else x }) }
}

fun Project.updateClip(clipId: Int, f: (Clip) -> Clip): Project {
    val (track, _) = findClip(clipId) ?: return this
    if (track.locked) return this
    return mapTrack(track.id) { it.withClips(it.clips.map { c -> if (c.id == clipId) f(c) else c }) }
}

fun Project.setClipGain(clipId: Int, gain: Float): Project = updateClip(clipId) { it.copy(gain = gain.coerceAtLeast(0f)) }

/** Longueur des fondus en trames ; ensemble ils ne dépassent jamais le clip. */
fun Project.setClipFades(clipId: Int, fadeInLen: Long, fadeOutLen: Long, shape: FadeShape = FadeShape.LINEAR): Project =
    updateClip(clipId) { c ->
        val inLen = fadeInLen.coerceIn(0, c.length)
        val outLen = fadeOutLen.coerceIn(0, c.length - inLen)
        c.copy(fadeIn = Fade.fadeIn(inLen, shape), fadeOut = Fade.fadeOut(outLen, shape))
    }

fun Project.deleteClip(clipId: Int, ripple: Boolean = false): Project {
    val (track, c) = findClip(clipId) ?: return this
    if (track.locked) return this
    val without = mapTrack(track.id) { it.withClips(it.clips.filter { x -> x.id != clipId }) }
    return if (ripple) without.mapTrack(track.id) { t ->
        t.withClips(t.clips.map { x -> if (x.start >= c.end) x.copy(start = x.start - c.length) else x })
    } else without
}

/** Copie le clip juste après lui, sur la même piste. */
fun Project.duplicateClip(clipId: Int): Pair<Project, Int?> {
    val (track, c) = findClip(clipId) ?: return this to null
    if (track.locked) return this to null
    val copy = c.copy(id = nextId, start = c.end)
    return mapTrack(track.id) { it.withClips(it.clips + copy) }.copy(nextId = nextId + 1) to copy.id
}

/**
 * Retourne dans le temps la plage [[from], [to]) des pistes visées : chaque morceau est lu à
 * l'envers et sa place est symétrique autour du milieu de la plage. Aucun fichier n'est réécrit.
 */
fun Project.reverseRange(trackIds: Collection<Int>, from: Long, to: Long): Project {
    if (to <= from) return this
    val ids = editable(trackIds)
    if (ids.isEmpty()) return this
    var p = splitAt(ids, from).splitAt(ids, to)
    p = p.copy(tracks = p.tracks.map { t ->
        if (t.id !in ids) return@map t
        t.withClips(t.clips.map { c ->
            if (c.start >= from && c.end <= to) {
                c.copy(
                    start = from + to - c.end,
                    reversed = !c.reversed,
                    fadeIn = c.fadeOut.reversed(),
                    fadeOut = c.fadeIn.reversed(),
                )
            } else c
        })
    })
    return p
}

private fun Fade.reversed(): Fade = if (len <= 0) this else Fade(len, to, from, shape)

/**
 * Remplace l'audio de [[from], [to]) d'une piste par une nouvelle source de [newLength] trames
 * (le résultat d'un effet). Avec [ripple], ce qui suit se décale de la différence de durée ; sans,
 * la queue éventuelle (écho, réverbération) se superpose à la suite.
 */
fun Project.replaceRange(trackId: Int, from: Long, to: Long, sourceId: String, newLength: Long, ripple: Boolean, name: String = ""): Project {
    if (track(trackId)?.locked != false) return this
    var p = deleteRange(listOf(trackId), from, to, ripple = false)
    val delta = newLength - (to - from)
    if (ripple && delta != 0L) {
        p = p.mapTrack(trackId) { t ->
            t.withClips(t.clips.map { c -> if (c.start >= to) c.copy(start = c.start + delta) else c })
                .copy(envelope = t.envelope.map { if (it.frame >= to) it.copy(frame = it.frame + delta) else it })
        }
    }
    return p.addClip(trackId, sourceId, from, 0, newLength, name).first
}

// ---- Repères -------------------------------------------------------------------------------

fun Project.addMarker(pos: Long, end: Long = pos, name: String = ""): Pair<Project, Int> {
    val m = Marker(nextId, pos.coerceAtLeast(0), maxOf(end, pos).coerceAtLeast(0), name)
    return copy(markers = (markers + m).sortedBy { it.pos }, nextId = nextId + 1) to m.id
}

fun Project.removeMarker(id: Int): Project = copy(markers = markers.filter { it.id != id })

fun Project.updateMarker(id: Int, f: (Marker) -> Marker): Project =
    copy(markers = markers.map { if (it.id == id) f(it) else it }.sortedBy { it.pos })

// ---- Enveloppe de volume -------------------------------------------------------------------

/** Gain maximal d'un point d'enveloppe (×2 = +6 dB) : l'échelle de l'outil va de 0 à cette valeur. */
const val ENVELOPE_MAX_GAIN = 2f

/**
 * Pose un point d'enveloppe à la trame [frame] (il remplace celui qui s'y trouve déjà). Rend le projet et le rang
 * du point dans la liste, ou −1 si la piste n'existe pas ou est verrouillée.
 */
fun Project.addEnvelopePoint(trackId: Int, frame: Long, gain: Float): Pair<Project, Int> {
    val t = track(trackId)
    if (t == null || t.locked) return this to -1
    val f = frame.coerceAtLeast(0)
    val point = EnvPoint(f, gain.coerceIn(0f, ENVELOPE_MAX_GAIN))
    val list = t.envelope.toMutableList()
    val same = list.indexOfFirst { it.frame == f }
    val index = if (same >= 0) { list[same] = point; same } else {
        val after = list.indexOfFirst { it.frame > f }.let { if (it < 0) list.size else it }
        list.add(after, point)
        after
    }
    return mapTrack(trackId) { it.copy(envelope = list) } to index
}

/** Déplace le point de rang [index] ; il ne dépasse jamais ses voisins, l'ordre des points reste croissant. */
fun Project.moveEnvelopePoint(trackId: Int, index: Int, frame: Long, gain: Float): Project {
    val t = track(trackId)
    if (t == null || t.locked || index !in t.envelope.indices) return this
    val e = t.envelope
    val lo = if (index > 0) e[index - 1].frame + 1 else 0L
    val hi = if (index < e.size - 1) e[index + 1].frame - 1 else Long.MAX_VALUE
    val f = if (lo > hi) e[index].frame else frame.coerceIn(lo, hi)
    val list = e.toMutableList()
    list[index] = EnvPoint(f, gain.coerceIn(0f, ENVELOPE_MAX_GAIN))
    return mapTrack(trackId) { it.copy(envelope = list) }
}

fun Project.removeEnvelopePoint(trackId: Int, index: Int): Project {
    val t = track(trackId)
    if (t == null || t.locked || index !in t.envelope.indices) return this
    return mapTrack(trackId) { it.copy(envelope = it.envelope.filterIndexed { i, _ -> i != index }) }
}

fun Project.clearEnvelope(trackId: Int): Project {
    val t = track(trackId)
    if (t == null || t.locked || t.envelope.isEmpty()) return this
    return mapTrack(trackId) { it.copy(envelope = emptyList()) }
}
