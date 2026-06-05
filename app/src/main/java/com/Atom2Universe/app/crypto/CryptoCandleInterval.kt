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
    val binanceInterval: String
) {
    M1(R.string.crypto_chart_int_1m, "1m"),
    M5(R.string.crypto_chart_int_5m, "5m"),
    M15(R.string.crypto_chart_int_15m, "15m"),
    H1(R.string.crypto_chart_int_1h, "1h"),
    H4(R.string.crypto_chart_int_4h, "4h"),
    D1(R.string.crypto_chart_int_1d, "1d"),
    W1(R.string.crypto_chart_int_1w, "1w"),
    MN1(R.string.crypto_chart_int_1mo, "1M");

    companion object {
        const val FETCH_LIMIT = 1000
    }
}
