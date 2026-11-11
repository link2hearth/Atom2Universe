package com.Atom2Universe.app.studio.data

import com.Atom2Universe.app.studio.dsp.SynthPatch
import com.Atom2Universe.app.studio.dsp.SynthPresets
import com.Atom2Universe.app.studio.engine.Inst
import com.Atom2Universe.app.studio.engine.Lane
import com.Atom2Universe.app.studio.engine.PadPosition
import com.Atom2Universe.app.studio.engine.Note
import com.Atom2Universe.app.studio.engine.StepCond
import com.Atom2Universe.app.studio.engine.StepPattern
import com.Atom2Universe.app.studio.engine.StudioEngine
import com.Atom2Universe.app.studio.engine.TrackKind
import com.Atom2Universe.app.studio.engine.SynthBank
import com.Atom2Universe.app.studio.engine.TrackMix
import com.Atom2Universe.app.studio.music.Scales
import com.Atom2Universe.app.studio.music.Style
import com.Atom2Universe.app.studio.music.Styles
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import kotlin.math.abs

/**
 * Le morceau en cours d'édition. **Immuable** : chaque modification fabrique un nouveau projet (comme le
 * modèle de l'éditeur audio), ce qui rend l'annulation et la sauvegarde triviales plus tard.
 * [tracks] est la liste libre des pistes, dans l'ordre d'affichage ; chaque motif a une ligne par piste rythmique
 * (même cachée), et ses notes portent le numéro de leur piste mélodique. Les notes se jouent dans la tonalité [keyPc]
 * et la gamme [scaleId].
 */
class StudioProject(
    val styleId: String,
    val bpm: Int,
    val swing: Float,
    val tracks: List<Track>,
    val space: Float,
    val echo: Float,
    val keyPc: Int,
    val scaleId: String,
    /** Les quatre emplacements de motif (A à D), tous de la même longueur. */
    val patterns: List<StepPattern>,
    /** Emplacement en cours d'édition. */
    val current: Int = 0,
    /** Le morceau : les emplacements joués l'un après l'autre quand [songMode] est vrai. */
    val chain: List<Int> = emptyList(),
    val songMode: Boolean = false,
    /**
     * Les motifs affichés à la suite (vide : seulement [current]). Triés ; le premier est [current]. Le motif « en cours » est alors
     * leur mise bout à bout : on l'édite, on l'entend et on l'exporte comme un seul motif long.
     */
    val view: List<Int> = emptyList(),
    val heldPad: PadPosition? = null,
) {
    /** Les emplacements affichés, dans l'ordre de la vue. */
    val viewSlots: List<Int> get() = view.ifEmpty { listOf(current) }

    /** La longueur d'un motif (la même pour A à D). */
    val baseSteps: Int get() = patterns[0].stepCount

    /** Le motif en cours d'édition : celui de l'emplacement, ou les emplacements de la vue mis bout à bout. */
    val pattern: StepPattern by lazy {
        val slots = viewSlots
        if (slots.size == 1) patterns[slots[0]] else StepPattern.concat(slots.map { patterns[it] })
    }

    /** La longueur du motif en cours d'édition (celle de la vue). */
    val stepCount: Int get() = baseSteps * viewSlots.size

    /** La piste de numéro [id], s'il y en a une. */
    fun trackOrNull(id: Int): Track? {
        for (t in tracks) if (t.id == id) return t
        return null
    }

    fun track(id: Int): Track = trackOrNull(id) ?: throw NoSuchElementException("piste $id")

    /** Les pistes muettes et celles en solo (si au moins une l'est, seules celles-là s'entendent). */
    val muted: Set<Int> get() = tracks.filter { it.muted }.map { it.id }.toSet()
    val solo: Set<Int> get() = tracks.filter { it.solo }.map { it.id }.toSet()

    /** La première piste qui joue des notes (celle du clavier au départ), de préférence visible. */
    val firstMelodic: Track? get() = tracks.firstOrNull { it.melodic && !it.hidden } ?: tracks.firstOrNull { it.melodic }

    /** Canal MIDI en base zéro, commun à l'accordage GXSCC et à l'export. Le canal 9 est réservé à la batterie. */
    internal fun midiChannel(id: Int): Int {
        var channel = 0
        for (track in tracks) {
            if (track.id == id) return if (track.melodic) channel else 9
            if (track.melodic) channel = if (channel == 8) 10 else (channel + 1).coerceAtMost(15)
        }
        error("Piste MIDI introuvable : $id")
    }

    fun copy(
        styleId: String = this.styleId,
        bpm: Int = this.bpm,
        swing: Float = this.swing,
        tracks: List<Track> = this.tracks,
        space: Float = this.space,
        echo: Float = this.echo,
        keyPc: Int = this.keyPc,
        scaleId: String = this.scaleId,
        /** Remplace le motif en cours d'édition. */
        pattern: StepPattern? = null,
        patterns: List<StepPattern> = this.patterns,
        current: Int = this.current,
        chain: List<Int> = this.chain,
        songMode: Boolean = this.songMode,
        view: List<Int> = this.view,
        heldPad: PadPosition? = this.heldPad,
    ): StudioProject {
        val all = when {
            pattern == null -> patterns
            else -> {
                val slots = view.ifEmpty { listOf(current) }
                val out = patterns.toMutableList()
                if (slots.size == 1) {
                    out[slots[0]] = pattern
                } else {
                    val parts = StepPattern.split(pattern, slots.size, patterns[0].stepCount)
                    for ((i, slot) in slots.withIndex()) out[slot] = parts[i]
                }
                out
            }
        }
        return StudioProject(styleId, bpm, swing, tracks, space, echo, keyPc, scaleId, all, current, chain, songMode, view, heldPad)
    }

    /** Remplace une piste (même numéro). */
    fun withTrack(t: Track): StudioProject = copy(tracks = tracks.map { if (it.id == t.id) t else it })

    companion object {
        /** Nombre d'emplacements de motif. */
        const val SLOTS = 4

        /** Longueur maximale du morceau. */
        const val MAX_CHAIN = 16

        /** Un morceau vide : les huit pistes de départ, aucun pas, aucune note. */
        fun blank(stepCount: Int = 16): StudioProject {
            val base = Styles.HOUSE
            return StudioProject(
                styleId = base.id,
                bpm = 120,
                swing = 0f,
                tracks = Track.defaults(SynthPresets.byId(base.patchId), 0.25f),
                space = base.space,
                echo = base.echo,
                keyPc = base.keyPc,
                scaleId = base.scaleId,
                patterns = List(SLOTS) { Styles.emptyPattern(stepCount) },
            )
        }

        fun fromStyle(style: Style, stepCount: Int = 16, seed: Long = style.id.hashCode().toLong()) = StudioProject(
            styleId = style.id,
            bpm = style.bpm,
            swing = style.swing,
            tracks = Track.defaults(SynthPresets.byId(style.patchId), style.space.coerceIn(0.1f, 0.6f)),
            space = style.space,
            echo = style.echo,
            keyPc = style.keyPc,
            scaleId = style.scaleId,
            patterns = listOf(Styles.generate(style, stepCount, seed)) + List(SLOTS - 1) { Styles.emptyPattern(stepCount) },
        )
    }
}

/** Lit et écrit un [StudioProject] en JSON. Un texte abîmé ou d'une autre forme donne `null`, jamais une exception. */
object StudioProjectCodec {

    /** Pas de lecture des anciennes versions : un seul testeur, on repart d'un projet neuf. */
    private const val VERSION = 3

    internal fun encodePattern(pattern: StepPattern): JSONObject {
        val lanes = JSONArray()
        for (lane in pattern.lanes) {
            lanes.put(
                JSONObject()
                    .put("i", lane.instrument)
                    .put("v", JSONArray().also { a -> lane.velocity.forEach { a.put(it.toDouble()) } })
                    .also { o -> if (lane.gate != 0.9f) o.put("gate", lane.gate.toDouble()) }
                    // La hauteur de chaque pas, seulement si l'un d'eux n'est pas à 60 (la hauteur d'origine)
                    .also { o -> if (lane.note.any { it != 60 }) o.put("n", JSONArray().also { a -> lane.note.forEach { a.put(it) } }) }
                    // La durée de chaque coup, en pour cent, seulement si l'un d'eux n'est pas à 100
                    .also { o -> if (lane.hold.any { it != 1f }) o.put("h", JSONArray().also { a -> lane.hold.forEach { a.put(Math.round(it * 100f)) } }) }
                    // Quand chaque pas joue, seulement si l'un d'eux ne joue pas à chaque tour
                    .also { o -> if (lane.cond.any { it != 0 }) o.put("c", JSONArray().also { a -> lane.cond.forEach { a.put(it) } }) },
            )
        }
        val notes = JSONArray()
        for (n in pattern.notes) {
            notes.put(
                JSONArray().put(n.step).put(n.pitch).put(n.length).put(n.velocity.toDouble()).put(n.track),
            )
        }
        return JSONObject().put("lanes", lanes).put("notes", notes).also { o ->
            // Geste du pad : deux tableaux de 0 à 100 (entiers, pour garder le fichier petit)
            pattern.autoFilter?.let { a -> o.put("autoFilter", JSONArray().also { j -> a.forEach { v -> j.put(Math.round(v * 100f)) } }) }
            pattern.autoSpace?.let { a -> o.put("autoSpace", JSONArray().also { j -> a.forEach { v -> j.put(Math.round(v * 100f)) } }) }
        }
    }

