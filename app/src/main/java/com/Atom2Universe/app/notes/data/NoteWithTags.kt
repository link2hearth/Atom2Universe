package com.Atom2Universe.app.notes.data

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class NoteWithTags(
    @Embedded val note: Note,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = NoteTag::class,
            parentColumn = "noteId",
            entityColumn = "tagId"
        )
    )
    val tags: List<Tag>,
    @Relation(
        parentColumn = "groupId",
        entityColumn = "id"
    )
    val group: NoteGroup?
)
