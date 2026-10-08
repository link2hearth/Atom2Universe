package com.Atom2Universe.app.notes.sync

import androidx.room.withTransaction
import com.Atom2Universe.app.notes.data.Note
import com.Atom2Universe.app.notes.data.NoteGroup
import com.Atom2Universe.app.notes.data.NoteTag
import com.Atom2Universe.app.notes.data.NotesDatabase
import com.Atom2Universe.app.notes.data.Tag
import com.Atom2Universe.app.notes.data.TagCategory
import com.Atom2Universe.app.notes.editor.MarkdownSyntax
import java.util.UUID

/** Tout ce que [NotesSyncStore.apply] doit écrire ici. */
class ApplyJob(
    val notes: NotesPlan,
    val groups: NotesMerge.NamesPlan<SyncGroup>,
    val tags: NotesMerge.NamesPlan<SyncTag>,
    val categories: NotesMerge.NamesPlan<SyncCategory>,
    /** Les clés (`g:nom`…) supprimées : une note qui y est rattachée n'en recrée pas. */
    val removedKeys: Set<String>,
)

/** La base des notes vue par la synchronisation : une photo, et l'écriture d'un plan. */
class NotesSyncStore(private val db: NotesDatabase) {

    class Snapshot(
        val notes: List<SyncNote>,
        val groups: List<SyncGroup>,
        val tags: List<SyncTag>,
        val categories: List<SyncCategory>,
    )

    private val noteDao = db.noteDao()
    private val groupDao = db.noteGroupDao()
    private val tagDao = db.tagDao()
    private val categoryDao = db.tagCategoryDao()

    suspend fun snapshot(): Snapshot = db.withTransaction {
        // Une note sans identité (base d'avant la sync) en reçoit une ici : sans elle, elle ne pourrait pas voyager.
        val rows = noteDao.getAllNotesForSync().map { if (it.uuid.isBlank()) it.copy(uuid = UUID.randomUUID().toString()).also { n -> noteDao.updateNote(n) } else it }
        val groups = groupDao.getAllGroupsForBackup()
        val tags = tagDao.getAllTagsForBackup()
        val categories = categoryDao.getAllCategoriesForBackup()
        val groupName = groups.associate { it.id to it.name }
        val tagName = tags.associate { it.id to it.name }
        val tagsOf = noteDao.getAllNoteTagsForBackup().groupBy({ it.noteId }, { tagName[it.tagId] })
        val categoryName = categories.associate { it.id to it.name }
        Snapshot(
            notes = rows.map { it.toSync(it.groupId?.let(groupName::get), tagsOf[it.id].orEmpty().filterNotNull()) },
            groups = groups.map { SyncGroup(it.name, it.description, it.colorHex, it.textColorMode, it.iconName, it.position, it.dateModified) },
            tags = tags.map { SyncTag(it.name, it.colorHex, it.sortOrder, it.textColorMode, it.categoryId?.let(categoryName::get)) },
            categories = categories.map { SyncCategory(it.name, it.sortOrder) },
        )
    }

    private fun Note.toSync(groupName: String?, tags: List<String>) = SyncNote(
        uuid = uuid, title = title, content = content, groupName = groupName, tags = tags,
        isPinned = isPinned, isFavorite = isFavorite, colorHex = colorHex, textColorMode = textColorMode,
        dateCreated = dateCreated, dateModified = dateModified, deletedAt = deletedAt,
    )

    /** La note d'ici telle qu'elle est maintenant, pour vérifier qu'elle n'a pas bougé depuis la photo. */
    private suspend fun currentSync(uuid: String): SyncNote? {
        val n = noteDao.getNoteByUuid(uuid) ?: return null
        val withTags = noteDao.getNoteWithTagsById(n.id) ?: return null
        return n.toSync(withTags.group?.name, withTags.tags.map { it.name })
    }