    /**
     * Lit un motif de [steps] pas pour les pistes [tracks] ; `null` si sa forme est mauvaise. Une piste rythmique sans ligne
     * reçoit une ligne vide ; une note d'une piste inconnue est ignorée.
     */
    internal fun decodePattern(o: JSONObject, steps: Int, tracks: List<Track>): StepPattern? {
        val laneArray = o.getJSONArray("lanes")
        val byId = HashMap<Int, Lane>()
        for (k in 0 until laneArray.length()) {
            val l = laneArray.getJSONObject(k)
            val v = l.getJSONArray("v")
            if (v.length() != steps) return null
            val id = l.getInt("i")
            val n = l.optJSONArray("n")?.takeIf { it.length() == steps }
            val h = l.optJSONArray("h")?.takeIf { it.length() == steps }
            val c = l.optJSONArray("c")?.takeIf { it.length() == steps }
            byId[id] = Lane(
                id, FloatArray(steps) { v.getDouble(it).toFloat().coerceIn(0f, 1f) },
                IntArray(steps) { n?.getInt(it)?.coerceIn(36, 84) ?: 60 },
                l.optDouble("gate", 0.9).toFloat().takeIf { it.isFinite() } ?: 0.9f,
                FloatArray(steps) { if (h == null) 1f else h.getInt(it).coerceIn(50, 200) / 100f },
                IntArray(steps) { c?.getInt(it)?.coerceIn(0, StepCond.COUNT - 1) ?: StepCond.ALWAYS },
            )
        }
        val lanes = tracks.filterNot { it.melodic }.map { byId[it.id] ?: Lane(it.id, FloatArray(steps)) }
        val melodic = tracks.filter { it.melodic }.map { it.id }.toSet()
        val notes = ArrayList<Note>()
        val na = o.getJSONArray("notes")
        for (k in 0 until na.length()) {
            val n = na.getJSONArray(k)
            val step = n.getInt(0)
            if (step !in 0 until steps) return null
            val track = n.getInt(4)
            if (track !in melodic) continue
            val length = n.getInt(2).coerceIn(1, steps - step)
            notes += Note(step, n.getInt(1).coerceIn(0, 127), length, n.getDouble(3).toFloat().coerceIn(0f, 1f), track)
        }
        return StepPattern(steps, lanes, notes, readAuto(o.optJSONArray("autoFilter"), steps), readAuto(o.optJSONArray("autoSpace"), steps))
    }

    internal fun encodeSynth(s: SynthPatch): JSONObject = JSONObject()
        .put("id", s.id)
        .put("wave", s.wave)
        .put("detune", s.detuneCents.toDouble())
        .put("sub", s.subLevel.toDouble())
        .put("cutoff", s.cutoffHz.toDouble())
        .put("res", s.resonance.toDouble())
        .put("envOct", s.envOctaves.toDouble())
        .put("fDecay", s.filterDecay.toDouble())
        .put("a", s.attack.toDouble())
        .put("d", s.decay.toDouble())
        .put("s", s.sustain.toDouble())
        .put("r", s.release.toDouble())
        .put("key", s.keyTrack.toDouble())
        .put("lfoRate", s.lfoRate.toDouble())
        .put("lfoFilter", s.lfoFilter.toDouble())
        .put("drive", s.drive.toDouble())
        .put("vol", s.volume.toDouble())
        .put("interval", s.interval)

    internal fun decodeSynth(sy: JSONObject) = SynthPatch(
        id = sy.getString("id"),
        wave = sy.getInt("wave").coerceIn(0, SynthPatch.WAVE_PIANO),
        detuneCents = sy.getDouble("detune").toFloat().coerceIn(0f, 1200f),
        subLevel = sy.getDouble("sub").toFloat().coerceIn(0f, 1f),
        cutoffHz = sy.getDouble("cutoff").toFloat().coerceIn(40f, 16000f),
        resonance = sy.getDouble("res").toFloat().coerceIn(0.3f, 12f),
        envOctaves = sy.getDouble("envOct").toFloat().coerceIn(0f, 6f),
        filterDecay = sy.getDouble("fDecay").toFloat().coerceIn(0.01f, 5f),
        attack = sy.getDouble("a").toFloat().coerceIn(0.002f, 5f),
        decay = sy.getDouble("d").toFloat().coerceIn(0.01f, 5f),
        sustain = sy.getDouble("s").toFloat().coerceIn(0f, 1f),
        release = sy.getDouble("r").toFloat().coerceIn(0.005f, 8f),
        keyTrack = sy.getDouble("key").toFloat().coerceIn(0f, 1f),
        lfoRate = sy.getDouble("lfoRate").toFloat().coerceIn(0f, 20f),
        lfoFilter = sy.getDouble("lfoFilter").toFloat().coerceIn(0f, 4f),
        drive = sy.getDouble("drive").toFloat().coerceIn(0f, 1f),
        volume = sy.getDouble("vol").toFloat().coerceIn(0.1f, 3f),
        interval = sy.optInt("interval", 0).coerceIn(0, 24),
    )

    internal fun encodeTrack(t: Track): JSONObject {
        val m = t.mix
        return JSONObject()
            .put("id", t.id)
            .put("kind", t.kind)
            .put("voice", t.voice)
            .also { o ->
                if (t.kind == TrackKind.SYNTH && t.synthBank != SynthBank.STUDIO) o.put("synthBank", t.synthBank.name)
                if (t.chipProgramAssigned) o.put("chipProgramAssigned", true)
                if (t.chipSmoothBass) o.put("chipSmoothBass", true)
                if (t.fmProgram != Track.DEFAULT_FM_PROGRAM) o.put("fmProgram", t.fmProgram)
                if (t.fmBrightness != Track.DEFAULT_FM_BRIGHTNESS) o.put("fmBrightness", t.fmBrightness.toDouble())
                if (t.fmAttack != Track.DEFAULT_FM_ATTACK) o.put("fmAttack", t.fmAttack.toDouble())
                if (t.melodic && t.rhythmOctave != Track.DEFAULT_RHYTHM_OCTAVE) o.put("rhythmOctave", t.rhythmOctave)
                if (t.padOrder != Int.MAX_VALUE) o.put("padOrder", t.padOrder)
                if (t.melodic && t.padPlayMode != PadPlayMode.DRIVE) o.put("padPlayMode", t.padPlayMode.key)
                when (t.padSize) {
                    PadSize.WIDE -> o.put("padWide", true)
                    PadSize.HALF -> o.put("padHalf", true)
                    PadSize.NORMAL -> Unit
                }
            }
            .put("hidden", t.hidden)
            .put("muted", t.muted)
            .put("solo", t.solo)
            .put("bank", t.bank)
            .put("program", t.program)
            .put("arp", JSONArray().put(t.arpMode).put(t.arpRate).put(t.arpOctaves))
            .put("drum", JSONArray().put(t.kit).put(t.tune).put(t.length).put(t.sfKit))
            .put(
                "mix",
                JSONArray().put(m.level.toDouble()).put(m.pan.toDouble()).put(m.reverbSend.toDouble())
                    .put(m.delaySend.toDouble()).put(m.drive.toDouble()).put(m.crush.toDouble()).put(m.filter.toDouble())
                    .put(m.pump.toDouble()),
            )
            .also { o -> if (t.kind == TrackKind.SYNTH) o.put("synth", encodeSynth(t.synth)) }
    }

    internal fun decodeTrack(o: JSONObject): Track? {
        val id = o.getInt("id")
        if (id !in 0 until StudioEngine.MAX_TRACKS) return null
        val kind = o.getInt("kind")
        if (kind != TrackKind.DRUM && kind != TrackKind.SYNTH && kind != TrackKind.SF2) return null
        val synthBank = if (kind == TrackKind.SYNTH) {
            SynthBank.entries.firstOrNull { it.name == o.optString("synthBank") } ?: SynthBank.STUDIO
        } else SynthBank.STUDIO
        val voice = o.getInt("voice")
        if (kind == TrackKind.DRUM && voice !in 0 until Inst.DRUM_COUNT) return null
        val a = o.getJSONArray("mix")
        val arp = o.getJSONArray("arp")
        val drum = o.optJSONArray("drum")
        return Track(
            id = id,
            kind = kind,
            voice = if (kind == TrackKind.DRUM) voice else 0,
            mix = TrackMix(
                level = a.getDouble(0).toFloat().coerceIn(0f, 1.5f),
                pan = a.getDouble(1).toFloat().coerceIn(-1f, 1f),
                reverbSend = a.getDouble(2).toFloat().coerceIn(0f, 1f),
                delaySend = a.getDouble(3).toFloat().coerceIn(0f, 1f),
                drive = a.getDouble(4).toFloat().coerceIn(0f, 1f),
                crush = a.getDouble(5).toFloat().coerceIn(0f, 1f),
                filter = a.getDouble(6).toFloat().coerceIn(-1f, 1f),
                pump = a.optDouble(7, 0.0).toFloat().coerceIn(0f, 1f),
            ),
            rhythmOctave = o.optInt("rhythmOctave", Track.DEFAULT_RHYTHM_OCTAVE).coerceIn(-1, 9),
            padOrder = o.optInt("padOrder", Int.MAX_VALUE).coerceAtLeast(0),
            padPlayMode = if (kind == TrackKind.DRUM) PadPlayMode.DRIVE else PadPlayMode.fromKey(o.optString("padPlayMode")),
            padSize = when {
                o.optBoolean("padHalf") -> PadSize.HALF
                o.optBoolean("padWide") -> PadSize.WIDE
                else -> PadSize.NORMAL
            },
            hidden = o.optBoolean("hidden", false),
            muted = o.optBoolean("muted", false),
            solo = o.optBoolean("solo", false) && !o.optBoolean("hidden", false),
            synth = if (kind == TrackKind.SYNTH) decodeSynth(o.getJSONObject("synth")) else SynthPresets.BASS,
            synthBank = synthBank,
            chipProgramAssigned = kind == TrackKind.SYNTH && o.optBoolean("chipProgramAssigned",
                synthBank.isGx || o.optInt("program") != 0),
            chipSmoothBass = kind == TrackKind.SYNTH && o.optBoolean("chipSmoothBass"),
            fmProgram = o.optInt("fmProgram", Track.DEFAULT_FM_PROGRAM).coerceIn(0, 15),
            fmBrightness = readFmAxis(o, "fmBrightness", Track.DEFAULT_FM_BRIGHTNESS),
            fmAttack = readFmAxis(o, "fmAttack", Track.DEFAULT_FM_ATTACK),
            bank = o.optInt("bank", 0).coerceIn(0, 16383),
            program = o.optInt("program", 0).coerceIn(0, 127),
            arpMode = arp.getInt(0).coerceIn(0, 4),
            arpRate = arp.getInt(1).coerceIn(0, 2),
            arpOctaves = arp.getInt(2).coerceIn(1, 3),
            kit = (drum?.optInt(0, 0) ?: 0).coerceIn(0, Track.KIT_COUNT - 1),
            tune = (drum?.optInt(1, 0) ?: 0).coerceIn(-12, 12),
            length = (drum?.optInt(2, 100) ?: 100).coerceIn(50, 200),
            sfKit = if (kind == TrackKind.DRUM) (drum?.optInt(3, -1) ?: -1).coerceIn(-1, 127) else -1,
        )
    }

