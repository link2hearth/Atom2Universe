package com.Atom2Universe.app.games.papattes

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.min
import kotlin.random.Random

enum class Biome { MEADOW, MARSH, ICE }

/** [WATER] n'est un sol que pour un corps qui flotte. */
enum class Surface(val friction: Float) {
    GRASS(1f), MUD(.85f), SNOW(.75f), ICE(.04f), WOOD(.85f), WATER(.6f)
}

enum class DecorKind { ROCK, BUSH, MUSHROOM, REEDS, LILY_PAD, SNOW_PILE }

/** Ce qui flotte sur l'eau : un rondin solide, ou une planche (ou un glaçon) qui cède sous le pied. */
enum class Floater { NONE, LOG, PLANK }

/** Un morceau de sol : le dessus d'un bloc, de ([x0], [y0]) à ([x1], [y1]). */
class Segment(val x0: Float, val y0: Float, val x1: Float, val y1: Float, val surface: Surface, val floater: Floater = Floater.NONE) {
    val floating: Boolean get() = floater != Floater.NONE
    fun yAt(x: Float) = if (x1 == x0) y0 else y0 + (y1 - y0) * (x - x0) / (x1 - x0)
}

class Water(val x0: Float, val x1: Float, val level: Float)

class Decor(val x: Float, val y: Float, val kind: DecorKind, val size: Float)

/** Une planche sur un pivot, au-dessus d'un trou : elle repose côté départ et bascule quand on passe le pivot. */
class Seesaw(val pivotX: Float, val pivotY: Float, val halfLength: Float, val restAngle: Float)

/** Un champignon rebondissant : son chapeau est un disque de centre ([x], [y]). */
class Mushroom(val x: Float, val y: Float, val radius: Float, val groundY: Float)

/** Une zone de rafales : [push] en m/s² (négatif = vent de face), par bouffées de [period] secondes. */
class Wind(val x0: Float, val x1: Float, val push: Float, val period: Float, val phase: Float) {
    /** Poussée à l'instant [t], entre 35 % et 100 % de [push]. */
    fun at(t: Float): Float = push * (.35f + .65f * maxOf(0f, kotlin.math.sin(2f * PI.toFloat() * t / period + phase)))
}

class GoldSeed(val x: Float, val y: Float)

/**
 * Le parcours d'une étape, tiré de son numéro : le même numéro donne toujours le même parcours.
 *
 * Les biomes tournent par six étapes (prairie, marais, banquise) ; à chaque tour complet, les
 * obstacles grandissent. Chaque obstacle demande un pas différent : petit pas sûr, grande
 * enjambée, bond, élan, ou vitesse pour passer avant que ça cède.
 *
 * Le parcours est le même pour toutes les jambes, mais taillé à leur mesure : les trous d'eau
 * rétrécissent pour celles qui portent moins loin que [WIDEST_GAP], et les graines d'or perchées
 * se placent à hauteur de la bestiole. Aucune paire de jambes ne tombe sur un passage impossible.
 */
class PapattesCourse(val stage: Int, val legs: Legs = Legs.STILTS) {
    val biome = Biome.entries[((stage - 1) / STAGES_PER_BIOME) % Biome.entries.size]
    /** 0 à 5 dans le premier tour, puis +3 par tour. */
    val level = (stage - 1) % STAGES_PER_BIOME + 3 * ((stage - 1) / (STAGES_PER_BIOME * Biome.entries.size))

    val segments = ArrayList<Segment>()
    val waters = ArrayList<Water>()
    val decor = ArrayList<Decor>()
    val seesaws = ArrayList<Seesaw>()
    val mushrooms = ArrayList<Mushroom>()
    val winds = ArrayList<Wind>()
    val goldSeeds = ArrayList<GoldSeed>()
    var checkpointX = 0f; private set
    var finishX = 0f; private set
    var endX = 0f; private set
    val startX = 1.6f

