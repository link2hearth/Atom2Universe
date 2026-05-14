package com.Atom2Universe.app.crypto.clicker.engine

import java.util.Locale
import kotlin.math.*

/**
 * Port fidèle de la classe JS LayeredNumber (layered-number.js).
 * Gère des nombres arbitrairement grands via un système à deux couches :
 *   - layer 0 : mantisse × 10^exposant  (notation scientifique standard)
 *   - layer 1 : 10^value                (pour exposant >= LAYER1_THRESHOLD)
 */
class LayeredNumber {

    var sign: Int = 0
    var layer: Int = 0
    var mantissa: Double = 0.0
    var exponent: Double = 0.0
    var value: Double = 0.0

    // ─── Constructeurs ────────────────────────────────────────────────────────

    constructor()

    constructor(num: Double) { fromNumber(num) }

    constructor(num: Int) { fromNumber(num.toDouble()) }

    constructor(num: Long) { fromNumber(num.toDouble()) }

    constructor(str: String) {
        val trimmed = str.trim()
        if (trimmed.isEmpty()) return
        val numeric = trimmed.toDoubleOrNull()
        if (numeric != null && numeric.isFinite()) {
            fromNumber(numeric)
        }
    }

    constructor(obj: Map<String, Any?>) {
        sign = (obj["sign"] as? Number)?.toInt() ?: 0
        layer = (obj["layer"] as? Number)?.toInt() ?: 0
        mantissa = (obj["mantissa"] as? Number)?.toDouble() ?: 0.0
        exponent = (obj["exponent"] as? Number)?.toDouble() ?: 0.0
        value = (obj["value"] as? Number)?.toDouble() ?: 0.0
        normalize()
    }

    constructor(other: LayeredNumber) {
        sign = other.sign
        layer = other.layer
        mantissa = other.mantissa
        exponent = other.exponent
        value = other.value
    }

    // ─── Companion (statics JS) ───────────────────────────────────────────────

    companion object {
        const val LAYER1_THRESHOLD = 1_000_000.0
        const val LAYER1_DOWN = 5.0
        const val LOG_DIFF_LIMIT = 15.0
        const val EPSILON = 1e-12
        var mantissaFractionDigits = 2

        fun zero() = LayeredNumber(0.0)
        fun one() = LayeredNumber(1.0)

        fun fromJSON(obj: Map<String, Any?>?): LayeredNumber =
            if (obj == null) zero() else LayeredNumber(obj)

        fun fromLayer0(mantissa: Double, exponent: Double = 0.0, sign: Int = 1): LayeredNumber {
            val inst = LayeredNumber()
            if (mantissa == 0.0) return inst.also { it.setZeroState() }
            inst.sign = if (sign >= 0) 1 else -1
            inst.layer = 0
            inst.mantissa = abs(mantissa)
            inst.exponent = exponent
            inst.normalize()
            return inst
        }

        fun fromLayer1(value: Double, sign: Int = 1): LayeredNumber {
            if (value == Double.NEGATIVE_INFINITY) return zero()
            val inst = LayeredNumber()
            inst.sign = if (sign >= 0) 1 else -1
            inst.layer = 1
            inst.value = value
            inst.normalize()
            return inst
        }

        fun cast(v: Any?): LayeredNumber = when (v) {
            is LayeredNumber -> v
            is Double -> LayeredNumber(v)
            is Int -> LayeredNumber(v)
            is Long -> LayeredNumber(v)
            is Number -> LayeredNumber(v.toDouble())
            is String -> LayeredNumber(v)
            is Map<*, *> -> @Suppress("UNCHECKED_CAST") LayeredNumber(v as Map<String, Any?>)
            else -> zero()
        }

        fun formatExponent(v: Double): String {
            if (!v.isFinite()) return "∞"
            val a = abs(v)
            return when {
                a < 10   -> String.format(Locale.US, "%.2f", v)
                a < 100  -> String.format(Locale.US, "%.1f", v)
                a < 1e4  -> String.format(Locale.US, "%.0f", v)
                else     -> v.toBigDecimal().toEngineeringString()
                    .replace("+", "").replace("e0", "")
            }
        }
    }

