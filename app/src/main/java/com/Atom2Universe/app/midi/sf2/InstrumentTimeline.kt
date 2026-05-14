package com.Atom2Universe.app.midi.sf2

import com.Atom2Universe.app.midi.analyzer.MidiFileAnalyzer.RequiredInstruments.Sf2PresetKey

/**
 * Analyzes a MIDI file and creates a complete timeline of instrument requirements.
 *
 * This is used for Phase 3 optimization: instead of loading all instruments at once,
 * we load them dynamically based on when they're needed in the timeline.
 */
class InstrumentTimeline {

    companion object {
        // Default lookahead for preloading (30 seconds)
        const val DEFAULT_LOOKAHEAD_MS = 30_000L

        // Time after which unused instruments can be unloaded
        const val UNLOAD_DELAY_MS = 30_000L
    }

    /**
     * A program change event in the timeline
     */
    data class ProgramChangeEvent(
        val timeMs: Long,
        val channel: Int,
        val bank: Int,
        val program: Int
    ) {
        val presetKey: Sf2PresetKey get() = Sf2PresetKey(bank, program)
    }

    /**
     * Time range during which an instrument is actively used
     */
    data class InstrumentUsageRange(
        val presetKey: Sf2PresetKey,
        val startMs: Long,
        val endMs: Long,  // Last note-off + release time
        val noteCount: Int
    ) {
        val durationMs: Long get() = endMs - startMs
    }

    /**
     * Statistics about instrument usage across the entire MIDI
     */
    data class InstrumentUsageStats(
        val presetKey: Sf2PresetKey,
        val totalNoteCount: Int,
        val totalDurationMs: Long,
        val usageRanges: List<InstrumentUsageRange>,
        val firstUseMs: Long,
        val lastUseMs: Long
    ) {
        /**
         * Score for determining "core" instruments (higher = more important)
         */
        val importanceScore: Int get() = totalNoteCount + (totalDurationMs / 1000).toInt()
    }

    /**
     * Snapshot of what instruments are needed at a specific time
     */
    data class InstrumentSnapshot(
        val timeMs: Long,
        val activeInstruments: Set<Sf2PresetKey>,
        val upcomingInstruments: Set<Sf2PresetKey>,  // Within lookahead window
        val allRequiredNow: Set<Sf2PresetKey>  // active + upcoming
    )

    /**
     * The loading strategy determined based on memory constraints
     */
    enum class LoadingStrategy {
        LOAD_ALL,       // Enough RAM - load everything at start
        DYNAMIC         // Limited RAM - load/unload dynamically
    }

    /**
     * Result of timeline analysis
     */
    data class AnalysisResult(
        val strategy: LoadingStrategy,
        val allInstruments: Set<Sf2PresetKey>,
        val coreInstruments: Set<Sf2PresetKey>,  // Always keep loaded
        val initialInstruments: Set<Sf2PresetKey>,  // Load at start
        val programChanges: List<ProgramChangeEvent>,
        val usageStats: Map<Sf2PresetKey, InstrumentUsageStats>,
        val totalMemoryEstimateMB: Long,
        val durationMs: Long
    )

    // Timeline data
    private val programChanges = mutableListOf<ProgramChangeEvent>()
    private val channelState = mutableMapOf<Int, Sf2PresetKey>()  // Current program per channel
    private val usageRanges = mutableMapOf<Sf2PresetKey, MutableList<InstrumentUsageRange>>()
    private val noteCountPerInstrument = mutableMapOf<Sf2PresetKey, Int>()

    // Active notes tracking for calculating usage ranges
    private val activeNotes = mutableMapOf<Sf2PresetKey, MutableList<Long>>()  // Start times

    private var durationMs: Long = 0

    /**
     * Initialize default instruments for all 16 channels
     */
    init {
        // Default: all channels use bank 0, program 0 (Piano)
        // Except channel 9 (percussion) which uses bank 128
        for (ch in 0 until 16) {
            val bank = if (ch == 9) 128 else 0
            channelState[ch] = Sf2PresetKey(bank, 0)
        }
    }

    /**
     * Add a program change event to the timeline
     */
    fun addProgramChange(timeMs: Long, channel: Int, bank: Int, program: Int) {
        val event = ProgramChangeEvent(timeMs, channel, bank, program)
        programChanges.add(event)
        channelState[channel] = event.presetKey

        // Ensure this instrument has tracking initialized
        if (!usageRanges.containsKey(event.presetKey)) {
            usageRanges[event.presetKey] = mutableListOf()
        }
    }

