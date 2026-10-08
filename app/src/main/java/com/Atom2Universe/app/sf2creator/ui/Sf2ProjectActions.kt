package com.Atom2Universe.app.sf2creator.ui

import android.content.Context
import android.net.Uri
import com.Atom2Universe.app.R
import com.Atom2Universe.app.sf2creator.data.Sf2ProjectClipboard
import com.Atom2Universe.app.sf2creator.data.Sf2ProjectCopier
import com.Atom2Universe.app.sf2creator.data.Sf2ProjectRepository
import com.Atom2Universe.app.sf2creator.writer.Sf2Writer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Project actions shared by the gallery and the project screen. */
object Sf2ProjectActions {

    /** File name offered when saving a project as SF2. */
    fun fileName(projectName: String): String =
        projectName.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifEmpty { "SoundFont" } + ".sf2"

    /** Writes the project as an SF2 file to [uri]. */
    suspend fun exportToUri(context: Context, projectId: Long, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val temp = File(context.cacheDir, "sf2_export_$projectId.sf2")
        try {
            if (!Sf2ProjectRepository(context).exportProjectToSf2(projectId, temp) || !Sf2Writer().validateSf2(temp)) {
                return@withContext false
            }
            context.contentResolver.openOutputStream(uri)?.use { out ->
                temp.inputStream().use { it.copyTo(out) }
            } ?: return@withContext false
            true
        } catch (e: Exception) {
            false
        } finally {
            temp.delete()
        }
    }

    // ==================== Clipboard ====================

    fun copyPrograms(context: Context, projectId: Long, ids: List<Long>, names: List<String>) =
        copy(context, Sf2ProjectClipboard.Kind.PROGRAMS, projectId, ids, names, R.string.sf2_clip_program, R.string.sf2_clip_programs)

    fun copyInstruments(context: Context, projectId: Long, presetZoneIds: List<Long>, names: List<String>) =
        copy(context, Sf2ProjectClipboard.Kind.INSTRUMENTS, projectId, presetZoneIds, names, R.string.sf2_clip_instrument, R.string.sf2_clip_instruments)

    fun copySamples(context: Context, projectId: Long, ids: List<Long>, names: List<String>) =
        copy(context, Sf2ProjectClipboard.Kind.SAMPLES, projectId, ids, names, R.string.sf2_clip_sample, R.string.sf2_clip_samples)

    private fun copy(context: Context, kind: Sf2ProjectClipboard.Kind, projectId: Long, ids: List<Long>, names: List<String>, one: Int, many: Int) {
        if (ids.isEmpty()) return
        val label = if (ids.size == 1) context.getString(one, names.firstOrNull().orEmpty()) else context.getString(many, ids.size)
        Sf2ProjectClipboard.set(context, Sf2ProjectClipboard.Content(kind, projectId, ids, label))
    }

    sealed class PasteOutcome {
        /** [ids]: the new programs, preset zones or samples. */
        class Pasted(val kind: Sf2ProjectClipboard.Kind, val ids: List<Long>) : PasteOutcome()
        object NeedsProgram : PasteOutcome()
        object NeedsInstrument : PasteOutcome()
        object Gone : PasteOutcome()
        object Empty : PasteOutcome()
    }

    /**
     * Pastes the clipboard: programs into the project, instruments into [programId], samples
     * into [instrumentId].
     */
    suspend fun paste(context: Context, projectId: Long, programId: Long?, instrumentId: Long?): PasteOutcome {
        val content = Sf2ProjectClipboard.get(context) ?: return PasteOutcome.Empty
        val copier = Sf2ProjectCopier(context)
        val ids = when (content.kind) {
            Sf2ProjectClipboard.Kind.PROGRAMS -> copier.copyPrograms(content.ids, projectId)
            Sf2ProjectClipboard.Kind.INSTRUMENTS ->
                copier.copyInstruments(content.ids, programId ?: return PasteOutcome.NeedsProgram)
            Sf2ProjectClipboard.Kind.SAMPLES ->
                copier.copySamples(content.ids, instrumentId ?: return PasteOutcome.NeedsInstrument)
        }
        return if (ids.isEmpty()) PasteOutcome.Gone else PasteOutcome.Pasted(content.kind, ids)
    }

    /** Message for a paste that did not happen, or null when it did. */
    fun failureMessage(context: Context, outcome: PasteOutcome): String? = when (outcome) {
        is PasteOutcome.Pasted, PasteOutcome.Empty -> null
        PasteOutcome.NeedsProgram -> context.getString(R.string.sf2_choose_program_first)
        PasteOutcome.NeedsInstrument -> context.getString(R.string.sf2_choose_instrument_first)
        PasteOutcome.Gone -> context.getString(R.string.sf2_paste_gone)
    }
}
