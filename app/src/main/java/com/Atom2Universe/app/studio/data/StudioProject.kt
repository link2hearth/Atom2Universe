package com.Atom2Universe.app.studio.data

import android.content.Context
import com.Atom2Universe.app.studio.dsp.SynthPatch
import com.Atom2Universe.app.studio.dsp.SynthPresets
import com.Atom2Universe.app.studio.engine.Inst
import com.Atom2Universe.app.studio.engine.Lane
import com.Atom2Universe.app.studio.engine.Note
import com.Atom2Universe.app.studio.engine.StepPattern
import com.Atom2Universe.app.studio.engine.StudioEngine
import com.Atom2Universe.app.studio.engine.TrackMix
import com.Atom2Universe.app.studio.music.Style
import com.Atom2Universe.app.studio.music.Styles
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import kotlin.math.abs

/**
 * Le morceau en cours d'édition. **Immuable** : chaque modification fabrique un nouveau projet (comme le
 * modèle de l'éditeur audio), ce qui rend l'annulation et la sauvegarde triviales plus tard.
 * [pattern] a toujours une ligne par instrument rythmique, dans l'ordre de [Inst] ; le synthé joue les
 * notes de [StepPattern.notes], dans la tonalité [keyPc] et la gamme [scaleId].
 */
class StudioProject(
    val styleId: String,
    val bpm: Int,
    val swing: Float,
    val synth: SynthPatch,
    /** « Ambiance » du synthé : part envoyée à la réverbération et à l'écho. */
    /** Mixage de chaque piste, dans l'ordre de [Inst] : volume, panoramique, envois, effets de piste. */
    val mix: List<TrackMix>,
    val space: Float,
    val echo: Float,
    val muted: Set<Int>,
    val keyPc: Int,
    val scaleId: String,
    /** Les quatre emplacements de motif (A à D), tous de la même longueur. */
    val patterns: List<StepPattern>,
    /** Arpégiateur : mode (voir [com.Atom2Universe.app.studio.engine.Arp]), vitesse (0 = croche, 1 = double-croche, 2 = triple), octaves. */
    val arpMode: Int = 0,
    val arpRate: Int = 1,
    val arpOctaves: Int = 1,
    /** Pistes en solo : si au moins une l'est, seules celles-là s'entendent. */
    val solo: Set<Int> = emptySet(),
    /** Emplacement en cours d'édition. */
    val current: Int = 0,
    /** Le morceau : les emplacements joués l'un après l'autre quand [songMode] est vrai. */
    val chain: List<Int> = emptyList(),
    val songMode: Boolean = false,
) {
    /** Le motif en cours d'édition. */
    val pattern: StepPattern get() = patterns[current]

    val stepCount: Int get() = patterns[0].stepCount

    /** « Ambiance » du synthé : sa part envoyée à la réverbération (l'écho en reçoit les sept dixièmes). */
    val synthSpace: Float get() = mix[Inst.SYNTH].reverbSend

    fun copy(
        styleId: String = this.styleId,
        bpm: Int = this.bpm,
        swing: Float = this.swing,
        synth: SynthPatch = this.synth,
        mix: List<TrackMix> = this.mix,
        space: Float = this.space,
        echo: Float = this.echo,
        muted: Set<Int> = this.muted,
        keyPc: Int = this.keyPc,
        scaleId: String = this.scaleId,
        /** Remplace le motif en cours d'édition. */
        pattern: StepPattern? = null,
        patterns: List<StepPattern> = this.patterns,
        arpMode: Int = this.arpMode,
        arpRate: Int = this.arpRate,
        arpOctaves: Int = this.arpOctaves,
        solo: Set<Int> = this.solo,
        current: Int = this.current,
        chain: List<Int> = this.chain,
        songMode: Boolean = this.songMode,
    ): StudioProject {
        val all = if (pattern != null) patterns.toMutableList().also { it[current] = pattern } else patterns
        return StudioProject(
            styleId, bpm, swing, synth, mix, space, echo, muted, keyPc, scaleId, all, arpMode, arpRate, arpOctaves, solo,
            current, chain, songMode,
        )
    }

    companion object {
        /** Nombre d'emplacements de motif. */
        const val SLOTS = 4

        /** Longueur maximale du morceau. */
        const val MAX_CHAIN = 16

        /** Réglages de mixage d'origine, l'« ambiance » du synthé valant [synthSpace]. */
        fun defaultMix(synthSpace: Float = 0.25f): List<TrackMix> = TrackMix.defaults().toMutableList().also {
            it[Inst.SYNTH] = it[Inst.SYNTH].copy(reverbSend = synthSpace, delaySend = synthSpace * 0.7f)
        }

        fun fromStyle(style: Style, stepCount: Int = 16, seed: Long = style.id.hashCode().toLong()) = StudioProject(
            styleId = style.id,
            bpm = style.bpm,
            swing = style.swing,
            synth = SynthPresets.byId(style.patchId),
            mix = defaultMix(style.space.coerceIn(0.1f, 0.6f)),
            space = style.space,
            echo = style.echo,
            muted = emptySet(),
            keyPc = style.keyPc,
            scaleId = style.scaleId,
            patterns = listOf(Styles.generate(style, stepCount, seed)) + List(SLOTS - 1) { Styles.emptyPattern(stepCount) },
        )
    }
}

