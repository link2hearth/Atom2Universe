package com.Atom2Universe.app.notes.data

import androidx.room.Embedded

data class TagWithCount(
    @Embedded val tag: Tag,
    val usageCount: Int
)
