package com.Atom2Universe.app.studio.engine

import com.Atom2Universe.app.studio.dsp.Clap
import com.Atom2Universe.app.studio.dsp.ExternalSynth
import com.Atom2Universe.app.studio.dsp.Fast
import com.Atom2Universe.app.studio.dsp.Hat
import com.Atom2Universe.app.studio.dsp.Instrument
import com.Atom2Universe.app.studio.dsp.Kick
import com.Atom2Universe.app.studio.dsp.Perc
import com.Atom2Universe.app.studio.dsp.PingPongDelay
import com.Atom2Universe.app.studio.dsp.PolySynth
import com.Atom2Universe.app.studio.dsp.Sf2Instrument
import com.Atom2Universe.app.studio.dsp.Snare
import com.Atom2Universe.app.studio.dsp.StereoReverb
import com.Atom2Universe.app.studio.dsp.Tom
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/** Modes de l'arpégiateur : il joue une à une, en boucle, les notes tenues. */
object Arp {
    const val OFF = 0
    const val UP = 1
    const val DOWN = 2
    const val UP_DOWN = 3
    const val RANDOM = 4
}

/** Les sept sons de batterie du Studio et le synthé d'origine ; ce sont aussi les numéros des pistes d'un projet neuf. */
object Inst {
    const val KICK = 0
    const val SNARE = 1
    const val CLAP = 2
    const val HAT = 3
    const val OPEN_HAT = 4
    const val TOM = 5
    const val PERC = 6
    const val SYNTH = 7

    /** Les sept sons rythmiques, qui ont chacun une ligne de pas (le synthé joue des notes, pas des pas). */
    const val DRUM_COUNT = 7

    /** Pistes d'un projet neuf. */
    const val COUNT = 8
}

/** Le genre d'une piste : un son de batterie (une ligne de pas), le synthé maison, ou un instrument de soundfont (des notes). */
object TrackKind {
    const val DRUM = 0
    const val SYNTH = 1
    const val SF2 = 2
}

/**
 * Ce que le moteur doit savoir d'une piste pour fabriquer son instrument. [id] (0 à [StudioEngine.MAX_TRACKS] − 1) relie la
 * piste à ses pas et à ses notes ; [voice] est le son de batterie ([Inst]) pour une piste rythmique ; [bank] et [program]
 * choisissent l'instrument d'une piste soundfont.
 */
data class TrackSpec(val id: Int, val kind: Int, val voice: Int = 0, val bank: Int = 0, val program: Int = 0) {
    val melodic: Boolean get() = kind != TrackKind.DRUM

    companion object {
        /** Les huit pistes d'un projet neuf. */
        fun defaults(): List<TrackSpec> =
            List(Inst.DRUM_COUNT) { TrackSpec(it, TrackKind.DRUM, it) } + TrackSpec(Inst.SYNTH, TrackKind.SYNTH)
    }
}

/**
 * Une ligne rythmique : pour chaque pas, une vélocité (0 = silence). [instrument] est le numéro de la piste. Le tableau
 * [note] ne sert qu'aux instruments qui jouent une hauteur (aucun pour l'instant) ; [gate] est gardé pour les évolutions.
 */
class Lane(
    val instrument: Int,
    val velocity: FloatArray,
    val note: IntArray = IntArray(velocity.size) { 60 },
    val gate: Float = 0.9f,
)

/**
 * Une note d'une piste mélodique ([track]) : elle commence au pas [step], dure [length] pas, à la hauteur MIDI [pitch].
 * Plusieurs notes peuvent commencer au même pas (accord).
 */
class Note(val step: Int, val pitch: Int, val length: Int = 1, val velocity: Float = 1f, val track: Int = Inst.SYNTH)

/**
 * Un motif de [stepCount] doubles-croches (seize pas = une mesure en 4/4) : [lanes] pour les pistes rythmiques,
 * [notes] pour les pistes mélodiques. Immuable : on en fabrique un nouveau pour le modifier.
 */