/** Lit et écrit un [StudioProject] en JSON. Un texte abîmé ou d'une autre forme donne `null`, jamais une exception. */
object StudioProjectCodec {

    /** Pas de lecture des anciennes versions : un seul testeur, on repart d'un projet neuf. */
    private const val VERSION = 2

    private fun encodePattern(pattern: StepPattern): JSONObject {
        val lanes = JSONArray()
        for (lane in pattern.lanes) {
            lanes.put(
                JSONObject()
                    .put("i", lane.instrument)
                    .put("v", JSONArray().also { a -> lane.velocity.forEach { a.put(it.toDouble()) } }),
            )
        }
        val notes = JSONArray()
        for (n in pattern.notes) {
            notes.put(
                JSONArray().put(n.step).put(n.pitch).put(n.length).put(n.velocity.toDouble()),
            )
        }
        return JSONObject().put("lanes", lanes).put("notes", notes).also { o ->
            // Geste du pad : deux tableaux de 0 à 100 (entiers, pour garder le fichier petit)
            pattern.autoFilter?.let { a -> o.put("autoFilter", JSONArray().also { j -> a.forEach { v -> j.put(Math.round(v * 100f)) } }) }
            pattern.autoSpace?.let { a -> o.put("autoSpace", JSONArray().also { j -> a.forEach { v -> j.put(Math.round(v * 100f)) } }) }
        }
    }

    /** Lit un motif de [steps] pas ; `null` si sa forme est mauvaise. */
    private fun decodePattern(o: JSONObject, steps: Int): StepPattern? {
        val laneArray = o.getJSONArray("lanes")
        if (laneArray.length() != Inst.DRUM_COUNT) return null
        val lanes = ArrayList<Lane>(Inst.DRUM_COUNT)
        for (k in 0 until Inst.DRUM_COUNT) {
            val l = laneArray.getJSONObject(k)
            if (l.getInt("i") != k) return null
            val v = l.getJSONArray("v")
            if (v.length() != steps) return null
            lanes += Lane(k, FloatArray(steps) { v.getDouble(it).toFloat().coerceIn(0f, 1f) })
        }
        val notes = ArrayList<Note>()
        val na = o.getJSONArray("notes")
        for (k in 0 until na.length()) {
            val n = na.getJSONArray(k)
            val step = n.getInt(0)
            if (step !in 0 until steps) return null
            val length = n.getInt(2).coerceIn(1, steps - step)
            notes += Note(step, n.getInt(1).coerceIn(0, 127), length, n.getDouble(3).toFloat().coerceIn(0f, 1f))
        }
        return StepPattern(steps, lanes, notes, readAuto(o.optJSONArray("autoFilter"), steps), readAuto(o.optJSONArray("autoSpace"), steps))
    }

