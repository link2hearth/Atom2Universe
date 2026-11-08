package com.Atom2Universe.app.studio.music

import com.Atom2Universe.app.studio.engine.Inst
import com.Atom2Universe.app.studio.engine.Lane
import com.Atom2Universe.app.studio.engine.Note
import com.Atom2Universe.app.studio.engine.StepPattern
import java.util.Random
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Une gamme : ses intervalles en demi-tons depuis la tonique. */
class Scale(val id: String, val intervals: IntArray) {
    val size: Int get() = intervals.size
}

object Scales {
    val MINOR_PENT = Scale("minor_pent", intArrayOf(0, 3, 5, 7, 10))
    val MAJOR_PENT = Scale("major_pent", intArrayOf(0, 2, 4, 7, 9))
    val MINOR = Scale("minor", intArrayOf(0, 2, 3, 5, 7, 8, 10))
    val MAJOR = Scale("major", intArrayOf(0, 2, 4, 5, 7, 9, 11))
    val DORIAN = Scale("dorian", intArrayOf(0, 2, 3, 5, 7, 9, 10))
    val BLUES = Scale("blues", intArrayOf(0, 3, 5, 6, 7, 10))
    val CHROMATIC = Scale("chromatic", IntArray(12) { it })

    val ALL = listOf(MINOR_PENT, MAJOR_PENT, MINOR, MAJOR, DORIAN, BLUES, CHROMATIC)

    fun byId(id: String): Scale = ALL.firstOrNull { it.id == id } ?: MINOR_PENT

    /** Vrai si la hauteur MIDI [pitch] est une note de la gamme [scale] en tonalité [keyPc] (0 = do … 11 = si). */
    fun contains(scale: Scale, keyPc: Int, pitch: Int): Boolean {
        val d = ((pitch - keyPc) % 12 + 12) % 12
        for (i in scale.intervals) if (i == d) return true
        return false
    }

    /**
     * Les hauteurs affichées dans le piano roll, de la plus grave à la plus aiguë, entre [low] et [low] + 12 × [octaves]
     * compris. Verrouillé : seulement les notes de la gamme ; libre : tous les demi-tons.
     */
    fun rows(scale: Scale, keyPc: Int, low: Int, octaves: Int, locked: Boolean): IntArray {
        val out = ArrayList<Int>()
        for (p in low..low + 12 * octaves) if (!locked || contains(scale, keyPc, p)) out += p
        return out.toIntArray()
    }
}

/**
 * Un style de départ : tempo, swing, son du synthé, quantité d'effets, et une grille de « tendances » par
 * instrument. Chaque caractère d'une grille vaut une double-croche :
 * `X` toujours, `x` presque toujours, `o` une fois sur deux, `u` rarement, `.` jamais.
 * Le même style avec une autre graine donne un autre motif dans la même veine : c'est le bouton Hasard.
 */
class Style(
    val id: String,
    val bpm: Int,
    val swing: Float,
    val patchId: String,
    val space: Float,
    val echo: Float,
    /** Une chaîne de 16 caractères par instrument (absent = silence). */
    val drums: Map<Int, String>,
    /** Où le synthé peut jouer (même alphabet) ; ses notes sont tirées dans la gamme. */
    val synthSteps: String,
    val synthRoot: Int,
    /** Durée des notes du synthé, en pas (une nappe tient plusieurs pas). */
    val synthLength: Int,
    val scaleId: String = "minor_pent",
    /** Si présent, chaque note du synthé devient un accord : ces intervalles au-dessus de la note tirée. */
    val chord: IntArray? = null,
    /**
     * Une ligne de notes : la k-ième note jouée prend le degré `motif[k % taille]` de la gamme (au-delà du dernier degré, on
     * monte d'une octave). Le Hasard change alors les pas joués, pas la mélodie : ça reste une vraie ligne de basse.
     */
    val motif: IntArray? = null,
    /** Une suite d'accords (décalage de la tonique, intervalles) qui change tous les 8 pas. Elle remplace [chord]. */
    val progression: List<Pair<Int, IntArray>>? = null,
    /** Vrai : la caisse claire et le clap peuvent tomber sur le même pas (électro). */
    val layerClap: Boolean = false,
) {
    /** Tonalité : la tonique du synthé ramenée à un nom de note (0 = do … 11 = si). */
    val keyPc: Int get() = synthRoot % 12

    /** Le même style avec une autre tonique et une autre gamme (le Hasard suit la tonalité choisie). */
    fun inKey(root: Int, scaleId: String) = Style(
        id, bpm, swing, patchId, space, echo, drums, synthSteps, root, synthLength, scaleId, chord, motif, progression, layerClap,
    )
}

object Styles {

    /** Poids de chaque degré de la gamme dans le tirage des notes (la tonique domine). */
    private val DEGREE_WEIGHT = intArrayOf(5, 2, 2, 3, 1, 2, 1)