    private val rnd = Random(stage * 7919L + 17L)
    // Le sol commence loin derrière le départ : la caméra montre ce qui est en retrait de la bestiole.
    private var x = -8f
    private var y = 0f
    private val ground = when (biome) { Biome.MEADOW -> Surface.GRASS; Biome.MARSH -> Surface.MUD; Biome.ICE -> Surface.SNOW }
    private val difficulty = level.coerceAtMost(12)
    /** Places possibles pour les graines d'or, trouvées en route : au-dessus des trous, en haut des bonds. */
    private val seedSpots = ArrayList<GoldSeed>()
    /** Part de la largeur prévue que gardent les trous d'eau, selon la portée des jambes. */
    val gapScale = (legs.reach / WIDEST_GAP).coerceAtMost(1f)
    /** Hauteur d'une graine perchée au-dessus du sol : à portée de tête de la bestiole debout. */
    private val seedLift = legs.length + .25f

    init {
        val target = 34f + 3f * difficulty
        flat(13f)
        var checkpointPlaced = false
        while (x < target) {
            if (!checkpointPlaced && x > target / 2) {
                flat(3.2f)
                checkpointX = x - 1.6f
                checkpointPlaced = true
            }
            feature()
            flat(rnd.range(.6f, 1.1f))
        }
        flat(6f)
        finishX = x - 3.5f
        flat(9f)
        endX = x
        placeGoldSeeds()
        placeDecor()
    }

    private fun feature() {
        when (biome) {
            Biome.MEADOW -> when (pick(1, 3, 3, 3, if (level >= 1) 3 else 0, if (level >= 2) 2 else 0,
                if (level >= 3) 2 else 0, if (level >= 4) 1 else 0)) {
                0 -> flat(rnd.range(1.2f, 2f))
                1 -> slope(rnd.range(1.8f, 3.2f), rnd.sign() * rnd.range(.12f, .2f + .03f * difficulty))
                2 -> bump(rnd.range(1.4f, 2.6f), rnd.range(.08f, .14f + .025f * difficulty))
                3 -> step(rnd.sign() * rnd.range(.06f, (.1f + .025f * difficulty).coerceAtMost(MAX_STEP)))
                4 -> gap(rnd.range(.25f, min(1f, .45f + .05f * difficulty)))
                5 -> mushroomLedge()
                6 -> seesaw()
                else -> windy()
            }
            Biome.MARSH -> when (pick(1, 4, if (level >= 2) 2 else 0, if (level >= 1) 3 else 0, if (level >= 3) 2 else 0, 2)) {
                0 -> flat(rnd.range(1.2f, 2f))
                1 -> gap(rnd.range(.3f, min(1.05f, .45f + .07f * difficulty)))
                2 -> logs(rnd.range(2f, 2.6f + .15f * difficulty))
                3 -> planks(rnd.range(1.4f, 2f + .15f * difficulty), Surface.WOOD)
                4 -> seesaw()
                else -> bump(rnd.range(1.4f, 2.4f), rnd.range(.06f, .12f + .02f * difficulty))
            }
            Biome.ICE -> when (pick(1, 2, 4, 1, 1, if (level >= 1) 3 else 0, if (level >= 2) 2 else 0, if (level >= 3) 2 else 0)) {
                0 -> flat(rnd.range(1.2f, 2f))
                1 -> slope(rnd.range(2f, 3f), rnd.sign() * rnd.range(.1f, .16f + .025f * difficulty),
                    if (rnd.nextBoolean()) Surface.ICE else ground)
                2 -> flat(rnd.range(1.2f, 1.8f + .2f * difficulty), Surface.ICE)
                3 -> bump(rnd.range(1.6f, 2.6f), rnd.range(.06f, .1f + .02f * difficulty))
                4 -> step(rnd.sign() * rnd.range(.06f, (.09f + .02f * difficulty).coerceAtMost(MAX_STEP)))
                5 -> windy()
                6 -> gap(rnd.range(.3f, min(.9f, .4f + .05f * difficulty)))
                else -> planks(rnd.range(1.4f, 1.8f + .12f * difficulty), Surface.SNOW)
            }
        }
    }

    private fun pick(vararg weights: Int): Int {
        var roll = rnd.nextInt(weights.sum())
        for (i in weights.indices) { roll -= weights[i]; if (roll < 0) return i }
        return 0
    }

    private fun flat(length: Float, surface: Surface = ground) = line(x + length, y, surface)

    private fun slope(length: Float, rise: Float, surface: Surface = ground) = line(x + length, y + rise, surface)

    private fun line(toX: Float, toY: Float, surface: Surface) {
        segments += Segment(x, y, toX, toY, surface)
        x = toX; y = toY
    }

