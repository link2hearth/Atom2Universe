package com.Atom2Universe.app.games.papattes

import kotlin.random.Random

/**
 * Ce que le joueur possède : les étapes finies et leurs étoiles, les graines, l'enclos, le carnet
 * et l'œuf mystère en couveuse.
 *
 * Indépendant d'Android : l'activité le lit et l'écrit sous forme d'une chaîne ([encode] /
 * [decode]) dans ses préférences.
 */
class PapattesProgress {
    /** La plus haute étape finie (0 au départ). */
    var cleared = 0; private set
    var seeds = 0; private set
    val pen = mutableListOf(Critter.STARTER)
    /** La bestiole qui marche, indice dans [pen]. */
    var active = 0; private set
    private val discovered = BooleanArray(Parts.COUNT)
    private val seen = HashSet<Int>()
    /** Étoiles par étape, en bits (voir [PapattesGame.stars]). */
    private val stars = HashMap<Int, Int>()
    /** L'œuf mystère acheté : la bestiole qu'il contient (tirée à l'achat), ou null. */
    var egg: Critter? = null; private set
    /** L'œuf est prêt à éclore : une étape nouvelle a été finie depuis l'achat. */
    var eggReady = false; private set

    init { meet(Critter.STARTER) }

    val activeCritter: Critter get() = pen[active.coerceIn(0, pen.size - 1)]
    val discoveredCount: Int get() = discovered.count { it }
    val seenCount: Int get() = seen.size
    fun isDiscovered(part: Int) = discovered[part]
    val penFull: Boolean get() = pen.size >= PEN_SIZE
    fun starsOf(stage: Int): Int = stars[stage] ?: 0

    private fun meet(critter: Critter) {
        for (p in Parts.of(critter)) discovered[p] = true
        seen += critter.code
    }

    class Reward(
        val stage: Int, val firstTime: Boolean, val seeds: Int, val gold: Int,
        val stars: Int, val newStars: Int, val eggReady: Boolean
    )

    /**
     * Une étape vient d'être finie avec [earned] étoiles et [gold] graines d'or. Une étape finie
     * pour la première fois rend l'œuf en couveuse prêt à éclore.
     */
    fun clearStage(stage: Int, earned: Int, gold: Int = 0): Reward {
        val first = stage > cleared
        val gained = (if (first) FIRST_SEEDS + SEEDS_PER_STAGE * stage else REPLAY_SEEDS) + GOLD_VALUE * gold
        seeds += gained
        if (first) cleared = stage
        val before = starsOf(stage)
        val newStars = earned and before.inv()
        stars[stage] = before or earned
        val readied = first && egg != null && !eggReady
        if (readied) eggReady = true
        return Reward(stage, first, gained, gold, before or earned, newStars, readied)
    }

    val canBuyEgg: Boolean get() = egg == null && seeds >= EGG_COST

    /** Achète l'œuf mystère : n'importe quelle bestiole, pièces connues ou non. */
    fun buyEgg(rnd: Random): Boolean {
        if (!canBuyEgg) return false
        seeds -= EGG_COST
        egg = Critter(Head.entries.random(rnd), Body.entries.random(rnd), Legs.entries.random(rnd))
        eggReady = false
        return true
    }

    val canHatch: Boolean get() = egg != null && eggReady && !penFull

    /** L'œuf éclot : sa bestiole rejoint l'enclos et le carnet. */
    fun hatch(): Critter? {
        if (!canHatch) return null
        val critter = egg ?: return null
        egg = null
        eggReady = false
        pen += critter
        meet(critter)
        return critter
    }

    fun canBreed(a: Int, b: Int) = a != b && a in pen.indices && b in pen.indices && seeds >= BREED_COST && !penFull