    /**
     * Add a note event (for tracking instrument usage)
     */
    fun addNote(timeMs: Long, channel: Int, durationMs: Long) {
        val preset = channelState[channel] ?: return

        // Count notes
        noteCountPerInstrument[preset] = (noteCountPerInstrument[preset] ?: 0) + 1

        // Track active notes for range calculation
        val endTime = timeMs + durationMs
        if (endTime > this.durationMs) {
            this.durationMs = endTime
        }

        // Update or create usage range
        updateUsageRange(preset, timeMs, endTime)
    }

    /**
     * Update usage range for an instrument
     */
    private fun updateUsageRange(preset: Sf2PresetKey, startMs: Long, endMs: Long) {
        val ranges = usageRanges.getOrPut(preset) { mutableListOf() }

        if (ranges.isEmpty()) {
            // First range for this instrument
            ranges.add(InstrumentUsageRange(preset, startMs, endMs, 1))
            return
        }

        val lastRange = ranges.last()

        // If this note overlaps or is close to the last range, extend it
        // "Close" means within 5 seconds - instruments used closely together are one "usage"
        if (startMs <= lastRange.endMs + 5000) {
            // Extend the range
            ranges[ranges.lastIndex] = lastRange.copy(
                endMs = maxOf(lastRange.endMs, endMs),
                noteCount = lastRange.noteCount + 1
            )
        } else {
            // New separate usage range
            ranges.add(InstrumentUsageRange(preset, startMs, endMs, 1))
        }
    }

    /**
     * Set the total duration of the MIDI file
     */
    fun setDuration(durationMs: Long) {
        this.durationMs = maxOf(this.durationMs, durationMs)
    }

    /**
     * Analyze the timeline and determine loading strategy
     *
     * @param availableMemoryMB Available memory budget for samples
     * @param estimatedMemoryPerPresetMB Average memory per preset (from SF2 analysis)
     * @param maxCoreInstruments Maximum number of "core" instruments to keep always loaded
     */
    fun analyze(
        availableMemoryMB: Long,
        estimatedMemoryPerPresetMB: Long = 5,  // Conservative default
        maxCoreInstruments: Int = 8
    ): AnalysisResult {
        // Sort program changes by time
        programChanges.sortBy { it.timeMs }

        // Collect all unique instruments
        val allInstruments = mutableSetOf<Sf2PresetKey>()

        // Add default instruments (position 0)
        for (ch in 0 until 16) {
            val bank = if (ch == 9) 128 else 0
            allInstruments.add(Sf2PresetKey(bank, 0))
        }

        // Add instruments from program changes
        allInstruments.addAll(programChanges.map { it.presetKey })

        // Add instruments from note tracking
        allInstruments.addAll(noteCountPerInstrument.keys)

        // Calculate usage stats for each instrument
        val usageStats = allInstruments.associateWith { preset ->
            val ranges = usageRanges[preset] ?: emptyList()
            val noteCount = noteCountPerInstrument[preset] ?: 0
            val totalDuration = ranges.sumOf { it.durationMs }
            val firstUse = ranges.minOfOrNull { it.startMs } ?: 0L
            val lastUse = ranges.maxOfOrNull { it.endMs } ?: durationMs

            InstrumentUsageStats(
                presetKey = preset,
                totalNoteCount = noteCount,
                totalDurationMs = totalDuration,
                usageRanges = ranges.toList(),
                firstUseMs = firstUse,
                lastUseMs = lastUse
            )
        }

        // Determine core instruments (most used)
        val coreInstruments = usageStats.values
            .sortedByDescending { it.importanceScore }
            .take(maxCoreInstruments)
            .map { it.presetKey }
            .toSet()

        // Determine initial instruments (needed at position 0 + lookahead)
        val initialInstruments = getInstrumentsForTimeRange(0, DEFAULT_LOOKAHEAD_MS)

        // Calculate total memory needed
        val totalMemoryMB = allInstruments.size * estimatedMemoryPerPresetMB

        // Determine strategy
        val strategy = if (totalMemoryMB <= availableMemoryMB) {
            LoadingStrategy.LOAD_ALL
        } else {
            LoadingStrategy.DYNAMIC
        }

        return AnalysisResult(
            strategy = strategy,
            allInstruments = allInstruments,
            coreInstruments = coreInstruments,
            initialInstruments = initialInstruments + coreInstruments,
            programChanges = programChanges.toList(),
            usageStats = usageStats,
            totalMemoryEstimateMB = totalMemoryMB,
            durationMs = durationMs
        )
    }

