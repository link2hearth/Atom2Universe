package com.Atom2Universe.app.music.sync.peer

data class DiscoveredPeer(
    val deviceId: String,
    val host: String,
    val port: Int,
    val latestEventTimestamp: Long = 0L,
    val latestLyricsTimestamp: Long = 0L,
    val discoveredAt: Long = System.currentTimeMillis()
)
