package com.Atom2Universe.app.midi.sf2

import android.content.Context
import com.Atom2Universe.app.R
import kotlin.math.abs
import kotlin.math.pow

/**
 * Validates SF2 files and reports potential issues that could cause
 * audio problems or unexpected behavior during playback.
 */
object Sf2Validator {
    /**
     * Validation result containing all detected issues
     */
    data class ValidationResult(
        val isValid: Boolean,
        val issues: List<Issue>,
        val warnings: List<Warning>,
        val stats: Stats
    ) {
        val hasProblems: Boolean get() = issues.isNotEmpty() || warnings.isNotEmpty()
        val hasCriticalIssues: Boolean get() = issues.any { it.severity == Severity.CRITICAL }

        /** Report in plain words, in the language of [context]. */
        fun getDetailedReport(context: Context): String = buildString {
            val res = context.resources
            append(context.getString(R.string.midi_ui_sf2_report_contents,
                res.getQuantityString(R.plurals.midi_ui_sf2_report_presets, stats.presetCount, stats.presetCount),
                res.getQuantityString(R.plurals.midi_ui_sf2_report_samples, stats.sampleCount, stats.sampleCount)))
            append("\n\n")

            if (!hasProblems) {
                append(context.getString(R.string.midi_ui_sf2_report_fine))
                return@buildString
            }

            for (issue in issues) {
                append(issue.describe(context))
                append("\n")
                issue.sampleNames.take(MAX_LISTED).forEach { name ->
                    append(context.getString(R.string.midi_ui_sf2_report_item, name))
                    append("\n")
                }
                if (issue.sampleNames.size > MAX_LISTED) {
                    val more = issue.sampleNames.size - MAX_LISTED
                    append(res.getQuantityString(R.plurals.midi_ui_sf2_report_more, more, more))
                    append("\n")
                }
                append("\n")
            }

            if (warnings.isNotEmpty()) {
                append(context.getString(R.string.midi_ui_sf2_report_details_title))
                append("\n")
                warnings.take(MAX_LISTED).forEach { warning ->
                    append(context.getString(R.string.midi_ui_sf2_report_item, warning.describe(context)))
                    append("\n")
                }
                if (warnings.size > MAX_LISTED) {
                    val more = warnings.size - MAX_LISTED
                    append(res.getQuantityString(R.plurals.midi_ui_sf2_report_more, more, more))
                    append("\n")
                }
            }
        }
    }

    // Lignes listées au plus dans le rapport pour chaque rubrique
    private const val MAX_LISTED = 20

    enum class Severity { WARNING, ERROR, CRITICAL }

    enum class IssueKind { NO_PRESETS, NO_SAMPLES, NO_INSTRUMENTS, MANY_STRETCHED_ZONES, MANY_SUSPICIOUS_PRESETS, INVALID_SAMPLES }

    /** A problem of the whole file; [count] and [sampleNames] depend on [kind]. */
    data class Issue(
        val severity: Severity,
        val kind: IssueKind,
        val count: Int = 0,
        val sampleNames: List<String> = emptyList()
    ) {
        fun describe(context: Context): String {
            val res = context.resources
            return when (kind) {
                IssueKind.NO_PRESETS -> context.getString(R.string.midi_ui_sf2_issue_no_presets)
                IssueKind.NO_SAMPLES -> context.getString(R.string.midi_ui_sf2_issue_no_samples)
                IssueKind.NO_INSTRUMENTS -> context.getString(R.string.midi_ui_sf2_issue_no_instruments)
                IssueKind.MANY_STRETCHED_ZONES ->
                    res.getQuantityString(R.plurals.midi_ui_sf2_issue_stretched, count, count)
                IssueKind.MANY_SUSPICIOUS_PRESETS ->
                    res.getQuantityString(R.plurals.midi_ui_sf2_issue_suspicious, count, count)
                IssueKind.INVALID_SAMPLES ->
                    res.getQuantityString(R.plurals.midi_ui_sf2_issue_invalid_samples, count, count)
            }
        }
    }

    enum class WarningKind { STRETCHED_PRESET, MISMATCHED_INSTRUMENT }