class StepPattern(
    val stepCount: Int,
    val lanes: List<Lane>,
    val notes: List<Note> = emptyList(),
    /** Geste du pad enregistré dans la boucle : [AUTO_RES] valeurs par pas, de 0 à 1 (null = rien d'enregistré). */
    val autoFilter: FloatArray? = null,
    val autoSpace: FloatArray? = null,
) {
    fun copy(
        lanes: List<Lane> = this.lanes,
        notes: List<Note> = this.notes,
        autoFilter: FloatArray? = this.autoFilter,
        autoSpace: FloatArray? = this.autoSpace,
    ) = StepPattern(stepCount, lanes, notes, autoFilter, autoSpace)

    val hasAutomation: Boolean get() = autoFilter != null || autoSpace != null

    /** La ligne de la piste [track], s'il y en a une. */
    fun lane(track: Int): Lane? {
        for (i in 0 until lanes.size) if (lanes[i].instrument == track) return lanes[i]
        return null
    }

    /** Filtre maître enregistré à la position [pos] (en pas), ou rien. */
    fun filterAt(pos: Double): Float? = sample(autoFilter, pos)

    /** Réverbération en plus enregistrée à la position [pos] (en pas), ou rien. */
    fun spaceAt(pos: Double): Float? = sample(autoSpace, pos)

    private fun sample(a: FloatArray?, pos: Double): Float? {
        if (a == null || a.size != stepCount * AUTO_RES) return null
        val p = ((pos % stepCount) + stepCount) % stepCount * AUTO_RES
        val i0 = p.toInt().coerceIn(0, a.size - 1)
        val f = (p - i0).toFloat()
        return a[i0] * (1f - f) + a[(i0 + 1) % a.size] * f
    }

    companion object {
        /** Points d'automation par pas. */
        const val AUTO_RES = 4
    }
}

/**
 * Le moteur du Studio : séquenceur sample-accurate + pistes + mixage + limiteur.
 *
 * Tout se passe dans [render], appelé par le fil audio. L'horloge compte des **échantillons**, pas des
 * millisecondes : une frappe tombe au même échantillon quelle que soit la taille des blocs. Le bloc est
 * coupé en morceaux aux instants des événements (pas de séquenceur, fin de note), ce qui donne un
 * rythme exact, swing compris.
 *
 * Les pistes sont libres ([setTracks]) : chacune a son numéro, son instrument et, si elle joue des notes, son propre
 * arpégiateur. Les autres fils ne touchent au moteur que par les champs @Volatile et par la file de commandes.
 */
class StudioEngine(val sampleRate: Int) {

    /** Une piste en marche : son instrument et, si elle joue des notes, l'état de ses notes tenues et de son arpège. */
    private inner class Slot(val spec: TrackSpec, val instrument: Instrument, val melody: Melody?)

    @Volatile private var table: Array<Slot?> = arrayOfNulls(MAX_TRACKS)

    /** Fabrique le synthé d'une piste soundfont (le lecteur SF2 de l'appli) ; `null` : la piste reste muette. */
    @Volatile var sf2Factory: ((sampleRate: Int, bank: Int, program: Int) -> ExternalSynth?)? = null

    /** Le son enregistré au micro pour les pistes de percussion (`null` : retour à la cloche d'origine). */
    private var percData: FloatArray? = null
    private var percRate = 48_000

    private val reverb = StereoReverb(sampleRate)
    private val delay = PingPongDelay(sampleRate)
    private val masterL = com.Atom2Universe.app.studio.dsp.Svf(sampleRate)
    private val masterR = com.Atom2Universe.app.studio.dsp.Svf(sampleRate)
    private var masterFilterNow = 0f
    private var spaceBoostNow = 0f
    private val reverbBus = FloatArray(MAX_BLOCK)
    private val delayBus = FloatArray(MAX_BLOCK)

    /** L'instrument de la piste [id] (`null` s'il n'y en a pas). */
    fun instrument(id: Int): Instrument? = table.getOrNull(id)?.instrument

    /** Le synthé de la piste [id], si c'est une piste de synthé. */
    fun synthOf(id: Int): PolySynth? = instrument(id) as? PolySynth

    /** Le synthé d'origine (piste [Inst.SYNTH]) : sert surtout aux tests. */
    val synth: PolySynth get() = synthOf(Inst.SYNTH)!!

    /** Les pistes du moteur deviennent [specs] : celles qui ne changent pas gardent leur instrument (et leurs notes qui sonnent). */
    fun setTracks(specs: List<TrackSpec>) {
        val old = table
        val next = arrayOfNulls<Slot>(MAX_TRACKS)
        for (spec in specs) {
            if (spec.id !in 0 until MAX_TRACKS || next[spec.id] != null) continue
            val prev = old[spec.id]
            next[spec.id] = if (prev != null && prev.spec.kind == spec.kind && prev.spec.voice == spec.voice) {
                val ins = prev.instrument
                if (ins is Sf2Instrument && (prev.spec.bank != spec.bank || prev.spec.program != spec.program)) {
                    ins.setProgram(spec.bank, spec.program)
                }
                Slot(spec, ins, prev.melody)
            } else {
                create(spec)
            }
        }
        table = next
    }

