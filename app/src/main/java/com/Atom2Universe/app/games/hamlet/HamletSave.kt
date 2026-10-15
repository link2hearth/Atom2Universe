package com.Atom2Universe.app.games.hamlet

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** La sauvegarde du village : un seul fichier JSON, réécrit d'un coup (fichier temporaire puis renommage). */
object HamletSave {

    fun toJson(g: HamletGame): String {
        val root = JSONObject()
        root.put("version", 6)
        root.put("adventure", HamletAdventureSave.encode(g.adventure))
        root.put("stewardship", g.stewardship.encode())
        root.put("armory", g.armory.encode())
        root.put("shardShop", g.shardShop.encode())
        root.put("legends", g.legends.encode())
        root.put("neighbors", g.neighbors.encode())
        root.put("recentBuilds", JSONArray(g.buildHistory.recent))
        root.put("chronicle", JSONArray().also { a -> g.chronicle.rows.forEach { e -> a.put(JSONObject()
            .put("kind", e.kind.name).put("at", e.at).put("subject", e.subject).put("actor", e.actor).put("amount", e.amount)) } })
        root.put("realmNo", g.realms.current).put("realmName", g.realms.name())
        root.put("parked", JSONArray().also { a -> g.realms.stored().forEach { land ->
            a.put(terrain(land.objects.values, land.paths, land.hall, land.expand).put("no", land.no).put("name", land.name))
        } })
        val j = g.journey
        root.put("journey", JSONObject().put("chapter", j.chapter).put("supplies", j.supplies)
            .put("research", JSONArray(j.research.toList())).put("explored", JSONArray(j.explored.toList()))
            .put("expedition", j.expedition).put("departure", j.departure).put("arrival", j.arrival)
            .put("invitationDay", j.invitationDay).put("invitationsToday", j.invitationsToday))
        root.put("workSeconds", g.workSeconds).put("workStep", g.workStep).put("workTool", g.workTool)
        root.put("coins", g.coins).put("gems", g.gems).put("xp", g.xp)
        root.put("hall", g.hall).put("expand", g.expand).put("sandbox", g.sandbox)
        root.put("nextId", g.nextId).put("quest", g.quest).put("questBase", g.questBase)
        root.put("giftT", g.giftT).put("created", g.created).put("visitT", g.visitT)
        root.put("dailyDay", g.dailyDay).put("dailyClaimed", g.dailyClaimed)
        root.put("dailyBase", JSONArray(g.dailyBase.toList()))
        val stats = JSONObject()
        for ((k, v) in g.stats) stats.put(k, v)
        root.put("stats", stats)
        val pantry = JSONObject()
        for ((k, v) in g.pantry) if (v > 0) pantry.put(k, v)
        root.put("pantry", pantry)
        val land = terrain(g.objs.values, g.paths, g.hall, g.expand)
        root.put("paths", land.getJSONArray("paths")).put("objs", land.getJSONArray("objs"))
        return root.toString()
    }

    private fun terrain(objects: Collection<Obj>, ground: IntArray, hall: Int, expand: Int): JSONObject {
        val paths = JSONArray()
        for (i in ground.indices) if (ground[i] != 0) { paths.put(i); paths.put(ground[i]) }
        val objs = JSONArray()
        for (o in objects) {
            val j = JSONObject().put("id", o.id).put("t", o.t).put("x", o.x).put("y", o.y)
            if (o.flip) j.put("f", 1)
            if (o.tier > 1) j.put("tier", o.tier)
            if (o.tended) j.put("tended", true)
            o.crop?.let { j.put("c", it) }
            if (o.t0 != 0L) j.put("t0", o.t0)
            if (o.b0 != 0L) j.put("b0", o.b0)
            if (o.next != 0L) j.put("n", o.next)
            o.order?.let { j.put("o", JSONObject().put("from", it.from).put("t0", it.t0).put("recipeRank", it.recipeRank).put("recipeVersion", it.recipeVersion)) }
            objs.put(j)
        }
        return JSONObject().put("hall", hall).put("expand", expand).put("paths", paths).put("objs", objs)
    }

