package com.Atom2Universe.app.games.farm

import com.Atom2Universe.app.R
import org.json.JSONObject

enum class FarmProject(val label: Int, val description: Int, val helpNeeded: Int, val parcel: Int) {
    WELL(R.string.farm_project_well, R.string.farm_project_well_desc, 3, 1),
    POND(R.string.farm_project_pond, R.string.farm_project_pond_desc, 6, 2),
    PICNIC(R.string.farm_project_picnic, R.string.farm_project_picnic_desc, 9, 3);

    fun stage(progress: Int): Int = if (progress >= helpNeeded) 3 else progress * 3 / helpNeeded
    fun nextStageAt(progress: Int): Int = ((stage(progress) + 1) * helpNeeded + 2) / 3
}

enum class FarmGift(val label: Int, val villager: FarmVillager) {
    FLOWERS(R.string.farm_gift_flowers, FarmVillager.LUCIE),
    BIRDHOUSE(R.string.farm_gift_birdhouse, FarmVillager.MALO),
    BUNTING(R.string.farm_gift_bunting, FarmVillager.IRIS);

    companion object { const val FRIENDSHIP_NEEDED = 3 }
}

/** Permanent scenery, funded by the existing deliveries rather than another inventory currency. */
class FarmProjectsState {
    private val contributions = IntArray(FarmProject.entries.size)
    private val claimed = mutableSetOf<FarmGift>()
    private val placements = arrayOfNulls<FarmGift>(FarmProject.entries.size)
    var active: FarmProject? = FarmProject.WELL
        private set
    var reserve = 0
        private set
    fun progress(project: FarmProject) = contributions[project.ordinal]
    fun complete(project: FarmProject) = progress(project) == project.helpNeeded
    fun owns(gift: FarmGift) = gift in claimed
    fun decoration(slot: FarmProject) = placements[slot.ordinal]
    fun location(gift: FarmGift): FarmProject? = FarmProject.entries.firstOrNull { decoration(it) == gift }

    /** Derive the balance from successful deliveries: stale callbacks cannot grant another point. */
    internal fun sync(deliveries: Int) {
        reserve = (deliveries - contributions.sum()).coerceAtLeast(0)
        active?.let { project ->
            val used = minOf(reserve, project.helpNeeded - progress(project))
            contributions[project.ordinal] += used
            reserve -= used
            if (complete(project)) active = null
        }
    }

    internal fun select(project: FarmProject, deliveries: Int): Boolean {
        if (complete(project) || active == project) return false
        active = project
        sync(deliveries)
        return true
    }

    internal fun claim(gift: FarmGift, friendship: Int): Boolean {
        if (friendship < FarmGift.FRIENDSHIP_NEEDED || owns(gift)) return false
        claimed += gift
        return true
    }

    /** Moving/replacing is free. Replaced objects return to the collection, never get consumed. */
    internal fun place(gift: FarmGift, slot: FarmProject): Boolean {
        if (!owns(gift) || decoration(slot) == gift) return false
        location(gift)?.let { placements[it.ordinal] = null }
        placements[slot.ordinal] = gift
        return true
    }
    internal fun store(slot: FarmProject): Boolean {
        if (decoration(slot) == null) return false
        placements[slot.ordinal] = null
        return true
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("active", active?.name ?: JSONObject.NULL)
        put("progress", JSONObject().apply { FarmProject.entries.forEach { put(it.name, progress(it)) } })
        put("gifts", JSONObject().apply { claimed.forEach { put(it.name, true) } })
        put("placements", JSONObject().apply {
            FarmProject.entries.forEach { slot -> decoration(slot)?.let { put(slot.name, it.name) } }
        })
    }

    companion object {
        internal fun fromJson(json: JSONObject?, deliveries: Int): FarmProjectsState {
            val state = FarmProjectsState()
            if (json != null) {
                val progress = json.optJSONObject("progress")
                var remaining = deliveries.coerceAtLeast(0)
                FarmProject.entries.forEach { project ->
                    val value = (progress?.optInt(project.name) ?: 0).coerceIn(0, minOf(project.helpNeeded, remaining))
                    state.contributions[project.ordinal] = value
                    remaining -= value
                }
                state.active = when {
                    json.isNull("active") -> null
                    else -> FarmProject.entries.firstOrNull { it.name == json.optString("active") } ?: FarmProject.WELL
                }
                val gifts = json.optJSONObject("gifts")
                FarmGift.entries.filter { gifts?.optBoolean(it.name) == true }.forEach { state.claimed += it }
                val slots = json.optJSONObject("placements")
                FarmProject.entries.forEach { slot ->
                    FarmGift.entries.firstOrNull { it.name == slots?.optString(slot.name) && state.owns(it) && state.location(it) == null }
                        ?.let { state.placements[slot.ordinal] = it }
                }
            }
            state.sync(deliveries)
            return state
        }
    }
}

/** Northern gardens do not change any parcel/cell index or cover the winding roads. */
object FarmProjectsLayout {
    fun project(project: FarmProject): FarmLayout.Area {
        val land = FarmLayout.lands[project.parcel]
        val center = land.x + land.width / 2
        return FarmLayout.Area(center - 135f, 24f, center + 55f, 160f)
    }
    fun decoration(slot: FarmProject): FarmLayout.Area {
        val site = project(slot)
        return FarmLayout.Area(site.right + 20f, 82f, site.right + 90f, 162f)
    }
}