    private fun create(spec: TrackSpec): Slot = when (spec.kind) {
        TrackKind.SYNTH -> PolySynth(sampleRate).let { Slot(spec, it, Melody(it)) }
        TrackKind.SF2 -> {
            val source = sf2Factory?.invoke(sampleRate, spec.bank, spec.program)
            val ins = Sf2Instrument(sampleRate, source)
            ins.setProgram(spec.bank, spec.program)
            Slot(spec, ins, Melody(ins))
        }
        else -> Slot(spec, drum(spec.voice), null)
    }

    private fun drum(voice: Int): Instrument = when (voice) {
        Inst.KICK -> Kick(sampleRate)
        Inst.SNARE -> Snare(sampleRate)
        Inst.CLAP -> Clap(sampleRate)
        Inst.HAT -> Hat(sampleRate, open = false)
        Inst.OPEN_HAT -> Hat(sampleRate, open = true)
        Inst.TOM -> Tom(sampleRate)
        else -> Perc(sampleRate).also { it.setSample(percData, percRate) }
    }

    /** Vrai si les pistes de percussion jouent un son enregistré au micro. */
    val hasPercSample: Boolean get() = percData != null

    /** Le son enregistré au micro pour les pistes de percussion (`null` : retour à la cloche d'origine). */
    fun setPercSample(data: FloatArray?, sourceRate: Int) {
        percData = data
        percRate = sourceRate
        for (s in table) (s?.instrument as? Perc)?.setSample(data, sourceRate)
    }

    /** Réglages d'arpège de la piste [id] ; sans effet sur une piste de batterie. */
    fun setArp(id: Int, mode: Int, division: Float, octaves: Int) {
        val m = table.getOrNull(id)?.melody ?: return
        m.arpDivision = division
        m.arpOctaves = octaves
        m.arpMode = mode
    }

    /** Applique le mixage d'une piste. */
    fun applyMix(id: Int, m: TrackMix) {
        val ins = instrument(id) ?: return
        ins.level = m.level
        ins.pan = m.pan
        ins.reverbSend = m.reverbSend
        ins.delaySend = m.delaySend
        ins.drive = m.drive
        ins.crush = m.crush
        ins.filterSweep = m.filter
    }

    /** Muet ou non, pour la piste [id]. */
    fun setMuted(id: Int, muted: Boolean) {
        instrument(id)?.muted = muted
    }

    @Volatile var pattern: StepPattern = StepPattern(16, emptyList())

    /**
     * Le morceau : une suite de motifs de même longueur, joués l'un après l'autre (à chaque tour de boucle), puis on
     * recommence. `null` : on boucle simplement sur [pattern].
     */
    @Volatile var song: Array<StepPattern>? = null

    /** Rang, dans [song], du motif en cours (pour l'affichage). */
    @Volatile var songPosition: Int = 0
        private set
    @Volatile var bpm: Float = 120f
    @Volatile var swing: Float = 0f
    @Volatile var masterGain: Float = 0.8f

    /** Quantité de réverbération et d'écho renvoyée dans le mixage (0 à 1). */
    @Volatile var spaceAmount: Float = 0.25f
    @Volatile var echoAmount: Float = 0.2f

    // Gestes de jeu (pad XY) : ni sauvegardés ni mémorisés, le moteur les lisse pour ne produire aucun « zip ».
    /** 0 = filtre maître ouvert, 1 = très fermé (son étouffé). */
    @Volatile var masterFilter: Float = 0f

    /** Réverbération en plus de [spaceAmount], de 0 à 1. */
    @Volatile var spaceBoost: Float = 0f

    /** Vrai tant qu'un doigt tient le pad : ses valeurs priment alors sur le geste enregistré. */
    @Volatile var padActive: Boolean = false

    /** Pas en cours de lecture (pour l'affichage). */
    @Volatile var currentStep: Int = -1
        private set

    @Volatile var playing: Boolean = false
        private set

    /** Plus haut niveau de sortie depuis la dernière lecture de [takePeak]. */
    @Volatile private var peak = 0f

    /** Échantillons dont le niveau avant limiteur a dépassé 1 (le limiteur les a adoucis). */
    @Volatile var limitedSamples = 0L
        private set

