package com.Atom2Universe.app.games.mahjong

import java.time.LocalDate

/** Prices grow with purchases, not time: the player chooses any locked design. */
internal object MahjongCollection {
    fun price(purchases: Int): Int {
        val n = purchases.coerceIn(0, 20)
        return 350 + 100 * n + 12 * n * n
    }
    fun featuredMode(day: Long) = MahjongMode.entries[Math.floorMod(day, 6L).toInt()]
    fun isWeekend(day: Long) = LocalDate.ofEpochDay(day).dayOfWeek.value >= 6
    fun multiplier(mode: MahjongMode, day: Long) = if (isWeekend(day) || mode == featuredMode(day)) 2 else 1
    // Score is restored by undo, so replaying a match cannot farm pearls.
    fun comboBonus(game: MahjongGame) = ((game.score / 100 - game.matches).coerceAtLeast(0) / 2)
}
