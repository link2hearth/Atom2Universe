package com.Atom2Universe.app.music.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Journal immuable des écoutes. Chaque ligne = une écoute.
 *
 * Source de vérité pour l'historique et les play counts.
 * Append-only : jamais modifié, seulement inséré.
 * Sync = échange d'UUID manquants entre nœuds → pas de conflit possible.
 */
@Entity(
    tableName = "listen_events",
    indices = [
        Index(value = ["trackKey"]),
        Index(value = ["deviceId"]),
        Index(value = ["listenedAt"])
    ]
)
data class ListenEvent(
    @PrimaryKey val uuid: String,
    val trackKey: String,
    val deviceId: String,
    val listenedAt: Long,
    val durationListenedMs: Long,
    val trackDurationMs: Long,
    val title: String,
    val artist: String,
    val album: String,
    /** true = créé lors de la migration depuis earnedPlayCount (pas une écoute temps réel) */
    val isMigrated: Boolean = false
)