    /** Pour les tests : appelé à chaque pas joué avec son numéro et l'échantillon exact. */
    var onStep: ((step: Int, frame: Long) -> Unit)? = null

    /** Pour les tests : appelé à chaque note qui part réellement vers un instrument (après l'arpégiateur), avec son échantillon. */
    var onSynthNote: ((pitch: Int, frame: Long) -> Unit)? = null

    /** Position de lecture en pas, avec décimales (0 à l'arrêt) : sert à placer les notes jouées en direct. */
    @Volatile var playPosition: Double = 0.0
        private set

    val activeVoices: Int
        get() {
            var n = 0
            for (s in table) if (s != null) n += s.instrument.activeVoices
            return n
        }

    fun takePeak(): Float {
        val p = peak
        peak = 0f
        return p
    }

    // ---- Commandes venant des autres fils (une file à un producteur, un consommateur) ------------

    private val queue = LongArray(QUEUE_SIZE)
    @Volatile private var head = 0
    @Volatile private var tail = 0

    private val pushLock = Any()

    /** Plusieurs fils peuvent envoyer (interface, entrée MIDI) : un verrou entre eux ; le fil audio, lui, ne verrouille jamais. */
    private fun push(kind: Int, a: Int, b: Int, f: Float) {
        synchronized(pushLock) {
            val t = tail
            val next = (t + 1) % QUEUE_SIZE
            if (next == head) return // pleine : on perd la commande plutôt que de bloquer
            queue[t] = kind.toLong() or (a.toLong() shl 8) or (b.toLong() shl 24) or
                (java.lang.Float.floatToRawIntBits(f).toLong() shl 32)
            tail = next
        }
    }

    fun play() = push(CMD_PLAY, 0, 0, 0f)
    fun stop() = push(CMD_STOP, 0, 0, 0f)

    /** Joue la piste [track] tout de suite (au début du prochain bloc) : pads tactiles, clavier. */
    fun trigger(track: Int, note: Int = 60, velocity: Float = 1f) = push(CMD_TRIGGER, track, note, velocity)

    fun release(track: Int, note: Int) = push(CMD_RELEASE, track, note, 0f)

    // ---- État du séquenceur (fil audio seulement) ----------------------------------------------

    private var framesRendered = 0L
    private var running = false
    private var stepIndex = 0
    private var straightTime = 0.0
    private var nextStepTime = 0.0

    private var playOrigin = 0L
    private var lastFireFrame = 0L

    private val offFrame = LongArray(MAX_PENDING_OFFS)
    private val offNote = IntArray(MAX_PENDING_OFFS)
    private val offTrack = IntArray(MAX_PENDING_OFFS)
    private var offCount = 0

    init {
        setTracks(TrackSpec.defaults())
        val mix = TrackMix.defaults()
        for (i in 0 until Inst.COUNT) applyMix(i, mix[i])
    }

    /**
     * Remplit [left] et [right] avec [n] trames (n ≤ taille des tableaux). Pas d'allocation, pas de verrou.
     */
    fun render(left: FloatArray, right: FloatArray, n: Int) {
        // Les bus d'envoi ont une taille fixe : un très grand bloc est traité en tranches (même résultat).
        var done = 0
        while (done < n) {
            val c = min(MAX_BLOCK, n - done)
            if (done == 0 && c == n) {
                renderBlock(left, right, n)
            } else {
                // renderBlock écrit en tête de tableau : on passe par des vues de travail réutilisées
                System.arraycopy(left, done, tmpL, 0, c)
                System.arraycopy(right, done, tmpR, 0, c)
                renderBlock(tmpL, tmpR, c)
                System.arraycopy(tmpL, 0, left, done, c)
                System.arraycopy(tmpR, 0, right, done, c)
            }
            done += c
        }
    }

    private val tmpL = FloatArray(MAX_BLOCK)
    private val tmpR = FloatArray(MAX_BLOCK)

