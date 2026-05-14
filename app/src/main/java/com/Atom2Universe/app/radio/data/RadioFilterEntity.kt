package com.Atom2Universe.app.radio.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entité Room pour stocker les filtres radio (pays et langues) en cache
 */
@Entity(tableName = "radio_filters")
data class RadioFilterEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val filterType: String, // "country" ou "language"
    val value: String,
    val cachedAt: Long = System.currentTimeMillis()
)
