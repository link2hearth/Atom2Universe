package com.Atom2Universe.app.notes.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tag_categories")
data class TagCategory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val sortOrder: Int = 0,
    val dateCreated: Long = System.currentTimeMillis()
)