    /** A remark about one preset: [count] stretched notes, or the [instrumentName] it uses. */
    data class Warning(
        val kind: WarningKind,
        val presetName: String,
        val count: Int = 0,
        val instrumentName: String = ""
    ) {
        fun describe(context: Context): String = when (kind) {
            WarningKind.STRETCHED_PRESET ->
                context.resources.getQuantityString(R.plurals.midi_ui_sf2_warning_stretched, count, presetName, count)
            WarningKind.MISMATCHED_INSTRUMENT ->
                context.getString(R.string.midi_ui_sf2_warning_mismatch, presetName, instrumentName)
        }
    }

    data class Stats(
        val presetCount: Int,
        val instrumentCount: Int,
        val sampleCount: Int,
        val problematicZoneCount: Int,
        val suspiciousPresetCount: Int,
        val totalZoneCount: Int
    )

    /**
     * Validate an SF2 file's metadata without loading sample data.
     * This is fast and can be run during initial file selection.
     */
    fun validate(metadata: Sf2Metadata): ValidationResult {
        val issues = mutableListOf<Issue>()
        val warnings = mutableListOf<Warning>()
        var problematicZoneCount = 0
        var suspiciousPresetCount = 0
        var totalZoneCount = 0

        // Check basic structure
        if (metadata.presetCount == 0) {
            issues.add(Issue(Severity.CRITICAL, IssueKind.NO_PRESETS))
        }

        if (metadata.sampleHeaders.isEmpty()) {
            issues.add(Issue(Severity.CRITICAL, IssueKind.NO_SAMPLES))
        }

        if (metadata.instrumentHeaders.isEmpty()) {
            issues.add(Issue(Severity.CRITICAL, IssueKind.NO_INSTRUMENTS))
        }

        // Check each preset
        for (preset in metadata.presetHeaders) {
            val presetIssues = validatePreset(preset, metadata)

            if (presetIssues.problematicZones > 0) {
                problematicZoneCount += presetIssues.problematicZones

                if (presetIssues.problematicZones > 5) {
                    warnings.add(Warning(WarningKind.STRETCHED_PRESET, preset.name, count = presetIssues.problematicZones))
                }
            }

            if (presetIssues.isSuspicious) {
                suspiciousPresetCount++
            }

            totalZoneCount += presetIssues.totalZones

            // Check for unusual instrument names in standard presets
            presetIssues.suspiciousInstruments.forEach { (instName, presetName) ->
                warnings.add(Warning(WarningKind.MISMATCHED_INSTRUMENT, presetName, instrumentName = instName))
            }
        }

        // Check sample headers for issues
        val sampleIssues = validateSamples(metadata.sampleHeaders)
        issues.addAll(sampleIssues)

        // Summary warnings
        if (problematicZoneCount > 50) {
            issues.add(Issue(Severity.WARNING, IssueKind.MANY_STRETCHED_ZONES, count = problematicZoneCount))
        }

        if (suspiciousPresetCount > 10) {
            issues.add(Issue(Severity.WARNING, IssueKind.MANY_SUSPICIOUS_PRESETS, count = suspiciousPresetCount))
        }

        val stats = Stats(
            presetCount = metadata.presetCount,
            instrumentCount = metadata.instrumentHeaders.size,
            // Sans l'en-tête terminal (EOS)
            sampleCount = (metadata.sampleHeaders.size - 1).coerceAtLeast(0),
            problematicZoneCount = problematicZoneCount,
            suspiciousPresetCount = suspiciousPresetCount,
            totalZoneCount = totalZoneCount
        )

        val isValid = !issues.any { it.severity == Severity.CRITICAL }

        return ValidationResult(isValid, issues, warnings, stats)
    }

    private data class PresetValidationResult(
        val problematicZones: Int,
        val totalZones: Int,
        val isSuspicious: Boolean,
        val suspiciousInstruments: List<Pair<String, String>>
    )

