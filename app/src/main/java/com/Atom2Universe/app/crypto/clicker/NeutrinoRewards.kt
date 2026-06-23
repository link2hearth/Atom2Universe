package com.Atom2Universe.app.crypto.clicker

import android.content.Context
import com.Atom2Universe.app.R

/**
 * Source unique de vérité des récompenses en neutrinos des jeux.
 *
 * Les jeux appellent ces fonctions/constantes pour créditer le joueur, et la
 * fenêtre d'info du shop du clicker construit son résumé via [summary] à partir
 * des mêmes valeurs. Modifier un montant ICI met à jour le jeu ET l'affichage,
 * sans avoir à toucher le moindre string.
 */
object NeutrinoRewards {

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

    // ── Memory : EASY..HARD = 1..5 ─────────────────────────────────────────────
    fun memory(difficultyOrdinal: Int) = difficultyOrdinal + 1
    private val MEMORY_VALUES get() = (0 until 5).map { memory(it) }

    // ── Sudoku : EASY/MEDIUM/HARD ──────────────────────────────────────────────
    private val SUDOKU_VALUES = intArrayOf(5, 10, 20)
    fun sudoku(difficultyOrdinal: Int) = SUDOKU_VALUES[difficultyOrdinal]

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

    // ── StarBridges : selon la taille de la grille ─────────────────────────────
    fun starBridges(size: Int) = when (size) { 6 -> 5; 7 -> 10; else -> 15 }

    // ── Escape the Labyrinth : EASY/MEDIUM/HARD = 2/5/10, ×2 si parfait ────────
    private val ESCAPE_VALUES = intArrayOf(2, 5, 10)
    fun escape(difficultyOrdinal: Int, perfect: Boolean): Int {
        val base = ESCAPE_VALUES[difficultyOrdinal]
        return if (perfect) base * 2 else base
    }

    // ── Link : base difficulté × multiplicateur jumelles ───────────────────────
    fun linkBase(difficultyOrdinal: Int) = (difficultyOrdinal + 1) * 2   // 2/4/6
    fun linkMultiplier(pairsOrdinal: Int) = pairsOrdinal + 1             // 1/2/3
    fun link(difficultyOrdinal: Int, pairsOrdinal: Int) =
        linkBase(difficultyOrdinal) * linkMultiplier(pairsOrdinal)

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
            // Cartes / casino
            Entry(R.string.solitaire_title, "$SOLITAIRE_WIN", R.string.neutrino_info_note_win),
            Entry(R.string.memory_title, list(MEMORY_VALUES), R.string.neutrino_info_note_difficulty),
            Entry(R.string.blackjack_title, dash, R.string.neutrino_info_note_betting),
            Entry(R.string.roulette_title, dash, R.string.neutrino_info_note_betting),
            // Puzzle
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
            Entry(R.string.the_line_title, list(THELINE_VALUES), R.string.neutrino_info_note_level),
            Entry(R.string.sokoban_title, list(SOKOBAN_VALUES.toList()), R.string.neutrino_info_note_level),
            Entry(
                R.string.link_title,
                "${linkBase(0)}/${linkBase(1)}/${linkBase(2)} × ${linkMultiplier(0)}/${linkMultiplier(1)}/${linkMultiplier(2)}",
                R.string.neutrino_info_note_pairs
            ),
            Entry(R.string.escape_title, list(ESCAPE_VALUES.toList()), R.string.neutrino_info_note_perfect),
            // Arcade
            Entry(R.string.orbite_title, "1 / $SECONDS_PER_NEUTRINO s", R.string.neutrino_info_note_time),
            Entry(R.string.hex_runner_hub_title, "1 / $SECONDS_PER_NEUTRINO s", R.string.neutrino_info_note_time),
            Entry(R.string.match3_title, "1 / $SECONDS_PER_NEUTRINO s", R.string.neutrino_info_note_time),
            Entry(R.string.motocross_title, "1 / $METERS_PER_NEUTRINO m", R.string.neutrino_info_note_distance)
        )
    }
}
