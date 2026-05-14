package com.Atom2Universe.app.midi.sf2

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

        fun getSummary(): String = buildString {
            append("SF2 Validation: ")
            if (!hasProblems) {
                append("OK")
            } else {
                append("${issues.size} issues, ${warnings.size} warnings")
            }
            append("\n")
            append("Stats: ${stats.presetCount} presets, ${stats.instrumentCount} instruments, ${stats.sampleCount} samples")
            if (stats.problematicZoneCount > 0) {
                append("\n${stats.problematicZoneCount} zones with extreme pitch offsets")
            }
            if (stats.suspiciousPresetCount > 0) {
                append("\n${stats.suspiciousPresetCount} presets with unusual configurations")
            }
        }

        fun getDetailedReport(): String = buildString {
            append("=== SF2 Validation Report ===\n\n")
            append("Statistics:\n")
            append("  Presets: ${stats.presetCount}\n")
            append("  Instruments: ${stats.instrumentCount}\n")
            append("  Samples: ${stats.sampleCount}\n")
            append("  Problematic zones: ${stats.problematicZoneCount}\n")
            append("  Suspicious presets: ${stats.suspiciousPresetCount}\n")
            append("\n")

            if (issues.isNotEmpty()) {
                append("Issues (${issues.size}):\n")
                issues.forEach { issue ->
                    append("  [${issue.severity}] ${issue.message}\n")
                    issue.details?.let { append("    $it\n") }
                }
                append("\n")
            }

            if (warnings.isNotEmpty()) {
                append("Warnings (${warnings.size}):\n")
                warnings.take(20).forEach { warning ->
                    append("  ${warning.message}\n")
                }
                if (warnings.size > 20) {
                    append("  ... and ${warnings.size - 20} more warnings\n")
                }
            }

            if (!hasProblems) {
                append("No issues detected. SF2 file appears valid.\n")
            }
        }
    }

    enum class Severity { WARNING, ERROR, CRITICAL }

    data class Issue(
        val severity: Severity,
        val message: String,
        val details: String? = null
    )

    data class Warning(
        val message: String,
        val presetInfo: String? = null
    )

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
            issues.add(Issue(Severity.CRITICAL, "SF2 file has no presets"))
        }

        if (metadata.sampleHeaders.isEmpty()) {
            issues.add(Issue(Severity.CRITICAL, "SF2 file has no samples"))
        }

        if (metadata.instrumentHeaders.isEmpty()) {
            issues.add(Issue(Severity.CRITICAL, "SF2 file has no instruments"))
        }

        // Check each preset
        for (preset in metadata.presetHeaders) {
            val presetIssues = validatePreset(preset, metadata)

            if (presetIssues.problematicZones > 0) {
                problematicZoneCount += presetIssues.problematicZones

                if (presetIssues.problematicZones > 5) {
                    warnings.add(Warning(
                        "Preset '${preset.name}' (${preset.bank}:${preset.preset}) has ${presetIssues.problematicZones} zones with extreme pitch offsets",
                        "${preset.bank}:${preset.preset}"
                    ))
                }
            }

            if (presetIssues.isSuspicious) {
                suspiciousPresetCount++
            }

            totalZoneCount += presetIssues.totalZones

            // Check for unusual instrument names in standard presets
            presetIssues.suspiciousInstruments.forEach { (instName, presetName) ->
                warnings.add(Warning(
                    "Preset '$presetName' uses instrument '$instName' which may not match expected sound",
                    presetName
                ))
            }
        }

        // Check sample headers for issues
        val sampleIssues = validateSamples(metadata.sampleHeaders)
        issues.addAll(sampleIssues)

        // Summary warnings
        if (problematicZoneCount > 50) {
            issues.add(Issue(
                Severity.WARNING,
                "SF2 has many zones with extreme pitch configurations ($problematicZoneCount zones)",
                "This may cause incorrect playback for some instruments. The SF2 file may be corrupted or have unusual design."
            ))
        }

        if (suspiciousPresetCount > 10) {
            issues.add(Issue(
                Severity.WARNING,
                "$suspiciousPresetCount presets have suspicious configurations",
                "Some instruments may not sound as expected."
            ))
        }

        val stats = Stats(
            presetCount = metadata.presetCount,
            instrumentCount = metadata.instrumentHeaders.size,
            sampleCount = metadata.sampleHeaders.size,
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
        val issues = mutableListOf<Issue>()
        val invalidSampleDetails = mutableListOf<String>()

        for ((index, sample) in samples.withIndex()) {
            // Skip terminal/placeholder samples (EOS = End Of Samples)
            // These are normal SF2 structure markers, not real samples
            if (sample.name.equals("EOS", ignoreCase = true) ||
                sample.name.isBlank() ||
                (sample.start == 0L && sample.end == 0L && sample.sampleRate == 0)) {
                continue
            }

            val sampleProblems = mutableListOf<String>()

            // Check for invalid sample boundaries
            if (sample.end <= sample.start) {
                sampleProblems.add("invalid boundaries (end=${sample.end} <= start=${sample.start})")
            }

            // Check for invalid loop points (only if loop is enabled, i.e., points are non-zero)
            if (sample.startLoop > 0 || sample.endLoop > 0) {
                if (sample.endLoop < sample.startLoop) {
                    sampleProblems.add("invalid loop (endLoop=${sample.endLoop} < startLoop=${sample.startLoop})")
                }
            }

            // Check for invalid sample rate
            if (sample.sampleRate <= 0 || sample.sampleRate > 192000) {
                sampleProblems.add("invalid sample rate (${sample.sampleRate} Hz)")
            }

            // Check for invalid original pitch
            if (sample.originalPitch > 127 && sample.originalPitch != 255) {
                // 255 is special "unpitched" value for drums
                sampleProblems.add("invalid pitch (${sample.originalPitch})")
            }

            if (sampleProblems.isNotEmpty()) {
                invalidSampleDetails.add("'${sample.name}' [#$index]: ${sampleProblems.joinToString(", ")}")
            }
        }

        if (invalidSampleDetails.isNotEmpty()) {
            issues.add(Issue(
                Severity.WARNING,
                "${invalidSampleDetails.size} samples have invalid parameters:",
                invalidSampleDetails.joinToString("\n")
            ))
        }

        return issues
    }

    /**
     * Quick check to determine if an SF2 file might have problems.
     * Returns a user-friendly message if issues are likely.
     */
    fun quickCheck(metadata: Sf2Metadata): String? {
        val result = validate(metadata)

        return when {
            result.hasCriticalIssues -> {
                "Ce fichier SF2 semble corrompu ou invalide et pourrait ne pas fonctionner correctement."
            }
            result.stats.problematicZoneCount > 100 -> {
                "Ce fichier SF2 contient de nombreuses configurations inhabituelles (${result.stats.problematicZoneCount} zones problematiques). " +
                "Certains instruments pourraient sonner differemment de ce qui est attendu."
            }
            result.stats.suspiciousPresetCount > 20 -> {
                "Ce fichier SF2 a ${result.stats.suspiciousPresetCount} presets avec des configurations suspectes. " +
                "La qualite audio pourrait etre affectee."
            }
            result.warnings.size > 50 -> {
                "Ce fichier SF2 a quelques configurations inhabituelles mais devrait fonctionner."
            }
            else -> null
        }
    }
}
