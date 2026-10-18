package com.Atom2Universe.app.games.cards

import kotlin.random.Random

/** Ce qui s'est passé sur la table pendant un coup : l'écran en fait des cartes qui voyagent. */
sealed class CardEvent {
    /**
     * Une carte quitte la main de [seat] : vers le pli au centre, vers la défausse si [toPile], ou
     * vers la rangée du centre si [toRow] (le décompte du cribbage).
     */
    class Play(val seat: Int, val card: PlayingCard, val toPile: Boolean = false, val toRow: Boolean = false) : CardEvent()

    /**
     * [seat] pioche, à la pioche ou, si [fromDiscard], sur la défausse. [card] n'est connue que si
     * c'est le joueur lui-même ou si elle venait de la défausse (sinon elle reste cachée).
     */
    class Draw(val seat: Int, val card: PlayingCard? = null, val fromDiscard: Boolean = false) : CardEvent()

    /** [seat] ramasse le pli (et la rangée) du centre. */
    class Take(val seat: Int) : CardEvent()

    /** [cards] passent de la main de [from] à celle de [to] (la Pêche : les cartes demandées). Elles sont connues de tous. */
    class Give(val from: Int, val to: Int, val cards: List<PlayingCard>) : CardEvent()

    /** La rangée du centre devient exactement [cards] (qui les a posées, dans l'ordre) : le décompte d'une main, par exemple. */
    class Lay(val cards: List<Pair<Int, PlayingCard>>) : CardEvent()
}

/** Les trois niveaux des adversaires, communs à tous les jeux de cartes. */
object CardLevel {
    const val EASY = 0
    const val MEDIUM = 1
    const val HARD = 2
    const val COUNT = 3
}

/**
 * Le cœur d'un jeu de cartes, sans une ligne d'Android : l'état de la partie, ses règles, et les
 * adversaires. L'écran ne fait que montrer cet état et transmettre les choix du joueur.
 *
 * **Un coup est un simple entier** (un numéro de carte, ou un code spécial : piocher, passer…).
 * C'est ce qui permet de sauvegarder une partie sans rien écrire de plus : la graine du mélange et la
 * liste des coups jouées suffisent à la rejouer à l'identique. Pour que ça tienne, un moteur ne doit
 * dépendre que de sa graine et des coups reçus — jamais de l'heure, ni d'un tirage qui ne se refait
 * pas (un mélange en cours de partie dérive sa propre graine de l'état, voir `Random(seed + n)`).
 *
 * Le joueur est toujours le siège 0 ; les sièges suivent le sens des aiguilles d'une montre : le 1 à
 * gauche, le 2 en face, le 3 à droite.
 */
abstract class CardEngine {

    /** Nombre de joueurs. */
    abstract val seats: Int

    /** Le siège qui doit jouer, ou -1 quand la partie est finie. */
    abstract val current: Int

    val isOver: Boolean get() = current < 0

    /** Les coups permis au siège [current]. Jamais vide tant que la partie continue. */
    abstract fun legalMoves(): IntArray

    protected abstract fun apply(move: Int)

    /** Les cartes qui ont voyagé depuis le dernier [drainEvents]. */
    private val pending = ArrayList<CardEvent>()
    protected fun emit(event: CardEvent) { pending.add(event) }

    fun drainEvents(): List<CardEvent> = pending.toList().also { pending.clear() }

    /** Joue [move] pour le siège courant. Un coup défendu est une erreur de programmation, pas un cas de jeu. */
    fun play(move: Int) {
        require(!isOver) { "la partie est finie" }
        require(legalMoves().contains(move)) { "coup défendu : $move" }
        apply(move)
    }

    /** Un coup du siège courant pour ce [level] (voir [CardLevel]). */
    abstract fun aiMove(level: Int, random: Random): Int

    /** Une copie indépendante : les adversaires y essaient des coups sans toucher la vraie partie. */
    abstract fun copy(): CardEngine

    /** Les points de chaque siège (leur sens dépend du jeu : manches gagnées, pénalités…). */
    abstract val scores: IntArray

    /** Les vainqueurs, une fois la partie finie (plusieurs en cas d'égalité). */
    abstract fun winners(): Set<Int>

    // ── Simulation : la recherche par parties au hasard ─────────────────────────────────────

    /**
     * Rebat les cartes qu'on ne voit pas, dans cette copie, comme si elles étaient réparties au
     * hasard — sans contredire ce qu'on a déjà appris (qui n'a plus de cœur, par exemple).
     */
    protected open fun determinize(seat: Int, random: Random) {}

    /** Le coup rapide dont se servent les parties simulées (une règle simple, jamais une recherche). */
    protected open fun rolloutMove(random: Random): Int = legalMoves().let { it[random.nextInt(it.size)] }

    /** La fin d'une partie simulée : par défaut la fin de la partie, mais un jeu à manches s'arrête à la manche. */
    protected open fun simulationDone(): Boolean = isOver

    /** Ce que vaut l'issue d'une simulation pour [seat] : plus c'est grand, mieux c'est. */
    protected open fun utility(seat: Int): Double = if (seat in winners()) 1.0 else 0.0

    /** Garde-fou : une simulation ne dure pas plus longtemps que ça. */
    protected open val simulationLimit: Int = 150

    /**
     * L'IA des niveaux difficiles : pour chaque coup permis, elle imagine [samples] façons dont les
     * cartes cachées pourraient être réparties, joue la fin de la partie avec des règles simples, et
     * garde le coup qui s'en sort le mieux en moyenne.
     *
     * Le hasard des cartes cachées bruite la mesure : on ne quitte donc le coup conseillé par la règle
     * simple ([preferred]) que si un autre fait mieux d'au moins [margin] par partie imaginée. Ainsi la
     * recherche ne fait jamais pire que la règle qu'elle utilise, et corrige quand elle voit clair.
     */
    protected fun monteCarloMove(moves: IntArray, samples: Int, random: Random, preferred: Int = moves[0],
                                 margin: Double = 0.0): Int {
        if (moves.size == 1) return moves[0]
        val seat = current
        val totals = DoubleArray(moves.size)
        // Les mêmes mondes imaginés pour tous les coups : la comparaison est plus juste.
        repeat(samples) {
            val world = copy().also { it.determinize(seat, random) }
            for (i in moves.indices) {
                val sim = world.copy()
                sim.apply(moves[i])
                var guard = simulationLimit
                while (!sim.simulationDone() && guard-- > 0) sim.apply(sim.rolloutMove(random))
                totals[i] += sim.utility(seat)
            }
        }
        var best = 0
        for (i in 1 until moves.size) if (totals[i] > totals[best] + 1e-9) best = i
        val liked = moves.indexOf(preferred).takeIf { it >= 0 } ?: return moves[best]
        return if (totals[best] > totals[liked] + margin * samples) moves[best] else moves[liked]
    }
}
