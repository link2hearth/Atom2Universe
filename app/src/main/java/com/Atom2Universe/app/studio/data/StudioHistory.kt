package com.Atom2Universe.app.studio.data

import com.Atom2Universe.app.studio.dsp.SynthPresets
import com.Atom2Universe.app.studio.engine.TrackKind
import com.Atom2Universe.app.studio.engine.TrackMix
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Un changement d'un morceau : une pièce (un motif, une piste, un réglage), telle qu'elle était et telle qu'elle est devenue (`null` : absente). */
class Change(val key: String, val before: String?, val after: String?)

/** Une action de l'utilisateur : tout ce qu'elle a changé. On l'applique pour la faire, à l'envers pour l'annuler. */
class Action(val changes: List<Change>)

/** Là où le journal est écrit (un fichier, sur l'appareil) : on lui ajoute des lignes, et de temps en temps on le réécrit en entier. */
interface StudioPersist {
    fun append(lines: List<String>)

    /** Remplace le morceau de départ par [base] et le journal par [lines]. */
    fun compact(base: StudioProject, lines: List<String>)
}

/**
 * Le journal des actions d'un morceau : au lieu de réécrire tout le morceau à chaque modification, on écrit l'action (ce qu'elle a changé,
 * avant et après). Le morceau enregistré est le point de départ ; en rejouant le journal par-dessus, on retrouve le morceau tel qu'il était
 * à la fermeture, et aussi toute la pile d'annulation (jusqu'à [MAX] actions).
 *
 * Le journal est un texte, une action par ligne : `a` (une action, annulable ou non), `u` (on a annulé), `r` (on a rétabli).
 * Les pièces d'un morceau : `m.core` (style, tonalité, gamme), `m.mix` (tempo, swing, ambiances), `m.nav` (motif en cours, chaîne, vue),
 * `order` (l'ordre des pistes), `t<n>` (le caractère d'une piste : genre, son, kit), `x<n>` (le reste d'une piste : mixage, synthé,
 * arpège, muet/solo) et `p<n>` (un motif).
 */
object StudioHistory {

    /** Nombre d'actions qu'on peut annuler. */
    const val MAX = 100

    const val UNDO_LINE = """{"t":"u"}"""
    const val REDO_LINE = """{"t":"r"}"""

    private val NEUTRAL_MIX = TrackMix(0f, 0f, 0f, 0f)

    class Replayed(val project: StudioProject, val past: List<Action>, val future: List<Action>, val lines: Int, val valid: Int)

    private fun coreOf(t: Track): String = StudioProjectCodec.encodeTrack(
        t.copy(
            mix = NEUTRAL_MIX, hidden = false, muted = false, solo = false, synth = SynthPresets.BASS,
            arpMode = 0, arpRate = 1, arpOctaves = 1,
        ),
    ).toString()

    private fun extOf(t: Track): String = StudioProjectCodec.encodeTrack(t).toString()

    private fun patternOf(p: com.Atom2Universe.app.studio.engine.StepPattern): String =
        StudioProjectCodec.encodePattern(p).put("steps", p.stepCount).toString()

    private fun coreMeta(p: StudioProject) = JSONObject().put("style", p.styleId).put("key", p.keyPc).put("scale", p.scaleId).toString()

    private fun mixMeta(p: StudioProject) = JSONObject()
        .also { StudioProjectCodec.writePad(it, p.heldPad) }
        .put("bpm", p.bpm).put("swing", p.swing.toDouble()).put("space", p.space.toDouble()).put("echo", p.echo.toDouble()).toString()

    private fun navMeta(p: StudioProject) = JSONObject()
        .put("current", p.current).put("chain", JSONArray(p.chain)).put("songMode", p.songMode).put("view", JSONArray(p.view)).toString()

    private fun orderOf(p: StudioProject) = JSONArray(p.tracks.map { it.id }).toString()

