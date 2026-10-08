package com.Atom2Universe.app.sf2creator.data

import android.util.Log
import com.Atom2Universe.app.sf2creator.data.db.Sf2PatchDao
import com.Atom2Universe.app.sf2creator.data.db.entities.Sf2PatchEntity
import com.Atom2Universe.app.sf2creator.reader.Sf2ParsedInstrument
import com.Atom2Universe.app.sf2creator.reader.Sf2ParsedPreset
import com.Atom2Universe.app.sf2creator.reader.Sf2ParsedSample
import com.Atom2Universe.app.sf2creator.reader.Sf2ParsedZone
import org.json.JSONObject

/**
 * Service for managing SF2 patches (modifications to the original SF2 file).
 *
 * In the "Polyphone-like" architecture:
 * - The SF2 file is the source of truth
 * - We store only modifications (patches/deltas) in the database
 * - This service creates patches when the user modifies something
 * - This service applies patches when reading data for display/export
 *
 * Patch types:
 * - MODIFY_GEN: Change a generator value
 * - MODIFY_NAME: Rename an element
 * - DELETE: Mark an element as deleted
 * - ADD: Add a new element (stores full data in patchData)
 */
class Sf2PatchService(private val patchDao: Sf2PatchDao) {

    companion object {
        private const val TAG = "Sf2PatchService"

        // JSON keys
        private const val KEY_GEN_ID = "genId"
        private const val KEY_VALUE = "value"
        private const val KEY_OLD_VALUE = "oldValue"
        private const val KEY_NAME = "name"
        private const val KEY_OLD_NAME = "oldName"

        // For ADD patches
        private const val KEY_SAMPLE_NAME = "sampleName"
        private const val KEY_SAMPLE_RATE = "sampleRate"
        private const val KEY_ROOT_NOTE = "rootNote"
        private const val KEY_KEY_RANGE_START = "keyRangeStart"
        private const val KEY_KEY_RANGE_END = "keyRangeEnd"
        private const val KEY_LOOP_START = "loopStart"
        private const val KEY_LOOP_END = "loopEnd"
        private const val KEY_HAS_LOOP = "hasLoop"
        private const val KEY_AUDIO_FILE_PATH = "audioFilePath"
        private const val KEY_GENERATORS = "generators"
    }

    // ==================== Create Patches ====================

    /**
     * Create a patch when a generator value is modified.
     *
     * @param projectId Project ID
     * @param targetType Type of element (PRESET, INSTRUMENT, SAMPLE)
     * @param targetIndex Index in the original SF2 file
     * @param generatorId SF2 generator ID
     * @param newValue New generator value
     * @param oldValue Original value (for undo)
     */
    suspend fun createGeneratorPatch(
        projectId: Long,
        targetType: String,
        targetIndex: Int,
        generatorId: Int,
        newValue: Int,
        oldValue: Int? = null
    ): Long {
        val patchData = JSONObject().apply {
            put(KEY_GEN_ID, generatorId)
            put(KEY_VALUE, newValue)
            if (oldValue != null) {
                put(KEY_OLD_VALUE, oldValue)
            }
        }.toString()

        val patch = Sf2PatchEntity(
            projectId = projectId,
            targetType = targetType,
            targetIndex = targetIndex,
            patchType = Sf2PatchEntity.PATCH_MODIFY_GEN,
            patchData = patchData
        )

        Log.d(TAG, "Creating generator patch: $targetType[$targetIndex] gen=$generatorId value=$newValue")
        return patchDao.insertPatch(patch)
    }

    /**
     * Create a patch when an element is renamed.
     */
    suspend fun createNamePatch(
        projectId: Long,
        targetType: String,
        targetIndex: Int,
        newName: String,
        oldName: String? = null
    ): Long {
        val patchData = JSONObject().apply {
            put(KEY_NAME, newName)
            if (oldName != null) {
                put(KEY_OLD_NAME, oldName)
            }
        }.toString()

        val patch = Sf2PatchEntity(
            projectId = projectId,
            targetType = targetType,
            targetIndex = targetIndex,
            patchType = Sf2PatchEntity.PATCH_MODIFY_NAME,
            patchData = patchData
        )

        Log.d(TAG, "Creating name patch: $targetType[$targetIndex] name=$newName")
        return patchDao.insertPatch(patch)
    }

