package com.Atom2Universe.app.notes.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tags",
    foreignKeys = [
        ForeignKey(
            entity = TagCategory::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("categoryId")]
)
data class Tag(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val colorHex: String? = null,
    val sortOrder: Int = 0,
    val textColorMode: String = "auto",
    val categoryId: Long? = null,
    val dateCreated: Long = System.currentTimeMillis()
)