    // ─── Initialisation interne ────────────────────────────────────────────────

    fun setZeroState(): LayeredNumber {
        sign = 0; layer = 0; mantissa = 0.0; exponent = 0.0; value = 0.0
        return this
    }

    fun fromNumber(num: Double): LayeredNumber {
        if (!num.isFinite() || num == 0.0) return setZeroState()
        sign = if (num >= 0) 1 else -1
        val a = abs(num)
        if (a < 1e-12) return setZeroState()
        layer = 0; exponent = 0.0; mantissa = a
        normalize()
        return this
    }

    fun clone() = LayeredNumber(this)

    // ─── Normalisation (port exact du JS) ────────────────────────────────────

    fun normalize(): LayeredNumber {
        if (sign == 0 || mantissa == 0.0 || !mantissa.isFinite()) {
            if (layer == 0 && (mantissa == 0.0 || !mantissa.isFinite())) {
                sign = 0; mantissa = 0.0; exponent = 0.0
            }
        }
        if (sign == 0) return setZeroState()

        if (layer == 0) {
            if (mantissa == 0.0) { sign = 0; exponent = 0.0; return this }
            var mant = mantissa
            var exp = exponent
            val s = if (sign >= 0) 1 else -1
            if (!mant.isFinite()) {
                layer = 1; value = log10(abs(mant)) + exp
                mantissa = 0.0; exponent = 0.0; sign = s
                return this
            }
            while (mant >= 10.0) { mant /= 10.0; exp += 1.0 }
            while (mant < 1.0 && mant > 0.0) { mant *= 10.0; exp -= 1.0 }
            mantissa = mant; exponent = exp; sign = s
            if (exp >= LAYER1_THRESHOLD) {
                val log10 = log10(mantissa) + exponent
                layer = 1; value = log10; mantissa = 0.0; exponent = 0.0
            }
        } else if (layer == 1) {
            if (!value.isFinite()) { value = Double.POSITIVE_INFINITY }
            if (value < LAYER1_DOWN) {
                val log10 = value
                val exp = floor(log10)
                val mant = 10.0.pow(log10 - exp)
                layer = 0; mantissa = mant; exponent = exp; value = 0.0
                normalize()
            }
        }
        return this
    }

    fun toLayer(targetLayer: Int): LayeredNumber {
        if (sign == 0) return zero()
        if (targetLayer == layer) return clone()
        val tl = maxOf(targetLayer, 0)
        val result = clone()
        while (result.layer < tl) {
            if (result.layer == 0) {
                val log10 = log10(result.mantissa) + result.exponent
                result.layer = 1; result.value = log10
                result.mantissa = 0.0; result.exponent = 0.0
            } else break
        }
        while (result.layer > tl) {
            if (result.layer == 1) {
                val log10 = result.value
                val exp = floor(log10)
                val mant = 10.0.pow(log10 - exp)
                result.layer = 0; result.mantissa = mant; result.exponent = exp
                result.value = 0.0; result.normalize()
            } else break
        }
        return result
    }

    // ─── Comparaison ──────────────────────────────────────────────────────────

    fun isZero() = sign == 0

    fun compare(other: Any?): Int {
        val b = cast(other)
        if (sign == 0 && b.sign == 0) return 0
        if (sign >= 0 && b.sign < 0) return 1
        if (sign < 0 && b.sign >= 0) return -1
        if (sign == 0) return -b.sign
        if (b.sign == 0) return sign
        val s = sign
        if (layer != b.layer) return (if (layer > b.layer) 1 else -1) * s
        if (layer == 0) {
            if (exponent != b.exponent) return (if (exponent > b.exponent) 1 else -1) * s
            if (mantissa != b.mantissa) return (if (mantissa > b.mantissa) 1 else -1) * s
            return 0
        }
        if (value != b.value) return (if (value > b.value) 1 else -1) * s
        return 0
    }