    private fun validatePreset(preset: Sf2PresetHeader, metadata: Sf2Metadata): PresetValidationResult {
        var problematicZones = 0
        var totalZones = 0
        val suspiciousInstruments = mutableListOf<Pair<String, String>>()

        // Get zones for this preset
        val presetZones = getPresetZones(preset, metadata)

        for (pZone in presetZones) {
            val instrumentIndex = pZone.instrumentIndex ?: continue
            val instrument = metadata.instrumentHeaders.getOrNull(instrumentIndex) ?: continue

            // Check if instrument name seems unrelated to preset
            if (isInstrumentNameSuspicious(preset.name, instrument.name)) {
                suspiciousInstruments.add(instrument.name to preset.name)
            }

            // Get instrument zones
            val instZones = getInstrumentZones(instrument, metadata)
            totalZones += instZones.size

            for (iZone in instZones) {
                val sampleId = iZone.sampleId ?: continue
                val sample = metadata.sampleHeaders.getOrNull(sampleId) ?: continue

                // Check for extreme pitch offset (only truly extreme cases)
                // Many SF2 files intentionally stretch samples - this is normal
                val rootKey = iZone.rootKey ?: sample.originalPitch
                val keyRangeCenter = (iZone.keyRange.first + iZone.keyRange.last) / 2
                val semitoneOffset = abs(keyRangeCenter - rootKey)

                if (semitoneOffset > 36) { // More than 3 octaves
                    val rate = 2.0.pow((keyRangeCenter - rootKey) / 12.0)
                    if (rate < 0.05 || rate > 20.0) {
                        problematicZones++
                    }
                }
            }
        }

        val isSuspicious = suspiciousInstruments.isNotEmpty() ||
                          (problematicZones > totalZones / 4 && problematicZones > 3)

        return PresetValidationResult(problematicZones, totalZones, isSuspicious, suspiciousInstruments)
    }

    private fun getPresetZones(preset: Sf2PresetHeader, metadata: Sf2Metadata): List<Sf2ZoneData> {
        // Find zones for this preset based on bag indices
        val startBag = preset.bagIndex
        val nextPresetIndex = metadata.presetHeaders.indexOf(preset) + 1
        val endBag = if (nextPresetIndex < metadata.presetHeaders.size) {
            metadata.presetHeaders[nextPresetIndex].bagIndex
        } else {
            metadata.presetBags.size
        }

        val zones = mutableListOf<Sf2ZoneData>()
        for (bagIndex in startBag until endBag) {
            val bag = metadata.presetBags.getOrNull(bagIndex) ?: continue
            val zone = parsePresetZoneGenerators(bag.generatorIndex, metadata.presetGenerators)
            zones.add(zone)
        }
        return zones
    }

    private fun getInstrumentZones(instrument: Sf2InstrumentHeader, metadata: Sf2Metadata): List<Sf2ZoneData> {
        val startBag = instrument.bagIndex
        val nextInstIndex = metadata.instrumentHeaders.indexOf(instrument) + 1
        val endBag = if (nextInstIndex < metadata.instrumentHeaders.size) {
            metadata.instrumentHeaders[nextInstIndex].bagIndex
        } else {
            metadata.instrumentBags.size
        }

        val zones = mutableListOf<Sf2ZoneData>()
        for (bagIndex in startBag until endBag) {
            val bag = metadata.instrumentBags.getOrNull(bagIndex) ?: continue
            val zone = parseInstrumentZoneGenerators(bag.generatorIndex, metadata.instrumentGenerators)
            zones.add(zone)
        }
        return zones
    }

    private fun parsePresetZoneGenerators(
        startGenIndex: Int,
        generators: List<Sf2GeneratorEntry>
    ): Sf2ZoneData {
        var keyRange = 0..127
        var rootKey: Int? = null
        var instrumentIndex: Int? = null
        val endGen = startGenIndex + 20 // Approximate

        for (genIndex in startGenIndex until minOf(endGen, generators.size)) {
            val gen = generators.getOrNull(genIndex) ?: break

            // Stop if we hit a terminal generator
            if (gen.operator == Sf2Generator.INSTRUMENT.id) {
                instrumentIndex = gen.amount and 0xFFFF
                break
            }

            when (gen.operator) {
                Sf2Generator.KEY_RANGE.id -> {
                    val lo = gen.amount and 0xFF
                    val hi = (gen.amount shr 8) and 0xFF
                    keyRange = lo..hi
                }
                Sf2Generator.OVERRIDING_ROOT_KEY.id -> {
                    if (gen.amount in 0..127) {
                        rootKey = gen.amount
                    }
                }
            }
        }

        return Sf2ZoneData(
            keyRange = keyRange,
            rootKey = rootKey,
            instrumentIndex = instrumentIndex
        )
    }

