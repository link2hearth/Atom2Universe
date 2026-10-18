package com.Atom2Universe.app.games.farm

import org.json.JSONObject

/**
 * One farm, as it travels through Drive.
 *
 * The game is not described again here. What crosses the network is the very JSON that
 * [FarmState.save] already writes into the preferences, carried whole and untouched. A sync
 * layer that listed crops and parcels of its own would be a second reader of the save format,
 * and it would drift the day the format moves again - the same trap the ready-harvest badge
 * avoids by going through [FarmState] rather than re-reading the save.
 *
 * Around that opaque blob sits only what sharing needs:
 * - [seq] is the cloud version number, a new one at every upload. Comparing it with the number
 *   a device last saw is what tells "nobody played elsewhere" from "someone did". It is only
 *   ever compared for equality, never used to order two farms: two devices whose clocks
 *   disagree would order themselves wrong.
 * - [format] is the save version inside [state], checked before anything is applied.
 * - [savedAt] and [deviceName] are shown when the player has to choose between two farms.
 *   They describe, they never decide.
 */
data class FarmSyncFile(
    val seq: Long,
    val format: Int,
    val savedAt: Long,
    val deviceName: String,
    /** The raw contents of the `state` preference, opaque on purpose. */
    val state: String
) {
    /** Parsed once, and only to describe the farm on screen. A broken blob simply shows zeroes. */
    private val summary: JSONObject? by lazy { runCatching { JSONObject(state) }.getOrNull() }

    val coins: Long get() = summary?.optLong("coins") ?: 0L
    val harvests: Int get() = summary?.optInt("harvests") ?: 0

    fun toJson(): String = JSONObject().apply {
        put("version", ENVELOPE_VERSION)
        put("seq", seq)
        put("format", format)
        put("savedAt", savedAt)
        put("device", deviceName)
        put("state", state)
    }.toString()

    companion object {
        /** Version of the envelope itself, not of the farm it carries. */
        const val ENVELOPE_VERSION = 1

        /**
         * Null when the file is not a farm envelope at all. Better to act as if the cloud were
         * empty than to hand a malformed farm to the game.
         */
        fun fromJson(raw: String): FarmSyncFile? = runCatching {
            val j = JSONObject(raw)
            val state = j.getString("state")
            require(state.isNotBlank())
            FarmSyncFile(
                seq        = j.optLong("seq", 0L),
                format     = j.optInt("format", 0),
                savedAt    = j.optLong("savedAt", 0L),
                deviceName = j.optString("device", ""),
                state      = state
            )
        }.getOrNull()

        /** The save version written inside a blob, or 0 when it cannot be read. */
        fun formatOf(state: String): Int =
            runCatching { JSONObject(state).getInt("version") }.getOrDefault(0)
    }
}
