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

// ─── Tickets gacha ───────────────────────────────────────────────────────────

data class GachaTicketSyncData(
    val totalTickets: Int,
    val lastTicketAwardMs: Long
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("totalTickets", totalTickets)
        put("lastTicketAwardMs", lastTicketAwardMs)
    }

    companion object {
        fun fromJson(j: JSONObject) = GachaTicketSyncData(
            totalTickets      = j.optInt("totalTickets", 0),
            lastTicketAwardMs = j.optLong("lastTicketAwardMs", 0L)
        )
    }
}

// ─── Stats de jeux ────────────────────────────────────────────────────────────

data class GameStatsSyncData(
    val solitairePlayed: Int = 0,
    val solitaireWon: Int = 0,
    val colorStackHardPlayed: Int = 0,
    val colorStackHardWon: Int = 0,
    val colorStackHardBestMs: Long = 0L,
    val sudokuPlayed: Int = 0,
    val sudokuWon: Int = 0,
    val chessPlayed: Int = 0,
    val chessWon: Int = 0,
    val draughtsPlayed: Int = 0,
    val draughtsWon: Int = 0,
    val game2048Played: Int = 0,
    val game2048Won: Int = 0,
    val blackjackPlayed: Int = 0,
    val blackjackWon: Int = 0,
    val pipeTapHardWon: Int = 0,
    val hexRunnerBestMs: Long = 0L
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("solitairePlayed",      solitairePlayed)
        put("solitaireWon",         solitaireWon)
        put("colorStackHardPlayed", colorStackHardPlayed)
        put("colorStackHardWon",    colorStackHardWon)
        put("colorStackHardBestMs", colorStackHardBestMs)
        put("sudokuPlayed",         sudokuPlayed)
        put("sudokuWon",            sudokuWon)
        put("chessPlayed",          chessPlayed)
        put("chessWon",             chessWon)
        put("draughtsPlayed",       draughtsPlayed)
        put("draughtsWon",          draughtsWon)
        put("game2048Played",       game2048Played)
        put("game2048Won",          game2048Won)
        put("blackjackPlayed",      blackjackPlayed)
        put("blackjackWon",         blackjackWon)
        put("pipeTapHardWon",       pipeTapHardWon)
        put("hexRunnerBestMs",      hexRunnerBestMs)
    }

    companion object {
        fun fromJson(j: JSONObject) = GameStatsSyncData(
            solitairePlayed      = j.optInt("solitairePlayed", 0),
            solitaireWon         = j.optInt("solitaireWon", 0),
            colorStackHardPlayed = j.optInt("colorStackHardPlayed", 0),
            colorStackHardWon    = j.optInt("colorStackHardWon", 0),
            colorStackHardBestMs = j.optLong("colorStackHardBestMs", 0L),
            sudokuPlayed         = j.optInt("sudokuPlayed", 0),
            sudokuWon            = j.optInt("sudokuWon", 0),
            chessPlayed          = j.optInt("chessPlayed", 0),
            chessWon             = j.optInt("chessWon", 0),
            draughtsPlayed       = j.optInt("draughtsPlayed", 0),
            draughtsWon          = j.optInt("draughtsWon", 0),
            game2048Played       = j.optInt("game2048Played", 0),
            game2048Won          = j.optInt("game2048Won", 0),
            blackjackPlayed      = j.optInt("blackjackPlayed", 0),
            blackjackWon         = j.optInt("blackjackWon", 0),
            pipeTapHardWon       = j.optInt("pipeTapHardWon", 0),
            hexRunnerBestMs      = j.optLong("hexRunnerBestMs", 0L)
        )
    }
}

// ─── Fichier racine ───────────────────────────────────────────────────────────
// Pour ajouter un nouveau module : ajouter un champ nullable + entrées toJson/fromJson.

data class GamesSyncFile(
    val version: Int = 1,
    val lastModified: Long,
    val clicker: ClickerSyncData? = null,
    val gacha: GachaSyncData? = null,
    val elementTokens: Int = 0,
    val gachaTickets: GachaTicketSyncData? = null,
    val gameStats: GameStatsSyncData? = null
) {
    fun toJson(): String = JSONObject().apply {
        put("version", version)
        put("lastModified", lastModified)
        clicker?.let { put("clicker", it.toJson()) }
        gacha?.let { put("gacha", it.toJson()) }
        put("elementTokens", elementTokens)
        gachaTickets?.let { put("gachaTickets", it.toJson()) }
        gameStats?.let { put("gameStats", it.toJson()) }
    }.toString()

    companion object {
        fun fromJson(json: String): GamesSyncFile {
            val j = JSONObject(json)
            return GamesSyncFile(
                version       = j.optInt("version", 1),
                lastModified  = j.optLong("lastModified", 0L),
                clicker       = j.optJSONObject("clicker")?.let { ClickerSyncData.fromJson(it) },
                gacha         = j.optJSONObject("gacha")?.let { GachaSyncData.fromJson(it) },
                elementTokens = j.optInt("elementTokens", 0),
                gachaTickets  = j.optJSONObject("gachaTickets")?.let { GachaTicketSyncData.fromJson(it) },
                gameStats     = j.optJSONObject("gameStats")?.let { GameStatsSyncData.fromJson(it) }
            )
        }
    }
}
