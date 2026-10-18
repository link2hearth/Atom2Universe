package com.Atom2Universe.app.readingprogress.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Progression de lecture d'un livre ou d'une BD, identifiée par titre (pas de chemin/URI :
 * ceux-ci diffèrent d'un appareil à l'autre). Source de vérité pour la reprise de lecture
 * synchronisée entre appareils.
 */
@Entity(tableName = "reading_progress")
data class ReadingProgressEntity(
    /** Clé de matching entre appareils : "$mediaType|${title normalisé}" */
    @PrimaryKey val bookKey: String,

    /** "book" ou "comic" (StatsRepository.MODULE_BOOK / MODULE_COMIC) */
    val mediaType: String,

    /** Titre brut (affichage) */
    val title: String,

    /** Progression 0f..1f */
    val progressPercent: Float,

    /** Dernier instant où la progression a été mise à jour (sert au dernier-écrivain-gagne de la sync) */
    val lastReadTimestamp: Long,

    /** Appareil d'origine de cette progression (null = créée localement) */
    val sourceDeviceId: String? = null
)

/** Construit la clé de matching normalisée, partagée entre lecture locale et sync. */
fun readingProgressKey(mediaType: String, title: String): String =
    "$mediaType|${title.trim().lowercase()}"