    val HOUSE = Style(
        id = "house", bpm = 124, swing = 0.04f, patchId = "bass", space = 0.25f, echo = 0.15f,
        drums = mapOf(
            Inst.KICK to "X...X...X...X...",     // quatre temps, toujours
            Inst.CLAP to "....X.......X...",     // temps 2 et 4
            Inst.OPEN_HAT to "..X...X...X...X.", // les contretemps : l'âme de la house
            Inst.HAT to "x..ux..ux..ux..u",
            Inst.SNARE to "..............u.",
            Inst.PERC to "..u...o...u...o.",
        ),
        synthSteps = "..X...X...X...Xo", synthRoot = 33, synthLength = 1, scaleId = "minor_pent",
        motif = intArrayOf(0, 0, 2, 0, 3),
    )

    val TECHNO = Style(
        id = "techno", bpm = 132, swing = 0f, patchId = "dist_bass", space = 0.2f, echo = 0.2f,
        drums = mapOf(
            Inst.KICK to "X...X...X...X.u.",
            Inst.CLAP to "....x.......x...",
            Inst.OPEN_HAT to "..o...o...o...o.",
            Inst.HAT to "u.uxu.uxu.uxu.ux",
            Inst.PERC to ".u..u..u.u..u...",
        ),
        synthSteps = ".xxx.xxx.xxx.xxx", synthRoot = 28, synthLength = 1, scaleId = "minor_pent",
        motif = intArrayOf(0, 0, 0, 0, 0, 0, 3, 0),
    )

    val HIPHOP = Style(
        id = "hiphop", bpm = 90, swing = 0.18f, patchId = "sub", space = 0.2f, echo = 0.15f,
        drums = mapOf(
            Inst.KICK to "X.....o.o.x.....",     // le un, puis des syncopes, jamais sous la caisse claire
            Inst.SNARE to "....X.......X...",    // boom-bap : temps 2 et 4
            Inst.HAT to "x.x.x.x.x.x.x.x.",
            Inst.OPEN_HAT to "..............o.",
            Inst.PERC to ".u.......u......",
        ),
        synthSteps = "X.....o.o.x.o...", synthRoot = 29, synthLength = 3, scaleId = "minor_pent",
        motif = intArrayOf(0, 0, 3, 2),
    )

    val LOFI = Style(
        id = "lofi", bpm = 76, swing = 0.3f, patchId = "pluck", space = 0.4f, echo = 0.3f,
        drums = mapOf(
            Inst.KICK to "X.....u..x.o....",
            Inst.SNARE to "....X.......X...",
            Inst.HAT to "x.o.x.o.x.o.x.o.",
            Inst.PERC to ".......u.....u..",
            Inst.TOM to "..............u.",
        ),
        synthSteps = "X.....x.X..u....", synthRoot = 57, synthLength = 3, scaleId = "dorian",
        // Des accords de septième qui changent tous les 8 pas
        progression = listOf(
            0 to intArrayOf(0, 3, 7, 10), 5 to intArrayOf(0, 3, 7, 10),
            8 to intArrayOf(0, 4, 7, 11), 7 to intArrayOf(0, 3, 7, 10),
        ),
    )

    val ELECTRO = Style(
        id = "electro", bpm = 122, swing = 0f, patchId = "arp", space = 0.2f, echo = 0.25f,
        drums = mapOf(
            Inst.KICK to "X.....X...X..u..",     // syncopé, façon 808
            Inst.CLAP to "....X.......X...",
            Inst.SNARE to "....x.......x...",    // posée sur le clap : permis ici
            Inst.HAT to "x.xux.xux.xux.xu",
            Inst.PERC to "..o...o...o..o..",
            Inst.TOM to "..............o.",
        ),
        synthSteps = "XxxxXxxxXxxxXxxx", synthRoot = 45, synthLength = 1, scaleId = "minor",
        motif = intArrayOf(0, 2, 4, 7, 4, 2), layerClap = true,
    )

    val AMBIENT = Style(
        id = "ambient", bpm = 70, swing = 0f, patchId = "pad", space = 0.6f, echo = 0.4f,
        drums = mapOf(
            Inst.KICK to "X.......u.......",
            Inst.HAT to "....u.......u...",
            Inst.OPEN_HAT to "......u.........",
            Inst.TOM to "........o.......",
            Inst.PERC to "...o.......o....",
        ),
        synthSteps = "X.......o.......", synthRoot = 48, synthLength = 8, scaleId = "minor",
        // Deux accords par mesure : quinte et neuvième sur une tonique qui descend
        progression = listOf(0, -4, -2, -5).map { it to intArrayOf(0, 7, 14) },
    )

    val TRAP = Style(
        id = "trap", bpm = 140, swing = 0f, patchId = "sub", space = 0.2f, echo = 0.2f,
        drums = mapOf(
            Inst.KICK to "X.....o...o..u..",
            Inst.CLAP to "........X.......",     // le demi-temps : un seul coup par mesure
            Inst.HAT to "x.x.x.x.x.x.xxxx",      // roulement final
            Inst.OPEN_HAT to "......u.......u.",
        ),
        synthSteps = "X.....o...o..u..", synthRoot = 31, synthLength = 4, scaleId = "minor",
        motif = intArrayOf(0, 0, 3, 0),
    )

