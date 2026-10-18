package com.Atom2Universe.app.notes.data

import androidx.room.Embedded

data class GroupWithCount(
    @Embedded val group: NoteGroup,
    val noteCount: Int
)
