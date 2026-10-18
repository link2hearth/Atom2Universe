package com.Atom2Universe.app.crypto

enum class CryptoComparisonWindow(
    val label: String,
    val klineInterval: String,
    val klineLimit: Int
) {
    LAST_UPDATE("—",    "",    0),
    FIVE_MIN(   "5 min","1m",  5),
    FIFTEEN_MIN("15 min","1m", 15),
    THIRTY_MIN( "30 min","1m", 30),
    ONE_HOUR(   "1h",   "1m",  60),
    TWO_HOURS(  "2h",   "1h",   2),
    FOUR_HOURS( "4h",   "1h",   4),
    SIX_HOURS(  "6h",   "1h",   6),
    TWELVE_HOURS("12h", "1h",  12),
    TWENTY_FOUR_HOURS("24h","1h",24);

    val usesKlines get() = klineLimit > 0

    companion object {
        fun fromIndex(index: Int) = entries.getOrElse(index) { LAST_UPDATE }
    }
}