    fun encode(p: StudioProject): String {
        val s = p.synth
        val synth = JSONObject()
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
        return JSONObject()
            .put("version", VERSION)
            .put("style", p.styleId)
            .put("bpm", p.bpm)
            .put("swing", p.swing.toDouble())
            .put("synth", synth)
            .put("mix", JSONArray().also { a ->
                for (m in p.mix) {
                    a.put(
                        JSONArray().put(m.level.toDouble()).put(m.pan.toDouble()).put(m.reverbSend.toDouble())
                            .put(m.delaySend.toDouble()).put(m.drive.toDouble()).put(m.crush.toDouble()).put(m.filter.toDouble()),
                    )
                }
            })
            .put("solo", JSONArray(p.solo.sorted()))
            .put("space", p.space.toDouble())
            .put("echo", p.echo.toDouble())
            .put("muted", JSONArray(p.muted.sorted()))
            .put("key", p.keyPc)
            .put("scale", p.scaleId)
            .put("steps", p.stepCount)
            .put("arpMode", p.arpMode)
            .put("arpRate", p.arpRate)
            .put("arpOctaves", p.arpOctaves)
            .put("patterns", JSONArray().also { a -> p.patterns.forEach { a.put(encodePattern(it)) } })
            .put("current", p.current)
            .put("chain", JSONArray(p.chain))
            .put("songMode", p.songMode)
            .toString()
    }

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
            if (steps != 16 && steps != 32) return null
            // Les quatre motifs ; un projet d'avant les motifs multiples n'avait que celui du dessus (devenu A).
            val patterns = ArrayList<StepPattern>(StudioProject.SLOTS)
            val pa = o.optJSONArray("patterns")
            if (pa != null) {
                if (pa.length() != StudioProject.SLOTS) return null
                for (k in 0 until StudioProject.SLOTS) patterns += decodePattern(pa.getJSONObject(k), steps) ?: return null
            } else {
                patterns += decodePattern(o, steps) ?: return null
                repeat(StudioProject.SLOTS - 1) { patterns += Styles.emptyPattern(steps) }
            }
            val chain = ArrayList<Int>()
            o.optJSONArray("chain")?.let { ca ->
                for (k in 0 until minOf(ca.length(), StudioProject.MAX_CHAIN)) {
                    ca.getInt(k).takeIf { it in 0 until StudioProject.SLOTS }?.let { chain += it }
                }
            }
            val sy = o.getJSONObject("synth")
            val synth = SynthPatch(
                id = sy.getString("id"),
                wave = sy.getInt("wave").coerceIn(0, 3),
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
            // Mixage : à défaut (projet d'avant le mixeur), les réglages d'origine, avec l'ambiance du synthé d'alors.
            val mixArray = o.optJSONArray("mix")
            val mix: List<TrackMix> = if (mixArray != null && mixArray.length() == Inst.COUNT) {
                List(Inst.COUNT) { k ->
                    val a = mixArray.getJSONArray(k)
                    TrackMix(
                        level = a.getDouble(0).toFloat().coerceIn(0f, 1.5f),
                        pan = a.getDouble(1).toFloat().coerceIn(-1f, 1f),
                        reverbSend = a.getDouble(2).toFloat().coerceIn(0f, 1f),
                        delaySend = a.getDouble(3).toFloat().coerceIn(0f, 1f),
                        drive = a.getDouble(4).toFloat().coerceIn(0f, 1f),
                        crush = a.getDouble(5).toFloat().coerceIn(0f, 1f),
                        filter = a.getDouble(6).toFloat().coerceIn(-1f, 1f),
                    )
                }
            } else {
                StudioProject.defaultMix(o.optDouble("synthSpace", 0.25).toFloat().coerceIn(0f, 1f))
            }
            val solo = HashSet<Int>()
            o.optJSONArray("solo")?.let { sa ->
                for (k in 0 until sa.length()) sa.getInt(k).takeIf { it in 0 until Inst.COUNT }?.let { solo += it }
            }
            val muted = HashSet<Int>()
            val m = o.getJSONArray("muted")
            for (k in 0 until m.length()) m.getInt(k).takeIf { it in 0 until Inst.COUNT }?.let { muted += it }
            StudioProject(
                styleId = o.getString("style"),
                bpm = o.getInt("bpm").coerceIn(40, 220),
                swing = o.getDouble("swing").toFloat().coerceIn(0f, 0.5f),
                synth = synth,
                mix = mix,
                space = o.getDouble("space").toFloat().coerceIn(0f, 1f),
                echo = o.getDouble("echo").toFloat().coerceIn(0f, 1f),
                muted = muted,
                keyPc = o.getInt("key").coerceIn(0, 11),
                scaleId = o.getString("scale"),
                patterns = patterns,
                arpMode = o.optInt("arpMode", 0).coerceIn(0, 4),
                arpRate = o.optInt("arpRate", 1).coerceIn(0, 2),
                arpOctaves = o.optInt("arpOctaves", 1).coerceIn(1, 3),
                solo = solo,
                current = o.optInt("current", 0).coerceIn(0, StudioProject.SLOTS - 1),
                chain = chain,
                songMode = o.optBoolean("songMode", false) && chain.isNotEmpty(),
            )
        } catch (_: JSONException) {
            null
        }
    }
}

