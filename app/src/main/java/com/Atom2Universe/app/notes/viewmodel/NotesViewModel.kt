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

    val recentNotes: StateFlow<List<NoteWithTags>> = repository.getRecentNotesWithTags(10)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val favoriteNotes: StateFlow<List<NoteWithTags>> = repository.getFavoriteNotesWithTags()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val allGroupsWithCount: StateFlow<List<GroupWithCount>> = repository.getAllGroupsWithCount()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val allTagsWithCount: StateFlow<List<TagWithCount>> = repository.getAllTagsWithCount()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val allCategories: StateFlow<List<TagCategory>> = repository.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // ─── Notes ───────────────────────────────────────────────────────────────

    fun getNotesByGroup(groupId: Long): Flow<List<NoteWithTags>> =
        repository.getNotesByGroupWithTags(groupId)

    suspend fun getNoteWithTagsById(noteId: Long): NoteWithTags? =
        repository.getNoteWithTagsById(noteId)

    suspend fun saveNote(note: Note): Long = repository.insertNote(note)

    suspend fun updateNote(note: Note) = repository.updateNote(note)

    suspend fun deleteNote(note: Note) = repository.deleteNote(note)

    fun togglePin(noteId: Long, isPinned: Boolean) = viewModelScope.launch {
        repository.toggleNotePinned(noteId, isPinned)
    }

    fun toggleFavorite(noteId: Long, isFavorite: Boolean) = viewModelScope.launch {
        repository.toggleNoteFavorite(noteId, isFavorite)
    }

    fun updateNoteColor(noteId: Long, colorHex: String?, textColorMode: String) =
        viewModelScope.launch {
            repository.updateNoteColor(noteId, colorHex, textColorMode)
        }

    fun moveNoteToGroup(noteId: Long, groupId: Long?) = viewModelScope.launch {
        repository.updateNoteGroup(noteId, groupId)
    }

    // ─── Tags for note ────────────────────────────────────────────────────────

    suspend fun setTagsForNote(noteId: Long, tagIds: List<Long>) =
        repository.setTagsForNote(noteId, tagIds)

    suspend fun addTagToNote(noteId: Long, tagId: Long) =
        repository.addTagToNote(noteId, tagId)

    suspend fun removeTagFromNote(noteId: Long, tagId: Long) =
        repository.removeTagFromNote(noteId, tagId)

    fun getNotesByTag(tagId: Long): Flow<List<NoteWithTags>> = repository.getNotesByTag(tagId)

    // ─── Search ───────────────────────────────────────────────────────────────

    private val _searchResults = MutableStateFlow<List<NoteWithTags>>(emptyList())
    val searchResults: StateFlow<List<NoteWithTags>> = _searchResults.asStateFlow()

    fun search(query: String) = viewModelScope.launch {
        _searchResults.value = if (query.isBlank()) emptyList()
        else repository.searchNotes(query)
    }

    // ─── Groups ───────────────────────────────────────────────────────────────

    suspend fun createGroup(name: String, colorHex: String? = null): Long {
        val count = repository.getGroupCount()
        return repository.insertGroup(NoteGroup(name = name, colorHex = colorHex, position = count))
    }

    suspend fun updateGroup(group: NoteGroup) = repository.updateGroup(group)

    suspend fun deleteGroup(group: NoteGroup) = repository.deleteGroup(group)

    fun updateGroupColor(groupId: Long, colorHex: String?, textColorMode: String) =
        viewModelScope.launch {
            repository.updateGroupColor(groupId, colorHex, textColorMode)
        }

    fun reorderGroups(groups: List<NoteGroup>) = viewModelScope.launch {
        groups.forEachIndexed { index, group ->
            repository.updateGroupPosition(group.id, index)
        }
    }

    // ─── Tags ─────────────────────────────────────────────────────────────────

    suspend fun createTag(name: String, colorHex: String? = null, categoryId: Long? = null): Long =
        repository.insertTag(Tag(name = name, colorHex = colorHex, categoryId = categoryId))

    suspend fun updateTag(tag: Tag) = repository.updateTag(tag)

    suspend fun deleteTag(tag: Tag) = repository.deleteTag(tag)

    fun reorderTags(tags: List<Tag>) = viewModelScope.launch {
        tags.forEachIndexed { index, tag ->
            repository.updateTagSortOrder(tag.id, index)
        }
    }

    fun moveTagToCategory(tagId: Long, categoryId: Long?) = viewModelScope.launch {
        repository.updateTagCategory(tagId, categoryId)
    }

    // ─── Categories ───────────────────────────────────────────────────────────

    suspend fun createCategory(name: String): Long =
        repository.insertCategory(TagCategory(name = name))

    suspend fun updateCategory(category: TagCategory) = repository.updateCategory(category)

    suspend fun deleteCategory(category: TagCategory) = repository.deleteCategory(category)

    fun reorderCategories(categories: List<TagCategory>) = viewModelScope.launch {
        categories.forEachIndexed { index, cat ->
            repository.updateCategorySortOrder(cat.id, index)
        }
    }

    // ─── Wiki navigation ──────────────────────────────────────────────────────

    suspend fun getOrCreateNoteForTag(tagName: String): Long {
        val existing = repository.getNoteByTitle(tagName)
        if (existing != null) return existing.id
        val now = System.currentTimeMillis()
        return repository.insertNote(
            Note(title = tagName, content = "", contentPlainText = "",
                dateCreated = now, dateModified = now)
        )
    }

    suspend fun getBacklinksForNote(noteTitle: String, noteId: Long): List<NoteWithTags> {
        val tag = repository.getTagByName(noteTitle) ?: return emptyList()
        return repository.getNotesByTagExcluding(tag.id, noteId)
    }

    // ─── Backup helpers ───────────────────────────────────────────────────────

    val repositoryRef: NotesRepository get() = repository
}