    private fun renderBlock(left: FloatArray, right: FloatArray, n: Int) {
        val blockStart = framesRendered
        drainCommands(blockStart)
        for (s in table) {
            val m = s?.melody ?: continue
            if (m.arpMode != m.lastArpMode) m.onArpModeChanged()
        }
        java.util.Arrays.fill(left, 0, n, 0f)
        java.util.Arrays.fill(right, 0, n, 0f)
        java.util.Arrays.fill(reverbBus, 0, n, 0f)
        java.util.Arrays.fill(delayBus, 0, n, 0f)

        var pos = 0
        while (true) {
            val ev = nextEventRelative(blockStart, pos, n)
            if (ev >= n) {
                renderInstruments(left, right, pos, n)
                break
            }
            if (ev > pos) {
                renderInstruments(left, right, pos, ev)
                pos = ev
            }
            fireDue(blockStart + pos)
        }

        // Retours d'effets (l'écho suit le tempo : une croche pointée), puis mixage final.
        // Le doigt sur le pad l'emporte ; sinon, pendant la lecture, c'est le geste enregistré dans le motif.
        var filterTarget = masterFilter
        var boostTarget = spaceBoost
        if (!padActive && running) {
            val pat = activePattern()
            pat.filterAt(playPosition)?.let { filterTarget = it }
            pat.spaceAt(playPosition)?.let { boostTarget = it }
        }
        spaceBoostNow += (boostTarget.coerceIn(0f, 1f) - spaceBoostNow) * 0.2f
        val space = (spaceAmount.coerceIn(0f, 1f) + spaceBoostNow * 0.8f).coerceAtMost(1f)
        reverb.process(reverbBus, left, right, 0, n, space * REVERB_RETURN)
        delay.delaySeconds = 60f / max(bpm, 20f) * 0.75f
        delay.process(delayBus, left, right, 0, n, echoAmount.coerceIn(0f, 1f) * DELAY_RETURN)

        // Filtre maître (geste du pad XY) : passe-bas qui se ferme, lissé d'un bloc à l'autre
        masterFilterNow += (filterTarget.coerceIn(0f, 1f) - masterFilterNow) * 0.2f
        if (masterFilterNow > 0.004f) {
            val cutoff = (18000.0 * Math.pow(150.0 / 18000.0, masterFilterNow.toDouble())).toFloat()
            masterL.set(cutoff, 1.1f)
            masterR.set(cutoff, 1.1f)
            for (i in 0 until n) {
                masterL.tick(left[i])
                masterR.tick(right[i])
                left[i] = masterL.lp
                right[i] = masterR.lp
            }
        } else {
            masterL.reset()
            masterR.reset()
        }

        // Mixage final : gain maître puis limiteur doux (jamais de plat dur, jamais au-dessus de 1).
        val gain = masterGain
        var blockPeak = 0f
        var limited = 0
        for (i in 0 until n) {
            val l = left[i] * gain
            val r = right[i] * gain
            if (abs(l) > 1f || abs(r) > 1f) limited++
            val ol = Fast.softLimit(l)
            val outR = Fast.softLimit(r)
            left[i] = ol
            right[i] = outR
            val a = max(abs(ol), abs(outR))
            if (a > blockPeak) blockPeak = a
        }
        if (blockPeak > peak) peak = blockPeak
        if (limited > 0) limitedSamples += limited
        framesRendered = blockStart + n
        playPosition = if (running && stepIndex > 0) {
            val stepFrames = sampleRate * 60.0 / max(bpm, 20f) / 4.0
            (stepIndex - 1) + ((blockStart + n - lastFireFrame) / stepFrames).coerceIn(0.0, 1.0)
        } else 0.0
    }

    private fun renderInstruments(left: FloatArray, right: FloatArray, from: Int, to: Int) {
        if (to <= from) return
        val t = table
        for (i in 0 until t.size) t[i]?.instrument?.render(left, right, reverbBus, delayBus, from, to)
    }

    private fun drainCommands(blockStart: Long) {
        var h = head
        while (h != tail) {
            val v = queue[h]
            h = (h + 1) % QUEUE_SIZE
            val kind = (v and 0xFF).toInt()
            val a = ((v ushr 8) and 0xFFFF).toInt()
            val b = ((v ushr 24) and 0xFF).toInt()
            val f = java.lang.Float.intBitsToFloat((v ushr 32).toInt())
            when (kind) {
                CMD_PLAY -> startSequencer(blockStart)
                CMD_STOP -> stopSequencer()
                CMD_TRIGGER -> table.getOrNull(a)?.let { s -> if (s.melody != null) s.melody.on(b, f, blockStart) else fire(s, b, f) }
                CMD_RELEASE -> table.getOrNull(a)?.let { s -> if (s.melody != null) s.melody.off(b) else s.instrument.noteOff(b) }
            }
        }
        head = h
    }