/** Garde le projet en cours entre deux ouvertures (un seul emplacement pour l'instant). */
class StudioProjectStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("studio_prefs", Context.MODE_PRIVATE)

    fun load(): StudioProject? = StudioProjectCodec.decode(prefs.getString(KEY, null))

    fun save(project: StudioProject) {
        prefs.edit().putString(KEY, StudioProjectCodec.encode(project)).apply()
    }

    private companion object {
        const val KEY = "current_project"
    }
}

/**
 * Les modifications du projet, appliquées au moteur au fur et à mesure. Tout se passe sur le fil de
 * l'interface ; le moteur reçoit chaque nouveau motif d'un coup (référence @Volatile).
 */
class StudioSession(private val engine: StudioEngine, initial: StudioProject) {

    var project: StudioProject = initial
        private set

    /** Le projet d'avant le dernier remplacement en bloc (style, hasard, effacer) : un seul pas en arrière pour l'instant. */
    private var undoSnapshot: StudioProject? = null

    val canUndo: Boolean get() = undoSnapshot != null

    fun undo() {
        val back = undoSnapshot ?: return
        undoSnapshot = null
        project = back
        pushAll()
    }

    init {
        pushAll()
    }

    private fun pushAll() {
        val p = project
        engine.pattern = p.pattern
        pushSong()
        engine.bpm = p.bpm.toFloat()
        engine.swing = p.swing
        engine.spaceAmount = p.space
        engine.echoAmount = p.echo
        engine.synth.patch = p.synth
        engine.applyMix(p.mix)
        pushArp()
        pushMute()
    }

    /** Muet = demandé par l'utilisateur ; en plus, quand une piste est en solo, toutes les autres se taisent. */
    private fun pushMute() {
        val p = project
        for (i in 0 until Inst.COUNT) {
            engine.instruments[i].muted = i in p.muted || (p.solo.isNotEmpty() && i !in p.solo)
        }
    }

    private fun pushArp() {
        engine.arpMode = project.arpMode
        engine.arpDivision = ARP_DIVISIONS[project.arpRate.coerceIn(0, 2)]
        engine.arpOctaves = project.arpOctaves
    }

    fun setArpMode(mode: Int) {
        project = project.copy(arpMode = mode.coerceIn(0, 4))
        pushArp()
    }

    fun setArpRate(rate: Int) {
        project = project.copy(arpRate = rate.coerceIn(0, 2))
        pushArp()
    }

    fun setArpOctaves(octaves: Int) {
        project = project.copy(arpOctaves = octaves.coerceIn(1, 3))
        pushArp()
    }

    // ---- Mixeur ----------------------------------------------------------------------------------------------

    fun track(lane: Int): TrackMix = project.mix[lane]

    /** Change les réglages de mixage d'une piste (volume, panoramique, envois, effets). */
    fun updateTrack(lane: Int, change: (TrackMix) -> TrackMix) {
        val mix = project.mix.toMutableList()
        mix[lane] = change(mix[lane])
        project = project.copy(mix = mix)
        engine.applyMix(project.mix)
    }

