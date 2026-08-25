package com.Atom2Universe.app.crypto

import androidx.annotation.StringRes
import com.Atom2Universe.app.R

/**
 * Intervalle d'une bougie, mappé sur le paramètre `interval` de l'endpoint klines
 * de Binance (`/api/v3/klines`). Pour chaque intervalle on télécharge [FETCH_LIMIT]
 * bougies ; le graphique en affiche une fenêtre et permet de glisser/zoomer.
 */
enum class CryptoCandleInterval(
    @StringRes val labelRes: Int,
    val binanceInterval: String,
    /** Durée approximative d'une bougie en millisecondes (le mois est pris à 30 jours). */
    val approxMillis: Long
) {
    M1(R.string.crypto_chart_int_1m, "1m", 60_000L),
    M5(R.string.crypto_chart_int_5m, "5m", 5 * 60_000L),
    M15(R.string.crypto_chart_int_15m, "15m", 15 * 60_000L),
    H1(R.string.crypto_chart_int_1h, "1h", 3_600_000L),
    H4(R.string.crypto_chart_int_4h, "4h", 4 * 3_600_000L),
    D1(R.string.crypto_chart_int_1d, "1d", 86_400_000L),
    W1(R.string.crypto_chart_int_1w, "1w", 7 * 86_400_000L),
    MN1(R.string.crypto_chart_int_1mo, "1M", 30 * 86_400_000L);

    companion object {
        const val FETCH_LIMIT = 1000
    }
}
