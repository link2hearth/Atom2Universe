package com.Atom2Universe.app.crypto.sync

import org.json.JSONObject

// ─── LayeredNumber ────────────────────────────────────────────────────────────

data class LayeredNumberData(
    val sign: Int,
    val layer: Int,
    val mantissa: Double,
    val exponent: Double,
    val value: Double
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("sign", sign)
        put("layer", layer)
        put("mantissa", mantissa)
        put("exponent", exponent)
        put("value", value)
    }

    fun isGreaterThan(other: LayeredNumberData): Boolean {
        if (sign != other.sign) return sign > other.sign
        val sameSign = sign >= 0
        if (layer != other.layer) return (layer > other.layer) == sameSign
        return when (layer) {
            0 -> if (exponent != other.exponent) (exponent > other.exponent) == sameSign
                 else (mantissa > other.mantissa) == sameSign
            else -> (value > other.value) == sameSign
        }
    }

    companion object {
        fun fromJson(j: JSONObject) = LayeredNumberData(
            sign     = j.optInt("sign", 0),
            layer    = j.optInt("layer", 0),
            mantissa = j.optDouble("mantissa", 0.0),
            exponent = j.optDouble("exponent", 0.0),
            value    = j.optDouble("value", 0.0)
        )
    }
}

// ─── Clicker ─────────────────────────────────────────────────────────────────

data class ClickerSyncData(
    val atoms: LayeredNumberData,
    val lifetime: LayeredNumberData,
    val perClick: LayeredNumberData,
    val perSecond: LayeredNumberData,
    val godFingerLevel: Int,
    val starCoreLevel: Int
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("atoms", atoms.toJson())
        put("lifetime", lifetime.toJson())
        put("perClick", perClick.toJson())
        put("perSecond", perSecond.toJson())
        put("godFingerLevel", godFingerLevel)
        put("starCoreLevel", starCoreLevel)
    }

    companion object {
        fun fromJson(j: JSONObject) = ClickerSyncData(
            atoms         = LayeredNumberData.fromJson(j.getJSONObject("atoms")),
            lifetime      = LayeredNumberData.fromJson(j.getJSONObject("lifetime")),
            perClick      = LayeredNumberData.fromJson(j.getJSONObject("perClick")),
            perSecond     = LayeredNumberData.fromJson(j.getJSONObject("perSecond")),
            godFingerLevel = j.optInt("godFingerLevel", 0),
            starCoreLevel  = j.optInt("starCoreLevel", 0)
        )
    }
}

// ─── Gacha ───────────────────────────────────────────────────────────────────

data class GachaSyncData(
    val copies: Map<Int, Int>   // atomicNumber → nombre de copies
) {
    fun toJson(): JSONObject = JSONObject().apply {
        val copiesObj = JSONObject()
        copies.forEach { (atomicNumber, count) -> copiesObj.put(atomicNumber.toString(), count) }
        put("copies", copiesObj)
    }

    companion object {
        fun fromJson(j: JSONObject): GachaSyncData {
            val copiesObj = j.optJSONObject("copies") ?: return GachaSyncData(emptyMap())
            val copies = mutableMapOf<Int, Int>()
            copiesObj.keys().forEach { key ->
                val n = key.toIntOrNull() ?: return@forEach
                copies[n] = copiesObj.optInt(key, 0)
            }
            return GachaSyncData(copies)
        }
    }
}

// ─── Fichier racine ───────────────────────────────────────────────────────────
// Pour ajouter un nouveau module : ajouter un champ nullable + entrées toJson/fromJson.

data class GamesSyncFile(
    val version: Int = 1,
    val lastModified: Long,
    val clicker: ClickerSyncData? = null,
    val gacha: GachaSyncData? = null,
    val elementTokens: Int = 0
) {
    fun toJson(): String = JSONObject().apply {
        put("version", version)
        put("lastModified", lastModified)
        clicker?.let { put("clicker", it.toJson()) }
        gacha?.let { put("gacha", it.toJson()) }
        put("elementTokens", elementTokens)
    }.toString()

    companion object {
        fun fromJson(json: String): GamesSyncFile {
            val j = JSONObject(json)
            return GamesSyncFile(
                version       = j.optInt("version", 1),
                lastModified  = j.optLong("lastModified", 0L),
                clicker       = j.optJSONObject("clicker")?.let { ClickerSyncData.fromJson(it) },
                gacha         = j.optJSONObject("gacha")?.let { GachaSyncData.fromJson(it) },
                elementTokens = j.optInt("elementTokens", 0)
            )
        }
    }
}
