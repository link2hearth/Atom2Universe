package com.Atom2Universe.app.crypto

import androidx.annotation.StringRes
import com.Atom2Universe.app.R

enum class MainClickerRefreshInterval(
    val preferenceValue: String,
    val intervalMs: Long,
    @StringRes val labelRes: Int
) {
    FIVE_SEC("5s", 5_000L, R.string.crypto_refresh_interval_5s),
    TEN_SEC("10s", 10_000L, R.string.crypto_refresh_interval_10s),
    THIRTY_SEC("30s", 30_000L, R.string.crypto_refresh_interval_30s),
    ONE_MIN("1m", 60_000L, R.string.crypto_refresh_interval_1m),
    TWO_MIN("2m", 120_000L, R.string.crypto_refresh_interval_2m),
    FIVE_MIN("5m", 300_000L, R.string.crypto_refresh_interval_5m);

    companion object {
        private val fallback = ONE_MIN

        fun fromPreference(value: String?): MainClickerRefreshInterval {
            return entries.firstOrNull { it.preferenceValue == value } ?: fallback
        }

        fun fromIndex(index: Int): MainClickerRefreshInterval =
            entries.getOrElse(index) { fallback }
    }
}