    private fun readFmAxis(o: JSONObject, key: String, fallback: Float): Float =
        o.optDouble(key, fallback.toDouble()).toFloat().takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: fallback

    internal fun writePad(o: JSONObject, position: PadPosition?) {
        if (position != null) o.put("heldPad", JSONArray().put(position.filter.toDouble()).put(position.space.toDouble()))
    }

    internal fun readPad(o: JSONObject): PadPosition? {
        val pair = o.optJSONArray("heldPad") ?: return null
        val filter = pair.optDouble(0)
        val space = pair.optDouble(1)
        if (!filter.isFinite() || !space.isFinite()) return null
        return PadPosition(filter.toFloat().coerceIn(0f, 1f), space.toFloat().coerceIn(0f, 1f))
    }

    fun encode(p: StudioProject): String = JSONObject()
        .also { writePad(it, p.heldPad) }
        .put("version", VERSION)
        .put("style", p.styleId)
        .put("bpm", p.bpm)
        .put("swing", p.swing.toDouble())
        .put("tracks", JSONArray().also { a -> p.tracks.forEach { a.put(encodeTrack(it)) } })
        .put("space", p.space.toDouble())
        .put("echo", p.echo.toDouble())
        .put("key", p.keyPc)
        .put("scale", p.scaleId)
        .put("steps", p.baseSteps)
        .put("patterns", JSONArray().also { a -> p.patterns.forEach { a.put(encodePattern(it)) } })
        .put("current", p.current)
        .put("chain", JSONArray(p.chain))
        .put("songMode", p.songMode)
        .toString()

    private fun readAuto(a: JSONArray?, steps: Int): FloatArray? {
        if (a == null || a.length() != steps * StepPattern.AUTO_RES) return null
        return FloatArray(a.length()) { (a.getInt(it) / 100f).coerceIn(0f, 1f) }
    }

    fun decode(text: String?): StudioProject? {
        if (text.isNullOrBlank()) return null
        return try {
            val o = JSONObject(text)
            if (o.getInt("version") != VERSION) return null
            val steps = o.getInt("steps")
            if (steps != 16 && steps != 32 && steps != 64) return null
            val tracks = ArrayList<Track>()
            val ta = o.getJSONArray("tracks")
            if (ta.length() > StudioEngine.MAX_TRACKS) return null
            for (k in 0 until ta.length()) {
                val t = decodeTrack(ta.getJSONObject(k)) ?: return null
                if (tracks.any { it.id == t.id }) return null
                tracks += t
            }
            val pa = o.getJSONArray("patterns")
            if (pa.length() != StudioProject.SLOTS) return null
            val patterns = ArrayList<StepPattern>(StudioProject.SLOTS)
            for (k in 0 until StudioProject.SLOTS) patterns += decodePattern(pa.getJSONObject(k), steps, tracks) ?: return null
            val chain = ArrayList<Int>()
            o.optJSONArray("chain")?.let { ca ->
                for (k in 0 until minOf(ca.length(), StudioProject.MAX_CHAIN)) {
                    ca.getInt(k).takeIf { it in 0 until StudioProject.SLOTS }?.let { chain += it }
                }
            }
            StudioProject(
                heldPad = readPad(o),
                styleId = o.getString("style"),
                bpm = o.getInt("bpm").coerceIn(40, 220),
                swing = o.getDouble("swing").toFloat().coerceIn(0f, 0.5f),
                tracks = tracks,
                space = o.getDouble("space").toFloat().coerceIn(0f, 1f),
                echo = o.getDouble("echo").toFloat().coerceIn(0f, 1f),
                keyPc = o.getInt("key").coerceIn(0, 11),
                scaleId = o.getString("scale"),
                patterns = patterns,
                current = o.optInt("current", 0).coerceIn(0, StudioProject.SLOTS - 1),
                chain = chain,
                songMode = o.optBoolean("songMode", false) && chain.isNotEmpty(),
            )
        } catch (_: JSONException) {
            null
        }
    }
}

/**
 * Les modifications du projet, appliquées au moteur au fur et à mesure. Tout se passe sur le fil de
 * l'interface ; le moteur reçoit chaque nouveau motif d'un coup (référence @Volatile). Les pistes sont désignées par
 * leur numéro ([Track.id]) ; les fonctions de notes et de son prennent, à défaut, la piste du synthé d'origine.
 */