    fun greaterThan(other: Any?) = compare(other) > 0
    fun lessThan(other: Any?) = compare(other) < 0
    fun greaterOrEqual(other: Any?) = compare(other) >= 0
    fun lessOrEqual(other: Any?) = compare(other) <= 0
    fun equalTo(other: Any?) = compare(other) == 0

    // ─── Arithmétique ────────────────────────────────────────────────────────

    fun add(other: Any?): LayeredNumber {
        val b = cast(other)
        if (sign == 0) return b.clone()
        if (b.sign == 0) return clone()
        if (sign != b.sign) {
            return if (sign < 0) b.subtract(negate()) else subtract(b.negate())
        }
        if (layer == b.layer) {
            if (layer == 0) {
                return when {
                    exponent == b.exponent -> fromLayer0(mantissa + b.mantissa, exponent, sign)
                    exponent > b.exponent -> {
                        val diff = exponent - b.exponent
                        if (diff > LOG_DIFF_LIMIT) clone()
                        else fromLayer0(mantissa + b.mantissa / 10.0.pow(diff), exponent, sign)
                    }
                    else -> {
                        val diff = b.exponent - exponent
                        if (diff > LOG_DIFF_LIMIT) b.clone()
                        else fromLayer0(b.mantissa + mantissa / 10.0.pow(diff), b.exponent, sign)
                    }
                }
            }
            val max = maxOf(value, b.value)
            val min = minOf(value, b.value)
            if (max - min > LOG_DIFF_LIMIT) return fromLayer1(max, sign)
            return fromLayer1(max + log10(1.0 + 10.0.pow(min - max)), sign)
        }
        if (layer > b.layer) {
            val lifted = b.toLayer(layer)
            if (lifted.layer != layer) return clone()
            return add(lifted)
        }
        val lifted = toLayer(b.layer)
        if (lifted.layer != b.layer) return b.clone()
        return lifted.add(b)
    }

    fun subtract(other: Any?): LayeredNumber {
        val b = cast(other)
        if (b.sign == 0) return clone()
        if (sign == 0) return b.negate()
        if (sign != b.sign) return add(b.negate())
        val cmp = compare(b)
        if (cmp == 0) return zero()
        if (cmp < 0) return b.subtract(this).negate()
        if (layer == b.layer) {
            if (layer == 0) {
                return when {
                    exponent == b.exponent -> {
                        val m = mantissa - b.mantissa
                        if (m <= EPSILON) zero() else fromLayer0(m, exponent, sign)
                    }
                    exponent > b.exponent -> {
                        val diff = exponent - b.exponent
                        if (diff > LOG_DIFF_LIMIT) return clone()
                        val m = mantissa - b.mantissa / 10.0.pow(diff)
                        if (m <= EPSILON) zero() else fromLayer0(m, exponent, sign)
                    }
                    else -> clone()
                }
            }
            val max = value; val min = b.value
            if (max - min > LOG_DIFF_LIMIT) return clone()
            val inner = 1.0 - 10.0.pow(min - max)
            if (inner <= EPSILON) return zero()
            return fromLayer1(max + log10(inner), sign)
        }
        if (layer > b.layer) {
            val lifted = b.toLayer(layer)
            if (lifted.layer != layer) return clone()
            return subtract(lifted)
        }
        val lifted = toLayer(b.layer)
        if (lifted.layer != b.layer) return b.negate().subtract(negate())
        return lifted.subtract(b)
    }

    fun multiply(other: Any?): LayeredNumber {
        val b = cast(other)
        if (sign == 0 || b.sign == 0) return zero()
        val s = sign * b.sign
        if (layer == 0 && b.layer == 0) {
            return fromLayer0(mantissa * b.mantissa, exponent + b.exponent, s)
        }
        val highLayer = maxOf(layer, b.layer)
        val aL = toLayer(highLayer)
        val bL = b.toLayer(highLayer)
        if (highLayer == 1) return fromLayer1(aL.value + bL.value, s)
        val rv = log10(abs(toNumber())) + log10(abs(b.toNumber()))
        return fromLayer1(rv, s)
    }