    /**
     * Create a patch when an element is deleted.
     */
    suspend fun createDeletePatch(
        projectId: Long,
        targetType: String,
        targetIndex: Int
    ): Long {
        val patch = Sf2PatchEntity(
            projectId = projectId,
            targetType = targetType,
            targetIndex = targetIndex,
            patchType = Sf2PatchEntity.PATCH_DELETE,
            patchData = "{}"
        )

        Log.d(TAG, "Creating delete patch: $targetType[$targetIndex]")
        return patchDao.insertPatch(patch)
    }

    /**
     * Create a patch when a new sample is added.
     *
     * @param projectId Project ID
     * @param sampleName Name of the new sample
     * @param audioFilePath Path to the audio file (copied locally)
     * @param sampleRate Sample rate in Hz
     * @param rootNote Root note (MIDI note number)
     * @param keyRangeStart Key range start (MIDI note)
     * @param keyRangeEnd Key range end (MIDI note)
     * @param loopStart Loop start point (in samples)
     * @param loopEnd Loop end point (in samples)
     * @param hasLoop Whether the sample has a loop
     * @param generators Map of generator IDs to values
     */
    suspend fun createAddSamplePatch(
        projectId: Long,
        sampleName: String,
        audioFilePath: String,
        sampleRate: Int,
        rootNote: Int,
        keyRangeStart: Int,
        keyRangeEnd: Int,
        loopStart: Int = 0,
        loopEnd: Int = 0,
        hasLoop: Boolean = false,
        generators: Map<Int, Int> = emptyMap()
    ): Long {
        val patchData = JSONObject().apply {
            put(KEY_SAMPLE_NAME, sampleName)
            put(KEY_AUDIO_FILE_PATH, audioFilePath)
            put(KEY_SAMPLE_RATE, sampleRate)
            put(KEY_ROOT_NOTE, rootNote)
            put(KEY_KEY_RANGE_START, keyRangeStart)
            put(KEY_KEY_RANGE_END, keyRangeEnd)
            put(KEY_LOOP_START, loopStart)
            put(KEY_LOOP_END, loopEnd)
            put(KEY_HAS_LOOP, hasLoop)
            if (generators.isNotEmpty()) {
                val genJson = JSONObject()
                generators.forEach { (id, value) ->
                    genJson.put(id.toString(), value)
                }
                put(KEY_GENERATORS, genJson)
            }
        }.toString()

        // For ADD patches, targetIndex = -1 (not in original file)
        val patch = Sf2PatchEntity(
            projectId = projectId,
            targetType = Sf2PatchEntity.TARGET_SAMPLE,
            targetIndex = -1,
            patchType = Sf2PatchEntity.PATCH_ADD,
            patchData = patchData
        )

        Log.d(TAG, "Creating add sample patch: $sampleName")
        return patchDao.insertPatch(patch)
    }

    // ==================== Apply Patches ====================

    /**
     * Apply patches to a preset read from the SF2 file.
     * Returns the modified preset data.
     */
    suspend fun applyPresetPatches(
        projectId: Long,
        preset: Sf2ParsedPreset,
        patches: List<Sf2PatchEntity>? = null
    ): PatchedPreset {
        val relevantPatches = patches ?: patchDao.getPatchesForTarget(
            projectId,
            Sf2PatchEntity.TARGET_PRESET,
            preset.index
        )

        var name = preset.name
        val modifiedGenerators = preset.globalGenerators.toMutableMap()
        var isDeleted = false

        for (patch in relevantPatches) {
            when (patch.patchType) {
                Sf2PatchEntity.PATCH_MODIFY_NAME -> {
                    val data = JSONObject(patch.patchData)
                    name = data.optString(KEY_NAME, name)
                }
                Sf2PatchEntity.PATCH_MODIFY_GEN -> {
                    val data = JSONObject(patch.patchData)
                    val genId = data.getInt(KEY_GEN_ID)
                    val value = data.getInt(KEY_VALUE)
                    modifiedGenerators[genId] = value
                }
                Sf2PatchEntity.PATCH_DELETE -> {
                    isDeleted = true
                }
            }
        }

        return PatchedPreset(
            originalPreset = preset,
            name = name,
            modifiedGenerators = modifiedGenerators,
            isDeleted = isDeleted
        )
    }

