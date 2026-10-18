package com.Atom2Universe.app.crypto

import androidx.annotation.StringRes
import com.Atom2Universe.app.R

enum class MainClickerBackgroundInterval(
    val preferenceValue: String,
    val intervalMs: Long,
    @StringRes val labelRes: Int
) {
    OFF("off", 0L, R.string.crypto_background_interval_off),
    ONE_MIN("1", 60_000L, R.string.crypto_background_interval_1m),
    TWO_MIN("2", 120_000L, R.string.crypto_background_interval_2m),
    FIVE_MIN("5", 300_000L, R.string.crypto_background_interval_5m),
    TEN_MIN("10", 600_000L, R.string.crypto_background_interval_10m);

    companion object {
        private val fallback = ONE_MIN

        fun fromPreference(value: String?): MainClickerBackgroundInterval {
            return entries.firstOrNull { it.preferenceValue == value } ?: fallback
        }
    }
}
