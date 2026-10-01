package com.Atom2Universe.app.notes.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.Atom2Universe.app.notes.data.*
import com.Atom2Universe.app.notes.repository.NotesRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class NotesViewModel(private val repository: NotesRepository) : ViewModel() {

    val allNotesWithTags: StateFlow<List<NoteWithTags>> = repository.getAllNotesWithTags()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val trashedNotes: StateFlow<List<NoteWithTags>> = repository.getTrashedNotesWithTags()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val allGroupsWithCount: StateFlow<List<GroupWithCount>> = repository.getAllGroupsWithCount()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val allTagsWithCount: StateFlow<List<TagWithCount>> = repository.getAllTagsWithCount()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val allCategories: StateFlow<List<TagCategory>> = repository.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /**
     * Le filtre de la bibliothèque, gardé ici pour survivre à un aller-retour dans l'éditeur.
     * Un tag choisi dans l'écran des tags l'écrit avant de revenir à la bibliothèque.
     */
    val filter = MutableStateFlow<LibraryFilter>(LibraryFilter.All)

    sealed class LibraryFilter {
        data object All : LibraryFilter()
        data object Favorites : LibraryFilter()
        data class Group(val id: Long) : LibraryFilter()
        data class TagFilter(val id: Long) : LibraryFilter()
    }

    // ─── Notes ───────────────────────────────────────────────────────────────

    suspend fun getNoteWithTagsById(noteId: Long): NoteWithTags? = repository.getNoteWithTagsById(noteId)

    suspend fun insertNote(note: Note): Long = repository.insertNote(note)

    suspend fun updateNote(note: Note) = repository.updateNote(note)

    fun setPinned(noteId: Long, pinned: Boolean) = viewModelScope.launch { repository.setPinned(noteId, pinned) }

    fun setFavorite(noteId: Long, favorite: Boolean) = viewModelScope.launch { repository.setFavorite(noteId, favorite) }

    fun setColor(noteId: Long, colorHex: String?) = viewModelScope.launch { repository.updateNoteColor(noteId, colorHex) }

    fun moveNoteToGroup(noteId: Long, groupId: Long?) = viewModelScope.launch { repository.updateNoteGroup(noteId, groupId) }

    suspend fun duplicate(noteId: Long, title: String): Long? = repository.duplicateNote(noteId, title)

    suspend fun setTagsForNote(noteId: Long, tagIds: List<Long>) = repository.setTagsForNote(noteId, tagIds)

    /** Une note vide ne mérite pas la corbeille : elle disparaît tout de suite. */
    suspend fun deleteForever(noteId: Long) = repository.deleteNoteForever(noteId)

    // ─── Corbeille ────────────────────────────────────────────────────────────

    fun moveToTrash(noteId: Long) = viewModelScope.launch { repository.moveToTrash(noteId) }

    fun restore(noteId: Long) = viewModelScope.launch { repository.restoreFromTrash(noteId) }

    fun deleteFromTrash(noteId: Long) = viewModelScope.launch { repository.deleteNoteForever(noteId) }

    fun emptyTrash() = viewModelScope.launch { repository.emptyTrash() }

    fun purgeOldTrash() = viewModelScope.launch { repository.purgeTrash(TRASH_DAYS * 24L * 3600_000L) }

    // ─── Liens entre notes ────────────────────────────────────────────────────

    /** La note qui porte ce titre, créée vide si elle n'existe pas encore. */
    suspend fun getOrCreateNoteByTitle(title: String): Long {
        repository.getNoteByTitle(title)?.let { return it.id }
        val now = System.currentTimeMillis()
        return repository.insertNote(Note(title = title, dateCreated = now, dateModified = now))
    }

    suspend fun getBacklinks(title: String, noteId: Long): List<NoteWithTags> = repository.getBacklinks(title, noteId)

    suspend fun getAllTitles(): List<NoteTitle> = repository.getAllTitles()

    // ─── Groups ───────────────────────────────────────────────────────────────

    suspend fun createGroup(name: String): Long =
        repository.insertGroup(NoteGroup(name = name, position = repository.getGroupCount()))

    fun renameGroup(group: NoteGroup, name: String) = viewModelScope.launch { repository.updateGroup(group.copy(name = name)) }

    fun deleteGroup(group: NoteGroup) = viewModelScope.launch {
        repository.deleteGroup(group)
        if ((filter.value as? LibraryFilter.Group)?.id == group.id) filter.value = LibraryFilter.All
    }

    // ─── Tags ─────────────────────────────────────────────────────────────────

    suspend fun getAllTags(): List<Tag> = repository.getAllTagsList()

    /** Le tag de ce nom, créé s'il n'existe pas. */
    suspend fun getOrCreateTag(name: String): Long =
        repository.getTagByName(name)?.id ?: repository.insertTag(Tag(name = name))

    fun renameTag(tag: Tag, name: String) = viewModelScope.launch { repository.updateTag(tag.copy(name = name)) }

    fun deleteTag(tag: Tag) = viewModelScope.launch {
        repository.deleteTag(tag)
        if ((filter.value as? LibraryFilter.TagFilter)?.id == tag.id) filter.value = LibraryFilter.All
    }

    fun moveTagToCategory(tagId: Long, categoryId: Long?) = viewModelScope.launch { repository.updateTagCategory(tagId, categoryId) }

    // ─── Categories ───────────────────────────────────────────────────────────

    fun createCategory(name: String) = viewModelScope.launch { repository.insertCategory(TagCategory(name = name)) }

    fun renameCategory(category: TagCategory, name: String) = viewModelScope.launch { repository.updateCategory(category.copy(name = name)) }

    fun deleteCategory(category: TagCategory) = viewModelScope.launch { repository.deleteCategory(category) }

    // ─── Backup helpers ───────────────────────────────────────────────────────

    val repositoryRef: NotesRepository get() = repository

    companion object {
        const val TRASH_DAYS = 30
    }
}