    fun multiplyNumber(num: Double): LayeredNumber {
        if (num == 0.0) return zero()
        if (sign == 0) return zero()
        return if (layer == 0) {
            val s = if (num >= 0) sign else -sign
            fromLayer0(mantissa * abs(num), exponent, s)
        } else {
            val s = if (num >= 0) sign else -sign
            fromLayer1(value + log10(abs(num)), s)
        }
    }

    fun divide(other: Any?): LayeredNumber {
        val b = cast(other)
        if (b.isZero() || isZero()) return zero()
        val s = sign * b.sign
        if (layer == 0 && b.layer == 0) {
            return fromLayer0(mantissa / b.mantissa, exponent - b.exponent, s)
        }
        val highLayer = maxOf(layer, b.layer)
        val aL = toLayer(highLayer)
        val bL = b.toLayer(highLayer)
        if (highLayer == 1) return fromLayer1(aL.value - bL.value, s)
        val rv = log10(abs(toNumber())) - log10(abs(b.toNumber()))
        return fromLayer1(rv, s)
    }

    fun pow(power: Double): LayeredNumber {
        if (power == 0.0) return one()
        if (isZero()) return zero()
        val isEvenInt = power.isFinite() && power == floor(power) && abs(power % 2.0) == 0.0
        val resultSign = if (sign >= 0) 1 else if (isEvenInt) 1 else -1
        return if (layer == 0) {
            fromLayer0(mantissa.pow(power), exponent * power, resultSign)
        } else {
            fromLayer1(value * power, resultSign)
        }
    }

    fun negate(): LayeredNumber {
        val result = clone()
        result.sign *= -1
        return result
    }

    // ─── Opérateurs Kotlin ────────────────────────────────────────────────────

    operator fun plus(other: Any?) = add(other)
    operator fun minus(other: Any?) = subtract(other)
    operator fun times(other: Any?) = multiply(other)
    operator fun div(other: Any?) = divide(other)
    operator fun unaryMinus() = negate()
    operator fun compareTo(other: LayeredNumber) = compare(other)

    // ─── Conversion ──────────────────────────────────────────────────────────

    fun toNumber(): Double {
        if (sign == 0) return 0.0
        if (layer == 0) return sign * mantissa * 10.0.pow(exponent)
        return if (value > 308) sign * Double.POSITIVE_INFINITY else sign * 10.0.pow(value)
    }

    fun toJSON(): Map<String, Any> = mapOf(
        "sign" to sign, "layer" to layer,
        "mantissa" to mantissa, "exponent" to exponent, "value" to value
    )

    // ─── Affichage (port exact du JS) ────────────────────────────────────────

    override fun toString(): String {
        if (sign == 0) return "0"
        if (layer == 0) {
            if (abs(exponent) < 6) {
                val numeric = sign * mantissa * 10.0.pow(exponent)
                val absolute = abs(numeric)
                return if (absolute >= 1) {
                    numeric.toLong().toString()
                } else {
                    String.format(Locale.FRENCH, "%.2f", numeric)
                }
            }
            val digits = mantissaFractionDigits
            val mantissaStr = String.format(Locale.FRENCH, "%.${digits}f", sign * mantissa)
            return "${mantissaStr}e${exponent.toInt()}"
        }
        val expText = formatExponent(value)
        val prefix = if (sign < 0) "-" else ""
        return "${prefix}10^${expText}"
    }

    fun format() = toString()

    override fun equals(other: Any?): Boolean {
        if (other !is LayeredNumber) return false
        return compare(other) == 0
    }

    override fun hashCode(): Int {
        var result = sign
        result = 31 * result + layer
        result = 31 * result + mantissa.hashCode()
        result = 31 * result + exponent.hashCode()
        result = 31 * result + value.hashCode()
        return result
    }
}
