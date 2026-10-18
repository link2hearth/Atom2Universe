package com.Atom2Universe.app.music.navidrome

data class NavidromeArtist(
    val id: String,
    val name: String,
    val albumCount: Int,
    val coverArtId: String?
)

data class NavidromeAlbum(
    val id: String,
    val name: String,
    val artist: String,
    val artistId: String,
    val songCount: Int,
    val year: Int?,
    val coverArtId: String?
)
