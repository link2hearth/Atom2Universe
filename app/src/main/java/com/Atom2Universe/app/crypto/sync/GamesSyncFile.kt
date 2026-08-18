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
    val starCoreLevel: Int,
    val apcToApsLevel: Int = 0,
    val apsToApcLevel: Int = 0,
    val allTimeTotalAtoms: LayeredNumberData = LayeredNumberData(0, 0, 0.0, 0.0, 0.0)
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("atoms", atoms.toJson())
        put("lifetime", lifetime.toJson())
        put("perClick", perClick.toJson())
        put("perSecond", perSecond.toJson())
        put("godFingerLevel", godFingerLevel)
        put("starCoreLevel", starCoreLevel)
        put("apcToApsLevel", apcToApsLevel)
        put("apsToApcLevel", apsToApcLevel)
        put("allTimeTotalAtoms", allTimeTotalAtoms.toJson())
    }

    companion object {
        fun fromJson(j: JSONObject) = ClickerSyncData(
            atoms         = LayeredNumberData.fromJson(j.getJSONObject("atoms")),
            lifetime      = LayeredNumberData.fromJson(j.getJSONObject("lifetime")),
            perClick      = LayeredNumberData.fromJson(j.getJSONObject("perClick")),
            perSecond     = LayeredNumberData.fromJson(j.getJSONObject("perSecond")),
            godFingerLevel = j.optInt("godFingerLevel", 0),
            starCoreLevel  = j.optInt("starCoreLevel", 0),
            // Absents des saves v1 : on retombe sur les valeurs neutres.
            apcToApsLevel  = j.optInt("apcToApsLevel", 0),
            apsToApcLevel  = j.optInt("apsToApcLevel", 0),
            allTimeTotalAtoms = j.optJSONObject("allTimeTotalAtoms")
                ?.let { LayeredNumberData.fromJson(it) }
                ?: LayeredNumberData(0, 0, 0.0, 0.0, 0.0)
        )
    }
}

// ─── Usines ──────────────────────────────────────────────────────────────────

data class FactoriesSyncData(
    val counts: Map<String, Int>   // FactoryType.id → nombre possédé
) {
    fun toJson(): JSONObject = JSONObject().apply {
        val obj = JSONObject()
        counts.forEach { (id, count) -> obj.put(id, count) }
        put("counts", obj)
    }

    companion object {
        fun fromJson(j: JSONObject): FactoriesSyncData {
            val obj = j.optJSONObject("counts") ?: return FactoriesSyncData(emptyMap())
            val counts = mutableMapOf<String, Int>()
            obj.keys().forEach { key -> counts[key] = obj.optInt(key, 0) }
            return FactoriesSyncData(counts)
        }
    }
}

// ─── Big Bang (prestige) ─────────────────────────────────────────────────────

data class BigBangSyncData(
    val unlocked: Boolean,
    val bigBangCount: Int,
    val levels: Map<String, Int>   // BigBangBonus.id → niveau
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("unlocked", unlocked)
        put("bigBangCount", bigBangCount)
        val obj = JSONObject()
        levels.forEach { (id, level) -> obj.put(id, level) }
        put("levels", obj)
    }

    companion object {
        fun fromJson(j: JSONObject): BigBangSyncData {
            val obj = j.optJSONObject("levels")
            val levels = mutableMapOf<String, Int>()
            obj?.keys()?.forEach { key -> levels[key] = obj.optInt(key, 0) }
            return BigBangSyncData(
                unlocked     = j.optBoolean("unlocked", false),
                bigBangCount = j.optInt("bigBangCount", 0),
                levels       = levels
            )
        }
    }
}

// ─── Coups critiques ─────────────────────────────────────────────────────────

data class CritSyncData(
    val chanceLevel: Int,
    val damageLevel: Int
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("chanceLevel", chanceLevel)
        put("damageLevel", damageLevel)
    }

    companion object {
        fun fromJson(j: JSONObject) = CritSyncData(
            chanceLevel = j.optInt("chanceLevel", 0),
            damageLevel = j.optInt("damageLevel", 0)
        )
    }
}

