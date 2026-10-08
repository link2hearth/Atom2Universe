package com.Atom2Universe.app.books

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "book_shelf_entries")
data class BookShelfEntry(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val author: String = "",
    val sourcePath: String,
    val format: String,          // "epub", "pdf", "txt"
    val fileSize: Long = 0,
    val coverPath: String? = null,
    val totalItems: Int = 0,
    val lastReadItem: Int = 0,
    val rootId: String,
    val relativePath: String = "",
    val addedAt: Long = System.currentTimeMillis(),
    val lastOpenedAt: Long = 0
) {
    val progressPercent: Int
        get() = if (totalItems > 0) ((lastReadItem.toFloat() / totalItems) * 100).toInt().coerceIn(0, 100) else 0
}
