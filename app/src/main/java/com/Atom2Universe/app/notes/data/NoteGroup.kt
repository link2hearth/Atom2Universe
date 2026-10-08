package com.Atom2Universe.app.notes.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "note_groups")
data class NoteGroup(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val description: String = "",
    val colorHex: String? = null,
    val textColorMode: String = "auto",
    val iconName: String? = null,
    val position: Int = 0,
    val dateCreated: Long = System.currentTimeMillis(),
    val dateModified: Long = System.currentTimeMillis()
)