    /**
     * Apply patches to an instrument read from the SF2 file.
     */
    suspend fun applyInstrumentPatches(
        projectId: Long,
        instrument: Sf2ParsedInstrument,
        patches: List<Sf2PatchEntity>? = null
    ): PatchedInstrument {
        val relevantPatches = patches ?: patchDao.getPatchesForTarget(
            projectId,
            Sf2PatchEntity.TARGET_INSTRUMENT,
            instrument.index
        )

        var name = instrument.name
        val modifiedGenerators = instrument.globalGenerators.toMutableMap()
        var isDeleted = false

        for (patch in relevantPatches) {
            when (patch.patchType) {
                Sf2PatchEntity.PATCH_MODIFY_NAME -> {
                    val data = JSONObject(patch.patchData)
                    name = data.optString(KEY_NAME, name)
                }
                Sf2PatchEntity.PATCH_MODIFY_GEN -> {
                    val data = JSONObject(patch.patchData)
                    val genId = data.getInt(KEY_GEN_ID)
                    val value = data.getInt(KEY_VALUE)
                    modifiedGenerators[genId] = value
                }
                Sf2PatchEntity.PATCH_DELETE -> {
                    isDeleted = true
                }
            }
        }

        return PatchedInstrument(
            originalInstrument = instrument,
            name = name,
            modifiedGenerators = modifiedGenerators,
            isDeleted = isDeleted
        )
    }

    /**
     * Apply patches to a sample zone read from the SF2 file.
     */
    suspend fun applySamplePatches(
        projectId: Long,
        sample: Sf2ParsedSample,
        zone: Sf2ParsedZone,
        patches: List<Sf2PatchEntity>? = null
    ): PatchedSample {
        val relevantPatches = patches ?: patchDao.getPatchesForTarget(
            projectId,
            Sf2PatchEntity.TARGET_SAMPLE,
            sample.index
        )

        var name = sample.name
        val modifiedGenerators = zone.generators.toMutableMap()
        var isDeleted = false

        for (patch in relevantPatches) {
            when (patch.patchType) {
                Sf2PatchEntity.PATCH_MODIFY_NAME -> {
                    val data = JSONObject(patch.patchData)
                    name = data.optString(KEY_NAME, name)
                }
                Sf2PatchEntity.PATCH_MODIFY_GEN -> {
                    val data = JSONObject(patch.patchData)
                    val genId = data.getInt(KEY_GEN_ID)
                    val value = data.getInt(KEY_VALUE)
                    modifiedGenerators[genId] = value
                }
                Sf2PatchEntity.PATCH_DELETE -> {
                    isDeleted = true
                }
            }
        }

        return PatchedSample(
            originalSample = sample,
            originalZone = zone,
            name = name,
            modifiedGenerators = modifiedGenerators,
            isDeleted = isDeleted
        )
    }

