package com.Atom2Universe.app.music.navidrome

import com.Atom2Universe.app.music.model.MusicTrack

object NavidromeLibrary {

    @Volatile var artists: List<NavidromeArtist> = emptyList()
    @Volatile var albums: List<NavidromeAlbum> = emptyList()
    @Volatile var isLoaded: Boolean = false

    suspend fun loadArtists(client: SubsonicApiClient) {
        artists = client.fetchArtists()
        isLoaded = artists.isNotEmpty()
    }

    suspend fun loadAlbums(client: SubsonicApiClient, artistId: String) {
        albums = client.fetchAlbums(artistId)
    }

    suspend fun loadTracks(client: SubsonicApiClient, albumId: String): List<MusicTrack> {
        return client.fetchTracks(albumId)
    }

    fun clear() {
        artists = emptyList()
        albums = emptyList()
        isLoaded = false
    }
}