    /** Remplit [g] depuis [text]. Rend faux si le texte n'est pas un village utilisable. */
    fun fromJson(g: HamletGame, text: String): Boolean {
        // Décoder dans un village temporaire : un fichier invalide ne détruit jamais le village courant.
        val candidate = HamletGame(g::now, g.rng)
        if (!decode(candidate, text)) return false
        g.objs.clear(); g.objs.putAll(candidate.objs)
        g.stats.clear(); g.stats.putAll(candidate.stats)
        candidate.paths.copyInto(g.paths); candidate.occ.copyInto(g.occ)
        g.coins = candidate.coins; g.gems = candidate.gems; g.xp = candidate.xp
        g.hall = candidate.hall; g.expand = candidate.expand; g.sandbox = candidate.sandbox
        g.quest = candidate.quest; g.questBase = candidate.questBase; g.questNoted = -1
        g.giftT = candidate.giftT; g.created = candidate.created; g.nextId = candidate.nextId
        g.visitT = candidate.visitT
        g.dailyDay = candidate.dailyDay; g.dailyClaimed = candidate.dailyClaimed
        candidate.dailyBase.copyInto(g.dailyBase)
        g.pantry.clear(); g.pantry.putAll(candidate.pantry)
        g.journey.copyFrom(candidate.journey)
        g.adventure.copyFrom(candidate.adventure)
        g.stewardship.copyFrom(candidate.stewardship)
        g.armory.copyFrom(candidate.armory)
        g.shardShop.copyFrom(candidate.shardShop)
        g.legends.copyFrom(candidate.legends)
        g.neighbors.copyFrom(candidate.neighbors)
        g.realms.copyFrom(candidate.realms)
        g.buildHistory.clear()
        g.buildHistory.restoreRecent(candidate.buildHistory.recent)
        g.chronicle.restore(candidate.chronicle.rows)
        g.workSeconds = candidate.workSeconds; g.workStep = candidate.workStep; g.workTool = candidate.workTool
        g.bump()
        return true
    }

    private fun decode(g: HamletGame, text: String): Boolean = try { decodeChecked(g, text) } catch (_: Exception) { false }