    fun toggleSolo(lane: Int) {
        project = project.copy(solo = if (lane in project.solo) project.solo - lane else project.solo + lane)
        pushMute()
    }

    fun isSolo(lane: Int): Boolean = lane in project.solo

    /** Remplace le motif en cours d'édition. */
    private fun setPattern(pattern: StepPattern) {
        project = project.copy(pattern = pattern)
        engine.pattern = pattern
        pushSong()
    }

    /** Le moteur reçoit le morceau déjà résolu en motifs (ou rien : il boucle alors sur le motif en cours). */
    private fun pushSong() {
        val p = project
        engine.song = if (p.songMode && p.chain.isNotEmpty()) Array(p.chain.size) { p.patterns[p.chain[it]] } else null
    }

    // ---- Motifs A à D et morceau --------------------------------------------------------------------------------

    fun selectPattern(slot: Int) {
        if (slot !in 0 until StudioProject.SLOTS || slot == project.current) return
        project = project.copy(current = slot)
        engine.pattern = project.pattern
    }

    fun isPatternEmpty(slot: Int): Boolean {
        val p = project.patterns[slot]
        return p.notes.isEmpty() && p.lanes.all { l -> l.velocity.all { it <= 0f } }
    }

    /** Copie le motif [from] dans l'emplacement [to] (geste du pad compris). */
    fun copyPattern(from: Int, to: Int) {
        if (from == to || from !in 0 until StudioProject.SLOTS || to !in 0 until StudioProject.SLOTS) return
        undoSnapshot = project
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

    fun setSongMode(on: Boolean) {
        project = project.copy(songMode = on && project.chain.isNotEmpty())
        pushSong()
    }

    private fun withLane(lane: Int, change: (Lane) -> Lane) {
        val lanes = project.pattern.lanes.toMutableList()
        lanes[lane] = change(lanes[lane])
        setPattern(project.pattern.copy(lanes = lanes))
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
        undoSnapshot = project
        setPattern(project.pattern.copy(autoFilter = f.takeIf { a -> a.any { it > 0.001f } }, autoSpace = s.takeIf { a -> a.any { it > 0.001f } }))
    }

    fun clearGesture() {
        if (!project.pattern.hasAutomation) return
        undoSnapshot = project
        setPattern(project.pattern.copy(autoFilter = null, autoSpace = null))
    }

    // ---- Grille rythmique (la ligne du synthé en est un résumé : un pas est allumé si une note y commence) ----------

    fun isOn(lane: Int, step: Int): Boolean =
        if (lane == Inst.SYNTH) project.pattern.notes.any { it.step == step }
        else project.pattern.lanes[lane].velocity[step] > 0f

    /** Vélocité d'un pas (0 = éteint) ; pour le synthé, la plus forte des notes qui y commencent. */
    fun velocity(lane: Int, step: Int): Float =
        if (lane == Inst.SYNTH) project.pattern.notes.filter { it.step == step }.maxOfOrNull { it.velocity } ?: 0f
        else project.pattern.lanes[lane].velocity[step]

    fun setStep(lane: Int, step: Int, on: Boolean) {
        if (isOn(lane, step) == on) return
        if (lane == Inst.SYNTH) {
            if (on) {
                withNotes(project.pattern.notes + Note(step, defaultPitch(), 1, LEVELS[0]))
            } else {
                withNotes(project.pattern.notes.filter { it.step != step })
            }
            return
        }
        withLane(lane) { l ->
            val v = l.velocity.copyOf()
            v[step] = if (on) LEVELS[0] else 0f
            Lane(l.instrument, v, l.note, l.gate)
        }
    }

    fun toggleStep(lane: Int, step: Int) = setStep(lane, step, !isOn(lane, step))

    /** Fort → moyen → doux → fort… sur un pas allumé ; rend la nouvelle vélocité (0 si le pas est éteint). */
    fun cycleVelocity(lane: Int, step: Int): Float {
        val current = velocity(lane, step)
        if (current <= 0f) return 0f
        var idx = 0
        for (k in LEVELS.indices) if (abs(LEVELS[k] - current) < abs(LEVELS[idx] - current)) idx = k
        val next = LEVELS[(idx + 1) % LEVELS.size]
        if (lane == Inst.SYNTH) {
            withNotes(project.pattern.notes.map { if (it.step == step) Note(it.step, it.pitch, it.length, next) else it })
        } else {
            withLane(lane) { l ->
                val v = l.velocity.copyOf()
                v[step] = next
                Lane(l.instrument, v, l.note, l.gate)
            }
        }
        return next
    }

    /** Hauteur d'une nouvelle note posée depuis la grille rythmique : la tonique, dans l'octave des notes déjà là. */
    private fun defaultPitch(): Int {
        val notes = project.pattern.notes
        val octave = if (notes.isEmpty()) 24 else notes.minOf { it.pitch } / 12 * 12
        return octave + project.keyPc
    }

    // ---- Notes du synthé -------------------------------------------------------------------------------------

    /** La note qui couvre la case ([step], [pitch]), s'il y en a une. */
    fun noteAt(step: Int, pitch: Int): Note? =
        project.pattern.notes.firstOrNull { it.pitch == pitch && step >= it.step && step < it.step + it.length }

    /** Pose une note ; ce qu'elle recouvre à la même hauteur disparaît (le moteur relâche une note par sa hauteur). */
    fun addNote(step: Int, pitch: Int, length: Int, velocity: Float = LEVELS[0]) {
        if (step !in 0 until project.stepCount) return
        val len = length.coerceIn(1, project.stepCount - step)
        val kept = project.pattern.notes.filter { n ->
            !(n.pitch == pitch && n.step < step + len && n.step + n.length > step)
        }
        withNotes(kept + Note(step, pitch.coerceIn(0, 127), len, velocity))
    }

    fun removeNote(note: Note) = withNotes(project.pattern.notes.filter { it !== note })

    fun resizeNote(note: Note, length: Int) {
        val len = length.coerceIn(1, project.stepCount - note.step)
        val others = project.pattern.notes.filter { it !== note }
        val kept = others.filter { n ->
            !(n.pitch == note.pitch && n.step < note.step + len && n.step + n.length > note.step)
        }
        withNotes(kept + Note(note.step, note.pitch, len, note.velocity))
    }

    /** Change de tonalité : les notes se transposent par le plus court chemin (de −5 à +6 demi-tons). */
    fun setKey(keyPc: Int) {
        val k = ((keyPc % 12) + 12) % 12
        var delta = k - project.keyPc
        if (delta > 6) delta -= 12
        if (delta < -5) delta += 12
        val moved = project.pattern.notes.map { Note(it.step, (it.pitch + delta).coerceIn(0, 127), it.length, it.velocity) }
        project = project.copy(keyPc = k)
        withNotes(moved)
    }

    fun setScale(id: String) {
        project = project.copy(scaleId = id)
    }

    // ---- Son du synthé -----------------------------------------------------------------------------------------

    fun setPreset(id: String) {
        project = project.copy(synth = SynthPresets.byId(id))
        engine.synth.patch = project.synth
    }

    fun updateSynth(change: (SynthPatch) -> SynthPatch) {
        project = project.copy(synth = change(project.synth))
        engine.synth.patch = project.synth
    }

    fun setSynthSpace(v: Float) {
        val space = v.coerceIn(0f, 1f)
        updateTrack(Inst.SYNTH) { it.copy(reverbSend = space, delaySend = space * 0.7f) }
    }

    // ---- Ensemble du morceau -----------------------------------------------------------------------------------

    /** 16 ou 32 pas : on double en répétant le motif, on réduit en gardant le début. */
    fun setStepCount(count: Int) {
        if (count == project.stepCount || (count != 16 && count != 32)) return
        val old = project.stepCount
        // Tous les motifs changent de longueur ensemble (le morceau les enchaîne : ils restent de même longueur)
        val resized = project.patterns.map { resize(it, old, count) }
        project = project.copy(patterns = resized)
        engine.pattern = project.pattern
        pushSong()
    }

    private fun resize(source: StepPattern, old: Int, count: Int): StepPattern {
        val lanes = source.lanes.map { l ->
            Lane(l.instrument, FloatArray(count) { l.velocity[it % old] }, IntArray(count) { l.note[it % old] }, l.gate)
        }
        val notes = ArrayList<Note>()
        if (count > old) {
            for (n in source.notes) {
                notes += n
                notes += Note(n.step + old, n.pitch, n.length, n.velocity)
            }
        } else {
            for (n in source.notes) if (n.step < count) {
                notes += Note(n.step, n.pitch, minOf(n.length, count - n.step), n.velocity)
            }
        }
        fun resizeAuto(a: FloatArray?): FloatArray? =
            a?.takeIf { it.size == old * StepPattern.AUTO_RES }?.let { src -> FloatArray(count * StepPattern.AUTO_RES) { src[it % src.size] } }
        return StepPattern(count, lanes, notes, resizeAuto(source.autoFilter), resizeAuto(source.autoSpace))
    }

    /** Charge un style de départ : tempo, swing, son, tonalité, effets et motif. */
    fun applyStyle(style: Style, seed: Long = style.id.hashCode().toLong()) {
        undoSnapshot = project
        // L'arpégiateur est un réglage de jeu, pas de style : il reste comme il était.
        project = StudioProject.fromStyle(style, project.stepCount, seed)
            // Le mixage et le jeu (arpège, solo) sont à l'utilisateur, pas au style : ils restent comme ils étaient.
            .let { fresh ->
                // Le style remplace seulement le motif en cours : les autres motifs et le morceau restent.
                fresh.copy(
                    patterns = project.patterns.toMutableList().also { it[project.current] = fresh.pattern },
                    current = project.current, chain = project.chain, songMode = project.songMode,
                    arpMode = project.arpMode, arpRate = project.arpRate, arpOctaves = project.arpOctaves,
                    mix = project.mix, solo = project.solo, muted = project.muted,
                )
            }
        pushAll()
    }

    /** Hasard : un nouveau motif dans le style en cours (tempo, son et effets inchangés). */
    fun randomize(seed: Long) {
        val style = Styles.byId(project.styleId)
        undoSnapshot = project
        // Les notes suivent la tonalité et la gamme choisies, pas celles du style d'origine.
        val generated = Styles.generate(
            Style(
                style.id, style.bpm, style.swing, style.patchId, style.space, style.echo, style.drums, style.synthSteps,
                24 + project.keyPc + (style.synthRoot / 12 * 12 - 24), style.synthLength, project.scaleId, style.chord,
            ),
            project.stepCount, seed,
        )
        // Le geste enregistré reste : c'est un réglage de jeu, comme le mixage.
        setPattern(generated.copy(autoFilter = project.pattern.autoFilter, autoSpace = project.pattern.autoSpace))
    }

    fun clear() {
        undoSnapshot = project
        setPattern(Styles.emptyPattern(project.stepCount))
    }

    fun setMuted(lane: Int, muted: Boolean) {
        project = project.copy(muted = if (muted) project.muted + lane else project.muted - lane)
        pushMute()
    }

    fun isMuted(lane: Int): Boolean = lane in project.muted

    fun setBpm(bpm: Int) {
        project = project.copy(bpm = bpm.coerceIn(40, 220))
        engine.bpm = project.bpm.toFloat()
    }

    fun setSwing(swing: Float) {
        project = project.copy(swing = swing.coerceIn(0f, 0.5f))
        engine.swing = project.swing
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
) {
    var armed = false

    private val pressed = HashMap<Int, Double>()

    /** [at] : la position de lecture à l'instant de l'appui (par défaut : maintenant). */
    fun press(pitch: Int, at: Double = position()) {
        if (armed && isPlaying()) pressed[pitch] = at
    }

    fun release(pitch: Int, at: Double = position()) {
        val start = pressed.remove(pitch) ?: return
        val steps = session.project.stepCount
        val step = Math.floorMod(Math.round(start).toInt(), steps)
        val held = (at - start).coerceAtLeast(0.0)
        session.addNote(step, pitch, Math.round(held).toInt().coerceAtLeast(1))
    }

    /** Les notes encore tenues quand l'enregistrement s'arrête sont terminées là. */
    fun finishAll() {
        for (pitch in pressed.keys.toList()) release(pitch)
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