    /**
     * Croise deux bestioles de l'enclos : chaque pièce vient de l'un ou de l'autre parent, et une
     * fois sur dix une pièce mute en une pièce encore inconnue du carnet. Rend l'enfant, ou null si
     * le croisement est impossible.
     */
    fun breed(a: Int, b: Int, rnd: Random): Critter? {
        if (!canBreed(a, b)) return null
        val pa = pen[a]
        val pb = pen[b]
        var child = Critter(
            if (rnd.nextBoolean()) pa.head else pb.head,
            if (rnd.nextBoolean()) pa.body else pb.body,
            if (rnd.nextBoolean()) pa.legs else pb.legs
        )
        if (rnd.nextFloat() < MUTATION_CHANCE) {
            val unknown = (0 until Parts.COUNT).filter { !discovered[it] }
            if (unknown.isNotEmpty()) {
                val part = unknown[rnd.nextInt(unknown.size)]
                val heads = Head.entries.size
                val bodies = Body.entries.size
                child = when {
                    part < heads -> child.copy(head = Head.entries[part])
                    part < heads + bodies -> child.copy(body = Body.entries[part - heads])
                    else -> child.copy(legs = Legs.entries[part - heads - bodies])
                }
            }
        }
        seeds -= BREED_COST
        pen += child
        meet(child)
        return child
    }

    fun select(index: Int) { if (index in pen.indices) active = index }

    /** Relâche une bestiole ; la dernière ne peut pas partir. */
    fun release(index: Int): Boolean {
        if (pen.size <= 1 || index !in pen.indices) return false
        val walking = pen[active]
        pen.removeAt(index)
        active = pen.indexOf(walking).takeIf { it >= 0 } ?: 0
        return true
    }

    fun encode(): String = buildString {
        append("v2;").append(cleared).append(';').append(seeds).append(';').append(active).append(';')
        append(pen.joinToString(",") { it.code.toString() }).append(';')
        append(discovered.joinToString("") { if (it) "1" else "0" }).append(';')
        append(seen.sorted().joinToString(",")).append(';')
        append(stars.entries.sortedBy { it.key }.joinToString(",") { "${it.key}:${it.value}" }).append(';')
        append(egg?.code ?: -1).append(';').append(if (eggReady) 1 else 0)
    }

    companion object {
        const val PEN_SIZE = 10
        const val BREED_COST = 25
        const val FIRST_SEEDS = 10
        const val SEEDS_PER_STAGE = 2
        const val REPLAY_SEEDS = 4
        /** Graines rapportées par chaque graine d'or ramassée. */
        const val GOLD_VALUE = 3
        const val EGG_COST = 30
        const val MUTATION_CHANCE = .1f

        fun decode(text: String?): PapattesProgress {
            val p = PapattesProgress()
            val f = text?.split(';') ?: return p
            if (f.size < 8 || f[0] != "v2") return p
            p.cleared = f[1].toIntOrNull() ?: 0
            p.seeds = f[2].toIntOrNull() ?: 0
            val critters = f[4].split(',').mapNotNull { it.toIntOrNull()?.let(Critter::fromCode) }
            if (critters.isNotEmpty()) { p.pen.clear(); p.pen += critters }
            p.active = (f[3].toIntOrNull() ?: 0).coerceIn(0, p.pen.size - 1)
            f[5].forEachIndexed { i, c -> if (i < Parts.COUNT && c == '1') p.discovered[i] = true }
            f[6].split(',').mapNotNull { it.toIntOrNull() }.forEach { p.seen += it }
            for (entry in f[7].split(',')) {
                val (stage, bits) = entry.split(':').takeIf { it.size == 2 } ?: continue
                p.stars[stage.toIntOrNull() ?: continue] = bits.toIntOrNull() ?: continue
            }
            // Champs de l'œuf mystère, ajoutés après les autres.
            p.egg = f.getOrNull(8)?.toIntOrNull()?.let(Critter::fromCode)
            p.eggReady = p.egg != null && f.getOrNull(9) == "1"
            p.pen.forEach { p.meet(it) }
            return p
        }
    }
}
