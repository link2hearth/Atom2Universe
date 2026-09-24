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
//
// Contrairement au clicker, les records ne demandent jamais de choisir : à chaque
// synchronisation, on garde le meilleur de tous les appareils (le « tableau général »).
// Encore faut-il savoir ce qu'est « le meilleur » : un score se veut haut, un temps
// ou un nombre de coups se veut bas. D'où `better`.

enum class HighScoreType { INT, LONG, FLOAT }

/** Dans quel sens un record s'améliore. */
enum class RecordDirection { HIGHER, LOWER }

data class HighScoreKey(
    val prefsName: String,
    /** Nom exact de la clé, ou son début si [isPrefix]. */
    val key: String,
    val type: HighScoreType,
    val better: RecordDirection = RecordDirection.HIGHER,
    /**
     * Vrai pour les records rangés par difficulté ou par circuit (`best_time_EASY`,
     * `best_time_HARD`…) : toutes les clés qui commencent par [key] sont synchronisées,
     * y compris celles d'une difficulté ajoutée plus tard.
     */
    val isPrefix: Boolean = false
) {
    fun matches(prefs: String, fullKey: String): Boolean =
        prefs == prefsName && if (isPrefix) fullKey.startsWith(key) else fullKey == key
}

/** Identifiant stable utilisé comme clé JSON : `nomDesPrefs.clé`. */
fun highScoreId(prefsName: String, key: String) = "$prefsName.$key"

private val H = RecordDirection.HIGHER
private val L = RecordDirection.LOWER

val HIGH_SCORE_KEYS = listOf(
    HighScoreKey("balance_game",       "best_level_",     HighScoreType.INT,   H, isPrefix = true),
    HighScoreKey("bigger_save",        "best_score",      HighScoreType.INT),
    // Le plus gros astre jamais formé : la frise d'Accrétion.
    HighScoreKey("bigger_save",        "discovered_tier", HighScoreType.INT),
    HighScoreKey("circles_save",       "best_moves_",     HighScoreType.INT,   L, isPrefix = true),
    HighScoreKey("cosmo_run_save",     "best_score",      HighScoreType.INT),
    HighScoreKey("flappy_cat_save",    "best_score",      HighScoreType.INT),
    HighScoreKey("game2048_save",      "best_score",      HighScoreType.INT),
    HighScoreKey("game_stats",         "colorstack_hard_best_ms", HighScoreType.LONG, L),
    HighScoreKey("game_stats",         "hexrunner_best_ms",       HighScoreType.LONG, H),
    HighScoreKey("hot_potato_save",    "best_score",      HighScoreType.INT),
    // Intrication : le meilleur score d'une partie de 15 étages.
    HighScoreKey("link_save",          "best_score",      HighScoreType.INT),
    HighScoreKey("match3_forge",       "best_",           HighScoreType.LONG, H, isPrefix = true),
    HighScoreKey("roulette_stats",     "best_payout",     HighScoreType.INT),
    HighScoreKey("match3_save",        "best_score",      HighScoreType.INT),
    // Durée de survie : plus c'est long, mieux c'est.
    HighScoreKey("match3_save",        "best_time_ms",    HighScoreType.LONG),
    HighScoreKey("memory_save",        "best_time_",      HighScoreType.INT,   L, isPrefix = true),
    HighScoreKey("memory_save",        "best_moves_",     HighScoreType.INT,   L, isPrefix = true),
    HighScoreKey("minesweeper_prefs",  "best_",           HighScoreType.INT,   L, isPrefix = true),
    HighScoreKey("motocross_save",     "trial_best",      HighScoreType.INT),
    HighScoreKey("nuclea_save",        "best_wave",       HighScoreType.INT),
    HighScoreKey("orbite_save",        "best",            HighScoreType.INT),
    // Pas des préférences : la base Room de Particules, traduite par ParticulesRecords.
    HighScoreKey(ParticulesRecords.SOURCE, ParticulesRecords.KEY_HIGH_SCORE,    HighScoreType.LONG),
    HighScoreKey(ParticulesRecords.SOURCE, ParticulesRecords.KEY_HIGHEST_LEVEL, HighScoreType.INT),
    HighScoreKey(ParticulesRecords.SOURCE, ParticulesRecords.KEY_BEST_COMBO,    HighScoreType.INT),
    HighScoreKey("reflex_save",        "best_score",      HighScoreType.LONG),
    HighScoreKey("reflex_save",        "best_combo",      HighScoreType.INT),
    HighScoreKey("roguelike_save",     "best_floor",      HighScoreType.INT),
    HighScoreKey("sokoban_prefs",      "best_",           HighScoreType.INT,   L, isPrefix = true),
    HighScoreKey("stars_war_save",     "best_score",      HighScoreType.INT),
    HighScoreKey("stars_war_save",     "best_wave",       HighScoreType.INT),
    HighScoreKey("survivor_save",      "best_kills",      HighScoreType.INT),
    HighScoreKey("survivor_save",      "best_time",       HighScoreType.FLOAT),
    HighScoreKey("toybox_racers_save", "best_time_",      HighScoreType.FLOAT, L, isPrefix = true),
    HighScoreKey("trebuchet_game",     "best_distance",   HighScoreType.FLOAT),
    // Nombre de tirs pour raser un site : moins, c'est mieux.
    HighScoreKey("trebuchet_game",     "best_hits",       HighScoreType.INT,   L, isPrefix = true),
    HighScoreKey("wave_surf_save",     "best_altitude",   HighScoreType.INT),
    HighScoreKey("wave_surf_save",     "best_speed",      HighScoreType.INT)
)

