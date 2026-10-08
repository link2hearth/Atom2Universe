package com.Atom2Universe.app.games.roguelike

/** Règles communes au rendu, à la reconnaissance des gestes et aux simulations. */
internal object CombatTiming {
    const val STRIKE_MS = 1200L
    const val WINDUP_MS = 850L
    const val STRIKE_GOOD = 0.13f
    const val STRIKE_PERFECT = 0.045f
    const val PARRY_GOOD_MS = 160f
    const val PARRY_PERFECT_MS = 70f
    const val MAX_DURATION_BONUS = 0.15f
    const val MAX_WINDOW_BONUS = 0.20f

    private fun duration(hero: Hero?) = 1f + MAX_DURATION_BONUS * (hero?.timingAssistance ?: 0f)
    private fun width(hero: Hero?) = 1f + MAX_WINDOW_BONUS * (hero?.timingAssistance ?: 0f)
    fun strikeMs(hero: Hero?) = (STRIKE_MS * duration(hero)).toLong()
    fun windupMs(hero: Hero?) = (WINDUP_MS * duration(hero)).toLong()
    fun strikeGood(hero: Hero?) = STRIKE_GOOD * width(hero)
    fun strikePerfect(hero: Hero?) = STRIKE_PERFECT * width(hero)
    fun parryGood(hero: Hero?) = PARRY_GOOD_MS * width(hero) * duration(hero)
    fun parryPerfect(hero: Hero?) = PARRY_PERFECT_MS * width(hero) * duration(hero)
}
