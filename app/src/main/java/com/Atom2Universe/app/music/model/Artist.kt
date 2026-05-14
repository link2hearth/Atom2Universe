package com.Atom2Universe.app.music.model

data class Artist(
    val name: String,
    val albums: MutableList<Album> = mutableListOf(),
    var trackCount: Int = 0,
    private val albumCountOverride: Int? = null
) {
    val albumCount: Int
        get() = albumCountOverride ?: albums.size
}