    /**
     * Get instruments needed for a specific time range
     */
    fun getInstrumentsForTimeRange(startMs: Long, endMs: Long): Set<Sf2PresetKey> {
        val instruments = mutableSetOf<Sf2PresetKey>()

        // Find channel state at startMs
        val channelStateAtStart = mutableMapOf<Int, Sf2PresetKey>()
        for (ch in 0 until 16) {
            val bank = if (ch == 9) 128 else 0
            channelStateAtStart[ch] = Sf2PresetKey(bank, 0)
        }

        // Apply program changes up to startMs
        for (pc in programChanges) {
            if (pc.timeMs > startMs) break
            channelStateAtStart[pc.channel] = pc.presetKey
        }

        // Add current instruments at start position
        instruments.addAll(channelStateAtStart.values)

        // Add instruments from program changes within the range
        for (pc in programChanges) {
            if (pc.timeMs > endMs) break
            if (pc.timeMs >= startMs) {
                instruments.add(pc.presetKey)
            }
        }

        return instruments
    }

    /**
     * Get a snapshot of instrument requirements at a specific position
     */
    fun getSnapshot(positionMs: Long, lookaheadMs: Long = DEFAULT_LOOKAHEAD_MS): InstrumentSnapshot {
        // Active instruments (currently playing on channels)
        val activeInstruments = getInstrumentsForTimeRange(0, positionMs)
            .toMutableSet()

        // Recalculate based on actual channel state at position
        val channelStateAtPos = mutableMapOf<Int, Sf2PresetKey>()
        for (ch in 0 until 16) {
            val bank = if (ch == 9) 128 else 0
            channelStateAtPos[ch] = Sf2PresetKey(bank, 0)
        }
        for (pc in programChanges) {
            if (pc.timeMs > positionMs) break
            channelStateAtPos[pc.channel] = pc.presetKey
        }
        val currentActive = channelStateAtPos.values.toSet()

        // Upcoming instruments (within lookahead window)
        val upcoming = getInstrumentsForTimeRange(positionMs, positionMs + lookaheadMs)

        return InstrumentSnapshot(
            timeMs = positionMs,
            activeInstruments = currentActive,
            upcomingInstruments = upcoming - currentActive,
            allRequiredNow = currentActive + upcoming
        )
    }

    /**
     * Get program changes within a time range (for preloading decisions)
     */
    fun getProgramChangesInRange(startMs: Long, endMs: Long): List<ProgramChangeEvent> {
        return programChanges.filter { it.timeMs in startMs..endMs }
    }

    /**
     * Get the next program change after a given time
     */
    fun getNextProgramChange(afterMs: Long): ProgramChangeEvent? {
        return programChanges.firstOrNull { it.timeMs > afterMs }
    }

    /**
     * Check if an instrument can be safely unloaded at a given position
     * (not needed for at least UNLOAD_DELAY_MS)
     */
    fun canUnloadInstrument(preset: Sf2PresetKey, positionMs: Long): Boolean {
        val nextUse = getNextUseTime(preset, positionMs)
        return nextUse == null || nextUse > positionMs + UNLOAD_DELAY_MS
    }

    /**
     * Get the next time an instrument will be used after a position
     */
    fun getNextUseTime(preset: Sf2PresetKey, afterMs: Long): Long? {
        // Check program changes
        val nextProgramChange = programChanges.firstOrNull {
            it.timeMs > afterMs && it.presetKey == preset
        }

        // Check usage ranges
        val ranges = usageRanges[preset] ?: emptyList()
        val nextRange = ranges.firstOrNull { it.startMs > afterMs }

        return listOfNotNull(nextProgramChange?.timeMs, nextRange?.startMs).minOrNull()
    }

    /**
     * Clear all data (for reuse)
     */
    fun clear() {
        programChanges.clear()
        channelState.clear()
        usageRanges.clear()
        noteCountPerInstrument.clear()
        activeNotes.clear()
        durationMs = 0

        // Reinitialize channel state
        for (ch in 0 until 16) {
            val bank = if (ch == 9) 128 else 0
            channelState[ch] = Sf2PresetKey(bank, 0)
        }
    }
}
