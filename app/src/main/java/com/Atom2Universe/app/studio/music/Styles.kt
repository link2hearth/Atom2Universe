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
) {
    /** Tonalité : la tonique du synthé ramenée à un nom de note (0 = do … 11 = si). */
    val keyPc: Int get() = synthRoot % 12
}

object Styles {

    /** Poids de chaque degré de la gamme dans le tirage des notes (la tonique domine). */
    private val DEGREE_WEIGHT = intArrayOf(5, 2, 2, 3, 1, 2, 1)

    val HOUSE = Style(
        id = "house", bpm = 124, swing = 0f, patchId = "bass", space = 0.25f, echo = 0.15f,
        drums = mapOf(
            Inst.KICK to "X...X...X...X...",
            Inst.CLAP to "....X.......X...",
            Inst.SNARE to "......u.......u.",
            Inst.HAT to "u.x.u.x.u.x.u.x.",
            Inst.OPEN_HAT to "..X...X...X...X.",
            Inst.PERC to "u..u..u.u.u..u..",
            Inst.TOM to "..............u.",
        ),
        synthSteps = "X..o..o.X.o..o..", synthRoot = 33, synthLength = 1,
    )

    val TECHNO = Style(
        id = "techno", bpm = 132, swing = 0f, patchId = "acid", space = 0.2f, echo = 0.2f,
        drums = mapOf(
            Inst.KICK to "X...X...X...X...",
            Inst.CLAP to "....x.......x...",
            Inst.HAT to "o.x.o.x.o.x.o.x.",
            Inst.OPEN_HAT to "..x...x...x...x.",
            Inst.PERC to "u.o.u.o.u.o.u.o.",
        ),
        synthSteps = "oXoXoXoooXoXoXoX", synthRoot = 33, synthLength = 1,
    )

    val HIPHOP = Style(
        id = "hiphop", bpm = 88, swing = 0.22f, patchId = "sub", space = 0.2f, echo = 0.15f,
        drums = mapOf(
            Inst.KICK to "X.....o...X..o..",
            Inst.SNARE to "....X.......X...",
            Inst.HAT to "x.x.x.x.x.x.x.x.",
            Inst.OPEN_HAT to ".............o..",
            Inst.PERC to ".o..............",
        ),
        synthSteps = "X.....o...X.....", synthRoot = 33, synthLength = 2,
    )

    val LOFI = Style(
        id = "lofi", bpm = 76, swing = 0.3f, patchId = "strings", space = 0.4f, echo = 0.3f,
        drums = mapOf(
            Inst.KICK to "X.....o...o..o..",
            Inst.SNARE to "....x.......x...",
            Inst.HAT to "x.o.x.o.x.o.x.o.",
            Inst.PERC to "...u.....u......",
        ),
        synthSteps = "X.......o.......", synthRoot = 45, synthLength = 4,
        scaleId = "dorian", chord = intArrayOf(0, 3, 7, 10),
    )

    val ELECTRO = Style(
        id = "electro", bpm = 128, swing = 0f, patchId = "arp", space = 0.2f, echo = 0.25f,
        drums = mapOf(
            Inst.KICK to "X..o..X...o.X..o",
            Inst.CLAP to "....X.......X..o",
            Inst.SNARE to "......o.......o.",
            Inst.HAT to "x.x.x.x.x.x.x.x.",
            Inst.PERC to "o..o..o.o.o.o..o",
            Inst.TOM to "..............o.",
        ),
        synthSteps = "XoxoXoxoXoxoXoxo", synthRoot = 45, synthLength = 1,
        scaleId = "minor",
    )

    val AMBIENT = Style(
        id = "ambient", bpm = 70, swing = 0.1f, patchId = "pad", space = 0.6f, echo = 0.4f,
        drums = mapOf(
            Inst.KICK to "X.........o.....",
            Inst.HAT to "u...u.......u...",
            Inst.OPEN_HAT to "......u.........",
            Inst.TOM to "........o.......",
            Inst.PERC to "...o.......o....",
        ),
        synthSteps = "X.......o.......", synthRoot = 45, synthLength = 8,
        scaleId = "minor", chord = intArrayOf(0, 7, 12),
    )

    val ALL = listOf(HOUSE, TECHNO, HIPHOP, LOFI, ELECTRO, AMBIENT)

    fun byId(id: String): Style = ALL.firstOrNull { it.id == id } ?: HOUSE

    /** Une grille vide : une ligne par instrument rythmique, dans l'ordre de [Inst], et aucune note. */
    fun emptyPattern(stepCount: Int): StepPattern =
        StepPattern(stepCount, List(Inst.DRUM_COUNT) { Lane(it, FloatArray(stepCount)) }, emptyList())

    /**
     * Fabrique un motif de [stepCount] pas (16 ou 32) dans le style [style]. La même [seed] donne toujours le
     * même motif ; une autre graine, un cousin du même style.
     */
    fun generate(style: Style, stepCount: Int, seed: Long): StepPattern {
        val rng = Random(seed)
        val lanes = ArrayList<Lane>(Inst.DRUM_COUNT)
        for (inst in 0 until Inst.DRUM_COUNT) {
            val velocity = FloatArray(stepCount)
            val table = style.drums[inst]
            if (table != null) {
                for (s in 0 until stepCount) {
                    val (p, v) = tendency(table[s % table.length])
                    if (p > 0f && rng.nextFloat() < p) velocity[s] = v
                }
            }
            lanes += Lane(inst, velocity)
        }
        // Le charleston fermé coupe l'ouvert : jamais les deux sur le même pas.
        val hat = lanes[Inst.HAT].velocity
        val open = lanes[Inst.OPEN_HAT].velocity
        for (s in 0 until stepCount) if (open[s] > 0f) hat[s] = 0f

        // Notes du synthé : des départs tirés comme pour la batterie, chacun jusqu'au suivant au plus.
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
            val base = pickNote(style, scale, rng, step == 0)
            val intervals = style.chord ?: intArrayOf(0)
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

    private fun pickNote(style: Style, scale: Scale, rng: Random, forceRoot: Boolean): Int {
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
        val octave = if (rng.nextInt(100) < 15) 12 else 0
        return style.synthRoot + scale.intervals[degree] + octave
    }
}
