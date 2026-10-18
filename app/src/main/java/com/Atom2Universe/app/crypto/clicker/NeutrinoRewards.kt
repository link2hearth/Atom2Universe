package com.Atom2Universe.app.crypto.clicker

import android.content.Context
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.cards.CardGames
import com.Atom2Universe.app.games.kit.KitGames

/**
 * Source unique de vérité des récompenses en neutrinos des jeux.
 *
 * Les jeux appellent ces fonctions/constantes pour créditer le joueur, et la
 * fenêtre d'info du shop du clicker construit son résumé via [summary] à partir
 * des mêmes valeurs. Modifier un montant ICI met à jour le jeu ET l'affichage,
 * sans avoir à toucher le moindre string.
 */
object NeutrinoRewards {

    const val BILLIARD_MEDAL = 3
    const val BILLIARD_LESSON = 3
    const val BILLIARD_CLUB = 25
    const val BILLIARD_CUP = 15
    const val BILLIARD_LEAGUE = 30

    // ── Jeux de plateau (victoire du joueur contre l'IA, selon le niveau) ──────
    const val CHESS_TRAINING = 10
    const val CHESS_STANDARD = 20
    const val CHESS_EXPERT = 50
    const val DRAUGHTS_TRAINING = 10
    const val DRAUGHTS_STANDARD = 20
    const val DRAUGHTS_EXPERT = 50
    const val OTHELLO_WIN = 15

    // ── Montant fixe ───────────────────────────────────────────────────────────
    const val SOLITAIRE_WIN = 20
    const val GAME2048_WIN = 20

    // ── Memory : EASY..EXPERT = 1..6 ───────────────────────────────────────────
    fun memory(difficultyOrdinal: Int) = difficultyOrdinal + 1
    private val MEMORY_VALUES get() = com.Atom2Universe.app.games.memory.MemoryDifficulty.entries.map { memory(it.ordinal) }

    // ── Sudoku : EASY/MEDIUM/HARD ──────────────────────────────────────────────
    private val SUDOKU_VALUES = intArrayOf(5, 10, 20)
    fun sudoku(difficultyOrdinal: Int) = SUDOKU_VALUES[difficultyOrdinal]

    // Puzzle illustré : 24 / 54 / 96 / 150 / 216 / 294 pièces ; sans placement automatique.
    private val JIGSAW_VALUES = intArrayOf(2, 5, 10, 16, 24, 32)
    fun jigsaw(sizeOrdinal: Int) = JIGSAW_VALUES[sizeOrdinal.coerceIn(JIGSAW_VALUES.indices)]
    const val JIGSAW_GUIDE_COST = 1

    // ── ColorStack : EASY/MEDIUM/HARD ──────────────────────────────────────────
    private val COLORSTACK_VALUES = intArrayOf(1, 3, 7)
    fun colorStack(difficultyOrdinal: Int) = COLORSTACK_VALUES[difficultyOrdinal]

    // ── PipeTap : (ordinal+1)×2 → 2..10 ────────────────────────────────────────
    fun pipeTap(difficultyOrdinal: Int) = (difficultyOrdinal + 1) * 2
    private val PIPETAP_VALUES get() = (0 until 5).map { pipeTap(it) }

    // ── Minesweeper : (ordinal+1)×5 → 5..20 ────────────────────────────────────
    fun minesweeper(difficultyOrdinal: Int) = (difficultyOrdinal + 1) * 5
    private val MINESWEEPER_VALUES get() = (0 until 4).map { minesweeper(it) }

    // ── Circles : EASY 1, MEDIUM 2, HARD 3 (4 si ≥ 6 anneaux) ──────────────────
    fun circles(difficultyOrdinal: Int, ringCount: Int): Int = when (difficultyOrdinal) {
        0 -> 1
        1 -> 2
        else -> if (ringCount >= 6) 4 else 3
    }