    /** Le motif qui joue en ce moment : celui du morceau, ou le motif unique en boucle. */
    private fun activePattern(): StepPattern {
        val sg = song
        return if (sg != null && sg.isNotEmpty()) sg[songPosition.coerceIn(0, sg.size - 1)] else pattern
    }

    private fun startSequencer(blockStart: Long) {
        songPosition = 0
        running = true
        playing = true
        stepIndex = 0
        playOrigin = blockStart
        straightTime = blockStart.toDouble()
        nextStepTime = straightTime
    }

    private fun stopSequencer() {
        running = false
        playing = false
        currentStep = -1
        // Les notes tenues se relâchent, la batterie finit de sonner d'elle-même.
        for (i in 0 until offCount) table.getOrNull(offTrack[i])?.melody?.off(offNote[i])
        offCount = 0
    }

    /** Position, relative au bloc, du prochain événement à partir de [pos] ; [n] s'il n'y en a pas dans ce bloc. */
    private fun nextEventRelative(blockStart: Long, pos: Int, n: Int): Int {
        var t = Long.MAX_VALUE
        if (running) t = frameOf(nextStepTime)
        for (i in 0 until offCount) if (offFrame[i] < t) t = offFrame[i]
        val tb = table
        for (i in 0 until tb.size) {
            val m = tb[i]?.melody ?: continue
            val e = m.nextEvent()
            if (e < t) t = e
        }
        if (t == Long.MAX_VALUE) return n
        val rel = t - blockStart
        return if (rel >= n) n else max(rel.toInt(), pos)
    }

    /** Premier échantillon entier à partir de l'instant [t] ; la marge d un millième d échantillon évite qu un 7200,00002 (swing 0,2 en Float) devienne 7201. */
    private fun frameOf(t: Double): Long = ceil(t - 1e-3).toLong()

    private fun fireDue(frame: Long) {
        val tb = table
        for (i in 0 until tb.size) tb[i]?.melody?.arpNoteOffDue(frame)
        var i = 0
        while (i < offCount) {
            if (offFrame[i] <= frame) {
                table.getOrNull(offTrack[i])?.melody?.off(offNote[i])
                offCount--
                offFrame[i] = offFrame[offCount]
                offNote[i] = offNote[offCount]
                offTrack[i] = offTrack[offCount]
            } else {
                i++
            }
        }
        if (running && frameOf(nextStepTime) <= frame) fireStep(frame)
        // Après le pas : une note du motif qui commence à cet instant doit déjà compter dans l'arpège.
        for (k in 0 until tb.size) tb[k]?.melody?.arpTickDue(frame)
    }

    private fun fireStep(frame: Long) {
        val step = stepIndex
        val sg = song
        if (sg != null && sg.isNotEmpty()) {
            // Au début de chaque tour, on passe au motif suivant du morceau
            if (step > 0 && step % max(sg[0].stepCount, 1) == 0) songPosition = (songPosition + 1) % sg.size
        } else {
            songPosition = 0
        }
        val p = activePattern()
        val s = step % max(p.stepCount, 1)
        val stepFrames = sampleRate * 60.0 / max(bpm, 20f) / 4.0

        onStep?.invoke(step, frame)
        currentStep = s
        lastFireFrame = frame

        // Boucles à indices : aucune allocation d'itérateur dans le fil audio.
        val lanes = p.lanes
        for (i in 0 until lanes.size) {
            val lane = lanes[i]
            if (s >= lane.velocity.size) continue
            val v = lane.velocity[s]
            if (v <= 0f) continue
            val slot = table.getOrNull(lane.instrument) ?: continue
            if (slot.melody == null) fire(slot, lane.note[s], v)
        }
        val notes = p.notes
        for (i in 0 until notes.size) {
            val n = notes[i]
            if (n.step != s) continue
            val m = table.getOrNull(n.track)?.melody ?: continue
            m.on(n.pitch, n.velocity, frame)
            // La note est relâchée juste avant la fin de sa durée : deux notes de même hauteur collées se séparent.
            val held = (stepFrames * n.length * NOTE_GATE).roundToLong()
            scheduleOff(frame + max(1L, held), n.track, n.pitch)
        }

        straightTime += stepFrames
        stepIndex = step + 1
        val swingOffset = if (stepIndex % 2 == 1) min(max(swing, 0f), 0.5f) * stepFrames else 0.0
        nextStepTime = straightTime + swingOffset
    }

