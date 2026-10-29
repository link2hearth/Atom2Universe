package com.Atom2Universe.app.news

import com.Atom2Universe.app.A2UApplication
import com.Atom2Universe.app.R

/**
 * Paliers de fréquence des requêtes RSS du widget News (Google News, NASA…).
 * Le libellé court est affiché tel quel dans le popup, comme pour le widget Crypto.
 */
enum class NewsFetchInterval(val intervalMs: Long) {
    FIVE_MIN(5 * 60_000L),
    FIFTEEN_MIN(15 * 60_000L),
    THIRTY_MIN(30 * 60_000L),
    ONE_HOUR(60 * 60_000L),
    TWO_HOURS(2 * 60 * 60_000L),
    FOUR_HOURS(4 * 60 * 60_000L);

    /** Libellé court dans la langue de l'appli (« 15 min », « 2 h »). */
    val shortLabel: String
        get() {
            val minutes = (intervalMs / 60_000L).toInt()
            val context = A2UApplication.appContext
            return if (minutes < 60) context.getString(R.string.news_interval_minutes, minutes)
                   else context.getString(R.string.news_interval_hours, minutes / 60)
        }

    companion object {
        /** Valeur historique, utilisée quand rien n'est encore enregistré. */
        val DEFAULT = FIFTEEN_MIN

        fun fromIndex(index: Int): NewsFetchInterval = entries.getOrElse(index) { DEFAULT }
    }
}
