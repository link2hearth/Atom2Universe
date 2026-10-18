package com.Atom2Universe.app.notes.sync

import java.util.UUID

/** Le fichier des notes là-haut : ce que Drive répond quand on le lit. */
sealed class NotesCloudRead {
    data class Found(val text: String) : NotesCloudRead()
    data object Missing : NotesCloudRead()
    /** Drive n'a pas répondu : ce n'est pas un cloud vide, on n'écrit rien. */
    data object Failed : NotesCloudRead()
}

interface NotesCloud {
    suspend fun read(): NotesCloudRead
    suspend fun write(text: String): Boolean
}

interface NotesStateStore {
    fun load(): NotesSyncState
    fun save(state: NotesSyncState)
}

/**
 * Une synchronisation des notes, de bout en bout : photo d'ici, lecture du cloud, plan, écriture
 * ici, écriture là-haut, et seulement alors on note ce qu'on a échangé.
 *
 * L'ordre compte. La base de la fusion (l'état) n'est écrite qu'une fois les deux côtés à jour : si
 * l'envoi échouait après qu'on a noté « envoyé », le prochain tour prendrait le cloud, resté ancien,
 * pour une modification faite ailleurs, et écraserait ce qu'on vient d'écrire ici.
 */
class NotesSyncEngine(
    private val store: NotesSyncStore,
    private val cloud: NotesCloud,
    private val stateStore: NotesStateStore,
    private val deviceName: String,
    private val conflictSuffix: String,
    private val now: () -> Long = System::currentTimeMillis,
    private val newUuid: () -> String = { UUID.randomUUID().toString() },
) {
    sealed class Result {
        /** @property changedLocal des notes (ou des noms) ont été écrites ici ; @property uploaded le cloud a été réécrit */
        data class Done(val changedLocal: Boolean, val uploaded: Boolean) : Result()
        data object Failed : Result()
        /** Le fichier du cloud vient d'une version plus récente de l'application : on n'y touche pas. */
        data object TooNew : Result()
    }

    suspend fun sync(): Result {
        val snap = store.snapshot()
        val remote: NotesSyncFile? = when (val read = cloud.read()) {
            NotesCloudRead.Failed -> return Result.Failed
            NotesCloudRead.Missing -> null
            // Illisible : on ne sait pas ce qu'il contient, donc on ne l'écrase pas.
            is NotesCloudRead.Found -> NotesSyncFile.fromJson(read.text) ?: return Result.Failed
        }
        if (remote != null && remote.format > NotesSyncFile.FORMAT) return Result.TooNew

        val state = stateStore.load()
        val t = now()

        val notes = NotesMerge.planNotes(
            local = snap.notes, cloud = remote?.notes.orEmpty(), purged = remote?.purged.orEmpty(),
            state = state.notes, now = t, conflictSuffix = conflictSuffix, newUuid = newUuid,
        )
        fun removedOf(kind: NameKind): Map<String, Long> =
            remote?.removed.orEmpty().filterKeys { it.startsWith("${kind.letter}:") }.mapKeys { it.key.substring(2) }

        val groups = NotesMerge.planNames(
            snap.groups.associateBy { nameKey(it.name) }, remote?.groups.orEmpty().associateBy { nameKey(it.name) },
            removedOf(NameKind.GROUP), state.groups, t,
        ) { mine, theirs -> theirs.dateModified > mine.dateModified }
        // Les tags et les catégories n'ont pas de date : ce qui existe déjà ici n'est jamais remplacé.
        val tags = NotesMerge.planNames(
            snap.tags.associateBy { nameKey(it.name) }, remote?.tags.orEmpty().associateBy { nameKey(it.name) },
            removedOf(NameKind.TAG), state.tags, t,
        ) { _, _ -> false }
        val categories = NotesMerge.planNames(
            snap.categories.associateBy { nameKey(it.name) }, remote?.categories.orEmpty().associateBy { nameKey(it.name) },
            removedOf(NameKind.CATEGORY), state.categories, t,
        ) { _, _ -> false }

        val removed = LinkedHashMap<String, Long>()
        for ((k, v) in groups.removed) removed["${NameKind.GROUP.letter}:$k"] = v
        for ((k, v) in tags.removed) removed["${NameKind.TAG.letter}:$k"] = v
        for ((k, v) in categories.removed) removed["${NameKind.CATEGORY.letter}:$k"] = v

        val file = NotesSyncFile(
            format = NotesSyncFile.FORMAT, savedAt = t, deviceName = deviceName,
            notes = notes.cloudNotes, groups = groups.cloud, tags = tags.cloud, categories = categories.cloud,
            purged = notes.purged, removed = removed,
        )
        val needUpload = if (remote == null) {
            file.notes.isNotEmpty() || file.groups.isNotEmpty() || file.tags.isNotEmpty() || file.categories.isNotEmpty() || removed.isNotEmpty()
        } else {
            file.contentOnly() != remote.contentOnly()
        }

        val skipped = store.apply(ApplyJob(notes, groups, tags, categories, removed.keys))
        if (needUpload && !cloud.write(file.toJson())) return Result.Failed

        // Une note qu'on n'a pas osé écraser garde sa base d'avant : la prochaine fois, elle sera reprise.
        val hashes = HashMap(notes.hashes)
        for (id in skipped) {
            val old = state.notes[id]
            if (old != null) hashes[id] = old else hashes.remove(id)
        }
        stateStore.save(NotesSyncState(hashes, groups.synced, tags.synced, categories.synced))

        val changedLocal = notes.applyLocal.any { it.uuid !in skipped } || notes.newLocal.isNotEmpty() ||
            notes.deleteLocal.any { it !in skipped } ||
            groups.createLocal.isNotEmpty() || groups.updateLocal.isNotEmpty() || groups.deleteLocal.isNotEmpty() ||
            tags.createLocal.isNotEmpty() || tags.deleteLocal.isNotEmpty() ||
            categories.createLocal.isNotEmpty() || categories.deleteLocal.isNotEmpty()
        return Result.Done(changedLocal, needUpload)
    }
}
