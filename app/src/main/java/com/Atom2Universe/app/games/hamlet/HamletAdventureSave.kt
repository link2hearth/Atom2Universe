package com.Atom2Universe.app.games.hamlet

import org.json.JSONArray
import org.json.JSONObject

/** Données permanentes seulement : quitter un combat ne coûte rien, il peut être rejoué librement. */
object HamletAdventureSave {
    fun encode(a: HamletAdventure): JSONObject {
        val j = JSONObject().put("bag", JSONArray(a.bag.toList())).put("frontier", a.frontier)
            .put("trial", a.trial).put("bounty", a.bounty).put("raid", a.raid).put("arena", a.arena)
            .put("team", a.selectedTeam).put("productionAt", a.productionAt).put("auto", a.auto)
            .put("recruitmentPulls", a.recruitmentPulls).put("pityEpic", a.pityEpic)
            .put("pityLegendary", a.pityLegendary).put("pityMythic", a.pityMythic)
            .put("recruitmentWork", a.recruitmentWork).put("innTicketProgress", a.innTicketProgress)
            .put("recruitmentVersion", 2).put("shardFocusHero", a.shardFocusHero)
            .put("productionRemainder", JSONArray(a.productionRemainder.toList()))
        for ((key, values) in listOf("tech" to a.tech, "workers" to a.workers, "troops" to a.troops,
            "troopTier" to a.troopTier, "relics" to a.relics, "pets" to a.pets, "teamPets" to a.teamPets, "teamRelics" to a.teamRelics)) j.put(key, JSONArray(values.toList()))
        j.put("teams", JSONArray(a.teams.map { JSONArray(it.toList()) }))
        j.put("tactics", JSONArray(a.tactics.map { it.name }))
        val heroes = JSONArray()
        for (h in a.heroes) heroes.put(JSONObject().put("id", h.id).put("owned", h.owned).put("level", h.level)
            .put("stars", h.stars).put("shards", h.shards).put("skills", JSONArray(h.skills.toList()))
            .put("gear", JSONArray(h.gear.toList())).put("gearTier", JSONArray(h.gearTier.toList())).put("traits", JSONArray(h.traits.toList())))
        j.put("heroes", heroes)
        val stars = JSONObject(); for ((k, v) in a.stars) stars.put(k.toString(), v)
        return j.put("stars", stars)
    }
    fun decode(a: HamletAdventure, j: JSONObject?) {
        if (j == null) return
        fun fill(key: String, dest: IntArray, lo: Int = 0, hi: Int = 1_000_000) {
            j.optJSONArray(key)?.let { arr -> for (i in dest.indices) dest[i] = arr.optInt(i, dest[i]).coerceIn(lo, hi) }
        }
        j.optJSONArray("bag")?.let { arr -> for (i in a.bag.indices) a.bag[i] = arr.optLong(i, a.bag[i]).coerceAtLeast(0) }
        a.frontier = j.optInt("frontier").coerceIn(0, 1_000_000)
        a.trial = j.optInt("trial").coerceIn(0, 1_000_000); a.bounty = j.optInt("bounty").coerceIn(0, 1_000_000)
        a.raid = j.optInt("raid").coerceIn(0, 1_000_000); a.arena = j.optInt("arena").coerceIn(0, 1_000_000)
        a.selectedTeam = j.optInt("team").coerceIn(0, 2)
        j.optJSONArray("tactics")?.let { arr -> for (i in a.tactics.indices) {
            a.tactics[i] = BattleTactic.entries.firstOrNull { it.name == arr.optString(i) } ?: BattleTactic.BALANCED
        } }
        a.productionAt = j.optLong("productionAt", a.productionAt); a.auto = j.optBoolean("auto", true)
        a.recruitmentPulls = j.optInt("recruitmentPulls").coerceAtLeast(0)
        a.pityEpic = j.optInt("pityEpic").coerceIn(0, HamletAdventure.PITY_EPIC - 1)
        a.pityLegendary = j.optInt("pityLegendary").coerceIn(0, HamletAdventure.PITY_LEGENDARY - 1)
        a.pityMythic = j.optInt("pityMythic").coerceIn(0, HamletAdventure.PITY_MYTHIC - 1)
        a.recruitmentWork = j.optLong("recruitmentWork").coerceIn(0, HamletAdventure.WORK_INVITATION_POINTS - 1)
        a.innTicketProgress = if (j.optInt("recruitmentVersion") >= 2) j.optLong("innTicketProgress").coerceIn(0, HamletAdventure.INN_INVITATION_UNITS - 1) else 0
        a.productionRemainder.fill(0.0)
        j.optJSONArray("productionRemainder")?.let { values ->
            for (i in a.productionRemainder.indices) {
                val fraction = values.optDouble(i, 0.0)
                a.productionRemainder[i] = if (fraction.isFinite()) fraction.coerceIn(0.0, 0.999999999999) else 0.0
            }
        }
        fill("tech", a.tech); fill("workers", a.workers, hi = 24); fill("troops", a.troops)
        var remaining = a.workerCapacity()
        for (i in a.workers.indices) { a.workers[i] = minOf(a.workers[i], remaining); remaining -= a.workers[i] }
        fill("troopTier", a.troopTier, 1, 10); fill("relics", a.relics); fill("pets", a.pets)
        fill("teamPets", a.teamPets, -1, 2); fill("teamRelics", a.teamRelics, -1, 2)
        j.optJSONArray("heroes")?.let { arr ->
            val seen = HashSet<Int>()
            for (i in 0 until arr.length()) {
                val h = arr.optJSONObject(i) ?: continue
                val id = h.optInt("id", -1)
                if (id !in a.heroes.indices || !seen.add(id)) continue
                val dest = a.heroes[id]
                dest.owned = h.optBoolean("owned"); dest.level = h.optInt("level", 1).coerceIn(1, 1_000_000)
                dest.stars = h.optInt("stars").coerceIn(0, HamletHeroes.STAR_CAP); dest.shards = h.optLong("shards").coerceAtLeast(0)
                for ((key, values) in listOf("skills" to dest.skills, "gear" to dest.gear, "gearTier" to dest.gearTier, "traits" to dest.traits))
                    h.optJSONArray(key)?.let { v -> for (n in values.indices) values[n] = v.optInt(n, values[n]).coerceIn(if (key == "skills") 1 else 0, if (key == "gearTier") 3 else 1_000_000) }
            }
        }
        // Ces héros ne peuvent jamais être perdus en jeu ; une sauvegarde partielle reste jouable.
        HamletAdventure.STARTERS.forEach { a.heroes[it].owned = true }
        a.shardFocusHero = j.optInt("shardFocusHero", -1).takeIf { a.heroes.getOrNull(it)?.owned == true } ?: -1
        j.optJSONArray("teams")?.let { teams -> for (i in a.teams.indices) teams.optJSONArray(i)?.let { t ->
            val seen = HashSet<Int>()
            for (n in 0..5) {
                val hero = t.optInt(n, -1)
                a.teams[i][n] = if (n < a.slotCount() && hero in a.heroes.indices && a.heroes[hero].owned && seen.add(hero)) hero else -1
            }
        } }
        for (team in a.teams) {
            val seen = HashSet<Int>()
            for (slot in team.indices) {
                val hero = team[slot]
                if (slot >= a.slotCount() || hero !in a.heroes.indices || !a.heroes[hero].owned || !seen.add(hero)) team[slot] = -1
            }
        }
        if (a.teams[a.selectedTeam].all { it < 0 }) {
            val fallback = a.heroes.firstOrNull { it.owned } ?: a.heroes[1].also { it.owned = true }
            a.teams[a.selectedTeam][0] = fallback.id
        }
        for (i in 0..2) {
            if (a.teamPets[i] >= 0 && a.pets[a.teamPets[i]] == 0) a.teamPets[i] = -1
            if (a.teamRelics[i] >= 0 && a.relics[a.teamRelics[i]] == 0) a.teamRelics[i] = -1
        }
        j.optJSONObject("stars")?.let { s -> for (k in s.keys()) k.toIntOrNull()?.takeIf { it >= 0 && it < a.frontier }?.let { a.stars[it] = s.optInt(k).coerceIn(0, 3) } }
    }
}