    /**
     * Ce qui sépare [a] de [b]. Avec [undoableOnly], seulement ce qu'on annule (le contenu : motifs, pistes, sons de batterie, tonalité) ;
     * le tempo, les volumes, les réglages de synthé, muet et solo, et l'affichage ne s'annulent pas.
     */
    fun diff(a: StudioProject, b: StudioProject, undoableOnly: Boolean): List<Change> {
        val out = ArrayList<Change>()
        fun add(key: String, x: String?, y: String?) {
            if (x != y) out += Change(key, x, y)
        }
        add("m.core", coreMeta(a), coreMeta(b))
        if (!undoableOnly) {
            add("m.mix", mixMeta(a), mixMeta(b))
            add("m.nav", navMeta(a), navMeta(b))
        }
        add("order", orderOf(a), orderOf(b))
        for (id in (a.tracks.map { it.id } + b.tracks.map { it.id }).distinct()) {
            val ta = a.trackOrNull(id)
            val tb = b.trackOrNull(id)
            add("t$id", ta?.let(::coreOf), tb?.let(::coreOf))
            // Le mixage d'une piste ne s'annule pas, sauf quand la piste apparaît ou disparaît avec l'action
            if (!undoableOnly || (ta == null) != (tb == null)) add("x$id", ta?.let(::extOf), tb?.let(::extOf))
        }
        for (s in 0 until StudioProject.SLOTS) {
            val pa = a.patterns[s]
            val pb = b.patterns[s]
            if (pa !== pb) add("p$s", patternOf(pa), patternOf(pb))
        }
        return out
    }

    /** Applique [changes] à [p] : à l'endroit ([forward]) ou à l'envers (annuler). Ce qui ne se lit pas est ignoré. */
    fun apply(p: StudioProject, changes: List<Change>, forward: Boolean): StudioProject {
        fun value(c: Change): String? = if (forward) c.after else c.before
        var project = p
        try {
            for (c in changes) {
                if (!c.key.startsWith("m.")) continue
                val v = value(c) ?: continue
                val o = JSONObject(v)
                project = when (c.key) {
                    "m.core" -> project.copy(styleId = o.getString("style"), keyPc = o.getInt("key").coerceIn(0, 11), scaleId = o.getString("scale"))
                    "m.mix" -> project.copy(
                        heldPad = StudioProjectCodec.readPad(o),
                        bpm = o.getInt("bpm").coerceIn(40, 220), swing = o.getDouble("swing").toFloat().coerceIn(0f, 0.5f),
                        space = o.getDouble("space").toFloat().coerceIn(0f, 1f), echo = o.getDouble("echo").toFloat().coerceIn(0f, 1f),
                    )
                    "m.nav" -> {
                        val chain = o.getJSONArray("chain").let { a -> List(a.length()) { a.getInt(it) } }.filter { it in 0 until StudioProject.SLOTS }
                        val view = o.getJSONArray("view").let { a -> List(a.length()) { a.getInt(it) } }.filter { it in 0 until StudioProject.SLOTS }
                        project.copy(
                            current = o.getInt("current").coerceIn(0, StudioProject.SLOTS - 1), chain = chain,
                            songMode = o.getBoolean("songMode") && chain.isNotEmpty(), view = view,
                        )
                    }
                    else -> project
                }
            }

            // Les pistes : d'abord celles qui disparaissent ou changent de caractère, puis leur mixage
            var tracks = project.tracks
            for (c in changes) if (c.key.startsWith("t")) {
                val id = c.key.substring(1).toIntOrNull() ?: continue
                val v = value(c)
                if (v == null) {
                    tracks = tracks.filter { it.id != id }
                    continue
                }
                val core = StudioProjectCodec.decodeTrack(JSONObject(v)) ?: continue
                tracks = if (tracks.none { it.id == id }) tracks + core else tracks.map {
                    if (it.id != id) it else it.copy(
                        kind = core.kind, voice = core.voice, bank = core.bank, program = core.program,
                        synthBank = core.synthBank,
                        chipProgramAssigned = core.chipProgramAssigned, chipSmoothBass = core.chipSmoothBass,
                        fmProgram = core.fmProgram, fmBrightness = core.fmBrightness, fmAttack = core.fmAttack,
                        kit = core.kit, tune = core.tune, length = core.length, sfKit = core.sfKit,
                        padOrder = core.padOrder, padSize = core.padSize, padPlayMode = core.padPlayMode,
                    )
                }
            }
            for (c in changes) if (c.key.startsWith("x")) {
                val id = c.key.substring(1).toIntOrNull() ?: continue
                val ext = StudioProjectCodec.decodeTrack(JSONObject(value(c) ?: continue)) ?: continue
                tracks = tracks.map {
                    if (it.id != id) it else it.copy(
                        mix = ext.mix, hidden = ext.hidden, muted = ext.muted, solo = ext.solo,
                        synth = if (it.kind == TrackKind.SYNTH) ext.synth else it.synth,
                        arpMode = ext.arpMode, arpRate = ext.arpRate, arpOctaves = ext.arpOctaves,
                    )
                }
            }
            changes.firstOrNull { it.key == "order" }?.let { c ->
                val v = value(c) ?: return@let
                val ids = JSONArray(v).let { a -> List(a.length()) { a.getInt(it) } }
                tracks = ids.mapNotNull { id -> tracks.firstOrNull { it.id == id } } + tracks.filter { it.id !in ids }
            }

            // Les motifs, avec les pistes telles qu'elles sont maintenant
            val patterns = project.patterns.toMutableList()
            for (c in changes) if (c.key.startsWith("p")) {
                val s = c.key.substring(1).toIntOrNull()?.takeIf { it in 0 until StudioProject.SLOTS } ?: continue
                val o = JSONObject(value(c) ?: continue)
                StudioProjectCodec.decodePattern(o, o.getInt("steps"), tracks)?.let { patterns[s] = it }
            }
            project = project.copy(tracks = tracks, patterns = patterns)
        } catch (_: JSONException) {
            return p
        }
        return project
    }

