package com.Atom2Universe.app.midi.sf2

import android.app.ActivityManager
import android.content.Context
import com.Atom2Universe.app.midi.analyzer.MidiFileAnalyzer.RequiredInstruments
import com.Atom2Universe.app.midi.analyzer.MidiFileAnalyzer.RequiredInstruments.Sf2PresetKey
import java.io.Closeable
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Manages dynamic loading and unloading of SF2 samples based on playback position.
 *
 * Phase 3 optimization: Instead of loading all instruments at once, this preloader
 * loads instruments on-demand with lookahead to ensure smooth playback.
 *
 * Two strategies:
 * - LOAD_ALL: If enough RAM, load everything at start (simplest, no seek issues)
 * - DYNAMIC: Load/unload based on timeline, pause on seek if needed
 */
class SamplePreloader(
    private val context: Context,
    private val sf2Path: String,
    private val metadata: Sf2Metadata
) : Closeable {

    companion object {
        // Memory safety margin (keep at least this much free)
        private const val MEMORY_SAFETY_MARGIN_MB = 100L

        // Preload thread pool size
        private const val PRELOAD_THREADS = 2

        // Position update interval for preloading decisions
        private const val POSITION_UPDATE_INTERVAL_MS = 500L

        // How far ahead to preload (in addition to timeline lookahead)
        private const val PRELOAD_MARGIN_MS = 5000L
    }

    /**
     * Listener for preloader state changes
     */
    interface PreloaderListener {
        fun onLoadingStateChanged(isLoading: Boolean, progress: Float, message: String)
        fun onStrategyDetermined(strategy: InstrumentTimeline.LoadingStrategy)
        fun onError(message: String)
    }

    /**
     * State of a preset in the preloader
     */
    enum class PresetState {
        NOT_LOADED,
        LOADING,
        LOADED,
        UNLOADING
    }

    /**
     * Information about a loaded preset
     */
    private data class LoadedPresetInfo(
        val presetKey: Sf2PresetKey,
        val sampleIds: Set<Int>,
        val memorySizeBytes: Long,
        val loadedAtMs: Long
    )

    // State
    private var strategy: InstrumentTimeline.LoadingStrategy = InstrumentTimeline.LoadingStrategy.LOAD_ALL
    private var timeline: InstrumentTimeline? = null
    private var analysisResult: InstrumentTimeline.AnalysisResult? = null
    private val isInitialized = AtomicBoolean(false)
    private val isReleased = AtomicBoolean(false)

    // Currently loaded presets and their state
    private val presetStates = ConcurrentHashMap<Sf2PresetKey, PresetState>()
    private val loadedPresets = ConcurrentHashMap<Sf2PresetKey, LoadedPresetInfo>()

    // Sample data management
    private var currentSf2File: Sf2File? = null
    private val sampleDataLock = Any()

    // The compact sample array (grows as we load more instruments)
    private var compactSampleData: ShortArray = ShortArray(0)
    private var remappedHeaders: List<Sf2SampleHeader> = emptyList()
    private var currentPresetMap: MutableMap<String, Sf2Preset> = mutableMapOf()

    // Sample remapping tracking
    private val sampleRemapping = ConcurrentHashMap<Int, SampleRemapInfo>()
    private var nextCompactOffset = 0L

    // Playback position tracking
    private val currentPositionMs = AtomicLong(0)

    // Threading
    private var preloadExecutor: ScheduledExecutorService? = null
    private var preloadFuture: ScheduledFuture<*>? = null
    private val loadingQueue = LinkedBlockingQueue<Sf2PresetKey>()

    // Listener
    private var listener: PreloaderListener? = null

    /**
     * Sample remap info for incremental loading
     */
    private data class SampleRemapInfo(
        val originalStart: Long,
        val originalEnd: Long,
        val compactStart: Long,
        val loopStartOffset: Long,
        val loopEndOffset: Long
    )

    /**
     * Set the listener for state changes
     */
    fun setListener(listener: PreloaderListener?) {
        this.listener = listener
    }

    /**
     * Initialize the preloader with a timeline
     *
     * @param timeline The instrument timeline from MIDI analysis
     * @return The initial Sf2File ready for playback
     */
    fun initialize(timeline: InstrumentTimeline): Sf2File? {
        if (isReleased.get()) {
            return null
        }

        this.timeline = timeline

        // Calculate available memory
        val availableMemoryMB = getAvailableMemoryMB()

        // Estimate memory per preset (based on SF2 metadata)
        val avgMemoryPerPresetMB = estimateMemoryPerPreset()

        // Analyze timeline to determine strategy
        val analysis = timeline.analyze(
            availableMemoryMB = availableMemoryMB - MEMORY_SAFETY_MARGIN_MB,
            estimatedMemoryPerPresetMB = avgMemoryPerPresetMB
        )
        this.analysisResult = analysis
        this.strategy = analysis.strategy

        listener?.onStrategyDetermined(strategy)

        return when (strategy) {
            InstrumentTimeline.LoadingStrategy.LOAD_ALL -> {
                // Load all instruments at once
                initializeLoadAll(analysis)
            }
            InstrumentTimeline.LoadingStrategy.DYNAMIC -> {
                // Load initial instruments, start preload monitoring
                initializeDynamic(analysis)
            }
        }
    }

    /**
     * Initialize with LOAD_ALL strategy - load everything at start
     */
    private fun initializeLoadAll(analysis: InstrumentTimeline.AnalysisResult): Sf2File? {
        listener?.onLoadingStateChanged(true, 0f, "Chargement de tous les instruments...")

        try {
            // Use existing streaming loader to load all required instruments
            val requiredInstruments = RequiredInstruments(
                presets = analysis.allInstruments,
                hasPercussion = analysis.allInstruments.any { it.bank == 128 },
                programChangesByTime = emptyList()
            )

            val sf2File = Sf2FileCache.getStreaming(sf2Path, requiredInstruments)

            if (sf2File != null) {
                currentSf2File = sf2File
                // Mark all presets as loaded
                for (preset in analysis.allInstruments) {
                    presetStates[preset] = PresetState.LOADED
                }
                isInitialized.set(true)

                listener?.onLoadingStateChanged(false, 1f, "Prêt")
            }

            return sf2File

        } catch (e: Exception) {
            listener?.onError("Erreur de chargement: ${e.message}")
            return null
        }
    }

    /**
     * Initialize with DYNAMIC strategy - load initial instruments and start monitoring
     */
    private fun initializeDynamic(analysis: InstrumentTimeline.AnalysisResult): Sf2File? {
        listener?.onLoadingStateChanged(true, 0f, "Chargement initial...")

        try {
            // Load initial instruments (position 0 + lookahead + core)
            val initialPresets = analysis.initialInstruments

            // Create initial SF2 with just these presets
            val sf2File = loadPresetsIncremental(initialPresets.toList())

            if (sf2File != null) {
                currentSf2File = sf2File
                isInitialized.set(true)

                // Start preload monitoring thread
                startPreloadMonitoring()

                listener?.onLoadingStateChanged(false, 1f, "Prêt (chargement dynamique)")
            }

            return sf2File

        } catch (e: Exception) {
            listener?.onError("Erreur de chargement: ${e.message}")
            return null
        }
    }

    /**
     * Load presets incrementally (for DYNAMIC mode)
     */
    private fun loadPresetsIncremental(presets: List<Sf2PresetKey>): Sf2File? {
        synchronized(sampleDataLock) {
            val parser = Sf2StreamingParser()

            // Find sample IDs needed for these presets
            val requiredSampleIds = mutableSetOf<Int>()
            for (preset in presets) {
                if (presetStates[preset] == PresetState.LOADED) continue

                presetStates[preset] = PresetState.LOADING
                val sampleIds = findSampleIdsForPreset(preset)
                requiredSampleIds.addAll(sampleIds)
            }

            if (requiredSampleIds.isEmpty() && currentSf2File != null) {
                // Nothing new to load
                return currentSf2File
            }

            // Filter to only samples not yet loaded
            val newSampleIds = requiredSampleIds.filter { !sampleRemapping.containsKey(it) }

            if (newSampleIds.isNotEmpty()) {
                // Load new samples and expand compact array
                loadNewSamples(newSampleIds)
            }

            // Rebuild preset map with new presets
            buildPresetsForLoaded(presets)

            // Create new Sf2File with updated data
            val sf2File = Sf2File(
                name = metadata.name,
                sampleData = compactSampleData,
                presetMap = currentPresetMap.toMap(),
                sampleHeaders = remappedHeaders
            )

            // Mark presets as loaded
            for (preset in presets) {
                presetStates[preset] = PresetState.LOADED
            }

            currentSf2File = sf2File
            return sf2File
        }
    }

    /**
     * Load new samples into the compact array
     */
    private fun loadNewSamples(sampleIds: List<Int>) {
        // Calculate how much new data we need
        var newDataSize = 0L
        for (sampleId in sampleIds) {
            val header = metadata.sampleHeaders.getOrNull(sampleId) ?: continue
            newDataSize += (header.end - header.start)
        }

        // Expand compact array
        val oldSize = compactSampleData.size
        val newSize = oldSize + newDataSize.toInt()
        val newArray = ShortArray(newSize)
        System.arraycopy(compactSampleData, 0, newArray, 0, oldSize)

        // Load new samples
        java.io.RandomAccessFile(java.io.File(metadata.filePath), "r").use { raf ->
            for (sampleId in sampleIds) {
                val header = metadata.sampleHeaders.getOrNull(sampleId) ?: continue
                val sampleLength = (header.end - header.start).toInt()
                if (sampleLength <= 0) continue

                // Record remapping
                sampleRemapping[sampleId] = SampleRemapInfo(
                    originalStart = header.start,
                    originalEnd = header.end,
                    compactStart = nextCompactOffset,
                    loopStartOffset = header.startLoop - header.start,
                    loopEndOffset = header.endLoop - header.start
                )

                // Read sample data
                val byteOffset = metadata.smplByteOffset + (header.start * 2)
                raf.seek(byteOffset)
                val buffer = java.nio.ByteBuffer.allocate(sampleLength * 2)
                    .order(java.nio.ByteOrder.LITTLE_ENDIAN)
                raf.channel.read(buffer)
                buffer.rewind()

                val destOffset = nextCompactOffset.toInt()
                for (i in 0 until sampleLength) {
                    newArray[destOffset + i] = buffer.short
                }

                nextCompactOffset += sampleLength
            }
        }

        compactSampleData = newArray

        // Update remapped headers
        remappedHeaders = metadata.sampleHeaders.mapIndexed { index, header ->
            val remap = sampleRemapping[index]
            if (remap != null) {
                Sf2SampleHeader(
                    name = header.name,
                    start = remap.compactStart,
                    end = remap.compactStart + (header.end - header.start),
                    startLoop = remap.compactStart + remap.loopStartOffset,
                    endLoop = remap.compactStart + remap.loopEndOffset,
                    sampleRate = header.sampleRate,
                    originalPitch = header.originalPitch,
                    pitchCorrection = header.pitchCorrection,
                    sampleLink = header.sampleLink,
                    sampleType = header.sampleType
                )
            } else {
                header
            }
        }
    }

    /**
     * Find sample IDs used by a preset
     */
    private fun findSampleIdsForPreset(preset: Sf2PresetKey): Set<Int> {
        val sampleIds = mutableSetOf<Int>()

        // Find preset header
        val presetIndex = metadata.presetHeaders.indexOfFirst {
            it.bank == preset.bank && it.preset == preset.program
        }
        if (presetIndex < 0 || presetIndex >= metadata.presetHeaders.size - 1) {
            return sampleIds
        }

        val presetHeader = metadata.presetHeaders[presetIndex]
        val nextPreset = metadata.presetHeaders.getOrNull(presetIndex + 1)
        val zoneStart = presetHeader.bagIndex
        val zoneEnd = nextPreset?.bagIndex ?: metadata.presetBags.size

        // Process preset zones
        for (zoneIndex in zoneStart until zoneEnd) {
            val bag = metadata.presetBags.getOrNull(zoneIndex) ?: continue
            val nextBag = metadata.presetBags.getOrNull(zoneIndex + 1)
            val genStart = bag.generatorIndex
            val genEnd = nextBag?.generatorIndex ?: metadata.presetGenerators.size

            // Find instrument reference
            for (genIndex in genStart until genEnd) {
                val gen = metadata.presetGenerators.getOrNull(genIndex) ?: continue
                if (gen.operator == Sf2Generator.INSTRUMENT.id) {
                    val instrumentIndex = gen.amount and 0xFFFF  // Unsigned word
                    sampleIds.addAll(findSampleIdsForInstrument(instrumentIndex))
                }
            }
        }

        return sampleIds
    }

    /**
     * Find sample IDs used by an instrument
     */
    private fun findSampleIdsForInstrument(instrumentIndex: Int): Set<Int> {
        val sampleIds = mutableSetOf<Int>()

        if (instrumentIndex < 0 || instrumentIndex >= metadata.instrumentHeaders.size - 1) {
            return sampleIds
        }

        val instrument = metadata.instrumentHeaders[instrumentIndex]
        val nextInstrument = metadata.instrumentHeaders.getOrNull(instrumentIndex + 1)
        val zoneStart = instrument.bagIndex
        val zoneEnd = nextInstrument?.bagIndex ?: metadata.instrumentBags.size

        for (zoneIndex in zoneStart until zoneEnd) {
            val bag = metadata.instrumentBags.getOrNull(zoneIndex) ?: continue
            val nextBag = metadata.instrumentBags.getOrNull(zoneIndex + 1)
            val genStart = bag.generatorIndex
            val genEnd = nextBag?.generatorIndex ?: metadata.instrumentGenerators.size

            for (genIndex in genStart until genEnd) {
                val gen = metadata.instrumentGenerators.getOrNull(genIndex) ?: continue
                if (gen.operator == Sf2Generator.SAMPLE_ID.id) {
                    val sampleId = gen.amount and 0xFFFF  // Unsigned word
                    sampleIds.add(sampleId)

                    // Also add linked samples
                    val header = metadata.sampleHeaders.getOrNull(sampleId)
                    if (header != null && header.sampleLink > 0) {
                        sampleIds.add(header.sampleLink)
                    }
                }
            }
        }

        return sampleIds
    }

    /**
     * Build presets for currently loaded samples
     */
    private fun buildPresetsForLoaded(newPresets: List<Sf2PresetKey>) {
        val parser = Sf2StreamingParser()

        for (preset in newPresets) {
            if (currentPresetMap.containsKey("${preset.bank}:${preset.program}")) continue

            val builtPreset = buildSinglePreset(preset)
            if (builtPreset != null) {
                currentPresetMap["${preset.bank}:${preset.program}"] = builtPreset
            }
        }
    }

    /**
     * Build a single preset using current remapped headers
     */
    private fun buildSinglePreset(presetKey: Sf2PresetKey): Sf2Preset? {
        val presetIndex = metadata.presetHeaders.indexOfFirst {
            it.bank == presetKey.bank && it.preset == presetKey.program
        }
        if (presetIndex < 0 || presetIndex >= metadata.presetHeaders.size - 1) {
            return null
        }

        val preset = metadata.presetHeaders[presetIndex]
        val nextPreset = metadata.presetHeaders.getOrNull(presetIndex + 1)
        val zoneStart = preset.bagIndex
        val zoneEnd = nextPreset?.bagIndex ?: metadata.presetBags.size

        val regions = mutableListOf<Sf2Region>()
        var presetGlobal = Sf2ZoneData.createDefaults()

        for (zoneIndex in zoneStart until zoneEnd) {
            val bag = metadata.presetBags.getOrNull(zoneIndex) ?: continue
            val nextBag = metadata.presetBags.getOrNull(zoneIndex + 1)
            val genStart = bag.generatorIndex
            val genEnd = nextBag?.generatorIndex ?: metadata.presetGenerators.size

            val zoneData = collectGeneratorValues(metadata.presetGenerators, genStart, genEnd)

            if (zoneData.instrumentIndex == null) {
                presetGlobal = mergeZoneData(presetGlobal, zoneData)
                continue
            }

            val instrumentZones = buildInstrumentZones(zoneData.instrumentIndex)
            val presetApplied = mergeZoneData(presetGlobal, zoneData)

            for ((instZone, _) in instrumentZones) {
                // instZone already has instrument global merged in (from buildInstrumentZones),
                // merge directly with presetApplied to avoid double-applying additive generators
                val combined = mergeZoneData(presetApplied, instZone)
                val region = Sf2Region.fromZoneData(combined, compactSampleData, remappedHeaders)
                if (region != null) {
                    regions.add(region)
                }
            }
        }

        return Sf2Preset(
            name = preset.name,
            bank = preset.bank,
            program = preset.preset,
            regions = regions
        )
    }

    /**
     * Build instrument zones for a given instrument index
     */
    private fun buildInstrumentZones(instrumentIndex: Int): List<Pair<Sf2ZoneData, Sf2ZoneData>> {
        val result = mutableListOf<Pair<Sf2ZoneData, Sf2ZoneData>>()

        if (instrumentIndex < 0 || instrumentIndex >= metadata.instrumentHeaders.size - 1) {
            return result
        }

        val instrument = metadata.instrumentHeaders[instrumentIndex]
        val nextInstrument = metadata.instrumentHeaders.getOrNull(instrumentIndex + 1)
        val zoneStart = instrument.bagIndex
        val zoneEnd = nextInstrument?.bagIndex ?: metadata.instrumentBags.size

        var globalZone = Sf2ZoneData.createDefaults()

        for (zoneIndex in zoneStart until zoneEnd) {
            val bag = metadata.instrumentBags.getOrNull(zoneIndex) ?: continue
            val nextBag = metadata.instrumentBags.getOrNull(zoneIndex + 1)
            val genStart = bag.generatorIndex
            val genEnd = nextBag?.generatorIndex ?: metadata.instrumentGenerators.size

            val zoneData = collectGeneratorValues(metadata.instrumentGenerators, genStart, genEnd)

            if (zoneData.sampleId == null) {
                globalZone = mergeZoneData(globalZone, zoneData)
            } else {
                val merged = mergeZoneData(globalZone, zoneData)
                val withHeader = merged.copy(
                    sampleHeader = remappedHeaders.getOrNull(merged.sampleId ?: 0)
                )
                result.add(Pair(withHeader, globalZone))
            }
        }

        return result
    }

    // Helper functions for zone data collection (similar to Sf2StreamingParser)
    private fun collectGeneratorValues(generators: List<Sf2GeneratorEntry>, start: Int, end: Int): Sf2ZoneData {
        var data = Sf2ZoneData.createDefaults()
        for (i in start until end) {
            val gen = generators.getOrNull(i) ?: continue
            data = applyGenerator(data, gen)
        }
        return data
    }

    private fun applyGenerator(data: Sf2ZoneData, gen: Sf2GeneratorEntry): Sf2ZoneData {
        return when (gen.operator) {
            Sf2Generator.KEY_RANGE.id -> data.copy(keyRange = (gen.amount and 0xFF)..(gen.amount shr 8 and 0xFF))
            Sf2Generator.VEL_RANGE.id -> data.copy(velRange = (gen.amount and 0xFF)..(gen.amount shr 8 and 0xFF))
            Sf2Generator.INSTRUMENT.id -> data.copy(instrumentIndex = gen.amount and 0xFFFF)  // Unsigned word
            Sf2Generator.SAMPLE_ID.id -> data.copy(sampleId = gen.amount and 0xFFFF)  // Unsigned word
            Sf2Generator.SAMPLE_MODES.id -> data.copy(sampleModes = gen.amount)
            Sf2Generator.OVERRIDING_ROOT_KEY.id -> data.copy(rootKey = gen.amount)
            Sf2Generator.COARSE_TUNE.id -> data.copy(coarseTune = gen.amount.toShort().toInt())
            Sf2Generator.FINE_TUNE.id -> data.copy(fineTune = gen.amount.toShort().toInt())
            Sf2Generator.SCALE_TUNING.id -> data.copy(scaleTuning = gen.amount)
            Sf2Generator.START_ADDRS_OFFSET.id -> data.copy(startOffset = gen.amount)
            Sf2Generator.END_ADDRS_OFFSET.id -> data.copy(endOffset = gen.amount.toShort().toInt())
            Sf2Generator.STARTLOOP_ADDRS_OFFSET.id -> data.copy(startLoopOffset = gen.amount)
            Sf2Generator.ENDLOOP_ADDRS_OFFSET.id -> data.copy(endLoopOffset = gen.amount.toShort().toInt())
            Sf2Generator.INITIAL_ATTENUATION.id -> data.copy(attenuation = gen.amount)
            Sf2Generator.PAN.id -> data.copy(pan = (gen.amount.toShort() / 500f).coerceIn(-1f, 1f))
            Sf2Generator.INITIAL_FILTER_FC.id -> data.copy(filterFc = gen.amount)
            Sf2Generator.INITIAL_FILTER_Q.id -> data.copy(filterQ = gen.amount)
            else -> data
        }
    }

    private fun mergeZoneData(base: Sf2ZoneData, overlay: Sf2ZoneData): Sf2ZoneData {
        return base.copy(
            keyRange = if (overlay.keyRange != 0..127) overlay.keyRange else base.keyRange,
            velRange = if (overlay.velRange != 0..127) overlay.velRange else base.velRange,
            instrumentIndex = overlay.instrumentIndex ?: base.instrumentIndex,
            sampleId = overlay.sampleId ?: base.sampleId,
            sampleHeader = overlay.sampleHeader ?: base.sampleHeader,
            sampleModes = if (overlay.sampleModes != 0) overlay.sampleModes else base.sampleModes,
            rootKey = overlay.rootKey ?: base.rootKey,
            coarseTune = if (overlay.coarseTune != 0) overlay.coarseTune else base.coarseTune,
            fineTune = if (overlay.fineTune != 0) overlay.fineTune else base.fineTune,
            scaleTuning = if (overlay.scaleTuning != 100) overlay.scaleTuning else base.scaleTuning,
            startOffset = overlay.startOffset + base.startOffset,
            endOffset = overlay.endOffset + base.endOffset,
            startLoopOffset = overlay.startLoopOffset + base.startLoopOffset,
            endLoopOffset = overlay.endLoopOffset + base.endLoopOffset,
            attenuation = overlay.attenuation + base.attenuation,
            pan = overlay.pan ?: base.pan,
            filterFc = overlay.filterFc ?: base.filterFc,
            filterQ = overlay.filterQ ?: base.filterQ,
            exclusiveClass = if (overlay.exclusiveClass != 0) overlay.exclusiveClass else base.exclusiveClass,
            reverbSend = overlay.reverbSend ?: base.reverbSend,
            chorusSend = overlay.chorusSend ?: base.chorusSend,
            volumeEnvelope = mergeEnvelope(base.volumeEnvelope, overlay.volumeEnvelope),
            modEnvelope = mergeEnvelope(base.modEnvelope, overlay.modEnvelope),
            vibLfoDelay = overlay.vibLfoDelay ?: base.vibLfoDelay,
            vibLfoFreq = overlay.vibLfoFreq ?: base.vibLfoFreq,
            vibLfoToPitch = overlay.vibLfoToPitch ?: base.vibLfoToPitch,
            modLfoDelay = overlay.modLfoDelay ?: base.modLfoDelay,
            modLfoFreq = overlay.modLfoFreq ?: base.modLfoFreq,
            modLfoToPitch = overlay.modLfoToPitch ?: base.modLfoToPitch,
            modLfoToFilterFc = overlay.modLfoToFilterFc ?: base.modLfoToFilterFc,
            modLfoToVolume = overlay.modLfoToVolume ?: base.modLfoToVolume,
            modEnvToPitch = overlay.modEnvToPitch ?: base.modEnvToPitch,
            modEnvToFilterFc = overlay.modEnvToFilterFc ?: base.modEnvToFilterFc
        )
    }

    private fun mergeEnvelope(base: Sf2EnvelopeData, overlay: Sf2EnvelopeData): Sf2EnvelopeData {
        return Sf2EnvelopeData(
            delay = overlay.delay ?: base.delay,
            attack = overlay.attack ?: base.attack,
            hold = overlay.hold ?: base.hold,
            decay = overlay.decay ?: base.decay,
            sustain = overlay.sustain ?: base.sustain,
            release = overlay.release ?: base.release,
            keynumToHold = overlay.keynumToHold ?: base.keynumToHold,
            keynumToDecay = overlay.keynumToDecay ?: base.keynumToDecay
        )
    }

    /**
     * Start background preload monitoring (DYNAMIC mode only)
     */
    private fun startPreloadMonitoring() {
        if (strategy != InstrumentTimeline.LoadingStrategy.DYNAMIC) return

        preloadExecutor = Executors.newScheduledThreadPool(PRELOAD_THREADS)
        preloadFuture = preloadExecutor?.scheduleAtFixedRate(
            { checkAndPreload() },
            POSITION_UPDATE_INTERVAL_MS,
            POSITION_UPDATE_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        )
    }

    /**
     * Check current position and preload upcoming instruments
     */
    private fun checkAndPreload() {
        if (isReleased.get() || !isInitialized.get()) return

        val timeline = this.timeline ?: return
        val position = currentPositionMs.get()

        // Get snapshot of what's needed
        val snapshot = timeline.getSnapshot(
            position,
            InstrumentTimeline.DEFAULT_LOOKAHEAD_MS + PRELOAD_MARGIN_MS
        )

        // Find instruments that need to be loaded
        val toLoad = snapshot.allRequiredNow.filter { preset ->
            presetStates[preset] != PresetState.LOADED &&
            presetStates[preset] != PresetState.LOADING
        }

        if (toLoad.isNotEmpty()) {
            try {
                loadPresetsIncremental(toLoad)
            } catch (e: Exception) {
                // Handle error silently
            }
        }

        // Check for instruments to unload (optional, for memory savings)
        // Only unload if we're running low on memory
        val usedMemoryMB = compactSampleData.size.toLong() * 2 / 1024 / 1024
        val availableMB = getAvailableMemoryMB()
        if (availableMB < MEMORY_SAFETY_MARGIN_MB * 2) {
            checkAndUnload(position)
        }
    }

    /**
     * Check and unload unused instruments (for memory savings)
     */
    private fun checkAndUnload(positionMs: Long) {
        val timeline = this.timeline ?: return
        val analysis = this.analysisResult ?: return

        for ((preset, state) in presetStates) {
            if (state != PresetState.LOADED) continue
            if (analysis.coreInstruments.contains(preset)) continue  // Never unload core

            if (timeline.canUnloadInstrument(preset, positionMs)) {
                // Mark for unloading (actual unloading is complex, skip for now)
                // In a full implementation, we'd need to rebuild the compact array
            }
        }
    }

    /**
     * Update current playback position (called from playback engine)
     */
    fun updatePosition(positionMs: Long) {
        currentPositionMs.set(positionMs)
    }

    /**
     * Handle seek event - load required instruments if needed
     *
     * @param targetPositionMs Target seek position
     * @return true if ready to play, false if loading required (will callback when ready)
     */
    fun handleSeek(targetPositionMs: Long, onReady: () -> Unit): Boolean {
        if (strategy == InstrumentTimeline.LoadingStrategy.LOAD_ALL) {
            // All instruments already loaded
            currentPositionMs.set(targetPositionMs)
            return true
        }

        val timeline = this.timeline ?: return true

        // Check what instruments are needed at target position
        val snapshot = timeline.getSnapshot(targetPositionMs)
        val missingInstruments = snapshot.allRequiredNow.filter { preset ->
            presetStates[preset] != PresetState.LOADED
        }

        if (missingInstruments.isEmpty()) {
            // All required instruments are loaded
            currentPositionMs.set(targetPositionMs)
            return true
        }

        // Need to load missing instruments
        listener?.onLoadingStateChanged(true, 0f, "Chargement des instruments...")

        // Load in background and callback when ready
        preloadExecutor?.submit {
            try {
                val total = missingInstruments.size
                missingInstruments.forEachIndexed { index, preset ->
                    loadPresetsIncremental(listOf(preset))
                    val progress = (index + 1).toFloat() / total
                    listener?.onLoadingStateChanged(true, progress, "Chargement ${index + 1}/$total")
                }

                currentPositionMs.set(targetPositionMs)
                listener?.onLoadingStateChanged(false, 1f, "Prêt")
                onReady()

            } catch (e: Exception) {
                listener?.onError("Erreur: ${e.message}")
            }
        }

        return false
    }

    /**
     * Get the current Sf2File (may change as instruments are loaded)
     */
    fun getCurrentSf2File(): Sf2File? {
        return currentSf2File
    }

    /**
     * Get available memory in MB
     */
    private fun getAvailableMemoryMB(): Long {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        return memInfo.availMem / 1024 / 1024
    }

    /**
     * Estimate memory per preset based on SF2 metadata
     */
    private fun estimateMemoryPerPreset(): Long {
        val totalSamplesMB = metadata.smplByteSize / 1024 / 1024
        val presetCount = maxOf(1, metadata.presetCount)
        // Assume samples are roughly evenly distributed (conservative estimate)
        return maxOf(1, totalSamplesMB / presetCount)
    }

    /**
     * Get current loading strategy
     */
    fun getStrategy(): InstrumentTimeline.LoadingStrategy = strategy

    /**
     * Get preloader statistics
     */
    fun getStats(): String {
        val loadedCount = presetStates.count { it.value == PresetState.LOADED }
        val totalCount = analysisResult?.allInstruments?.size ?: 0
        val memoryMB = compactSampleData.size.toLong() * 2 / 1024 / 1024
        return "Preloader[$strategy]: $loadedCount/$totalCount presets, ${memoryMB}MB"
    }

    override fun close() {
        isReleased.set(true)
        preloadFuture?.cancel(false)
        preloadExecutor?.shutdownNow()
        preloadExecutor = null

        currentSf2File?.close()
        currentSf2File = null

        presetStates.clear()
        loadedPresets.clear()
        sampleRemapping.clear()
        currentPresetMap.clear()
        compactSampleData = ShortArray(0)
    }
}