    private fun fire(slot: Slot, note: Int, velocity: Float) {
        // Un charleston fermé coupe les charlestons ouverts.
        if (slot.spec.voice == Inst.HAT) {
            val t = table
            for (i in 0 until t.size) {
                val o = t[i] ?: continue
                if (o.melody == null && o.spec.voice == Inst.OPEN_HAT) o.instrument.choke()
            }
        }
        slot.instrument.noteOn(note, velocity)
    }

    private fun scheduleOff(frame: Long, track: Int, note: Int) {
        if (offCount >= MAX_PENDING_OFFS) {
            table.getOrNull(track)?.melody?.off(note)
            return
        }
        offFrame[offCount] = frame
        offNote[offCount] = note
        offTrack[offCount] = track
        offCount++
    }

    /**
     * Les notes d'une piste mélodique : directement vers l'instrument, ou seulement « tenues » quand l'arpégiateur de la
     * piste les joue une à une sur la grille du tempo.
     */
    private inner class Melody(val inst: Instrument) {
        @Volatile var arpMode: Int = Arp.OFF

        /** Durée d'un coup d'arpège en pas : 2 = croche, 1 = double-croche, 0,5 = triple-croche. */
        @Volatile var arpDivision: Float = 1f
        @Volatile var arpOctaves: Int = 1
        @Volatile var arpGate: Float = 0.55f

        var lastArpMode = Arp.OFF
        private val held = IntArray(MAX_HELD)
        private val heldVel = FloatArray(MAX_HELD)
        private var heldCount = 0
        private val arpBase = IntArray(MAX_HELD * 3)
        private var arpIndex = 0
        private var arpNextTime = 0.0
        private var arpSounding = -1
        private var arpOffFrame = Long.MAX_VALUE
        private var arpRandom = 0x2545F491

        fun on(pitch: Int, velocity: Float, frame: Long) {
            if (arpMode == Arp.OFF) {
                inst.noteOn(pitch, velocity)
                onSynthNote?.invoke(pitch, frame)
                return
            }
            for (i in 0 until heldCount) if (held[i] == pitch) {
                heldVel[i] = velocity
                return
            }
            if (heldCount >= MAX_HELD) return
            if (heldCount == 0) {
                arpIndex = 0
                arpNextTime = if (running) alignedArpTime(frame) else frame.toDouble()
            }
            held[heldCount] = pitch
            heldVel[heldCount] = velocity
            heldCount++
        }

        fun off(pitch: Int) {
            if (arpMode == Arp.OFF) {
                inst.noteOff(pitch)
                return
            }
            for (i in 0 until heldCount) if (held[i] == pitch) {
                heldCount--
                held[i] = held[heldCount]
                heldVel[i] = heldVel[heldCount]
                return
            }
        }

        /** Le prochain échantillon où cette piste a quelque chose à faire (fin d'un coup d'arpège, coup suivant), ou le maximum. */
        fun nextEvent(): Long {
            var t = arpOffFrame
            if (arpMode != Arp.OFF && heldCount > 0) {
                val a = frameOf(arpNextTime)
                if (a < t) t = a
            }
            return t
        }

        fun arpNoteOffDue(frame: Long) {
            if (arpSounding >= 0 && arpOffFrame <= frame) {
                inst.noteOff(arpSounding)
                arpSounding = -1
                arpOffFrame = Long.MAX_VALUE
            }
        }

        fun arpTickDue(frame: Long) {
            if (arpMode != Arp.OFF && heldCount > 0 && frameOf(arpNextTime) <= frame) arpTick(frame)
        }

        private fun arpFrames(): Double = sampleRate * 60.0 / max(bpm, 20f) / 4.0 * max(arpDivision, 0.25f)

        /** Le prochain instant de la grille d'arpège (calée sur le début de la lecture) à partir de [now]. */
        private fun alignedArpTime(now: Long): Double {
            val d = arpFrames()
            // Un instant de la grille est « dû » à l'échantillon ceil(t) : l'instant de grille d'un pas qui vient de démarrer à
            // l'échantillon [now] peut donc être jusqu'à un échantillon AVANT [now]. On le garde (sinon, quand la durée d'un
            // pas n'est pas un nombre entier d'échantillons, comme à 124 bpm, l'arpège sautait au pas suivant, où la note
            // du motif n'était déjà plus tenue : le synthé se taisait).
            val k = kotlin.math.floor((now - 1.0 + 1e-3 - playOrigin) / d) + 1.0
            return playOrigin + k * d
        }

        fun onArpModeChanged() {
            // On change de régime : tout ce qui sonne est relâché, rien de coincé de l'ancien mode.
            inst.allOff()
            if (arpSounding >= 0) inst.noteOff(arpSounding)
            arpSounding = -1
            arpOffFrame = Long.MAX_VALUE
            heldCount = 0
            lastArpMode = arpMode
        }

        /** Joue le coup d'arpège suivant et prépare le suivant. */
        private fun arpTick(frame: Long) {
            // Les notes tenues, de la plus grave à la plus aiguë, répétées sur [arpOctaves] octaves.
            for (i in 1 until heldCount) {
                var j = i
                while (j > 0 && held[j - 1] > held[j]) {
                    val tp = held[j]; held[j] = held[j - 1]; held[j - 1] = tp
                    val tv = heldVel[j]; heldVel[j] = heldVel[j - 1]; heldVel[j - 1] = tv
                    j--
                }
            }
            val octaves = arpOctaves.coerceIn(1, 3)
            var n = 0
            for (o in 0 until octaves) for (i in 0 until heldCount) arpBase[n++] = held[i] + 12 * o
            val cycle = when (arpMode) {
                Arp.UP_DOWN -> if (n <= 1) n else 2 * n - 2
                else -> n
            }
            arpIndex = if (cycle > 0) arpIndex % cycle else 0
            val i = arpIndex
            val pitch = when (arpMode) {
                Arp.DOWN -> arpBase[n - 1 - i]
                Arp.UP_DOWN -> if (i < n) arpBase[i] else arpBase[2 * n - 2 - i]
                Arp.RANDOM -> {
                    var x = arpRandom
                    x = x xor (x shl 13); x = x xor (x ushr 17); x = x xor (x shl 5)
                    arpRandom = x
                    arpBase[(x ushr 1) % n]
                }
                else -> arpBase[i]
            }
            arpIndex++

            var vel = 0f
            for (k in 0 until heldCount) if (heldVel[k] > vel) vel = heldVel[k]
            if (arpSounding >= 0) inst.noteOff(arpSounding)
            inst.noteOn(pitch, vel)
            onSynthNote?.invoke(pitch, frame)
            arpSounding = pitch
            val frames = arpFrames()
            arpOffFrame = frame + max(1L, (frames * arpGate.coerceIn(0.1f, 0.95f)).roundToLong())
            arpNextTime += frames
        }
    }

