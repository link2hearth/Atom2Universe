package com.Atom2Universe.app.games.hamlet

import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.ByteArrayOutputStream

/** Files are prepared and checked before any live state changes. SAF is owned by the Activity. */
object HamletTransfer {
    const val MAX_BYTES = 16 * 1024 * 1024
    data class Issue(val code: String, val detail: String = "", val warning: Boolean = false)
    data class Prepared(val json: String?, val legacy: Boolean, val issues: List<Issue>) {
        val valid get() = json != null && issues.none { !it.warning }
        val warnings get() = issues.filter { it.warning }
    }

    fun exportWorld(game: HamletGame): String = HamletSave.toJson(game)

    /** Enforces the cap while reading, including providers with no reported file size. */
    fun readBounded(input: InputStream): String {
        val bytes = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            if (bytes.size() + n > MAX_BYTES) throw IllegalArgumentException("hamlet_realms_import_size")
            bytes.write(buffer, 0, n)
        }
        return bytes.toString(Charsets.UTF_8.name())
    }

    fun inspect(text: String): Prepared {
        if (text.length > MAX_BYTES || text.toByteArray(Charsets.UTF_8).size > MAX_BYTES) return Prepared(null, false, listOf(Issue("size")))
        val issues = mutableListOf<Issue>()
        var legacy = false
        return try {
            val source = JSONObject(text.removePrefix("\uFEFF"))
            legacy = !source.has("version") && source.has("castle") && source.has("objs")
            val root = if (legacy) convertLegacy(source, issues) else source
            if (root.optInt("version", 1) !in 1..6) issues.add(Issue("version"))
            validateLand(root, issues)
            val ids = HashSet<Int>()
            val realmIds = hashSetOf(root.optInt("realmNo", 1))
            fun unique(land: JSONObject) {
                val a = land.optJSONArray("objs") ?: return
                for (i in 0 until a.length()) {
                    val id = a.getJSONObject(i).optInt("id", -1)
                    if (id <= 0 || id == Int.MAX_VALUE || !ids.add(id)) issues.add(Issue("objects", id.toString()))
                }
            }
            unique(root)
            root.optJSONArray("parked")?.let { a ->
                for (i in 0 until a.length()) {
                    val land = a.getJSONObject(i)
                    val no = land.optInt("no", -1)
                    if (no < 1 || !realmIds.add(no) || land.has("parked")) issues.add(Issue("realms"))
                    validateLand(land, issues); unique(land)
                }
            }
            if (realmIds != (1..realmIds.size).toSet()) issues.add(Issue("realms"))
            for (key in arrayOf("coins", "gems", "xp")) if (root.has(key) && root.getLong(key) < 0) issues.add(Issue("format", key))
            val json = if (issues.any { !it.warning }) null else root.toString()
            if (json != null && !HamletSave.fromJson(HamletGame(), json)) issues.add(Issue("format"))
            Prepared(if (issues.any { !it.warning }) null else json, legacy, issues.distinct())
        } catch (_: Exception) { Prepared(null, legacy, issues + Issue("format")) }
    }

    /** Warnings are explicit: callers show them and obtain acceptance before a legacy conversion. */
    fun apply(game: HamletGame, prepared: Prepared, acceptWarnings: Boolean = false): Boolean {
        if (!prepared.valid || prepared.warnings.isNotEmpty() && !acceptWarnings) return false
        return HamletSave.fromJson(game, prepared.json ?: return false)
    }

    fun importWorld(game: HamletGame, text: String, acceptWarnings: Boolean = false): Boolean = apply(game, inspect(text), acceptWarnings)

    private fun validateLand(root: JSONObject, issues: MutableList<Issue>) {
        val objects = root.optJSONArray("objs") ?: run { issues.add(Issue("format")); return }
        val occupancy = BooleanArray(Hamlet.GRID * Hamlet.GRID)
        var halls = 0
        for (i in 0 until objects.length()) {
            val o = objects.getJSONObject(i)
            val id = o.optString("t")
            val def = Hamlet.byId[id]
            if (def == null || def.isPath) { issues.add(Issue("unknown_object", id)); continue }
            if (def.cat == Cat.HALL) halls++
            val x = o.getInt("x"); val y = o.getInt("y")
            val flip = o.optInt("f") == 1
            val w = if (flip) def.d else def.w; val d = if (flip) def.w else def.d
            if (x < 0 || y < 0 || x.toLong() + w > Hamlet.GRID || y.toLong() + d > Hamlet.GRID) {
                issues.add(Issue("geometry", id)); continue
            }
            for (yy in y until y + d) for (xx in x until x + w) {
                val n = yy * Hamlet.GRID + xx
                if (occupancy[n]) issues.add(Issue("geometry", id))
                occupancy[n] = true
            }
            if (o.has("c") && !o.isNull("c") && o.optString("c").isNotBlank()) {
                val crop = Hamlet.cropById[o.getString("c")]
                if (crop == null || def.cat != Cat.FIELD || crop.magic != def.magic) issues.add(Issue("unknown_crop", o.optString("c")))
            }
        }
        if (halls != 1) issues.add(Issue("hall"))
        if (root.optInt("hall", 1) !in 1..Hamlet.halls.size || root.optInt("expand") !in 0..19) issues.add(Issue("geometry"))
        root.optJSONArray("paths")?.let { a ->
            if (a.length() % 2 != 0) issues.add(Issue("paths"))
            var i = 0
            val seen = hashSetOf<Int>()
            while (i + 1 < a.length()) {
                val pos = a.getInt(i); val code = a.getInt(i + 1)
                if (pos !in occupancy.indices || code !in Hamlet.pathById || !seen.add(pos)) issues.add(Issue("paths"))
                i += 2
            }
        }
    }

    private fun convertLegacy(source: JSONObject, issues: MutableList<Issue>): JSONObject {
        val root = JSONObject(source.toString())
        fun convertLand(land: JSONObject) {
            land.put("hall", land.optInt("castle", 1))
            val oldPathNames = arrayOf("", "road", "cobble", "river", "bridge", "brickroad", "flagstone", "goldroad", "boardwalk", "marblepath", "darkstone", "mudpath", "redcarpet", "snowpath", "lavaflow", "haypath", "icepath", "sandpatch", "mosspath", "rosepath")
            land.optJSONArray("paths")?.let { paths ->
                var i = 1
                while (i < paths.length()) {
                    val oldCode = paths.getInt(i)
                    val oldName = oldPathNames.getOrNull(oldCode)
                    val nativeCode = oldName?.let { Hamlet.pathCode[it] }
                    if (nativeCode == null) issues.add(Issue("paths", oldCode.toString())) else paths.put(i, nativeCode)
                    i += 2
                }
            }
            val objects = land.getJSONArray("objs")
            for (i in 0 until objects.length()) {
                val o = objects.getJSONObject(i)
                val original = o.getString("t")
                o.put("t", when (original) { "castle" -> "hall"; "farm" -> "field"; "magicfarm" -> "magicfield"; else -> original })
                when (o.optString("c")) {
                    "cauliflower" -> o.put("c", "glowcabbage")
                    "asparagus" -> o.put("c", "starasparagus")
                    "pineapple" -> o.put("c", "sunpineapple")
                }
                if (original == "castle") {
                    // Original castle was 6x6; native hall is 4x4. Preserve its centre and all neighbours.
                    o.put("x", o.getInt("x") + 1).put("y", o.getInt("y") + 1)
                }
                if (o.has("ord")) o.put("o", o.get("ord"))
                if (o.has("next")) o.put("n", o.get("next"))
                if (o.optBoolean("f")) o.put("f", 1)
            }
        }
        convertLand(root)
        root.optJSONArray("parked")?.let { a -> for (i in 0 until a.length()) convertLand(a.getJSONObject(i)) }
        root.put("version", 6).put("gems", root.optInt("mojo")).put("questBase", root.optLong("qbase"))
        root.put("realmName", root.optString("name")).put("realmNo", root.optInt("realmNo", 1))
        if (!root.has("sandbox")) root.put("sandbox", true)
        root.optJSONObject("stats")?.let { stats ->
            for ((old, new) in mapOf("built_castle" to "built_hall", "built_farm" to "built_field", "built_magicfarm" to "built_magicfield", "cat_house" to "cat_home", "cat_farm" to "cat_field", "cat_castle" to "cat_hall")) {
                if (stats.has(old)) stats.put(new, stats.optLong(old))
            }
        }
        val oldGoals = arrayOf("planted" to 1, "harvested" to 1, "built_cottage" to 1, "taxes" to 2, "roads" to 5, "built_field" to 1,
            "built_bakery" to 1, "accepted" to 1, "delivered" to 1, "visited" to 1, "ordered" to 1, "pickedUp" to 1,
            "harvested" to 20, "cat_decor" to 5, "cat_nature" to 8, "cat_home" to 3, "delivered" to 10, "mojoUsed" to 1,
            "castle" to 1, "earned" to 25000, "harvested" to 100)
        val oldIndex = source.optInt("quest").coerceAtLeast(0)
        val oldGoal = oldGoals.getOrNull(oldIndex)
        val matched = oldGoal?.let { target -> Hamlet.quests.indexOfFirst { it.key == target.first && it.n == target.second } } ?: -1
        if (matched >= 0) {
            root.put("quest", matched)
        } else {
            val target = if (oldIndex >= oldGoals.size) Hamlet.quests.indexOfFirst { it.key == "harvested" && it.n == 100 } + 1
                else if (oldIndex == 17) Hamlet.quests.indexOfFirst { it.key == "work_used" }
                else Hamlet.quests.indexOfFirst { it.key == "cat_decor" && it.n == 5 }
            root.put("quest", target.coerceIn(0, Hamlet.quests.size))
            val key = Hamlet.quests.getOrNull(target)?.key
            root.put("questBase", key?.let { root.optJSONObject("stats")?.optLong(it) } ?: 0L)
        }
        if (oldIndex >= 9) issues.add(Issue("legacy_quests", warning = true))
        issues.add(Issue("legacy_layout", warning = true))
        if ((source.optJSONArray("myOrders")?.length() ?: 0) > 0 || source.has("friends")) issues.add(Issue("legacy_social", warning = true))
        if (source.has("cam") || source.has("news")) issues.add(Issue("legacy_presentation", warning = true))
        return root
    }
}
