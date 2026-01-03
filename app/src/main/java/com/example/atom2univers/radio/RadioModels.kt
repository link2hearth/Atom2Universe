package com.example.atom2univers.radio

data class RadioStation(
    val id: String,
    val name: String,
    val url: String,
    val country: String,
    val language: String,
    val favicon: String,
    val bitrate: Int?
)

data class RadioFilters(
    val countries: List<String>,
    val languages: List<String>
)

data class RadioSearchParams(
    val query: String,
    val country: String,
    val language: String
)

data class NowPlayingInfo(
    val artist: String,
    val title: String
)