    companion object {
        /** Nombre maximal de pistes (leurs numéros vont de 0 à MAX_TRACKS − 1). */
        const val MAX_TRACKS = 32

        private const val MAX_BLOCK = 4096
        private const val REVERB_RETURN = 1.0f
        private const val DELAY_RETURN = 0.8f
        private const val QUEUE_SIZE = 256
        private const val MAX_PENDING_OFFS = 128
        private const val MAX_HELD = 16
        private const val NOTE_GATE = 0.96
        private const val CMD_PLAY = 1
        private const val CMD_STOP = 2
        private const val CMD_TRIGGER = 3
        private const val CMD_RELEASE = 4
    }
}

/** Les motifs de départ du banc d'essai. */
object DemoPatterns {

    /** Une mesure de house : grosse caisse sur chaque temps, clap sur 2 et 4, charlestons, ligne de basse. */
    fun house(): StepPattern {
        fun lane(instrument: Int, vararg steps: Int, velocity: Float = 1f): Lane {
            val v = FloatArray(16)
            for (s in steps) v[s] = velocity
            return Lane(instrument, v)
        }

        // Pas de charleston fermé là où le charleston ouvert joue (il le couperait).
        val hat = FloatArray(16) { if (it % 4 == 2) 0f else if (it % 2 == 0) 0.45f else 0.7f }
        // La, La, Do, La, Ré, La, Mi : une basse qui roule sous les temps.
        val bass = listOf(0 to 33, 3 to 33, 6 to 36, 8 to 33, 10 to 38, 13 to 33, 14 to 40)
            .map { (step, pitch) -> Note(step, pitch, 1, 0.9f) }

        return StepPattern(
            16,
            listOf(
                lane(Inst.KICK, 0, 4, 8, 12),
                lane(Inst.CLAP, 4, 12, velocity = 0.9f),
                lane(Inst.SNARE, 7, 15, velocity = 0.35f),
                Lane(Inst.HAT, hat),
                lane(Inst.OPEN_HAT, 2, 6, 10, 14, velocity = 0.6f),
            ),
            bass,
        )
    }
}