    /**
     * Écrit le plan, en une transaction. Une note modifiée ici depuis la photo n'est pas écrasée (ni supprimée) :
     * elle est rendue dans l'ensemble retourné, et la prochaine synchronisation la reprendra.
     */
    suspend fun apply(job: ApplyJob): Set<String> = db.withTransaction {
        val now = System.currentTimeMillis()
        val skipped = HashSet<String>()

        val categoryByKey = categoryDao.getAllCategoriesForBackup().associateBy { nameKey(it.name) }.toMutableMap()
        for (c in job.categories.createLocal) {
            if (nameKey(c.name) !in categoryByKey) {
                val id = categoryDao.insertCategory(TagCategory(name = c.name, sortOrder = c.sortOrder))
                categoryByKey[nameKey(c.name)] = TagCategory(id, c.name, c.sortOrder)
            }
        }

        val groupByKey = groupDao.getAllGroupsForBackup().associateBy { nameKey(it.name) }.toMutableMap()
        for (g in job.groups.createLocal) {
            if (nameKey(g.name) !in groupByKey) {
                val made = NoteGroup(0, g.name, g.description, g.colorHex, g.textColorMode, g.iconName, g.position, now, g.dateModified)
                groupByKey[nameKey(g.name)] = made.copy(id = groupDao.insertGroup(made))
            }
        }
        for (g in job.groups.updateLocal) {
            val mine = groupByKey[nameKey(g.name)] ?: continue
            val next = mine.copy(description = g.description, colorHex = g.colorHex, textColorMode = g.textColorMode, iconName = g.iconName, position = g.position, dateModified = g.dateModified)
            groupDao.updateGroup(next)
            groupByKey[nameKey(g.name)] = next
        }

        val tagByKey = tagDao.getAllTagsForBackup().associateBy { nameKey(it.name) }.toMutableMap()
        for (t in job.tags.createLocal) {
            if (nameKey(t.name) !in tagByKey) {
                val cat = t.categoryName?.let { categoryByKey[nameKey(it)]?.id }
                val made = Tag(0, t.name, t.colorHex, t.sortOrder, t.textColorMode, cat, now)
                tagByKey[nameKey(t.name)] = made.copy(id = tagDao.insertTag(made))
            }
        }

        suspend fun groupIdOrCreate(name: String?): Long? {
            if (name.isNullOrBlank()) return null
            groupByKey[nameKey(name)]?.let { return it.id }
            if ("g:${nameKey(name)}" in job.removedKeys) return null
            val made = NoteGroup(name = name.trim(), position = groupByKey.size, dateCreated = now, dateModified = now)
            groupByKey[nameKey(name)] = made.copy(id = groupDao.insertGroup(made))
            return groupByKey.getValue(nameKey(name)).id
        }
        suspend fun tagIdOrCreate(name: String): Long? {
            val key = nameKey(name)
            tagByKey[key]?.let { return it.id }
            if ("t:$key" in job.removedKeys) return null
            val made = Tag(name = name.trim(), dateCreated = now)
            tagByKey[key] = made.copy(id = tagDao.insertTag(made))
            return tagByKey.getValue(key).id
        }

        suspend fun write(n: SyncNote, existing: Note?) {
            val note = Note(
                id = existing?.id ?: 0, uuid = n.uuid, groupId = groupIdOrCreate(n.groupName),
                title = n.title, content = n.content, contentPlainText = MarkdownSyntax.toPlainText(n.content),
                isPinned = n.isPinned, isFavorite = n.isFavorite, colorHex = n.colorHex, textColorMode = n.textColorMode,
                dateCreated = n.dateCreated, dateModified = n.dateModified, deletedAt = n.deletedAt,
            )
            val id = if (existing != null) { noteDao.updateNote(note); existing.id } else noteDao.insertNote(note)
            noteDao.deleteAllTagsForNote(id)
            for (name in n.tags) tagIdOrCreate(name)?.let { noteDao.insertNoteTag(NoteTag(id, it)) }
        }

        for (n in job.notes.applyLocal) {
            val expected = job.notes.expectedLocal[n.uuid]
            if (currentSync(n.uuid)?.hash() != expected) { skipped += n.uuid; continue }
            write(n, noteDao.getNoteByUuid(n.uuid))
        }
        for (n in job.notes.newLocal) write(n, null)
        for (uuid in job.notes.deleteLocal) {
            if (currentSync(uuid)?.hash() != job.notes.expectedLocal[uuid]) { skipped += uuid; continue }
            noteDao.deleteNoteByUuid(uuid)
        }

        // Les noms supprimés ailleurs : en dernier, pour que les notes écrites plus haut ne les ressuscitent pas.
        for (k in job.groups.deleteLocal) groupByKey[k]?.let { groupDao.deleteGroup(it) }
        for (k in job.tags.deleteLocal) tagByKey[k]?.let { tagDao.deleteTag(it) }
        for (k in job.categories.deleteLocal) categoryByKey[k]?.let { categoryDao.deleteCategory(it) }
        skipped
    }
}
