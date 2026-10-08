package com.Atom2Universe.app.books

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "book_shelf_roots")
data class BookShelfRoot(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val rootUri: String,
    val bookCount: Int = 0,
    val addedAt: Long = System.currentTimeMillis(),
    val lastScannedAt: Long = System.currentTimeMillis()
)
