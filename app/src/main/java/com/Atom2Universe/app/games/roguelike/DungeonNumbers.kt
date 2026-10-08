package com.Atom2Universe.app.games.roguelike

import android.content.Context
import com.Atom2Universe.app.R
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.round

/**
 * Abréviation des grands nombres du Donjon : 12 345 reste « 12345 », 290 123 devient « 290k »,
 * 1 250 000 devient « 1,25M » en français et « 1.25M » en anglais.
 *
 * Pourquoi pas le formateur du clicker ([com.Atom2Universe.app.crypto.clicker.engine.LayeredNumber]) ?
 * Il travaille en notation scientifique (« 1,25e6 ») ou en lettres (« 1.25a ») pour des nombres
 * astronomiques, et n'abrège qu'à partir du million : ce n'est pas la lecture « k / M » voulue ici.
 *
 * Le calcul pur ([split], [format] avec des suffixes fournis) ne touche pas Android, pour être
 * testé sans appareil ; seul [format] avec un [Context] va chercher les suffixes dans les ressources.
 */
object DungeonNumbers {

    /** En dessous, le nombre s'affiche en entier : les petits nombres du début ne changent pas. */
    const val ABBREVIATE_FROM = 100_000L

    /**
     * Un nombre découpé pour l'affichage : [mantissa] arrondie à [decimals] décimales, et l'index
     * du suffixe (0 = k, 1 = M, 2 = milliard…) ; [suffixIndex] vaut -1 quand on n'abrège pas.
     */
    data class Split(val mantissa: Double, val decimals: Int, val suffixIndex: Int)

    /** Nombre de paliers de suffixes connus (k, M, B, T, Qa, Qi) : de quoi couvrir tout un Long. */
    const val SUFFIX_COUNT = 6

    /** Découpe [value] : environ 3 chiffres significatifs, en passant au palier suivant si l'arrondi déborde (999 999 → 1M). */
    fun split(value: Long): Split {
        val a = abs(value.toDouble())
        if (abs(value) < ABBREVIATE_FROM) return Split(value.toDouble(), 0, -1)
        var index = 0
        var m = a / 1_000.0
        while (m >= 1_000.0 && index < SUFFIX_COUNT - 1) { m /= 1_000.0; index++ }
        var decimals = decimalsFor(m)
        var rounded = roundTo(m, decimals)
        if (rounded >= 1_000.0 && index < SUFFIX_COUNT - 1) {
            index++
            m /= 1_000.0
            decimals = decimalsFor(m)
            rounded = roundTo(m, decimals)
        }
        val sign = if (value < 0) -1.0 else 1.0
        return Split(sign * rounded, decimals, index)
    }

    private fun decimalsFor(m: Double) = when {
        m >= 100.0 -> 0
        m >= 10.0 -> 1
        else -> 2
    }

    private fun roundTo(m: Double, decimals: Int): Double {
        var f = 1.0
        repeat(decimals) { f *= 10.0 }
        return round(m * f) / f
    }

    /** Version pure : [suffixes] dans l'ordre k, M, B… ; le séparateur décimal suit [locale]. */
    fun format(value: Long, locale: Locale, suffixes: List<String>): String {
        val s = split(value)
        if (s.suffixIndex < 0) return String.format(locale, "%d", value)
        val nf = NumberFormat.getNumberInstance(locale).apply {
            isGroupingUsed = false
            minimumFractionDigits = 0          // « 1,2M » plutôt que « 1,20M »
            maximumFractionDigits = s.decimals
        }
        return nf.format(s.mantissa) + suffixes[s.suffixIndex.coerceAtMost(suffixes.lastIndex)]
    }

    /** Abrège [value] selon la langue de l'appli, suffixes tirés de `roguelike_number_suffixes`. */
    fun format(context: Context, value: Long): String {
        val res = context.resources
        // Chemin court (appelé à chaque image pour les PV) : pas de ressources à lire sous le seuil
        if (abs(value) < ABBREVIATE_FROM) return String.format(res.configuration.locales[0] ?: Locale.getDefault(), "%d", value)
        val locale = res.configuration.locales[0] ?: Locale.getDefault()
        return format(value, locale, res.getStringArray(R.array.roguelike_number_suffixes).toList())
    }

    fun format(context: Context, value: Int): String = format(context, value.toLong())
}