/** La ligne du tableau qui décrit cet identifiant, ou null s'il vient d'une autre version de l'app. */
fun findHighScoreKey(id: String): Pair<HighScoreKey, String>? {
    val dot = id.indexOf('.')
    if (dot <= 0) return null
    val prefs = id.substring(0, dot)
    val key = id.substring(dot + 1)
    val def = HIGH_SCORE_KEYS.firstOrNull { it.matches(prefs, key) } ?: return null
    return def to key
}

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
        /**
         * Le tableau général : pour chaque record, le meilleur des deux côtés.
         *
         * Une valeur nulle ou négative ne compte pas — c'est « jamais joué » (le
         * démineur range même -1). Sans cette règle, un temps de 0 battrait tous
         * les vrais temps d'un record « plus bas = mieux ».
         */
        fun merge(a: HighScoresSyncData?, b: HighScoresSyncData?): HighScoresSyncData {
            val left = a?.values ?: emptyMap()
            val right = b?.values ?: emptyMap()
            val merged = mutableMapOf<String, Double>()
            for (id in left.keys + right.keys) {
                val candidates = listOfNotNull(left[id], right[id]).filter { it > 0.0 }
                if (candidates.isEmpty()) continue
                // Inconnu de cette version : on le garde tel quel plutôt que de l'effacer
                // du cloud pour l'appareil qui, lui, sait le lire.
                val direction = findHighScoreKey(id)?.first?.better ?: RecordDirection.HIGHER
                merged[id] = if (direction == RecordDirection.LOWER) candidates.min() else candidates.max()
            }
            return HighScoresSyncData(merged)
        }

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
    /** atomicNumber → copies actuellement possédées (la fusion les consomme). */
    val copies: Map<Int, Int>,
    /** atomicNumber → copies cumulées depuis toujours. Ne baisse jamais : porte les bonus d'éléments. */
    val totalEver: Map<Int, Int> = emptyMap(),
    /** atomicNumber → copies obtenues via fusion. Exclues des bonus gacha (elles ont les leurs). */
    val fusionCopies: Map<Int, Int> = emptyMap()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("copies", intMapToJson(copies))
        put("totalEver", intMapToJson(totalEver))
        put("fusionCopies", intMapToJson(fusionCopies))
    }

    companion object {
        fun fromJson(j: JSONObject): GachaSyncData {
            val copies = intMapFromJson(j.optJSONObject("copies"))
            return GachaSyncData(
                copies = copies,
                // Saves v1/v2 : les compteurs permanents n'étaient pas sauvegardés. On repart des
                // copies possédées — le joueur peut y perdre, mais jamais gagner de bonus indu.
                totalEver    = j.optJSONObject("totalEver")?.let { intMapFromJson(it) } ?: copies,
                fusionCopies = intMapFromJson(j.optJSONObject("fusionCopies"))
            )
        }

        private fun intMapToJson(map: Map<Int, Int>): JSONObject = JSONObject().apply {
            map.forEach { (atomicNumber, count) -> put(atomicNumber.toString(), count) }
        }

        private fun intMapFromJson(obj: JSONObject?): Map<Int, Int> {
            if (obj == null) return emptyMap()
            val out = mutableMapOf<Int, Int>()
            obj.keys().forEach { key ->
                val n = key.toIntOrNull() ?: return@forEach
                out[n] = obj.optInt(key, 0)
            }
            return out
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

// ─── Compteurs par appareil ──────────────────────────────────────────────────
// Parties jouées, victoires, puzzles résolus… Ce ne sont pas des records : le
// « meilleur » des deux appareils n'aurait aucun sens. Chaque appareil publie SES
// compteurs sous son identifiant, et le total affiché est la somme de tous les
// appareils. Personne n'écrit jamais les compteurs d'un autre : rien ne peut être
// compté deux fois.

data class DeviceCountersData(
    /** Le nom lisible de l'appareil, pour le détail « qui a joué quoi ». */
    val name: String,
    /** Quand cet appareil a publié ses compteurs pour la dernière fois. */
    val updatedAt: Long,
    /** Identifiant `nomDesPrefs.clé` → valeur. */
    val counters: Map<String, Int>
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        put("updatedAt", updatedAt)
        val obj = JSONObject()
        counters.forEach { (id, n) -> obj.put(id, n) }
        put("counters", obj)
    }

    companion object {
        fun fromJson(j: JSONObject): DeviceCountersData {
            val obj = j.optJSONObject("counters")
            val counters = mutableMapOf<String, Int>()
            obj?.keys()?.forEach { key -> counters[key] = obj.optInt(key, 0) }
            return DeviceCountersData(
                name      = j.optString("name", ""),
                updatedAt = j.optLong("updatedAt", 0L),
                counters  = counters
            )
        }

        fun mapToJson(map: Map<String, DeviceCountersData>): JSONObject = JSONObject().apply {
            map.forEach { (deviceId, data) -> put(deviceId, data.toJson()) }
        }

        fun mapFromJson(obj: JSONObject?): Map<String, DeviceCountersData> {
            if (obj == null) return emptyMap()
            val out = mutableMapOf<String, DeviceCountersData>()
            obj.keys().forEach { key -> obj.optJSONObject(key)?.let { out[key] = fromJson(it) } }
            return out
        }
    }
}

/** Motif de suppression (`prefs.clé` ou `prefs.début*`) → date de la suppression. */
fun resetsToJson(map: Map<String, Long>): JSONObject = JSONObject().apply {
    map.forEach { (pattern, at) -> put(pattern, at) }
}

fun resetsFromJson(obj: JSONObject?): Map<String, Long> {
    if (obj == null) return emptyMap()
    val out = mutableMapOf<String, Long>()
    obj.keys().forEach { key -> out[key] = obj.optLong(key, 0L) }
    return out
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
    // ── Ajouts v2 : tous optionnels, une save v1 reste lisible telle quelle ──
    val neutrinos: Int = 0,
    val lifetimeNeutrinos: Int = 0,
    val factories: FactoriesSyncData? = null,
    val bigBang: BigBangSyncData? = null,
    val crit: CritSyncData? = null,
    val achievements: List<String>? = null,
    val highScores: HighScoresSyncData? = null,
    val fusion: FusionSyncData? = null,
    // ── Ajouts v4 : partagés entre appareils, jamais soumis au choix de partie ──
    val devices: Map<String, DeviceCountersData>? = null,
    val resets: Map<String, Long>? = null
) {
    fun toJson(): String = JSONObject().apply {
        put("version", version)
        put("lastModified", lastModified)
        clicker?.let { put("clicker", it.toJson()) }
        gacha?.let { put("gacha", it.toJson()) }
        put("elementTokens", elementTokens)
        gachaTickets?.let { put("gachaTickets", it.toJson()) }
        put("neutrinos", neutrinos)
        put("lifetimeNeutrinos", lifetimeNeutrinos)
        factories?.let { put("factories", it.toJson()) }
        bigBang?.let { put("bigBang", it.toJson()) }
        crit?.let { put("crit", it.toJson()) }
        achievements?.let { put("achievements", org.json.JSONArray(it)) }
        highScores?.let { put("highScores", it.toJson()) }
        fusion?.let { put("fusion", it.toJson()) }
        devices?.let { put("devices", DeviceCountersData.mapToJson(it)) }
        resets?.let { put("resets", resetsToJson(it)) }
    }.toString()

    companion object {
        /**
         * v1 = clicker/gacha/tickets/stats. v2 = + usines, Big Bang, crit, succès, neutrinos, records.
         * v3 = + compteurs gacha permanents (totalEver, fusionCopies) qui portent les bonus d'éléments
         * et de collection de raretés.
         * v4 = les stats de parties quittent la partie du clicker : compteurs par appareil
         * (`devices`) et suppressions datées (`resets`). L'ancien bloc `gameStats` est ignoré.
         */
        const val CURRENT_VERSION = 4

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
                neutrinos         = j.optInt("neutrinos", 0),
                lifetimeNeutrinos = j.optInt("lifetimeNeutrinos", 0),
                factories     = j.optJSONObject("factories")?.let { FactoriesSyncData.fromJson(it) },
                bigBang       = j.optJSONObject("bigBang")?.let { BigBangSyncData.fromJson(it) },
                crit          = j.optJSONObject("crit")?.let { CritSyncData.fromJson(it) },
                achievements  = achievementsArray?.let { arr ->
                    (0 until arr.length()).mapNotNull { arr.optString(it, null) }
                },
                highScores    = j.optJSONObject("highScores")?.let { HighScoresSyncData.fromJson(it) },
                fusion        = j.optJSONObject("fusion")?.let { FusionSyncData.fromJson(it) },
                devices       = j.optJSONObject("devices")?.let { DeviceCountersData.mapFromJson(it) },
                resets        = j.optJSONObject("resets")?.let { resetsFromJson(it) }
            )
        }
    }
}