    private fun bump(width: Float, height: Float) {
        val pieces = 6
        val startX = x
        val baseY = y
        for (i in 1..pieces) {
            val t = i / pieces.toFloat()
            line(startX + width * t, baseY + height * (1 - cos(2 * PI.toFloat() * t)) / 2, ground)
        }
        if (height > .2f) seedSpots += GoldSeed(startX + width / 2, baseY + height + seedLift)
    }

    private fun step(rise: Float) {
        y += rise
        flat(rnd.range(.8f, 1.3f))
    }

    private fun gap(planned: Float) {
        val width = maxOf(MIN_GAP, planned * gapScale)
        val level = y - .22f
        waters += Water(x, x + width, level)
        seedSpots += GoldSeed(x + width / 2, y + .35f)
        x += width
        flat(rnd.range(.8f, 1.2f))
    }

    /**
     * Une mare large, franchie sur des rondins flottants : longs, pour qu'on n'ait pas à viser un
     * point précis, et jamais plus écartés que [MAX_LOG_GAP] (moins pour les petites jambes).
     */
    private fun logs(width: Float) {
        val left = x
        val spacing = maxOf(MIN_GAP, MAX_LOG_GAP * gapScale)
        waters += Water(left, left + width, y - .22f)
        val count = ceil((width - spacing) / (LOG_LENGTH + spacing)).toInt().coerceAtLeast(1)
        val length = (width - (count + 1) * spacing * .75f) / count
        val gap = (width - count * length) / (count + 1)
        var cursor = left
        for (i in 0 until count) {
            val next = cursor + gap
            val top = y - .04f
            segments += Segment(next, top, next + length, top, Surface.WOOD, Floater.LOG)
            cursor = next + length
        }
        x = left + width
        flat(rnd.range(.8f, 1.2f))
    }

    /** Une passerelle de planches (ou de glaçons) presque jointives : chacune cède peu après qu'on a posé le pied dessus. */
    private fun planks(width: Float, surface: Surface) {
        val left = x
        waters += Water(left, left + width, y - .22f)
        val count = ceil(width / (PLANK_LENGTH + PLANK_GAP)).toInt()
        val length = (width - (count + 1) * PLANK_GAP) / count
        var cursor = left + PLANK_GAP
        for (i in 0 until count) {
            segments += Segment(cursor, y - .02f, cursor + length, y - .02f, surface, Floater.PLANK)
            cursor += length + PLANK_GAP
        }
        seedSpots += GoldSeed(left + width / 2, y + .5f)
        x = left + width
        flat(rnd.range(.8f, 1.2f))
    }

    /**
     * Un trou franchi sur une planche à bascule. Le pivot est au milieu du trou, un peu au-dessus du
     * sol ; au repos, la planche s'appuie sur la rive de départ.
     */
    private fun seesaw() {
        flat(.6f)
        val width = rnd.range(1.6f, 1.9f)
        val half = width / 2 + SEESAW_OVERLAP
        val height = (.18f + .025f * difficulty).coerceAtMost(.38f)
        waters += Water(x, x + width, y - .22f)
        val pivotX = x + width / 2
        // Penchée, la planche affleure la rive par le dessus : ni marche à monter en y entrant, ni à descendre en sortant.
        val angle = asin(((height + SEESAW_THICKNESS / 2) / half).coerceIn(0f, .6f))
        seesaws += Seesaw(pivotX, y + height, half, angle)
        seedSpots += GoldSeed(pivotX, y + height + .9f)
        x += width
        flat(rnd.range(1f, 1.4f))
    }

    /** Un mur trop haut pour une enjambée, précédé d'un champignon qui renvoie la bestiole en l'air. */
    private fun mushroomLedge() {
        flat(1.2f)
        val radius = .3f
        mushrooms += Mushroom(x + .1f, y + .38f, radius, y)
        flat(.75f)
        val rise = rnd.range(.6f, .75f + .02f * difficulty).coerceAtMost(.9f)
        seedSpots += GoldSeed(x - .1f, y + rise + 1.1f)
        y += rise
        flat(rnd.range(1.6f, 2.2f))
    }