    val ALL = listOf(HOUSE, TECHNO, HIPHOP, LOFI, ELECTRO, AMBIENT, TRAP)

    fun byId(id: String): Style = ALL.firstOrNull { it.id == id } ?: HOUSE

    /** Une grille vide : une ligne par instrument rythmique, dans l'ordre de [Inst], et aucune note. */
    fun emptyPattern(stepCount: Int): StepPattern =
        StepPattern(stepCount, List(Inst.DRUM_COUNT) { Lane(it, FloatArray(stepCount)) }, emptyList())

    /**
     * Fabrique un motif de [stepCount] pas (16 ou 32) dans le style [style]. La même [seed] donne toujours le
     * même motif ; une autre graine, un cousin du même style.
     */
    fun generate(style: Style, stepCount: Int, seed: Long): StepPattern {
        val lanes = ArrayList<Lane>(Inst.DRUM_COUNT)
        for (inst in 0 until Inst.DRUM_COUNT) {
            // Un tirage par piste : changer une grille ne bouleverse pas les autres pistes
            val rng = Random(seed * 31 + inst)
            val velocity = FloatArray(stepCount)
            val table = style.drums[inst]
            if (table != null) {
                for (s in 0 until stepCount) {
                    val c = table[s % table.length]
                    val (p, v) = tendency(c)
                    if (p > 0f && rng.nextFloat() < p) {
                        // Accent sur les temps, petite humanisation sur ce qui n'est pas sûr
                        val accent = if (s % 4 == 0) 0.1f else 0f
                        val jitter = if (c == 'X') 0f else (rng.nextFloat() - 0.5f) * 0.1f
                        velocity[s] = (v + accent + jitter).coerceIn(0.25f, 1f)
                    }
                }
            }
            lanes += Lane(inst, velocity)
        }
        // Le charleston fermé coupe l'ouvert : jamais les deux sur le même pas.
        val hat = lanes[Inst.HAT].velocity
        val open = lanes[Inst.OPEN_HAT].velocity
        for (s in 0 until stepCount) if (open[s] > 0f) hat[s] = 0f
        // Caisse claire et clap ne se superposent pas, sauf dans les styles qui le veulent
        if (!style.layerClap) {
            val snare = lanes[Inst.SNARE].velocity
            val clap = lanes[Inst.CLAP].velocity
            for (s in 0 until stepCount) if (clap[s] > 0f) snare[s] = 0f
        }

        // Notes du synthé : des départs tirés comme pour la batterie, chacun jusqu'au suivant au plus.
        val rng = Random(seed * 31 + 99)
        val scale = Scales.byId(style.scaleId)
        val starts = ArrayList<Pair<Int, Float>>()
        for (s in 0 until stepCount) {
            val (p, v) = tendency(style.synthSteps[s % style.synthSteps.length])
            if (p > 0f && rng.nextFloat() < p) starts += s to v
        }
        val notes = ArrayList<Note>()
        for ((k, start) in starts.withIndex()) {
            val (step, v) = start
            val next = if (k + 1 < starts.size) starts[k + 1].first else stepCount
            val length = max(1, min(style.synthLength, next - step))
            val prog = style.progression
            val chordOffset: Int
            val intervals: IntArray
            if (prog != null) {
                val block = prog[(step / 8) % prog.size]
                chordOffset = block.first
                intervals = block.second
            } else {
                chordOffset = 0
                intervals = style.chord ?: intArrayOf(0)
            }
            val base = (if (prog != null) style.synthRoot else pickNote(style, scale, rng, step == 0, k)) + chordOffset
            for (i in intervals) notes += Note(step, base + i, length, v)
        }
        return StepPattern(stepCount, lanes, notes)
    }

    private fun tendency(c: Char): Pair<Float, Float> = when (c) {
        'X' -> 1.0f to 1.0f
        'x' -> 0.9f to 0.8f
        'o' -> 0.45f to 0.7f
        'u' -> 0.15f to 0.5f
        else -> 0f to 0f
    }

    /** Les sons où une octave de plus a du sens ; une basse ne saute jamais d'octave au hasard. */
    private val LEAD_PATCHES = setOf("lead", "arp", "pluck", "bell")

    private fun pickNote(style: Style, scale: Scale, rng: Random, forceRoot: Boolean, k: Int): Int {
        val motif = style.motif
        if (motif != null && motif.isNotEmpty()) {
            val idx = motif[k % motif.size]
            return style.synthRoot + scale.intervals[idx % scale.size] + idx / scale.size * 12
        }
        if (forceRoot) return style.synthRoot
        var total = 0
        for (i in 0 until scale.size) total += DEGREE_WEIGHT[min(i, DEGREE_WEIGHT.size - 1)]
        var r = rng.nextInt(total)
        var degree = 0
        for (i in 0 until scale.size) {
            r -= DEGREE_WEIGHT[min(i, DEGREE_WEIGHT.size - 1)]
            if (r < 0) {
                degree = i
                break
            }
        }
        val octave = if (style.patchId in LEAD_PATCHES && rng.nextInt(100) < 15) 12 else 0
        return style.synthRoot + scale.intervals[degree] + octave
    }
}
