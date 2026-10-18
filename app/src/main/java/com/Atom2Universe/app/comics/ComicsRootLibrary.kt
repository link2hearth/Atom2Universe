package com.Atom2Universe.app.comics

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "comic_roots")
data class ComicsRootLibrary(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val rootUri: String,
    val comicCount: Int = 0,
    val addedAt: Long = System.currentTimeMillis(),
    val lastOpenedAt: Long = System.currentTimeMillis()
)