    // ── The Line : EASY/MEDIUM/HARD = 1/2/3 (par niveau) ───────────────────────
    fun theLine(difficultyOrdinal: Int) = difficultyOrdinal + 1
    private val THELINE_VALUES get() = (0 until 3).map { theLine(it) }

    // ── Sokoban : EASY/MEDIUM/HARD/EXPERT (par niveau résolu) ──────────────────
    private val SOKOBAN_VALUES = intArrayOf(1, 2, 4, 5)
    fun sokoban(difficultyOrdinal: Int) = SOKOBAN_VALUES[difficultyOrdinal]

    // ── Équilibre : EASY à EXTREME, par niveau réussi ─────────────────────────
    // Chaque test qui n'aboutit pas (raté ou interrompu) retire un neutrino,
    // sans jamais descendre sous 1 : réfléchir avant de relâcher le levier paie.
    private val BALANCE_VALUES = intArrayOf(3, 6, 12, 20, 32)
    const val BALANCE_RETRY_PENALTY = 1
    fun balance(difficultyOrdinal: Int, failedAttempts: Int = 0) =
        (BALANCE_VALUES[difficultyOrdinal] - failedAttempts * BALANCE_RETRY_PENALTY)
            .coerceAtLeast(1)

    // ── Mobile (variante d'Équilibre) : EASY à EXPERT, par niveau réussi ───────
    private val MOBILE_VALUES = intArrayOf(3, 6, 12, 20)
    fun mobile(difficultyOrdinal: Int) = MOBILE_VALUES[difficultyOrdinal]

    // ── StarBridges : selon la taille de la grille ─────────────────────────────
    fun starBridges(size: Int) = when (size) { 6 -> 5; 7 -> 10; else -> 15 }

    // ── Escape the Labyrinth : EASY/MEDIUM/HARD = 2/5/10, ×2 si parfait ────────
    private val ESCAPE_VALUES = intArrayOf(2, 5, 10)
    fun escape(difficultyOrdinal: Int, perfect: Boolean): Int {
        val base = ESCAPE_VALUES[difficultyOrdinal]
        return if (perfect) base * 2 else base
    }

    // ── Intrication (ex-Link) : à la fin de la partie, selon le score ───────────
    // Une partie de 15 étages jouée sans rien annuler vaut ~23 000 points, soit ~150
    // neutrinos pour une bonne demi-heure : le rythme des jeux payés au temps.
    const val LINK_POINTS_PER_NEUTRINO = 150
    fun link(score: Int) = score / LINK_POINTS_PER_NEUTRINO

    // ── Trébuchet : de 10 à 50 selon le site rasé ──────────────────────────────
    //
    // La récompense ne dépend **pas du nombre de tirs**, et c'est délibéré : au
    // trébuchet on règle sa machine sur plusieurs coups avant d'être sûr de toucher où
    // l'on vise. Compter les tirs punirait le réglage, c'est-à-dire le jeu lui-même.
    //
    // Elle dépend de ce que le site oppose : **de quoi il est fait** et **combien il en
    // faut**. Un hameau de bois de vingt-six corps vaut le minimum ; une métropole de
    // béton et d'acier de cent corps vaut le maximum. Les deux mesures existent déjà
    // sur la construction — `Structure.masonryShare` et le nombre de blocs — et rien
    // n'a eu besoin d'être inventé pour ça.
    const val TREBUCHET_MIN = 10
    const val TREBUCHET_MAX = 50

    /**
     * [difficulty] va de 0 (un hameau de bois) à 1 (une métropole de béton), et vient de
     * `TargetGenerator.difficulty` — **elle ne se recalcule pas ici**. Le même chiffre
     * décide de l'objectif de tirs du niveau ; deux calculs séparés finiraient par se
     * contredire, et un site paierait comme un facile en se jouant comme un difficile.
     */
    fun trebuchet(difficulty: Float): Int =
        (TREBUCHET_MIN + (TREBUCHET_MAX - TREBUCHET_MIN) * difficulty.coerceIn(0f, 1f))
            .toInt()
            .coerceIn(TREBUCHET_MIN, TREBUCHET_MAX)

