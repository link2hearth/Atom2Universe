package com.Atom2Universe.app.news

/**
 * Paliers de fréquence des requêtes RSS du widget News (Google News, NASA…).
 * Le libellé court est affiché tel quel dans le popup, comme pour le widget Crypto.
 */
enum class NewsFetchInterval(
    val intervalMs: Long,
    val shortLabel: String
) {
    FIVE_MIN(5 * 60_000L, "5 min"),
    FIFTEEN_MIN(15 * 60_000L, "15 min"),
    THIRTY_MIN(30 * 60_000L, "30 min"),
    ONE_HOUR(60 * 60_000L, "1 h"),
    TWO_HOURS(2 * 60 * 60_000L, "2 h"),
    FOUR_HOURS(4 * 60 * 60_000L, "4 h");

    companion object {
        /** Valeur historique, utilisée quand rien n'est encore enregistré. */
        val DEFAULT = FIFTEEN_MIN

        fun fromIndex(index: Int): NewsFetchInterval = entries.getOrElse(index) { DEFAULT }
    }
}
