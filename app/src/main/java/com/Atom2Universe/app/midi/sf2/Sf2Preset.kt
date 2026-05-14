package com.Atom2Universe.app.midi.sf2

/**
 * Represents a preset (instrument program) with its associated regions.
 * A preset maps bank/program numbers to a collection of regions that
 * define how notes should be played.
 */
data class Sf2Preset(
    val name: String,           // Preset name (up to 20 chars)
    val bank: Int,              // Bank number (0-128, 128 = percussion)
    val program: Int,           // Program number (0-127)
    val regions: List<Sf2Region> // List of regions for this preset
) {
    /**
     * Returns a unique key for this preset: "bank:program"
     */
    fun getKey(): String = "$bank:$program"

    /**
     * Finds all regions that match the given key and velocity
     */
    fun getMatchingRegions(key: Int, velocity: Int): List<Sf2Region> {
        val normalizedKey = key.coerceIn(0, 127)
        val normalizedVel = velocity.coerceIn(0, 127)
        return regions.filter { it.matches(normalizedKey, normalizedVel) }
    }

    /**
     * Returns true if this is a percussion preset (bank 128)
     */
    fun isPercussion(): Boolean = bank == 128

    companion object {
        const val PERCUSSION_BANK = 128
    }
}

/**
 * Represents an instrument (internal to SF2, referenced by presets)
 */
data class Sf2Instrument(
    val name: String,
    val zones: List<Sf2ZoneData>,
    val globalZone: Sf2ZoneData?
)