    // ── Nucléa : 2 neutrinos par vague terminée ────────────────────────────────
    const val NUCLEA_PER_WAVE = 2
    fun nuclea(wavesCleared: Int) = wavesCleared * NUCLEA_PER_WAVE

    // ── Réflexes : 1 neutrino par tranche de 5 000 points ─────────────────────
    // Le combo multiplie les points : bien jouer rapporte plus vite que durer.
    const val REFLEX_POINTS_PER_NEUTRINO = 5000
    fun reflex(score: Long) = (score / REFLEX_POINTS_PER_NEUTRINO).toInt()

    // ── Accrétion : 1 neutrino par tranche de 150 points ──────────────────────
    // Une partie jouée au hasard fait ~4 400 points en ~175 lâchers, soit un neutrino
    // toutes les douze secondes environ — le rythme des jeux payés au temps. Les chaînes
    // et les gros astres d'un joueur qui vise font monter ce rythme.
    const val BIGGER_POINTS_PER_NEUTRINO = 150
    fun bigger(score: Int) = score / BIGGER_POINTS_PER_NEUTRINO

    // ── Puzzles du kit (Simon Tatham) : par grille résolue, dans l'ordre des pastilles ──
    // Repère : le rythme des jeux payés au temps (1 neutrino / 15 s). Une grille se paie à
    // sa première résolution seulement, mais « Nouvelle partie » en fournit sans fin : les
    // tailles qui se règlent en quelques secondes (taquin 3×3, cube 4×4…) ne valent que 1,
    // sinon elles battraient tous les autres jeux. Les plateaux fixes du solitaire (croix,
    // octogone) se rejouent de mémoire : 5 seulement. Le mode Images (Logimage, Mosaïque) ne
    // rapporte rien, c'est voulu.
    private val KIT_VALUES = mapOf(
        "fifteen" to intArrayOf(1, 5, 10),
        "flip" to intArrayOf(1, 4, 6, 1, 5, 8),
        "sixteen" to intArrayOf(1, 5, 10),
        "twiddle" to intArrayOf(1, 5, 8, 12),
        "pegs" to intArrayOf(3, 6, 10, 5, 5),
        "untangle" to intArrayOf(1, 4, 7, 12, 18),
        "flood" to intArrayOf(2, 4, 8, 12),
        "samegame" to intArrayOf(2, 5, 10, 15),
        "guess" to intArrayOf(2, 5, 10),
        "towers" to intArrayOf(3, 6, 10),
        "unequal" to intArrayOf(3, 5, 8, 12),
        "keen" to intArrayOf(3, 6, 10),
        "unruly" to intArrayOf(2, 5, 9, 14),
        "lightup" to intArrayOf(3, 6, 10),
        "pattern" to intArrayOf(3, 6, 12, 20, 0),
        "rect" to intArrayOf(3, 6, 10),
        "dominosa" to intArrayOf(1, 4, 7, 11),
        "tents" to intArrayOf(3, 5, 8, 12),
        "singles" to intArrayOf(3, 5, 8, 12),
        "mosaic" to intArrayOf(3, 6, 10, 0),
        "range" to intArrayOf(3, 6, 10),
        "signpost" to intArrayOf(3, 5, 8, 12),
        "blackbox" to intArrayOf(3, 6, 10, 14),
        "map" to intArrayOf(3, 6, 10),
        "filling" to intArrayOf(3, 6, 10),
        "slant" to intArrayOf(3, 6, 10, 14),
        "netslide" to intArrayOf(1, 6, 10, 15),
        "inertia" to intArrayOf(3, 6, 10),
        "loopy" to intArrayOf(3, 6, 10),
        "pearl" to intArrayOf(3, 6, 10),
        "tracks" to intArrayOf(3, 6, 10),
        "magnets" to intArrayOf(3, 6, 10),
        "undead" to intArrayOf(3, 6, 10),
        "palisade" to intArrayOf(3, 6, 10),
        "galaxies" to intArrayOf(3, 6, 10),
        "cube" to intArrayOf(1, 5, 8),
    )