class StudioSession(
    private val engine: StudioEngine,
    initial: StudioProject,
    history: StudioHistory.Replayed? = null,
    private val persist: StudioPersist? = null,
) {

    var project: StudioProject = initial
        private set

    // ---- Historique : annuler / rétablir, et journal ------------------------------------------------------------------
    //
    // Une action = ce qu'elle a changé, avant et après (voir StudioHistory). On l'applique à l'envers pour l'annuler. Le journal écrit
    // ces actions sur le disque au fur et à mesure : en rouvrant le morceau, on rejoue le journal et on retrouve tout l'historique.
    // Rien d'autre n'est réécrit à chaque action ; le morceau de départ n'est refait que de temps en temps (compaction).

    private val past = ArrayDeque<Action>()
    private val future = ArrayDeque<Action>()
    private var openStart: StudioProject? = null
    private var fmPadTrack = -1
    private var fmPadBrightness = 0f
    private var fmPadAttack = 0f
    private var lastEditAt = 0L

    /** Ce que le journal contient déjà (le morceau de départ plus toutes les lignes écrites). */
    private var persisted: StudioProject = initial
    private val pending = ArrayList<String>()
    private var journalLines = 0

    /** Des modifications qui se suivent de moins de ce délai (en ms) forment une seule action : un trait qui peint dix pas s'annule d'un coup. */
    var coalesceMs = 350L

    val canUndo: Boolean get() = past.isNotEmpty() || openStart?.let { it !== project } == true
    val canRedo: Boolean get() = future.isNotEmpty()

    /** À appeler juste avant une modification du contenu : ouvre une action, sauf si elle prolonge la précédente. */
    private fun remember() {
        val now = System.nanoTime() / 1_000_000L
        val sameAction = openStart != null && now - lastEditAt < coalesceMs
        lastEditAt = now
        future.clear()
        if (sameAction) return
        closeOpen()
        openStart = project
    }

    /** L'action en cours est finie : ce qu'elle a changé entre dans la pile d'annulation et dans le journal. */
    private fun closeOpen() {
        val start = openStart ?: return
        openStart = null
        val changes = StudioHistory.diff(start, project, undoableOnly = true)
        if (changes.isEmpty()) return
        val action = Action(changes)
        past.addLast(action)
        while (past.size > StudioHistory.MAX) past.removeFirst()
        pending += StudioHistory.line(action, true)
        persisted = StudioHistory.apply(persisted, changes, true)
    }

    fun undo() {
        endFmPad()
        closeOpen()
        val action = past.removeLastOrNull() ?: return
        future.addLast(action)
        project = StudioHistory.apply(project, action.changes, false)
        persisted = StudioHistory.apply(persisted, action.changes, false)
        pending += StudioHistory.UNDO_LINE
        lastEditAt = 0L
        pushAll()
    }

    fun redo() {
        endFmPad()
        closeOpen()
        val action = future.removeLastOrNull() ?: return
        past.addLast(action)
        project = StudioHistory.apply(project, action.changes, true)
        persisted = StudioHistory.apply(persisted, action.changes, true)
        pending += StudioHistory.REDO_LINE
        lastEditAt = 0L
        pushAll()
    }

    /**
     * Écrit ce qui n'est pas encore dans le journal : les actions finies, puis ce qui a changé sans être annulable (tempo, volumes…).
     * À appeler peu après chaque modification et en quittant l'écran.
     */
    fun flush() {
        endFmPad()
        closeOpen()
        val rest = StudioHistory.diff(persisted, project, undoableOnly = false)
        if (rest.isNotEmpty()) {
            pending += StudioHistory.line(Action(rest), false)
            persisted = StudioHistory.apply(persisted, rest, true)
        }
        val sink = persist
        if (sink == null || pending.isEmpty()) {
            pending.clear()
            return
        }
        journalLines += pending.size
        if (journalLines > COMPACT_AT) compact(sink) else sink.append(pending.toList())
        pending.clear()
    }

    /** Le journal est devenu long : le morceau de départ redevient l'état d'avant la plus ancienne action gardée, et le journal repart de là. */
    private fun compact(sink: StudioPersist) {
        var base = project
        for (a in past.reversed()) base = StudioHistory.apply(base, a.changes, false)
        val lines = ArrayList<String>()
        for (a in past) lines += StudioHistory.line(a, true)
        for (a in future.reversed()) lines += StudioHistory.line(a, true)
        repeat(future.size) { lines += StudioHistory.UNDO_LINE }
        sink.compact(base, lines)
        journalLines = lines.size
        persisted = project
    }

    init {
        history?.let {
            past.addAll(it.past)
            future.addAll(it.future)
            journalLines = it.lines
        }
        pushAll()
    }

    private fun pushAll() {
        val p = project
        engine.setTracks(p.tracks.map { it.spec })
        engine.pattern = p.pattern
        pushSong()
        engine.bpm = p.bpm.toFloat()
        engine.swing = p.swing
        engine.heldPad = p.heldPad
        engine.spaceAmount = p.space
        engine.echoAmount = p.echo
        for (t in p.tracks) pushTrack(t)
        pushMute()
    }

    private fun pushTrack(t: Track) {
        engine.applyMix(t.id, t.mix)
        engine.synthOf(t.id)?.apply {
            patch = t.synth
            val program = if (t.synthBank == SynthBank.FM) t.fmProgram else t.program
            val brightness = if (t.id == fmPadTrack) fmPadBrightness else t.fmBrightness
            val attack = if (t.id == fmPadTrack) fmPadAttack else t.fmAttack
            selectBank(t.synthBank, program, project.midiChannel(t.id), t.chipSmoothBass, brightness, attack)
        }
        engine.setArp(t.id, t.arpMode, ARP_DIVISIONS[t.arpRate.coerceIn(0, 2)], t.arpOctaves)
        engine.setVoice(t.id, t.kit, t.tune, t.length)
    }

    /** Muet = demandé par l'utilisateur ; en plus, une piste cachée se tait, et quand une piste est en solo, toutes les autres aussi. */
    private fun pushMute() {
        val p = project
        val anySolo = p.tracks.any { it.solo }
        for (t in p.tracks) engine.setMuted(t.id, t.muted || t.hidden || (anySolo && !t.solo))
    }

    private fun changeTrack(id: Int, change: (Track) -> Track) {
        val t = project.trackOrNull(id) ?: return
        val n = change(t)
        project = project.withTrack(n)
        pushTrack(n)
    }

    // ---- Les pistes ---------------------------------------------------------------------------------------------

    /**
     * Ajoute une piste de [kind] ([TrackKind]) à la fin de la liste et rend son numéro (-1 : plus de place). [voice] est le
     * son de batterie d'une piste rythmique ; [bank] et [program] l'instrument d'une piste soundfont.
     */
    fun addTrack(kind: Int, voice: Int = 0, bank: Int = 0, program: Int = 0): Int {
        val id = Track.freeId(project.tracks.map { it.id })
        if (id < 0) return -1
        val t = when (kind) {
            TrackKind.DRUM -> Track.drum(id, voice.coerceIn(0, Inst.DRUM_COUNT - 1))
            TrackKind.SYNTH -> Track.synth(id, space = 0.25f)
            else -> Track.sf2(id, bank, program)
        }
        remember()
        // Une piste rythmique reçoit une ligne vide dans chacun des motifs
        val patterns = if (t.melodic) project.patterns
        else project.patterns.map { it.copy(lanes = it.lanes + Lane(id, FloatArray(it.stepCount))) }
        project = project.copy(tracks = project.tracks + t, patterns = patterns)
        pushAll()
        return id
    }

    /** Retire une piste, ses pas et ses notes (un pas en arrière les rend). */
    fun removeTrack(id: Int) {
        if (project.trackOrNull(id) == null) return
        if (id == fmPadTrack) endFmPad()
        remember()
        val patterns = project.patterns.map { p ->
            p.copy(lanes = p.lanes.filter { it.instrument != id }, notes = p.notes.filter { it.track != id })
        }
        project = project.copy(tracks = project.tracks.filter { it.id != id }, patterns = patterns)
        pushAll()
    }

    /** Cache ou montre une piste : cachée, elle ne s'affiche plus et ne s'entend plus, mais garde ses pas et ses notes. */
    fun setHidden(id: Int, hidden: Boolean) {
        changeTrack(id) { it.copy(hidden = hidden, solo = it.solo && !hidden) }
        pushMute()
    }

    fun isHidden(id: Int): Boolean = project.trackOrNull(id)?.hidden == true

    /** Place la piste [id] au rang [to] de la liste (glisser pour la ranger). */
    fun moveTrack(id: Int, to: Int) {
        val list = project.tracks.toMutableList()
        val from = list.indexOfFirst { it.id == id }
        if (from < 0) return
        val t = list.removeAt(from)
        list.add(to.coerceIn(0, list.size), t)
        remember()
        project = project.copy(tracks = list)
        for (t in list) if (t.synthBank != SynthBank.STUDIO) pushTrack(t)
    }

    fun padTracks(): List<Track> = project.tracks.sortedBy { it.padOrder }

    fun movePad(id: Int, target: Int) {
        val order = padTracks().map { it.id }.toMutableList()
        val to = order.indexOf(target)
        if (id == target || to < 0 || !order.remove(id)) return
        order.add(to, id)
        val ranks = order.withIndex().associate { it.value to it.index }
        closeOpen()
        remember()
        project = project.copy(tracks = project.tracks.map { it.copy(padOrder = ranks.getValue(it.id)) })
        closeOpen()
    }

    fun resizePad(id: Int) = togglePadSize(id, PadSize.WIDE)

    fun togglePadHalf(id: Int) = togglePadSize(id, PadSize.HALF)

    private fun togglePadSize(id: Int, size: PadSize) {
        val t = project.trackOrNull(id) ?: return
        applyTrackSetting(t.copy(padSize = if (t.padSize == size) PadSize.NORMAL else size))
    }

    fun setPadPlayMode(id: Int, mode: PadPlayMode) {
        val t = project.trackOrNull(id) ?: return
        if (t.melodic && t.padPlayMode != mode) applyTrackSetting(t.copy(padPlayMode = mode))
    }

    fun setRhythmOctave(track: Int, octave: Int) {
        val t = project.trackOrNull(track) ?: return
        val next = octave.coerceIn(-1, Track.maxRhythmOctave(project.keyPc))
        if (t.melodic && next != t.rhythmOctave) applyTrackSetting(t.copy(rhythmOctave = next))
    }

    private fun applyTrackSetting(track: Track) {
        closeOpen()
        remember()
        project = project.withTrack(track)
        closeOpen()
    }

    /** Choisit l'instrument d'une piste soundfont. */
    fun setSf2Program(id: Int, bank: Int, program: Int) {
        if (project.trackOrNull(id)?.kind != TrackKind.SF2) return
        changeTrack(id) { it.copy(bank = bank.coerceIn(0, 16383), program = program.coerceIn(0, 127)) }
        engine.setTracks(project.tracks.map { it.spec })
    }

    fun setSynthBank(id: Int, bank: SynthBank) {
        editSynthSound(id) { t ->
            if (bank.isGx && !t.chipProgramAssigned) {
                t.copy(synthBank = bank, program = SynthPresets.chipProgram(t.synth), chipProgramAssigned = true)
            } else t.copy(synthBank = bank)
        }
    }

    fun setChipProgram(id: Int, program: Int) {
        editSynthSound(id) { if (it.synthBank.isGx || it.synthBank == SynthBank.ORCHESTRAL) it.copy(program = program.coerceIn(0, 127), chipProgramAssigned = true) else it }
    }

    fun setChipSmoothBass(id: Int, enabled: Boolean) {
        editSynthSound(id) { if (it.synthBank.isGx) it.copy(chipSmoothBass = enabled) else it }
    }

    fun setFmProgram(id: Int, program: Int) {
        editSynthSound(id) { if (it.synthBank == SynthBank.FM) it.copy(fmProgram = program.coerceIn(0, 15)) else it }
    }

    /** En jeu, les mouvements vont directement aux registres. Seul le point de relâchement entre dans le projet. */
    fun setFmPad(id: Int, brightness: Float, attack: Float) {
        if (id < 0 || !brightness.isFinite() || !attack.isFinite()) return
        val x = brightness.coerceIn(0f, 1f)
        val y = attack.coerceIn(0f, 1f)
        if (fmPadTrack == id) {
            if (x == fmPadBrightness && y == fmPadAttack) return
        } else {
            val track = project.trackOrNull(id) ?: return
            if (track.synthBank != SynthBank.FM || track.fmProgram != 0) return
            endFmPad()
            if (x == track.fmBrightness && y == track.fmAttack) return
        }
        fmPadTrack = id
        fmPadBrightness = x
        fmPadAttack = y
        engine.synthOf(id)?.setFmPad(x, y)
    }

    fun endFmPad() {
        if (fmPadTrack == -1) return
        val track = project.trackOrNull(fmPadTrack)
        fmPadTrack = -1
        if (track == null || track.fmBrightness == fmPadBrightness && track.fmAttack == fmPadAttack) return
        closeOpen()
        remember()
        project = project.withTrack(track.copy(fmBrightness = fmPadBrightness, fmAttack = fmPadAttack))
        closeOpen()
    }

    private inline fun editSynthSound(id: Int, edit: (Track) -> Track) {
        if (id == fmPadTrack) endFmPad()
        val before = project.trackOrNull(id) ?: return
        if (before.kind != TrackKind.SYNTH) return
        val after = edit(before)
        if (after == before) return
        closeOpen()
        remember()
        project = project.withTrack(after)
        pushTrack(after)
        closeOpen()
    }

    /** Règle le son d'une piste de batterie : variante [kit], hauteur [tune] (demi-tons) et durée [length] (pour cent). */
    fun setDrumVoice(id: Int, kit: Int, tune: Int, length: Int) {
        if (project.trackOrNull(id)?.kind != TrackKind.DRUM) return
        remember()
        changeTrack(id) {
            it.copy(kit = kit.coerceIn(0, Track.KIT_COUNT - 1), tune = tune.coerceIn(-12, 12), length = length.coerceIn(50, 200))
        }
    }

    /** Fait jouer une piste de batterie par un kit du soundfont ([program] : numéro du kit, banque 128) ; -1 rend le son fabriqué. */
    fun setDrumSoundfont(id: Int, program: Int) {
        if (project.trackOrNull(id)?.kind != TrackKind.DRUM) return
        remember()
        changeTrack(id) { it.copy(sfKit = program.coerceIn(-1, 127)) }
        engine.setTracks(project.tracks.map { it.spec })
        pushTrack(project.track(id))
    }

    // ---- Arpégiateur d'une piste ---------------------------------------------------------------------------------

    fun setArpMode(mode: Int, track: Int = Inst.SYNTH) = changeTrack(track) { it.copy(arpMode = mode.coerceIn(0, 4)) }

    fun setArpRate(rate: Int, track: Int = Inst.SYNTH) = changeTrack(track) { it.copy(arpRate = rate.coerceIn(0, 2)) }

    fun setArpOctaves(octaves: Int, track: Int = Inst.SYNTH) = changeTrack(track) { it.copy(arpOctaves = octaves.coerceIn(1, 3)) }

    // ---- Mixeur ----------------------------------------------------------------------------------------------

    fun mixOf(id: Int): TrackMix = project.track(id).mix

    /** Change les réglages de mixage d'une piste (volume, panoramique, envois, effets). */
    fun updateTrack(id: Int, change: (TrackMix) -> TrackMix) = changeTrack(id) { it.copy(mix = change(it.mix)) }

    fun toggleSolo(id: Int) {
        changeTrack(id) { it.copy(solo = !it.solo) }
        pushMute()
    }

    fun isSolo(id: Int): Boolean = project.trackOrNull(id)?.solo == true

    fun setMuted(id: Int, muted: Boolean) {
        changeTrack(id) { it.copy(muted = muted) }
        pushMute()
    }

    fun isMuted(id: Int): Boolean = project.trackOrNull(id)?.muted == true

    /** Sans emplacement explicite, l'édition porte sur toute la vue (un ou plusieurs motifs). */
    private fun setPattern(pattern: StepPattern, slot: Int? = null) {
        remember()
        project = if (slot == null) project.copy(pattern = pattern) else {
            project.copy(patterns = project.patterns.toMutableList().also { it[slot] = pattern })
        }
        engine.pattern = project.pattern
        pushSong()
    }

    internal fun recordingSlot(track: Int): Int? = engine.activeTrackClip(track).takeIf { it in project.patterns.indices }

    internal fun patternAt(slot: Int?): StepPattern = if (slot == null) project.pattern else project.patterns[slot]

    /** Le moteur reçoit le morceau déjà résolu en motifs (ou rien : il boucle alors sur le motif en cours). */
    private fun pushSong() {
        val p = project
        engine.trackClipPatterns = p.patterns
        engine.song = if (p.songMode && p.chain.isNotEmpty()) Array(p.chain.size) { p.patterns[p.chain[it]] } else null
    }

    // ---- Motifs A à D et morceau --------------------------------------------------------------------------------

    fun selectPattern(slot: Int) = setView(listOf(slot))

    /**
     * Choisit les motifs affichés à la suite (au moins un). Le motif en cours devient leur mise bout à bout : on l'édite, on l'entend
     * et on l'exporte comme un seul motif long. Avec un seul emplacement, c'est le motif en cours habituel.
     */
    fun setView(slots: Collection<Int>) {
        val s = slots.filter { it in 0 until StudioProject.SLOTS }.distinct().sorted()
        if (s.isEmpty()) return
        val view = if (s.size == 1) emptyList() else s
        if (s.first() == project.current && view == project.view) return
        project = project.copy(current = s.first(), view = view)
        engine.pattern = project.pattern
    }

    fun isPatternEmpty(slot: Int): Boolean {
        val p = project.patterns[slot]
        return p.notes.isEmpty() && p.lanes.all { l -> l.velocity.all { it <= 0f } }
    }

    fun hasTrackClip(track: Int, slot: Int): Boolean {
        val pattern = project.patterns.getOrNull(slot) ?: return false
        return when (project.trackOrNull(track)?.melodic) {
            true -> pattern.notes.any { it.track == track }
            false -> pattern.lane(track)?.velocity?.any { it > 0f } == true
            null -> false
        }
    }

    /** Fige la combinaison des clips sur la durée de la scène de base, avec son geste de pad. */
    fun captureTrackClips(baseSlot: Int, overrides: Map<Int, Int>, destination: Int): Boolean {
        val patterns = project.patterns
        val base = patterns.getOrNull(baseSlot) ?: return false
        val previous = patterns.getOrNull(destination) ?: return false
        if (overrides.any { (track, slot) -> project.trackOrNull(track) == null || slot !in patterns.indices }) return false

        val clips = overrides.filterValues { it != baseSlot }
        val sources = clips.values.toSortedSet().associateWith { slot ->
            val p = patterns[slot]
            if (p.stepCount == base.stepCount) p else resize(p, base.stepCount)
        }
        val lanes = base.lanes.map { lane ->
            val source = clips[lane.instrument]?.let(sources::get) ?: return@map lane
            source.lane(lane.instrument) ?: Lane(lane.instrument, FloatArray(base.stepCount))
        } + sources.flatMap { (slot, source) ->
            source.lanes.filter { lane ->
                clips[lane.instrument] == slot && base.lane(lane.instrument) == null &&
                    project.track(lane.instrument).melodic == false
            }
        }
        val notes = base.notes.filterNot { it.track in clips } + sources.flatMap { (slot, source) ->
            source.notes.filter { clips[it.track] == slot }
        }
        val captured = base.copy(lanes = lanes, notes = notes)
        if (previous.stepCount == captured.stepCount) {
            if (previous.lanes == lanes && previous.notes == notes && previous.autoFilter === base.autoFilter && previous.autoSpace === base.autoSpace) return false
            if (StudioProjectCodec.encodePattern(previous).toString() == StudioProjectCodec.encodePattern(captured).toString()) return false
        }

        closeOpen()
        setPattern(captured, destination)
        closeOpen()
        return true
    }

    /** Duplique uniquement le clip de cette piste ; la destination garde ses autres pistes et son geste. */
    fun copyTrackClip(track: Int, from: Int, to: Int): Boolean = captureTrackClips(to, mapOf(track to from), to)

    /** Copie le motif [from] dans l'emplacement [to] (geste du pad compris). */
    fun copyPattern(from: Int, to: Int) {
        if (from == to || from !in 0 until StudioProject.SLOTS || to !in 0 until StudioProject.SLOTS) return
        remember()
        val all = project.patterns.toMutableList()
        all[to] = all[from]
        project = project.copy(patterns = all)
        engine.pattern = project.pattern
        pushSong()
    }

    fun chainAdd(slot: Int) {
        if (slot !in 0 until StudioProject.SLOTS || project.chain.size >= StudioProject.MAX_CHAIN) return
        project = project.copy(chain = project.chain + slot)
        pushSong()
    }

    fun chainRemoveLast() {
        if (project.chain.isEmpty()) return
        val chain = project.chain.dropLast(1)
        project = project.copy(chain = chain, songMode = project.songMode && chain.isNotEmpty())
        pushSong()
    }

    fun chainClear() {
        project = project.copy(chain = emptyList(), songMode = false)
        pushSong()
    }

    /** Déplace le bloc [from] du morceau au rang [to] (glisser pour ranger). */
    fun chainMove(from: Int, to: Int) {
        val list = project.chain.toMutableList()
        if (from !in list.indices) return
        val slot = list.removeAt(from)
        list.add(to.coerceIn(0, list.size), slot)
        project = project.copy(chain = list)
        pushSong()
    }

    /** Retire le bloc [index] du morceau. */
    fun chainRemoveAt(index: Int) {
        if (index !in project.chain.indices) return
        val chain = project.chain.toMutableList().also { it.removeAt(index) }
        project = project.copy(chain = chain, songMode = project.songMode && chain.isNotEmpty())
        pushSong()
    }

    /**
     * Un coup joué en direct (pads, roulement) pendant l'enregistrement : il s'écrit sur le pas le plus proche de [position]
     * (en pas, rendue par le moteur au moment exact du coup). Un coup du décompte tombé juste avant le début compte pour le
     * premier pas ; plus tôt, il est ignoré.
     */
    fun recordHit(track: Int, position: Double, velocity: Float, sourceSlot: Int = engine.activeTrackClip(track)) {
        if (project.trackOrNull(track)?.melodic != false || !position.isFinite()) return
        val r = Math.round(position)
        if (r < 0) return
        val slot = sourceSlot.takeIf { it in project.patterns.indices }
        val step = Math.floorMod(r, patternAt(slot).stepCount.toLong()).toInt()
        withLane(track, slot) { l ->
            val v = l.velocity.copyOf()
            v[step] = maxOf(v[step], velocity.coerceIn(0.1f, 1f))
            l.copy(velocity = v)
        }
    }

    /** Répète une mesure sur les motifs affichés. Les coups communs gardent leurs nuances. */
    internal fun spreadDrum(track: Int, hits: Int, offset: Int): Boolean {
        val lane = project.pattern.lane(track) ?: return false
        val bar = com.Atom2Universe.app.studio.music.BarRhythm.steps(hits, offset)
        if (lane.velocity.indices.all { (lane.velocity[it] > 0f) == bar[it % bar.size] }) return false
        val velocity = lane.velocity.copyOf()
        val pitch = lane.note.copyOf()
        val hold = lane.hold.copyOf()
        val condition = lane.cond.copyOf()
        for (i in velocity.indices) {
            if (!bar[i % bar.size]) velocity[i] = 0f
            else if (velocity[i] == 0f) {
                velocity[i] = LEVELS[0]
                pitch[i] = 60
                hold[i] = 1f
                condition[i] = StepCond.ALWAYS
            }
        }
        closeOpen()
        withLane(track) { it.copy(velocity = velocity, note = pitch, hold = hold, cond = condition) }
        closeOpen()
        return true
    }

    fun setSongMode(on: Boolean) {
        project = project.copy(songMode = on && project.chain.isNotEmpty())
        pushSong()
    }

    private fun withLane(track: Int, slot: Int? = null, change: (Lane) -> Lane) {
        val pattern = patternAt(slot)
        val lanes = pattern.lanes.map { if (it.instrument == track) change(it) else it }
        setPattern(pattern.copy(lanes = lanes), slot)
    }

    private fun withNotes(notes: List<Note>) = setPattern(project.pattern.copy(notes = notes))

    // ---- Geste du pad enregistré dans la boucle ------------------------------------------------------------------

    private var takeFilter: FloatArray? = null
    private var takeSpace: FloatArray? = null
    private var takeLast = -1

    /** Une prise commence : on part de ce qui est déjà enregistré (le nouveau geste écrase seulement ce qu'il touche). */
    fun beginGesture() {
        val n = project.stepCount * StepPattern.AUTO_RES
        takeFilter = project.pattern.autoFilter?.copyOf() ?: FloatArray(n)
        takeSpace = project.pattern.autoSpace?.copyOf() ?: FloatArray(n)
        takeLast = -1
    }

    /** Écrit la position du doigt à la position de lecture [position] (en pas) ; à appeler souvent pendant la prise. */
    fun recordGesture(position: Double, filter: Float, space: Float) {
        val f = takeFilter ?: return
        val s = takeSpace ?: return
        val n = f.size
        val steps = project.stepCount
        val idx = ((((position % steps) + steps) % steps) * StepPattern.AUTO_RES).toInt().coerceIn(0, n - 1)
        // Les points sautés entre deux appels reprennent la valeur courante (le doigt reste là où il est).
        var i = if (takeLast < 0) idx else (takeLast + 1) % n
        var guard = 0
        while (guard++ <= StepPattern.AUTO_RES * 2) {
            f[i] = filter.coerceIn(0f, 1f)
            s[i] = space.coerceIn(0f, 1f)
            if (i == idx) break
            i = (i + 1) % n
        }
        takeLast = idx
    }

    /** La prise est finie : le geste devient celui du motif (vide si tout est à zéro). */
    fun endGesture() {
        val f = takeFilter ?: return
        val s = takeSpace ?: return
        takeFilter = null
        takeSpace = null
        remember()
        setPattern(project.pattern.copy(autoFilter = f.takeIf { a -> a.any { it > 0.001f } }, autoSpace = s.takeIf { a -> a.any { it > 0.001f } }))
    }

    fun clearGesture() {
        if (!project.pattern.hasAutomation) return
        remember()
        setPattern(project.pattern.copy(autoFilter = null, autoSpace = null))
    }

    // ---- Grille rythmique (la ligne d'une piste mélodique en est un résumé : un pas est allumé si une note y commence) --

    private fun isMelodic(track: Int): Boolean = project.trackOrNull(track)?.melodic == true

    /** Un index partagé par les deux grilles tant que le motif affiché reste le même. */
    private class Melody(val notes: List<Note>, steps: Int) {
        val velocity = FloatArray(steps)
        val tails = BooleanArray(steps)
        init {
            for (note in notes) {
                if (note.step !in velocity.indices) continue
                velocity[note.step] = maxOf(velocity[note.step], note.velocity)
                for (s in note.step + 1 until minOf(steps, note.step + note.length)) tails[s] = true
            }
        }
    }

    private var indexedPattern: StepPattern? = null
    private var melodies = emptyMap<Int, Melody>()

    private fun melody(track: Int): Melody? {
        val pattern = project.pattern
        if (pattern !== indexedPattern) {
            melodies = pattern.notes.groupBy { it.track }.mapValues { Melody(it.value, pattern.stepCount) }
            indexedPattern = pattern
        }
        return melodies[track]
    }

    fun notesOf(track: Int): List<Note> = melody(track)?.notes.orEmpty()

    fun isOn(track: Int, step: Int): Boolean =
        if (isMelodic(track)) (melody(track)?.velocity?.getOrNull(step) ?: 0f) > 0f
        else (project.pattern.lane(track)?.velocity?.getOrNull(step) ?: 0f) > 0f

    /** Vélocité d'un pas (0 = éteint) ; pour une piste mélodique, la plus forte des notes qui y commencent. */
    fun velocity(track: Int, step: Int): Float =
        if (isMelodic(track)) melody(track)?.velocity?.getOrNull(step) ?: 0f
        else project.pattern.lane(track)?.velocity?.getOrNull(step) ?: 0f

    fun setStep(track: Int, step: Int, on: Boolean) {
        if (project.trackOrNull(track) == null || step !in 0 until project.stepCount) return
        if (isOn(track, step) == on) return
        if (isMelodic(track)) {
            if (on) {
                // Le prolongement d'une note longue n'est pas un pas libre : on la règle par sa bulle, pas en la touchant
                if (isTail(track, step)) return
                withNotes(project.pattern.notes + Note(step, rhythmEntryPitch(track), 1, LEVELS[0], track))
            } else {
                withNotes(project.pattern.notes.filter { !(it.track == track && it.step == step) })
            }
            return
        }
        withLane(track) { l ->
            val v = l.velocity.copyOf()
            v[step] = if (on) LEVELS[0] else 0f
            // Un pas qu'on rallume joue à chaque tour
            val c = if (on && l.cond[step] != StepCond.ALWAYS) l.cond.copyOf().also { it[step] = StepCond.ALWAYS } else l.cond
            l.copy(velocity = v, cond = c)
        }
    }

    /** Quand joue le pas [step] d'une piste de batterie ([StepCond]) ; une piste à notes joue toujours. */
    fun stepCond(track: Int, step: Int): Int =
        if (isMelodic(track)) StepCond.ALWAYS else project.pattern.lane(track)?.cond?.getOrNull(step) ?: StepCond.ALWAYS

    /** Vrai si le pas est le prolongement d'une note qui a commencé avant (une note longue de la piste [track]). */
    fun isTail(track: Int, step: Int): Boolean =
        isMelodic(track) && melody(track)?.tails?.getOrNull(step) == true

    fun toggleStep(track: Int, step: Int) = setStep(track, step, !isOn(track, step))

    /**
     * Ce qu'un pas allumé porte : sa force, sa longueur en pas (1 pour la batterie) et sa hauteur (touche MIDI ; pour la batterie,
     * 60 est la hauteur d'origine du son). Pour un pas de piste à notes, la longueur et la hauteur sont celles de la note la plus grave.
     */
    class StepInfo(val velocity: Float, val length: Int, val pitch: Int, val hold: Int = 100, val cond: Int = StepCond.ALWAYS)

    fun stepInfo(track: Int, step: Int): StepInfo? {
        if (!isOn(track, step)) return null
        if (isMelodic(track)) {
            val low = project.pattern.notes.filter { it.track == track && it.step == step }.minByOrNull { it.pitch } ?: return null
            return StepInfo(velocity(track, step), low.length, low.pitch)
        }
        val lane = project.pattern.lane(track) ?: return null
        return StepInfo(
            lane.velocity[step], 1, lane.note.getOrElse(step) { 60 }, Math.round(lane.hold.getOrElse(step) { 1f } * 100f),
            lane.cond.getOrElse(step) { StepCond.ALWAYS },
        )
    }

    // ---- Sélection : copier, couper, coller, supprimer --------------------------------------------------------------

    /** Ce que le presse-papiers garde : des coups de batterie et des notes, placés par rapport au bord gauche du cadre copié. */
    class Clip(val hits: List<Hit>, val notes: List<ClipNote>) {
        data class Hit(val track: Int, val rel: Int, val velocity: Float, val pitch: Int, val hold: Float, val cond: Int = StepCond.ALWAYS)
        data class ClipNote(val track: Int, val rel: Int, val pitch: Int, val length: Int, val velocity: Float)

        val isEmpty: Boolean get() = hits.isEmpty() && notes.isEmpty()

        /** La note la plus grave : coller dans le piano roll l'aligne sur la case touchée. */
        val lowPitch = notes.minOfOrNull { it.pitch } ?: 0
        val highPitch = notes.maxOfOrNull { it.pitch } ?: 0
        val span = maxOf(hits.maxOfOrNull { it.rel + 1 } ?: 0, notes.maxOfOrNull { it.rel + it.length } ?: 0)
        val firstStep = minOf(hits.minOfOrNull { it.rel } ?: Int.MAX_VALUE,
            notes.minOfOrNull { it.rel } ?: Int.MAX_VALUE).takeUnless { it == Int.MAX_VALUE } ?: 0

        fun alignedToFirstEvent(): Clip = if (firstStep == 0) this else Clip(
            hits.map { it.copy(rel = it.rel - firstStep) }, notes.map { it.copy(rel = it.rel - firstStep) })

        val tracks = (hits.map { it.track } + notes.map { it.track }).toSet()
    }

    /**
     * Copie ce qui est dans le cadre : sur les pistes [tracks], les pas de [stepFrom] à [stepTo] (inclus). Pour une piste à notes, seules
     * les notes qui commencent dans le cadre et dont la hauteur est entre [pitchLow] et [pitchHigh] comptent.
     */
    fun copyRange(tracks: Set<Int>, stepFrom: Int, stepTo: Int, pitchLow: Int = 0, pitchHigh: Int = 127): Clip {
        val hits = ArrayList<Clip.Hit>()
        val notes = ArrayList<Clip.ClipNote>()
        for (t in tracks) {
            if (isMelodic(t)) {
                for (n in project.pattern.notes) {
                    if (n.track == t && n.step in stepFrom..stepTo && n.pitch in pitchLow..pitchHigh) {
                        notes += Clip.ClipNote(t, n.step - stepFrom, n.pitch, n.length, n.velocity)
                    }
                }
            } else {
                val lane = project.pattern.lane(t) ?: continue
                for (s in stepFrom..minOf(stepTo, lane.velocity.size - 1)) {
                    if (lane.velocity[s] > 0f) hits += Clip.Hit(t, s - stepFrom, lane.velocity[s], lane.note[s], lane.hold.getOrElse(s) { 1f }, lane.cond.getOrElse(s) { 0 })
                }
            }
        }
        return Clip(hits, notes)
    }

    /** Vide le cadre (un pas en arrière le rend). */
    fun clearRange(tracks: Set<Int>, stepFrom: Int, stepTo: Int, pitchLow: Int = 0, pitchHigh: Int = 127) {
        remember()
        val lanes = project.pattern.lanes.map { l ->
            if (l.instrument !in tracks) {
                l
            } else {
                val v = l.velocity.copyOf()
                for (s in stepFrom..minOf(stepTo, v.size - 1)) v[s] = 0f
                l.copy(velocity = v)
            }
        }
        val notes = project.pattern.notes.filter { n -> !(n.track in tracks && n.step in stepFrom..stepTo && n.pitch in pitchLow..pitchHigh) }
        setPattern(project.pattern.copy(lanes = lanes, notes = notes))
    }

    /**
     * Colle [clip] à partir du pas [atStep] : les coups et les notes s'ajoutent à ce qui est là (une note remplace ce qu'elle recouvre à
     * la même hauteur). [pitchShift] transpose les notes ; [noteTrack] envoie toutes les notes sur cette piste (le piano roll colle sur la
     * piste ouverte). Ce qui sortirait de la boucle est ignoré. Un pas en arrière rend tout.
     */
    fun paste(clip: Clip, atStep: Int, pitchShift: Int = 0, noteTrack: Int? = null, trackMap: Map<Int, Int> = emptyMap()) {
        if (clip.isEmpty) return
        remember()
        val steps = project.stepCount
        val lanes = project.pattern.lanes.map { l ->
            val mine = clip.hits.filter { (trackMap[it.track] ?: it.track) == l.instrument && atStep + it.rel in 0 until steps }
            if (mine.isEmpty()) {
                l
            } else {
                val v = l.velocity.copyOf()
                val n = l.note.copyOf()
                val h = l.hold.copyOf()
                val c = l.cond.copyOf()
                for (hit in mine) {
                    val s = atStep + hit.rel
                    v[s] = hit.velocity
                    n[s] = hit.pitch
                    h[s] = hit.hold
                    c[s] = hit.cond
                }
                l.copy(velocity = v, note = n, hold = h, cond = c)
            }
        }
        var notes: List<Note> = project.pattern.notes
        for (c in clip.notes) {
            val track = noteTrack ?: trackMap[c.track] ?: c.track
            val s = atStep + c.rel
            if (!isMelodic(track) || s !in 0 until steps) continue
            val pitch = (c.pitch + pitchShift).coerceIn(0, 127)
            val len = c.length.coerceIn(1, steps - s)
            notes = notes.filter { n -> !(n.track == track && n.pitch == pitch && n.step < s + len && n.step + n.length > s) } + Note(s, pitch, len, c.velocity, track)
        }
        setPattern(project.pattern.copy(lanes = lanes, notes = notes))
    }

    /** Remet un pas allumé à ses réglages normaux : pleine force, une durée ou une longueur d'origine, la hauteur d'origine. */
    fun resetStep(track: Int, step: Int) {
        val melodic = isMelodic(track)
        editStep(
            track, step, velocity = LEVELS[0], length = if (melodic) 1 else null,
            pitch = if (melodic) null else 60, hold = if (melodic) null else 100, cond = if (melodic) null else StepCond.ALWAYS,
        )
    }

    /**
     * Règle un pas allumé : [velocity] (0,1 à 1), [length] en pas (pistes à notes) et [pitch] (la touche de la note la plus grave
     * pour une piste à notes, de 48 à 72 pour la batterie) ; [hold] (batterie) la durée du coup en pour cent du son, de 50 à 200.
     * Ce qu'on ne donne pas reste comme avant.
     */
    fun editStep(track: Int, step: Int, velocity: Float? = null, length: Int? = null, pitch: Int? = null, hold: Int? = null, cond: Int? = null) {
        if (!isOn(track, step)) return
        val vel = velocity?.coerceIn(0.1f, 1f)
        if (isMelodic(track)) {
            val here = project.pattern.notes.filter { it.track == track && it.step == step }
            val low = here.minByOrNull { it.pitch } ?: return
            val shift = if (pitch == null) 0 else pitch.coerceIn(0, 127) - low.pitch
            val edited = here.map { n ->
                val len = (length ?: n.length).coerceIn(1, project.stepCount - step)
                Note(step, (n.pitch + shift).coerceIn(0, 127), len, vel ?: n.velocity, track)
            }
            val others = project.pattern.notes.filter { n -> here.none { it === n } }
            // Une note allongée recouvre ce qui sonne à la même hauteur sur la même piste
            val kept = others.filter { o ->
                edited.none { e -> o.track == e.track && o.pitch == e.pitch && o.step < e.step + e.length && o.step + o.length > e.step }
            }
            withNotes(kept + edited)
            return
        }
        withLane(track) { l ->
            val v = l.velocity.copyOf()
            val n = l.note.copyOf()
            val h = l.hold.copyOf()
            val c = l.cond.copyOf()
            if (vel != null) v[step] = vel
            if (pitch != null) n[step] = pitch.coerceIn(48, 72)
            if (hold != null) h[step] = hold.coerceIn(50, 200) / 100f
            if (cond != null) c[step] = cond.coerceIn(0, StepCond.COUNT - 1)
            l.copy(velocity = v, note = n, hold = h, cond = c)
        }
    }

    /** Fort → moyen → doux → fort… sur un pas allumé ; rend la nouvelle vélocité (0 si le pas est éteint). */
    fun cycleVelocity(track: Int, step: Int): Float {
        val current = velocity(track, step)
        if (current <= 0f) return 0f
        var idx = 0
        for (k in LEVELS.indices) if (abs(LEVELS[k] - current) < abs(LEVELS[idx] - current)) idx = k
        val next = LEVELS[(idx + 1) % LEVELS.size]
        if (isMelodic(track)) {
            withNotes(project.pattern.notes.map { if (it.track == track && it.step == step) Note(it.step, it.pitch, it.length, next, it.track) else it })
        } else {
            withLane(track) { l ->
                val v = l.velocity.copyOf()
                v[step] = next
                l.copy(velocity = v)
            }
        }
        return next
    }

    /** Une attaque est prioritaire sur une note tenue. Une case libre fait entendre la note que le prochain appui y pose. */
    internal fun rhythmPreviewPitch(track: Int, step: Int): Int {
        val notes = notesOf(track)
        return notes.firstOrNull { it.step == step }?.pitch
            ?: notes.firstOrNull { step > it.step && step < it.step + it.length }?.pitch
            ?: rhythmEntryPitch(track)
    }

    /** Tonique de la piste commune aux nouveaux pas de Rythme, au repère de Notes et au bord gauche du pad. */
    internal fun rhythmEntryPitch(track: Int): Int {
        val key = project.keyPc
        val octave = project.track(track).rhythmOctave.coerceIn(-1, Track.maxRhythmOctave(key))
        return (octave + 1) * 12 + key
    }

    internal fun padPitches(track: Int): IntArray =
        Scales.rows(Scales.byId(project.scaleId), project.keyPc, rhythmEntryPitch(track), octaves = 1, locked = true)

    // ---- Notes d'une piste mélodique ---------------------------------------------------------------------------

    /** La note de la piste [track] qui couvre la case ([step], [pitch]), s'il y en a une. */
    fun noteAt(step: Int, pitch: Int, track: Int = Inst.SYNTH): Note? =
        project.pattern.notes.firstOrNull { it.track == track && it.pitch == pitch && step >= it.step && step < it.step + it.length }

    /** Pose une note ; ce qu'elle recouvre à la même hauteur sur la même piste disparaît (le moteur relâche une note par sa hauteur). */
    fun addNote(step: Int, pitch: Int, length: Int, velocity: Float = LEVELS[0], track: Int = Inst.SYNTH, slot: Int? = null) {
        val pattern = patternAt(slot)
        if (step !in 0 until pattern.stepCount || !isMelodic(track)) return
        val len = length.coerceIn(1, pattern.stepCount - step)
        val kept = pattern.notes.filter { n ->
            !(n.track == track && n.pitch == pitch && n.step < step + len && n.step + n.length > step)
        }
        setPattern(pattern.copy(notes = kept + Note(step, pitch.coerceIn(0, 127), len, velocity, track)), slot)
    }

    fun removeNote(note: Note) = withNotes(project.pattern.notes.filter {
        it.track != note.track || it.step != note.step || it.pitch != note.pitch
    })

    fun resizeNote(note: Note, length: Int) = editNote(note, note.step, note.pitch, length)

    /** Un seul changement au relâchement, même si le doigt a traversé plusieurs notes. */
    fun editNote(note: Note, step: Int, pitch: Int, length: Int = note.length, velocity: Float = note.velocity) {
        val notes = project.pattern.notes
        val current = notes.firstOrNull { it.track == note.track && it.step == note.step && it.pitch == note.pitch } ?: return
        if (step !in 0 until project.stepCount || pitch !in 0..127) return
        val len = length.coerceIn(1, project.baseSteps - step % project.baseSteps)
        if (current.step == step && current.pitch == pitch && current.length == len && current.velocity == velocity) return
        val kept = notes.filter { n ->
            n !== current && !(n.track == note.track && n.pitch == pitch && n.step < step + len && n.step + n.length > step)
        }
        withNotes(kept + Note(step, pitch, len, velocity, note.track))
    }

    /** Change de tonalité : les notes se transposent par le plus court chemin (de −5 à +6 demi-tons). */
    fun setKey(keyPc: Int) {
        remember()
        val k = ((keyPc % 12) + 12) % 12
        var delta = k - project.keyPc
        if (delta > 6) delta -= 12
        if (delta < -5) delta += 12
        val moved = project.pattern.notes.map { Note(it.step, (it.pitch + delta).coerceIn(0, 127), it.length, it.velocity, it.track) }
        project = project.copy(keyPc = k)
        withNotes(moved)
    }

    fun setScale(id: String) {
        project = project.copy(scaleId = id)
    }

    // ---- Son d'une piste de synthé ---------------------------------------------------------------------------

    fun setPreset(id: String, track: Int = Inst.SYNTH) = changeTrack(track) { if (it.kind == TrackKind.SYNTH) it.copy(synth = SynthPresets.byId(id)) else it }

    fun updateSynth(track: Int = Inst.SYNTH, change: (SynthPatch) -> SynthPatch) =
        changeTrack(track) { if (it.kind == TrackKind.SYNTH) it.copy(synth = change(it.synth)) else it }

    /** L'« ambiance » d'une piste : sa part envoyée à la réverbération (l'écho en reçoit les sept dixièmes). */
    fun setSynthSpace(v: Float, track: Int = Inst.SYNTH) {
        val space = v.coerceIn(0f, 1f)
        updateTrack(track) { it.copy(reverbSend = space, delaySend = space * 0.7f) }
    }

    // ---- Ensemble du morceau -----------------------------------------------------------------------------------

    /** 16, 32 ou 64 pas : on allonge en répétant le motif, on réduit en gardant le début. */
    fun setStepCount(count: Int) {
        if (count == project.baseSteps || (count != 16 && count != 32 && count != 64)) return
        remember()
        // Tous les motifs changent de longueur ensemble (le morceau les enchaîne : ils restent de même longueur)
        val resized = project.patterns.map { resize(it, count) }
        project = project.copy(patterns = resized)
        engine.pattern = project.pattern
        pushSong()
    }

    private fun resize(source: StepPattern, count: Int): StepPattern {
        val old = source.stepCount
        val lanes = source.lanes.map { l ->
            Lane(
                l.instrument, FloatArray(count) { l.velocity[it % old] }, IntArray(count) { l.note[it % old] }, l.gate,
                FloatArray(count) { l.hold[it % old] }, IntArray(count) { l.cond[it % old] },
            )
        }
        val notes = ArrayList<Note>()
        for (start in 0 until count step old) {
            for (n in source.notes) {
                val step = start + n.step
                if (step < count) notes += Note(step, n.pitch, minOf(n.length, count - step), n.velocity, n.track)
            }
        }
        fun resizeAuto(a: FloatArray?): FloatArray? =
            a?.takeIf { it.size == old * StepPattern.AUTO_RES }?.let { src -> FloatArray(count * StepPattern.AUTO_RES) { src[it % src.size] } }
        return StepPattern(count, lanes, notes, resizeAuto(source.autoFilter), resizeAuto(source.autoSpace))
    }

    /** Un motif vide, avec une ligne pour chaque piste rythmique du projet. */
    private fun emptyPattern(): StepPattern =
        StepPattern(project.stepCount, project.tracks.filter { !it.melodic }.map { Lane(it.id, FloatArray(project.stepCount)) })

    /**
     * Fait entrer un motif fabriqué par un style (sept sons de batterie, notes du synthé d'origine) dans les pistes du
     * projet : chaque son de batterie va à la première piste de ce son, les notes à la première piste mélodique ; les
     * autres pistes gardent ce qu'elles avaient dans [base].
     */
    private fun adapt(generated: StepPattern, base: StepPattern, steps: Int = project.stepCount): StepPattern {
        val tracks = project.tracks
        val firstOfVoice = HashSet<Int>()
        val lanes = ArrayList<Lane>()
        for (t in tracks) {
            if (t.melodic) continue
            val fresh = if (firstOfVoice.add(t.voice)) generated.lane(t.voice) else null
            lanes += if (fresh != null) Lane(t.id, fresh.velocity, fresh.note, fresh.gate)
            else base.lane(t.id) ?: Lane(t.id, FloatArray(steps))
        }
        val target = project.firstMelodic?.id
        val notes = ArrayList<Note>()
        for (n in base.notes) if (n.track != target) notes += n
        if (target != null) for (n in generated.notes) notes += Note(n.step, n.pitch, n.length, n.velocity, target)
        return generated.copy(lanes = lanes, notes = notes)
    }

    /** Charge un style de départ : tempo, swing, son, tonalité, effets et motif. */
    fun applyStyle(style: Style, seed: Long = style.id.hashCode().toLong()) {
        remember()
        val fresh = StudioProject.fromStyle(style, project.baseSteps, seed)
        val target = project.firstMelodic
        // Le style remplace seulement le motif en cours et le son du premier synthé : les autres motifs, le morceau, le
        // mixage et le jeu (arpège, solo) sont à l'utilisateur, pas au style.
        val tracks = project.tracks.map { t ->
            if (t.id == target?.id && t.kind == TrackKind.SYNTH) t.copy(synth = SynthPresets.byId(style.patchId)) else t
        }
        project = fresh.copy(
            heldPad = project.heldPad,
            tracks = tracks,
            patterns = project.patterns.toMutableList().also { it[project.current] = adapt(fresh.pattern, project.patterns[project.current], project.baseSteps) },
            current = project.current, chain = project.chain, songMode = project.songMode,
        )
        pushAll()
    }

    /** Hasard : un nouveau motif dans le style en cours (tempo, son et effets inchangés). */
    fun randomize(seed: Long) {
        val style = Styles.byId(project.styleId)
        remember()
        // Les notes suivent la tonalité et la gamme choisies, pas celles du style d'origine.
        val generated = Styles.generate(
            style.inKey(24 + project.keyPc + (style.synthRoot / 12 * 12 - 24), project.scaleId),
            project.stepCount, seed,
        )
        // Le geste enregistré reste : c'est un réglage de jeu, comme le mixage.
        setPattern(adapt(generated, project.pattern).copy(autoFilter = project.pattern.autoFilter, autoSpace = project.pattern.autoSpace))
    }

    fun clear() {
        remember()
        setPattern(emptyPattern())
    }

    fun setBpm(bpm: Int) {
        project = project.copy(bpm = bpm.coerceIn(40, 220))
        engine.bpm = project.bpm.toFloat()
    }

    fun setSwing(swing: Float) {
        project = project.copy(swing = swing.coerceIn(0f, 0.5f))
        engine.swing = project.swing
    }

    fun setHeldPad(position: PadPosition?) {
        if (position == project.heldPad) return
        project = project.copy(heldPad = position)
        engine.heldPad = position
    }

    fun setSpace(v: Float) {
        project = project.copy(space = v.coerceIn(0f, 1f))
        engine.spaceAmount = project.space
    }

    fun setEcho(v: Float) {
        project = project.copy(echo = v.coerceIn(0f, 1f))
        engine.echoAmount = project.echo
    }

    companion object {
        /** Les trois niveaux d'un pas : fort, moyen, doux. */
        val LEVELS = floatArrayOf(1.0f, 0.7f, 0.4f)

        /** Durée d'un coup d'arpège en pas pour chaque vitesse : croche, double-croche, triple-croche. */
        val ARP_DIVISIONS = floatArrayOf(2f, 1f, 0.5f)

        /** Nombre de lignes au-delà duquel le journal est réécrit à partir de l'état du moment. */
        const val COMPACT_AT = 300
    }
}