    /**
     * Get all added samples for a project.
     * These are samples that were added by the user (not in the original SF2).
     */
    suspend fun getAddedSamples(projectId: Long): List<AddedSample> {
        val addPatches = patchDao.getAddedSamples(projectId)
        return addPatches.map { patch ->
            val data = JSONObject(patch.patchData)
            AddedSample(
                patchId = patch.id,
                name = data.getString(KEY_SAMPLE_NAME),
                audioFilePath = data.getString(KEY_AUDIO_FILE_PATH),
                sampleRate = data.getInt(KEY_SAMPLE_RATE),
                rootNote = data.getInt(KEY_ROOT_NOTE),
                keyRangeStart = data.getInt(KEY_KEY_RANGE_START),
                keyRangeEnd = data.getInt(KEY_KEY_RANGE_END),
                loopStart = data.optInt(KEY_LOOP_START, 0),
                loopEnd = data.optInt(KEY_LOOP_END, 0),
                hasLoop = data.optBoolean(KEY_HAS_LOOP, false),
                generators = parseGenerators(data.optJSONObject(KEY_GENERATORS))
            )
        }
    }

    private fun parseGenerators(json: JSONObject?): Map<Int, Int> {
        if (json == null) return emptyMap()
        val result = mutableMapOf<Int, Int>()
        json.keys().forEach { key ->
            result[key.toInt()] = json.getInt(key)
        }
        return result
    }

    // ==================== Undo Operations ====================

    /**
     * Undo the last patch for a specific target.
     */
    suspend fun undoLastPatch(
        projectId: Long,
        targetType: String,
        targetIndex: Int
    ) {
        patchDao.deleteLastPatchForTarget(projectId, targetType, targetIndex)
    }

    /**
     * Clear all patches for a target (revert to original).
     */
    suspend fun revertToOriginal(
        projectId: Long,
        targetType: String,
        targetIndex: Int
    ) {
        patchDao.deletePatchesForTarget(projectId, targetType, targetIndex)
    }

    // ==================== Query Operations ====================

    /**
     * Check if a project has any patches.
     */
    suspend fun hasPatches(projectId: Long): Boolean {
        return patchDao.hasPatches(projectId)
    }

    /**
     * Check if a project has structural changes (additions or deletions).
     */
    suspend fun hasStructuralChanges(projectId: Long): Boolean {
        return patchDao.hasStructuralChanges(projectId)
    }

    /**
     * Get all patches for a project.
     */
    suspend fun getAllPatches(projectId: Long): List<Sf2PatchEntity> {
        return patchDao.getPatchesForProject(projectId)
    }
}

// ==================== Patched Data Classes ====================

/**
 * Preset with patches applied.
 */
data class PatchedPreset(
    val originalPreset: Sf2ParsedPreset,
    val name: String,
    val modifiedGenerators: Map<Int, Int>,
    val isDeleted: Boolean
) {
    val index: Int get() = originalPreset.index
    val programNumber: Int get() = originalPreset.programNumber
    val bankNumber: Int get() = originalPreset.bankNumber
    val zones get() = originalPreset.zones
}

/**
 * Instrument with patches applied.
 */
data class PatchedInstrument(
    val originalInstrument: Sf2ParsedInstrument,
    val name: String,
    val modifiedGenerators: Map<Int, Int>,
    val isDeleted: Boolean
) {
    val index: Int get() = originalInstrument.index
    val zones get() = originalInstrument.zones
}

/**
 * Sample with patches applied.
 */
data class PatchedSample(
    val originalSample: Sf2ParsedSample,
    val originalZone: Sf2ParsedZone,
    val name: String,
    val modifiedGenerators: Map<Int, Int>,
    val isDeleted: Boolean
) {
    val index: Int get() = originalSample.index
    val sampleRate: Int get() = originalSample.sampleRate
    val rootNote: Int get() = originalSample.originalPitch
    val keyRangeLow: Int get() = originalZone.keyRangeLow
    val keyRangeHigh: Int get() = originalZone.keyRangeHigh
}

/**
 * Sample added by the user (not in original SF2).
 */
data class AddedSample(
    val patchId: Long,
    val name: String,
    val audioFilePath: String,
    val sampleRate: Int,
    val rootNote: Int,
    val keyRangeStart: Int,
    val keyRangeEnd: Int,
    val loopStart: Int,
    val loopEnd: Int,
    val hasLoop: Boolean,
    val generators: Map<Int, Int>
)
