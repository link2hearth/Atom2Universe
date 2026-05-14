package com.Atom2Universe.app.crypto.clicker

import android.content.Context
import com.Atom2Universe.app.crypto.clicker.engine.LayeredNumber

class ClickerStatsRepository(context: Context) {

    private val prefs = context.getSharedPreferences("clicker_stats", Context.MODE_PRIVATE)

    fun load() = ClickerStats(
        totalClicks           = prefs.getLong("totalClicks", 0L),
        clicksDuringFrenzy    = prefs.getLong("clicksDuringFrenzy", 0L),
        apcFrenzyCount        = prefs.getInt("apcFrenzyCount", 0),
        apsFrenzyCount        = prefs.getInt("apsFrenzyCount", 0),
        maxCps                = prefs.getFloat("maxCps", 0f).toDouble(),
        maxClicksPerApcFrenzy = prefs.getInt("maxClicksPerApcFrenzy", 0),
        totalPlayTimeMs       = prefs.getLong("totalPlayTimeMs", 0L),
        lifetimeApcAtoms      = loadLayeredNumber("apc"),
        lifetimeApsAtoms      = loadLayeredNumber("aps"),
        spentFromApc          = loadLayeredNumber("spent_apc"),
        spentFromAps          = loadLayeredNumber("spent_aps")
    )

    fun save(stats: ClickerStats) {
        prefs.edit()
            .putLong("totalClicks",           stats.totalClicks)
            .putLong("clicksDuringFrenzy",    stats.clicksDuringFrenzy)
            .putInt("apcFrenzyCount",         stats.apcFrenzyCount)
            .putInt("apsFrenzyCount",         stats.apsFrenzyCount)
            .putFloat("maxCps",               stats.maxCps.toFloat())
            .putInt("maxClicksPerApcFrenzy",  stats.maxClicksPerApcFrenzy)
            .putLong("totalPlayTimeMs",       stats.totalPlayTimeMs)
            .also { saveLayeredNumber(it, "apc",      stats.lifetimeApcAtoms) }
            .also { saveLayeredNumber(it, "aps",      stats.lifetimeApsAtoms) }
            .also { saveLayeredNumber(it, "spent_apc", stats.spentFromApc) }
            .also { saveLayeredNumber(it, "spent_aps", stats.spentFromAps) }
            .apply()
    }

    fun reset() {
        prefs.edit().clear().apply()
    }

    private fun loadLayeredNumber(prefix: String): LayeredNumber =
        LayeredNumber(mapOf(
            "sign"     to prefs.getInt("${prefix}_sign", 0),
            "layer"    to prefs.getInt("${prefix}_layer", 0),
            "mantissa" to Double.fromBits(prefs.getLong("${prefix}_mantissa", 0L)),
            "exponent" to Double.fromBits(prefs.getLong("${prefix}_exponent", 0L)),
            "value"    to Double.fromBits(prefs.getLong("${prefix}_value", 0L))
        ))

    private fun saveLayeredNumber(
        editor: android.content.SharedPreferences.Editor,
        prefix: String,
        n: LayeredNumber
    ): android.content.SharedPreferences.Editor = editor
        .putInt("${prefix}_sign",            n.sign)
        .putInt("${prefix}_layer",           n.layer)
        .putLong("${prefix}_mantissa",       n.mantissa.toBits())
        .putLong("${prefix}_exponent",       n.exponent.toBits())
        .putLong("${prefix}_value",          n.value.toBits())
}
