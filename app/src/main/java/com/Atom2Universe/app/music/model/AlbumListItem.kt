package com.Atom2Universe.app.music.model

/**
 * Represents an item in the album list.
 * Can be either a regular album or a special "All Tracks" item.
 */
sealed class AlbumListItem {
    /**
     * A regular album item.
     */
    data class AlbumItem(val album: Album) : AlbumListItem()

    /**
     * Special item that represents all tracks from an artist.
     * Displayed at the top of the album list when viewing an artist.
     */
    data class AllTracksItem(
        val artistName: String,
        val tracks: List<MusicTrack>,
        val artistCustomIconPath: String? = null,  // Artist's custom icon (takes priority)
        val albumArtUris: List<String> = emptyList()  // First 4 album arts for 2x2 grid
    ) : AlbumListItem() {
        val trackCount: Int get() = tracks.size
        val hasCustomIcon: Boolean get() = artistCustomIconPath != null
    }
}