    /** Un plat battu par les rafales, de face le plus souvent. */
    private fun windy() {
        val length = rnd.range(3.5f, 5f + .2f * difficulty)
        val push = (1.2f + .12f * difficulty).coerceAtMost(2.6f) * (if (rnd.nextFloat() < .7f) -1f else 1f)
        winds += Wind(x, x + length, push, rnd.range(2.2f, 3.4f), rnd.range(0f, 6.28f))
        flat(length, if (biome == Biome.ICE && rnd.nextBoolean()) Surface.ICE else ground)
    }

    /** Trois graines d'or, réparties le long du parcours parmi les places trouvées en route. */
    private fun placeGoldSeeds() {
        val spots = seedSpots.filter { it.x > startX + 2f && it.x < finishX - 1f }.toMutableList()
        while (spots.size < GOLD_SEEDS) {
            // Faute de trous ou de bonds, une graine haut perchée que seules les grandes bestioles et les bonds atteignent.
            val at = rnd.range(startX + 4f, finishX - 2f)
            val top = surfaceAt(at)
            if (top.isFinite()) spots += GoldSeed(at, top + seedLift + .1f)
        }
        spots.sortBy { it.x }
        for (i in 0 until GOLD_SEEDS) goldSeeds += spots[(i * spots.size + spots.size / 2) / GOLD_SEEDS]
    }

    private fun placeDecor() {
        val kinds = when (biome) {
            Biome.MEADOW -> listOf(DecorKind.BUSH, DecorKind.ROCK)
            Biome.MARSH -> listOf(DecorKind.REEDS, DecorKind.ROCK)
            Biome.ICE -> listOf(DecorKind.SNOW_PILE, DecorKind.ROCK)
        }
        var cursor = 2f
        while (cursor < endX) {
            val at = cursor + rnd.range(0f, 1.5f)
            val top = surfaceAt(at)
            val clear = mushrooms.none { at > it.x - .8f && at < it.x + .8f }
            if (top.isFinite() && segmentAt(at)?.floating != true && clear) decor += Decor(at, top, kinds[rnd.nextInt(kinds.size)], rnd.range(.25f, .5f))
            cursor += rnd.range(2.5f, 5f)
        }
        if (biome == Biome.MARSH) for (w in waters) if (w.x1 - w.x0 > .6f && rnd.nextBoolean()) {
            decor += Decor(rnd.range(w.x0 + .15f, w.x1 - .15f), w.level, DecorKind.LILY_PAD, .3f)
        }
    }

    /** Le dessus fixe le plus haut à l'abscisse [x], ou −∞ au-dessus de l'eau. */
    fun surfaceAt(x: Float): Float {
        var best = Float.NEGATIVE_INFINITY
        for (s in segments) if (x >= s.x0 && x <= s.x1) best = maxOf(best, s.yAt(x))
        return best
    }

    fun segmentAt(x: Float): Segment? {
        var best: Segment? = null
        for (s in segments) if (x >= s.x0 && x <= s.x1 && (best == null || s.yAt(x) > best.yAt(x))) best = s
        return best
    }

    fun waterAt(x: Float): Water? = waters.firstOrNull { x > it.x0 && x < it.x1 }

    fun windAt(x: Float): Wind? = winds.firstOrNull { x >= it.x0 && x <= it.x1 }

    companion object {
        const val STAGES_PER_BIOME = 6
        const val MAX_LOG_GAP = .45f
        /** Le trou le plus large qu'on tire, enjambé par les échasses ; et le plus étroit, une fois rétréci. */
        const val WIDEST_GAP = 1.06f
        const val MIN_GAP = .18f
        const val LOG_LENGTH = 1.1f
        const val PLANK_LENGTH = .5f
        const val PLANK_GAP = .07f
        const val SEESAW_OVERLAP = .45f
        const val SEESAW_THICKNESS = .08f
        const val GOLD_SEEDS = 3
        /** Marche la plus haute, en mètres : au-delà, les petites jambes basculent en la descendant. */
        const val MAX_STEP = .16f
        /** Pour un corps qui flotte, l'eau porte à ce niveau-ci au-dessus de sa surface. */
        const val FLOAT_LIFT = .2f
    }
}

private fun Random.range(from: Float, to: Float) = from + nextFloat() * (to - from)
private fun Random.sign() = if (nextBoolean()) 1f else -1f
