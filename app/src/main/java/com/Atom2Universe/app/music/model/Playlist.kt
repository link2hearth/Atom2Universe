package com.Atom2Universe.app.music.model

/**
 * Représente une playlist de musique (pour l'affichage UI).
 * @param id Identifiant unique
 * @param name Nom de la playlist
 * @param isSystemPlaylist True si c'est une playlist système (ex: Favoris)
 * @param iconResId Resource ID pour l'icône (optionnel)
 */
data class Playlist(
    val id: String,
    val name: String,
    val isSystemPlaylist: Boolean = false,
    val iconResId: Int? = null
) {
    companion object {
        const val FAVORITES_ID = "favorites"
        const val TOP_PLAYED_ID = "top_played"
        const val LEAST_PLAYED_ID = "least_played"
        const val ALL_LIBRARY_ID = "all_library"
    }
}

/**
 * Données d'une playlist personnalisée (pour la persistance JSON).
 * Version 2: stocke les métadonnées des tracks au lieu des chemins.
 * @param id Identifiant unique (UUID)
 * @param name Nom de la playlist
 * @param tracks Liste des entrées de pistes (métadonnées)
 * @param createdAt Date de création (format ISO 8601)
 */
data class PlaylistData(
    val id: String,
    val name: String,
    val tracks: List<PlaylistTrackEntry>,
    val createdAt: String
)

/**
 * Entrée d'une piste dans une playlist (métadonnées pour matching).
 * @param title Titre de la piste
 * @param artist Artiste
 * @param album Album
 */
data class PlaylistTrackEntry(
    val title: String,
    val artist: String,
    val album: String
)