    // ── Jeux de cartes contre l'IA (OpenSpiel) : par partie gagnée, selon le niveau ──
    // Facile / moyen / difficile. Une manche de huit américain dure quelques minutes ; une partie
    // de dame de pique, une dizaine : elle paie plus.
    private val CARD_VALUES = mapOf(
        "crazyeights" to intArrayOf(3, 6, 12),
        "hearts" to intArrayOf(8, 16, 30),
        "spades" to intArrayOf(10, 20, 36),
        "cribbage" to intArrayOf(8, 16, 30),
        "ginrummy" to intArrayOf(6, 12, 25),
        "gofish" to intArrayOf(3, 6, 12),
        "euchre" to intArrayOf(8, 16, 30),
        "ohhell" to intArrayOf(8, 16, 30),
    )

    /** La récompense d'une partie de cartes gagnée : [game] est la clé du jeu, [level] 0 à 2. */
    fun cards(game: String, level: Int) = CARD_VALUES[game]?.getOrNull(level) ?: 0

    /**
     * Le prix d'une astuce dans un puzzle du kit (un indice, ou la solution selon le jeu). Une
     * partie aidée ne rapporte plus rien.
     */
    const val HINT_COST = 5

    /** La récompense d'une grille du kit : [game] est la clé du jeu, [preset] sa pastille. */
    fun kit(game: String, preset: Int) = KIT_VALUES[game]?.getOrNull(preset) ?: 0

    // ── Temps de jeu : 1 neutrino par tranche de 15 s ──────────────────────────
    const val SECONDS_PER_NEUTRINO = 15
    fun perTime(elapsedMs: Long) = (elapsedMs / (SECONDS_PER_NEUTRINO * 1000L)).toInt()

    // ── Distance : 1 neutrino par tranche de 500 m ─────────────────────────────
    const val METERS_PER_NEUTRINO = 500
    fun perDistance(distanceM: Float) = (distanceM / METERS_PER_NEUTRINO).toInt()

    // ═══════════════════════════════════════════════════════════════════════════
    //  Résumé pour la fenêtre d'info du shop — généré à partir des valeurs ci-dessus
    // ═══════════════════════════════════════════════════════════════════════════

    /** Une ligne du résumé : nom du jeu, valeur(s) affichée(s), note explicative. */
    data class Entry(val titleRes: Int, val value: String, val noteRes: Int)