/**
 * Enregistre ce qu'on joue en direct (clavier tactile, clavier MIDI) dans le motif pendant que la lecture tourne :
 * chaque note est placée sur le pas le plus proche de l'appui et dure jusqu'au relâchement, arrondi au pas.
 * [position] rend la position de lecture en pas (avec décimales, depuis le début de la lecture).
 */
class LiveRecorder(
    private val session: StudioSession,
    private val isPlaying: () -> Boolean,
    private val position: () -> Double,
    /** La piste mélodique qui reçoit les notes jouées. */
    private val track: () -> Int = { Inst.SYNTH },
) {
    var armed = false

    /** Les notes en cours, par piste et hauteur (clé : piste × 128 + hauteur) : l'instant de l'appui. */
    private class Attack(val start: Double, val velocity: Float, val slot: Int?, var holds: Int = 1)
    private val pressed = HashMap<Int, Attack>()

    /** [at] : la position de lecture à l'instant de l'appui (par défaut : maintenant) ; [onTrack] : la piste qui reçoit la note. */
    fun press(pitch: Int, at: Double = position(), onTrack: Int = track(), velocity: Float = 1f) {
        if (!armed || !isPlaying() || onTrack < 0 || pitch !in 0..127 || at < -0.5) return
        val id = onTrack * 128 + pitch
        val held = pressed[id]
        if (held == null) pressed[id] = Attack(at.coerceAtLeast(0.0), velocity.coerceIn(0f, 1f), session.recordingSlot(onTrack))
        else held.holds++
    }

    fun release(pitch: Int, at: Double = position(), onTrack: Int = track()): Boolean {
        val id = onTrack * 128 + pitch
        val attack = pressed[id] ?: return false
        if (--attack.holds > 0) return false
        pressed.remove(id)
        write(pitch, onTrack, attack, at)
        return true
    }

    private fun write(pitch: Int, onTrack: Int, attack: Attack, at: Double) {
        val steps = session.patternAt(attack.slot).stepCount
        val step = Math.floorMod(Math.round(attack.start), steps.toLong()).toInt()
        val held = (at - attack.start).coerceIn(0.0, steps.toDouble())
        session.addNote(step, pitch, Math.round(held).toInt().coerceAtLeast(1), attack.velocity, onTrack, attack.slot)
    }

    /** Les notes encore tenues quand l'enregistrement s'arrête sont terminées là. */
    fun finishAll() {
        val end = position()
        for ((key, attack) in pressed) write(key % 128, key / 128, attack, end)
        pressed.clear()
    }
}

/** Lit les messages MIDI à trois octets d'un clavier : note enclenchée ou relâchée (le canal 10, la batterie, est ignoré). */
object MidiNotes {
    fun handle(bytes: ByteArray, onNoteOn: (note: Int, velocity: Float) -> Unit, onNoteOff: (note: Int) -> Unit) {
        if (bytes.size < 3) return
        val status = bytes[0].toInt() and 0xF0
        val channel = bytes[0].toInt() and 0x0F
        if (channel == 9) return
        val note = bytes[1].toInt() and 0x7F
        val velocity = bytes[2].toInt() and 0x7F
        when (status) {
            0x90 -> if (velocity > 0) onNoteOn(note, velocity / 127f) else onNoteOff(note)
            0x80 -> onNoteOff(note)
        }
    }
}
