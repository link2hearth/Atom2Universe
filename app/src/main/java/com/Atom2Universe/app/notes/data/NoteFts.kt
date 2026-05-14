package com.Atom2Universe.app.notes.data

import androidx.room.Entity
import androidx.room.Fts4

@Fts4(contentEntity = Note::class)
@Entity(tableName = "notes_fts")
data class NoteFts(
    val title: String,
    val contentPlainText: String
)