    // ---- Les lignes du journal --------------------------------------------------------------------------------------

    fun line(a: Action, undoable: Boolean): String {
        val array = JSONArray()
        for (c in a.changes) {
            array.put(JSONObject().put("k", c.key).put("b", c.before ?: JSONObject.NULL).put("a", c.after ?: JSONObject.NULL))
        }
        return JSONObject().put("t", "a").put("h", if (undoable) 1 else 0).put("c", array).toString()
    }

    private sealed class Record {
        class Act(val action: Action, val undoable: Boolean) : Record()
        object Undo : Record()
        object Redo : Record()
    }

    private fun parse(line: String): Record? = try {
        val o = JSONObject(line)
        when (o.getString("t")) {
            "u" -> Record.Undo
            "r" -> Record.Redo
            "a" -> {
                val a = o.getJSONArray("c")
                val changes = List(a.length()) {
                    val c = a.getJSONObject(it)
                    Change(c.getString("k"), if (c.isNull("b")) null else c.getString("b"), if (c.isNull("a")) null else c.getString("a"))
                }
                Record.Act(Action(changes), o.optInt("h", 1) == 1)
            }
            else -> null
        }
    } catch (_: JSONException) {
        null
    }

    /**
     * Rejoue [lines] sur le morceau de départ [base] : le morceau tel qu'il était à la fermeture, et les piles d'annulation et de
     * rétablissement. S'arrête à la première ligne illisible (une coupure en pleine écriture) ; [Replayed.valid] dit combien de lignes étaient bonnes.
     */
    fun replay(base: StudioProject, lines: List<String>): Replayed {
        var project = base
        val past = ArrayDeque<Action>()
        val future = ArrayDeque<Action>()
        var valid = 0
        for (line in lines) {
            val r = parse(line) ?: break
            when (r) {
                is Record.Act -> {
                    project = apply(project, r.action.changes, true)
                    if (r.undoable) {
                        past.addLast(r.action)
                        future.clear()
                    }
                }
                Record.Undo -> {
                    val a = past.removeLastOrNull() ?: break
                    project = apply(project, a.changes, false)
                    future.addLast(a)
                }
                Record.Redo -> {
                    val a = future.removeLastOrNull() ?: break
                    project = apply(project, a.changes, true)
                    past.addLast(a)
                }
            }
            valid++
        }
        while (past.size > MAX) past.removeFirst()
        return Replayed(project, past.toList(), future.toList(), lines.size, valid)
    }
}
