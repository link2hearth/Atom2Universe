package com.Atom2Universe.app.comics

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/** Tri naturel : "2.jpg" < "10.jpg" < "page10b.jpg" */
internal fun naturalCompare(a: String, b: String): Int {
    val re = Regex("(\\d+)|(\\D+)")
    val aT = re.findAll(a).map { it.value }.toList()
    val bT = re.findAll(b).map { it.value }.toList()
    for (i in 0 until minOf(aT.size, bT.size)) {
        val an = aT[i].toLongOrNull()
        val bn = bT[i].toLongOrNull()
        val c = if (an != null && bn != null) an.compareTo(bn)
                else aT[i].lowercase().compareTo(bT[i].lowercase())
        if (c != 0) return c
    }
    return aT.size.compareTo(bT.size)
}

@Entity(tableName = "comic_entries")
data class ComicEntry(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val sourcePath: String,
    val format: String,
    val totalPages: Int,
    val currentPage: Int = 0,
    val addedAt: Long = System.currentTimeMillis(),
    val lastOpenedAt: Long = System.currentTimeMillis(),
    val rootId: String? = null,
    val relativePath: String? = null,
    val firstImagePath: String? = null
) {
    val progressPercent: Int
        get() = if (totalPages > 0) ((currentPage.toFloat() / totalPages) * 100).toInt().coerceIn(0, 100) else 0
}
