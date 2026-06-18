package com.Atom2Universe.app.crypto.clicker

import com.Atom2Universe.app.crypto.clicker.engine.LayeredNumber
import kotlin.math.*

internal object ClickerShopEngine {

    const val MAX_LEVEL = Int.MAX_VALUE

    private const val VISIBLE_MAX = 20
    private const val INTERNAL_MAX = 120
    private const val POWER = 0.5
    private const val POST_CURVE_OFFSET = 100
    private const val BONUS_EXPONENT = 30.0
    private const val BONUS_DENOMINATOR = 999.0
    private val RATIO_A = 295703.0 / 98901.0
    private val RATIO_B = 9091.0 / 899100.0
    private val RATIO_C = -1.0 / 9890100.0

    // Rabais exponentiel borné (bonus Big Bang « Compression de l'espace-temps ») :
    // retranche une pente au log du prix au-delà d'un seuil. Borné sous RATIO_B pour
    // garantir un prix toujours croissant (jamais gratuit), donc le mur subsiste, adouci.
    private const val DISCOUNT_THRESHOLD = 100        // pas d'effet au niveau <= 100
    private const val DISCOUNT_STEP = 0.0001          // réduction de pente par niveau Big Bang
    private const val DISCOUNT_MAX = 0.007            // plafond (< RATIO_B), atteint à 70 niveaux

    private fun slopeReduction(discountLevel: Int): Double =
        if (discountLevel <= 0) 0.0 else minOf(discountLevel * DISCOUNT_STEP, DISCOUNT_MAX)

    private fun effectiveIndex(level: Int): Int {
        val n = level.coerceAtLeast(0)
        if (n <= 0) return 0
        return if (n <= VISIBLE_MAX) {
            val span = (VISIBLE_MAX - 1).toDouble()
            val t = ((n - 1) / span).coerceIn(0.0, 1.0)
            val curved = t.pow(POWER)
            1 + (curved * (INTERNAL_MAX - 1)).roundToInt()
        } else {
            n + POST_CURVE_OFFSET
        }
    }

    private fun logBonus(level: Int): Double {
        val idx = effectiveIndex(level)
        if (idx <= 0) return Double.NEGATIVE_INFINITY
        return BONUS_EXPONENT * (idx - 1) / BONUS_DENOMINATOR
    }

    fun bonus(level: Int): LayeredNumber {
        if (level <= 0) return LayeredNumber.zero()
        val lb = logBonus(level)
        val v = 10.0.pow(lb)
        if (!v.isFinite() || v <= 0) return LayeredNumber.zero()
        return LayeredNumber(maxOf(1.0, floor(v)))
    }

    private fun logCost(level: Int, discountLevel: Int): Double {
        val idx = effectiveIndex(level)
        if (idx <= 0) return Double.NEGATIVE_INFINITY
        val lb = logBonus(level)
        val lr = RATIO_A + RATIO_B * idx + RATIO_C * idx * idx
        var lc = lb + lr
        val sr = slopeReduction(discountLevel)
        if (sr > 0.0 && level > DISCOUNT_THRESHOLD) {
            lc -= sr * (level - DISCOUNT_THRESHOLD)
        }
        return lc
    }

    fun cost(level: Int, discountLevel: Int = 0): LayeredNumber {
        val lc = logCost(level, discountLevel)
        val c = 10.0.pow(lc)
        if (!c.isFinite() || c <= 0) return LayeredNumber.zero()
        return LayeredNumber(maxOf(1.0, floor(c)))
    }

    fun batchCost(fromLevel: Int, quantity: Int, discountLevel: Int = 0): LayeredNumber {
        var total = LayeredNumber.zero()
        for (i in 0 until quantity) {
            total = total.add(cost(fromLevel + i + 1, discountLevel))
        }
        return total
    }

    fun effectiveBuyAmount(currentLevel: Int, requested: Int): Int =
        requested.coerceAtLeast(0)
}