    fun summary(context: Context): List<Entry> {
        fun list(values: List<Int>) = values.joinToString(" / ")
        val dash = "—"
        return listOf(
            // Plateau
            Entry(R.string.chess_title, "$CHESS_TRAINING / $CHESS_STANDARD / $CHESS_EXPERT", R.string.neutrino_info_note_vs_ai),
            Entry(R.string.draughts_title, "$DRAUGHTS_TRAINING / $DRAUGHTS_STANDARD / $DRAUGHTS_EXPERT", R.string.neutrino_info_note_vs_ai),
            Entry(R.string.othello_title, "$OTHELLO_WIN", R.string.neutrino_info_note_solo_win),
            Entry(R.string.billiard_title, list(listOf(BILLIARD_MEDAL, BILLIARD_CLUB, BILLIARD_CUP, BILLIARD_LEAGUE).distinct().sorted()), R.string.billiard_reward_info),
            // Cartes / casino
            Entry(R.string.solitaire_title, "$SOLITAIRE_WIN", R.string.neutrino_info_note_win),
            Entry(R.string.memory_title, list(MEMORY_VALUES), R.string.neutrino_info_note_difficulty),
            Entry(R.string.blackjack_title, dash, R.string.neutrino_info_note_betting),
            Entry(R.string.roulette_title, dash, R.string.neutrino_info_note_betting),
            // Puzzle
            Entry(R.string.jigsaw_title, list(JIGSAW_VALUES.toList()), R.string.neutrino_info_note_difficulty),
            Entry(R.string.game2048_title, "$GAME2048_WIN", R.string.neutrino_info_note_win),
            Entry(R.string.sudoku_title, list(SUDOKU_VALUES.toList()), R.string.neutrino_info_note_difficulty),
            Entry(R.string.minesweeper_title, list(MINESWEEPER_VALUES), R.string.neutrino_info_note_difficulty),
            Entry(R.string.color_stack_title, list(COLORSTACK_VALUES.toList()), R.string.neutrino_info_note_difficulty),
            Entry(R.string.pipetap_title, list(PIPETAP_VALUES), R.string.neutrino_info_note_difficulty),
            Entry(
                R.string.circles_hub_title,
                "${circles(0, 3)} / ${circles(1, 4)} / ${circles(2, 5)} (${circles(2, 6)})",
                R.string.neutrino_info_note_difficulty
            ),
            Entry(
                R.string.starbridges_title,
                "${starBridges(6)} / ${starBridges(7)} / ${starBridges(8)}",
                R.string.neutrino_info_note_size
            ),
            Entry(R.string.balance_title, list(BALANCE_VALUES.toList()), R.string.neutrino_info_note_retry),
            Entry(R.string.mobile_title, list(MOBILE_VALUES.toList()), R.string.neutrino_info_note_difficulty),
            Entry(R.string.the_line_title, list(THELINE_VALUES), R.string.neutrino_info_note_level),
            Entry(R.string.sokoban_title, list(SOKOBAN_VALUES.toList()), R.string.neutrino_info_note_level),
            Entry(
                R.string.link_title,
                "1 / $LINK_POINTS_PER_NEUTRINO",
                R.string.neutrino_info_note_score
            ),
            Entry(R.string.escape_title, list(ESCAPE_VALUES.toList()), R.string.neutrino_info_note_perfect),
            // Arcade
            Entry(
                R.string.trebuchet_title,
                "$TREBUCHET_MIN – $TREBUCHET_MAX",
                R.string.neutrino_info_note_site
            ),
            Entry(R.string.nuclea_title, "$NUCLEA_PER_WAVE", R.string.neutrino_info_note_wave),
            Entry(R.string.reflex_title, "1 / $REFLEX_POINTS_PER_NEUTRINO", R.string.neutrino_info_note_score),
            Entry(R.string.bigger_title, "1 / $BIGGER_POINTS_PER_NEUTRINO", R.string.neutrino_info_note_score),
            Entry(R.string.orbite_title, "1 / $SECONDS_PER_NEUTRINO s", R.string.neutrino_info_note_time),
            Entry(R.string.hex_runner_hub_title, "1 / $SECONDS_PER_NEUTRINO s", R.string.neutrino_info_note_time),
            Entry(R.string.match3_title, "1 / $SECONDS_PER_NEUTRINO s", R.string.neutrino_info_note_time),
            Entry(R.string.motocross_title, "1 / $METERS_PER_NEUTRINO m", R.string.neutrino_info_note_distance),
            Entry(R.string.cosmo_run_hub_title, "1 / $METERS_PER_NEUTRINO m", R.string.neutrino_info_note_distance)
        ) + KitGames.titles().mapNotNull { (id, title) ->
            // Les puzzles du kit, à la suite, dans l'ordre du hub (les 0 ne s'affichent pas).
            KIT_VALUES[id]?.let { v -> Entry(title, list(v.filter { it > 0 }), R.string.neutrino_info_note_size) }
        } + CardGames.titles().mapNotNull { (id, title) ->
            // Les jeux de cartes, après les puzzles.
            CARD_VALUES[id]?.let { v -> Entry(title, list(v.toList()), R.string.neutrino_info_note_vs_ai) }
        }
    }
}
