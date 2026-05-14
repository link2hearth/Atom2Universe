package com.Atom2Universe.app.notes.data

import androidx.room.Embedded
import androidx.room.Relation

data class GroupWithNotes(
    @Embedded val group: NoteGroup,
    @Relation(
        parentColumn = "id",
        entityColumn = "groupId"
    )
    val notes: List<Note>
)

data class GroupWithCount(
    @Embedded val group: NoteGroup,
    val noteCount: Int
)