    private fun parseInstrumentZoneGenerators(
        startGenIndex: Int,
        generators: List<Sf2GeneratorEntry>
    ): Sf2ZoneData {
        var keyRange = 0..127
        var rootKey: Int? = null
        var sampleId: Int? = null
        val endGen = startGenIndex + 20 // Approximate

        for (genIndex in startGenIndex until minOf(endGen, generators.size)) {
            val gen = generators.getOrNull(genIndex) ?: break

            // Stop if we hit a terminal generator
            if (gen.operator == Sf2Generator.SAMPLE_ID.id) {
                sampleId = gen.amount and 0xFFFF
                break
            }

            when (gen.operator) {
                Sf2Generator.KEY_RANGE.id -> {
                    val lo = gen.amount and 0xFF
                    val hi = (gen.amount shr 8) and 0xFF
                    keyRange = lo..hi
                }
                Sf2Generator.OVERRIDING_ROOT_KEY.id -> {
                    if (gen.amount in 0..127) {
                        rootKey = gen.amount
                    }
                }
            }
        }

        return Sf2ZoneData(
            keyRange = keyRange,
            rootKey = rootKey,
            sampleId = sampleId
        )
    }

    private fun isInstrumentNameSuspicious(presetName: String, instrumentName: String): Boolean {
        val presetLower = presetName.lowercase()
        val instLower = instrumentName.lowercase()

        // Known suspicious patterns from Musyng Kite
        if (instLower == "foobar" || instLower.contains("test") || instLower.contains("debug")) {
            return true
        }

        // Check for obvious mismatches
        val presetKeywords = extractKeywords(presetLower)
        val instKeywords = extractKeywords(instLower)

        // If preset is "Reed Organ" but instrument is "Piano" or similar
        val mismatchPairs = listOf(
            setOf("organ") to setOf("piano", "guitar", "bass", "drum"),
            setOf("piano") to setOf("organ", "guitar", "brass", "string"),
            setOf("guitar") to setOf("piano", "organ", "brass", "flute"),
            setOf("brass") to setOf("piano", "guitar", "string"),
            setOf("string") to setOf("brass", "drum", "percussion")
        )

        for ((presetCategory, instCategories) in mismatchPairs) {
            if (presetKeywords.any { it in presetCategory }) {
                if (instKeywords.any { it in instCategories }) {
                    return true
                }
            }
        }

        return false
    }

    private fun extractKeywords(name: String): Set<String> {
        return name.split(Regex("[^a-z0-9]+"))
            .filter { it.length > 2 }
            .toSet()
    }

    private fun validateSamples(samples: List<Sf2SampleHeader>): List<Issue> {
        val invalidSampleNames = mutableListOf<String>()

        for (sample in samples) {
            // Skip terminal/placeholder samples (EOS = End Of Samples)
            // These are normal SF2 structure markers, not real samples
            if (sample.name.equals("EOS", ignoreCase = true) ||
                sample.name.isBlank() ||
                (sample.start == 0L && sample.end == 0L && sample.sampleRate == 0)) {
                continue
            }

            val invalid =
                // Invalid sample boundaries
                sample.end <= sample.start ||
                // Invalid loop points (only if loop is enabled, i.e., points are non-zero)
                ((sample.startLoop > 0 || sample.endLoop > 0) && sample.endLoop < sample.startLoop) ||
                // Invalid sample rate
                sample.sampleRate <= 0 || sample.sampleRate > 192000 ||
                // Invalid original pitch (255 is the special "unpitched" value for drums)
                (sample.originalPitch > 127 && sample.originalPitch != 255)

            if (invalid) invalidSampleNames.add(sample.name)
        }

        if (invalidSampleNames.isEmpty()) return emptyList()
        return listOf(Issue(Severity.WARNING, IssueKind.INVALID_SAMPLES, invalidSampleNames.size, invalidSampleNames))
    }

    /**
     * User-friendly message (in the language of [context]) when the validation result says the file
     * is likely to have problems; null when it looks fine.
     */
    fun warningFor(context: Context, result: ValidationResult): String? = when {
        result.hasCriticalIssues -> context.getString(R.string.midi_sf2_val_critical)
        result.stats.problematicZoneCount > 100 ->
            context.getString(R.string.midi_sf2_val_zones, result.stats.problematicZoneCount)
        result.stats.suspiciousPresetCount > 20 ->
            context.getString(R.string.midi_sf2_val_presets, result.stats.suspiciousPresetCount)
        result.warnings.size > 50 -> context.getString(R.string.midi_sf2_val_minor)
        else -> null
    }
}