// ─── Fusion ──────────────────────────────────────────────────────────────────
// Les quarks ne voyagent pas seuls : les multiplicateurs APC/APS alimentent le
// calcul de production et les réussites par recette conditionnent des déblocages.
// Restaurer la monnaie sans eux donnerait une partie incohérente.

data class FusionSyncData(
    val quarks: Int,
    val totalWins: Int,
    val bonusMultApc: Double,
    val bonusMultAps: Double,
    val tries: Map<String, Int>,   // FusionRecipe.id → essais
    val wins: Map<String, Int>     // FusionRecipe.id → réussites
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("quarks", quarks)
        put("totalWins", totalWins)
        put("bonusMultApc", bonusMultApc)
        put("bonusMultAps", bonusMultAps)
        val triesObj = JSONObject()
        tries.forEach { (id, n) -> triesObj.put(id, n) }
        put("tries", triesObj)
        val winsObj = JSONObject()
        wins.forEach { (id, n) -> winsObj.put(id, n) }
        put("wins", winsObj)
    }

    companion object {
        private fun intMap(obj: JSONObject?): Map<String, Int> {
            if (obj == null) return emptyMap()
            val map = mutableMapOf<String, Int>()
            obj.keys().forEach { key -> map[key] = obj.optInt(key, 0) }
            return map
        }

        fun fromJson(j: JSONObject) = FusionSyncData(
            quarks       = j.optInt("quarks", 0),
            totalWins    = j.optInt("totalWins", 0),
            bonusMultApc = j.optDouble("bonusMultApc", 0.0),
            bonusMultAps = j.optDouble("bonusMultAps", 0.0),
            tries        = intMap(j.optJSONObject("tries")),
            wins         = intMap(j.optJSONObject("wins"))
        )
    }
}

// ─── High scores des jeux ────────────────────────────────────────────────────
// Chaque record vit dans les SharedPreferences de son propre jeu. Cette table est
// la seule source de vérité : pour synchroniser un nouveau record, ajouter une ligne
// ici, le reste suit automatiquement.

enum class HighScoreType { INT, LONG, FLOAT }

data class HighScoreKey(
    val prefsName: String,
    val key: String,
    val type: HighScoreType
) {
    /** Identifiant stable utilisé comme clé JSON. */
    val id: String get() = "$prefsName.$key"
}

val HIGH_SCORE_KEYS = listOf(
    HighScoreKey("flappy_cat_save", "best_score",    HighScoreType.INT),
    HighScoreKey("game2048_save",   "best_score",    HighScoreType.INT),
    HighScoreKey("hot_potato_save", "best_score",    HighScoreType.INT),
    HighScoreKey("match3_save",     "best_score",    HighScoreType.INT),
    HighScoreKey("match3_save",     "best_time_ms",  HighScoreType.LONG),
    HighScoreKey("motocross_save",  "best",          HighScoreType.FLOAT),
    HighScoreKey("orbite_save",     "best",          HighScoreType.INT),
    HighScoreKey("reflex_save",     "best_easy",     HighScoreType.INT),
    HighScoreKey("reflex_save",     "best_hard",     HighScoreType.INT),
    HighScoreKey("stars_war_save",  "best_score",    HighScoreType.INT),
    HighScoreKey("stars_war_save",  "best_wave",     HighScoreType.INT),
    HighScoreKey("survivor_save",   "best_kills",    HighScoreType.INT),
    HighScoreKey("survivor_save",   "best_time",     HighScoreType.FLOAT),
    HighScoreKey("wave_surf_save",  "best_altitude", HighScoreType.INT),
    HighScoreKey("wave_surf_save",  "best_speed",    HighScoreType.INT)
)