    private fun decodeChecked(g: HamletGame, text: String): Boolean {
        val root = try { JSONObject(text) } catch (e: Exception) { return false }
        if (root.optInt("version", 1) !in 1..6) return false
        val arr = root.optJSONArray("objs") ?: return false
        g.objs.clear(); g.stats.clear(); g.paths.fill(0); g.occ.fill(0)
        g.coins = root.optLong("coins").coerceAtLeast(0); g.gems = root.optInt("gems").coerceAtLeast(0); g.xp = root.optLong("xp").coerceAtLeast(0)
        g.hall = root.optInt("hall", 1).coerceIn(1, Hamlet.halls.size)
        g.expand = root.optInt("expand").coerceIn(0, 19)
        g.sandbox = root.optBoolean("sandbox")
        g.quest = root.optInt("quest").coerceIn(0, Hamlet.quests.size); g.questBase = root.optLong("questBase")
        g.giftT = root.optLong("giftT"); g.created = root.optLong("created"); g.visitT = root.optLong("visitT")
        g.dailyDay = root.optLong("dailyDay", -1); g.dailyClaimed = root.optInt("dailyClaimed") and 7
        root.optJSONArray("dailyBase")?.let { b -> for (i in g.dailyBase.indices) g.dailyBase[i] = b.optLong(i).coerceAtLeast(0) }
        root.optJSONObject("stats")?.let { s -> for (k in s.keys()) g.stats[k] = s.optLong(k) }
        g.pantry.clear()
        root.optJSONObject("pantry")?.let { p -> for (k in p.keys()) if (k in Hamlet.cropById) g.pantry[k] = p.optInt(k).coerceIn(0, HamletEconomy.pantryCap(Hamlet.halls.size)) }
        val journey = g.journey
        root.optJSONObject("journey")?.let { j ->
            journey.chapter = j.optInt("chapter").coerceIn(0, HamletJourney.chapters.size)
            journey.supplies = j.optInt("supplies").coerceIn(0, 1_000_000)
            journey.invitationDay = j.optLong("invitationDay", -1).coerceAtLeast(-1)
            journey.invitationsToday = j.optInt("invitationsToday").coerceIn(0, 2)
            fun array(key: String, dest: IntArray, lo: Int, hi: Int) {
                j.optJSONArray(key)?.let { a -> for (i in dest.indices) dest[i] = a.optInt(i, dest[i]).coerceIn(lo, hi) }
            }
            array("research", journey.research, 0, 5); array("explored", journey.explored, 0, 1_000_000)
            journey.expedition = j.optInt("expedition", -1).takeIf { it in HamletJourney.regions.indices } ?: -1
            if (journey.expedition >= 0) {
                journey.departure = j.optLong("departure")
                journey.arrival = j.optLong("arrival")
                if (journey.departure <= 0 || journey.arrival < journey.departure) journey.expedition = -1
            }
            if (journey.expedition < 0) { journey.departure = 0; journey.arrival = 0 }
        }
        g.workSeconds = root.optInt("workSeconds").coerceIn(0, 7200)
        g.workStep = root.optInt("workStep").coerceIn(0, 11)
        g.workTool = root.optInt("workTool", -1).coerceIn(-1, 2)
        root.optJSONArray("paths")?.let { p ->
            var i = 0
            while (i + 1 < p.length()) {
                val k = p.getInt(i)
                val code = p.getInt(i + 1)
                if (k in g.paths.indices && code in Hamlet.pathById) g.paths[k] = code
                i += 2
            }
        }
        var maxId = 0
        var hasHall = false
        for (n in 0 until arr.length()) {
            val j = arr.getJSONObject(n)
            val t = j.optString("t")
            val def = Hamlet.byId[t] ?: continue
            if (def.isPath) continue
            val o = Obj(j.getInt("id"), t, j.getInt("x"), j.getInt("y"), j.optInt("f") == 1)
            if (o.id <= 0 || o.id == Int.MAX_VALUE || o.id in g.objs) return false
            if (!g.inGrid(o.x, o.y) || !g.inGrid(o.x + o.w - 1, o.y + o.d - 1)) continue
            if (def.cat == Cat.HALL && hasHall) continue
            if (hasOverlap(g, o)) continue
            o.crop = j.optString("c").takeIf { def.cat == Cat.FIELD && Hamlet.cropById[it]?.magic == def.magic }
            o.t0 = j.optLong("t0"); o.b0 = j.optLong("b0"); o.next = j.optLong("n")
            o.tier = j.optInt("tier", 1).coerceIn(1, HamletEconomy.MAX_TIER); o.tended = j.optBoolean("tended") && o.crop != null
            if (t in HamletLife.produce && def.cat != Cat.RANCH && o.t0 == 0L) o.t0 = g.now()
            if (def.cat == Cat.SHOP) j.optJSONObject("o")?.let { o.order = Order(it.optString("from", Hamlet.visitors[0]), it.optLong("t0"), it.optInt("recipeRank").coerceIn(0, HamletEconomy.MAX_TIER), it.optInt("recipeVersion", 1).coerceIn(1, HamletIndustry.RECIPE_VERSION)) }
            if (def.cat == Cat.HALL) hasHall = true
            g.objs[o.id] = o
            for (y in o.y until o.y + o.d) for (x in o.x until o.x + o.w) g.occ[g.idx(x, y)] = o.id
            maxId = maxOf(maxId, o.id)
        }
        if (!hasHall) return false
        val lands = mutableListOf<HamletRealms.Land>()
        val current = root.optInt("realmNo", 1)
        val realmNumbers = hashSetOf(current)
        val allIds = g.objs.keys.toMutableSet()
        root.optJSONArray("parked")?.let { a ->
            for (i in 0 until a.length()) {
                val j = a.getJSONObject(i)
                val no = j.getInt("no")
                if (no < 1 || !realmNumbers.add(no)) return false
                // Parse only terrain fields: nested parked-world structures can never recurse.
                val terrainRoot = JSONObject().put("objs", j.getJSONArray("objs")).put("paths", j.optJSONArray("paths") ?: JSONArray())
                    .put("hall", j.optInt("hall", 1)).put("expand", j.optInt("expand"))
                val land = HamletGame(g::now)
                if (!decode(land, terrainRoot.toString())) return false
                for (id in land.objs.keys) { if (!allIds.add(id)) return false; maxId = maxOf(maxId, id) }
                lands.add(HamletRealms.Land(no, j.optString("name"), LinkedHashMap(land.objs), land.paths.copyOf(), land.hall, land.expand))
            }
        }
        if (realmNumbers != (1..realmNumbers.size).toSet()) return false
        g.realms.restore(current, root.optString("realmName"), lands)
        g.nextId = maxOf(root.optInt("nextId", 1), maxId + 1)
        g.ensureDaily()
        HamletAdventureSave.decode(g.adventure, root.optJSONObject("adventure"))
        g.neighbors.decode(root.optJSONObject("neighbors"))
        root.optJSONArray("recentBuilds")?.let { a -> g.buildHistory.restoreRecent((0 until a.length()).map { a.optString(it) }) }
        root.optJSONArray("chronicle")?.let { a -> g.chronicle.restore((0 until minOf(a.length(), 24)).mapNotNull { index ->
            val e = a.optJSONObject(index) ?: return@mapNotNull null
            val kind = HamletChronicle.Kind.entries.firstOrNull { it.name == e.optString("kind") } ?: return@mapNotNull null
            HamletChronicle.Entry(kind, e.optLong("at"), e.optString("subject"), e.optString("actor"), e.optLong("amount"))
        }) }
        if (g.sandbox) {
            g.adventure.heroes.forEach { it.owned = true }
            for (i in g.adventure.pets.indices) {
                g.adventure.pets[i] = maxOf(1, g.adventure.pets[i]); g.adventure.relics[i] = maxOf(1, g.adventure.relics[i])
            }
        }
        g.stewardship.decode(root.optJSONObject("stewardship"))
        g.armory.decode(root.optJSONObject("armory"))
        g.shardShop.decode(root.optJSONObject("shardShop"))
        g.legends.decode(root.optJSONObject("legends"))
        g.bump()
        return true
    }

    private fun hasOverlap(g: HamletGame, o: Obj): Boolean {
        for (y in o.y until o.y + o.d) for (x in o.x until o.x + o.w) if (g.occ[g.idx(x, y)] != 0) return true
        return false
    }

    fun file(dir: File, creative: Boolean = false) = File(dir, if (creative) "hamlet_creative.json" else "hamlet.json")

    fun write(dir: File, g: HamletGame, creative: Boolean = false): Boolean = try {
        val dest = file(dir, creative)
        val tmp = File(dir, dest.name + ".tmp")
        tmp.writeText(toJson(g))
        if (dest.exists() && decode(HamletGame(g::now), dest.readText())) {
            Files.copy(dest.toPath(), File(dir, dest.name + ".bak").toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        try {
            Files.move(tmp.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(tmp.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        true
    } catch (_: Exception) {
        false
    }

    fun read(dir: File, g: HamletGame, creative: Boolean = false): Boolean {
        val primary = file(dir, creative)
        for (f in listOf(primary, File(dir, primary.name + ".bak"))) {
            if (f.exists() && try { fromJson(g, f.readText()) } catch (_: Exception) { false }) return true
        }
        return false
    }
}