/** Valeurs stockées en Double : couvre Int, Long et Float sans perte utile. */
data class HighScoresSyncData(
    val values: Map<String, Double>
) {
    fun toJson(): JSONObject = JSONObject().apply {
        val obj = JSONObject()
        values.forEach { (id, v) -> obj.put(id, v) }
        put("values", obj)
    }

    companion object {
        fun fromJson(j: JSONObject): HighScoresSyncData {
            val obj = j.optJSONObject("values") ?: return HighScoresSyncData(emptyMap())
            val values = mutableMapOf<String, Double>()
            obj.keys().forEach { key -> values[key] = obj.optDouble(key, 0.0) }
            return HighScoresSyncData(values)
        }
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
    val version: Int = CURRENT_VERSION,
    val lastModified: Long,
    val clicker: ClickerSyncData? = null,
    val gacha: GachaSyncData? = null,
    val elementTokens: Int = 0,
    val gachaTickets: GachaTicketSyncData? = null,
    val gameStats: GameStatsSyncData? = null,
    // ── Ajouts v2 : tous optionnels, une save v1 reste lisible telle quelle ──
    val neutrinos: Int = 0,
    val lifetimeNeutrinos: Int = 0,
    val factories: FactoriesSyncData? = null,
    val bigBang: BigBangSyncData? = null,
    val crit: CritSyncData? = null,
    val achievements: List<String>? = null,
    val highScores: HighScoresSyncData? = null,
    val fusion: FusionSyncData? = null
) {
    fun toJson(): String = JSONObject().apply {
        put("version", version)
        put("lastModified", lastModified)
        clicker?.let { put("clicker", it.toJson()) }
        gacha?.let { put("gacha", it.toJson()) }
        put("elementTokens", elementTokens)
        gachaTickets?.let { put("gachaTickets", it.toJson()) }
        gameStats?.let { put("gameStats", it.toJson()) }
        put("neutrinos", neutrinos)
        put("lifetimeNeutrinos", lifetimeNeutrinos)
        factories?.let { put("factories", it.toJson()) }
        bigBang?.let { put("bigBang", it.toJson()) }
        crit?.let { put("crit", it.toJson()) }
        achievements?.let { put("achievements", org.json.JSONArray(it)) }
        highScores?.let { put("highScores", it.toJson()) }
        fusion?.let { put("fusion", it.toJson()) }
    }.toString()

    companion object {
        /** v1 = clicker/gacha/tickets/stats. v2 = + usines, Big Bang, crit, succès, neutrinos, records. */
        const val CURRENT_VERSION = 2

        fun fromJson(json: String): GamesSyncFile {
            val j = JSONObject(json)
            val achievementsArray = j.optJSONArray("achievements")
            return GamesSyncFile(
                version       = j.optInt("version", 1),
                lastModified  = j.optLong("lastModified", 0L),
                clicker       = j.optJSONObject("clicker")?.let { ClickerSyncData.fromJson(it) },
                gacha         = j.optJSONObject("gacha")?.let { GachaSyncData.fromJson(it) },
                elementTokens = j.optInt("elementTokens", 0),
                gachaTickets  = j.optJSONObject("gachaTickets")?.let { GachaTicketSyncData.fromJson(it) },
                gameStats     = j.optJSONObject("gameStats")?.let { GameStatsSyncData.fromJson(it) },
                neutrinos         = j.optInt("neutrinos", 0),
                lifetimeNeutrinos = j.optInt("lifetimeNeutrinos", 0),
                factories     = j.optJSONObject("factories")?.let { FactoriesSyncData.fromJson(it) },
                bigBang       = j.optJSONObject("bigBang")?.let { BigBangSyncData.fromJson(it) },
                crit          = j.optJSONObject("crit")?.let { CritSyncData.fromJson(it) },
                achievements  = achievementsArray?.let { arr ->
                    (0 until arr.length()).mapNotNull { arr.optString(it, null) }
                },
                highScores    = j.optJSONObject("highScores")?.let { HighScoresSyncData.fromJson(it) },
                fusion        = j.optJSONObject("fusion")?.let { FusionSyncData.fromJson(it) }
            )
        }
    }
}
