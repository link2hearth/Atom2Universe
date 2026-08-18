package com.Atom2Universe.app.music.sync.peer

import android.util.Log
import com.Atom2Universe.app.music.data.ListenEvent
import com.Atom2Universe.app.music.data.MusicDatabase
import com.Atom2Universe.app.music.sync.algorithm.ListenEventsMerger
import com.Atom2Universe.app.music.sync.algorithm.LyricsMerger
import com.Atom2Universe.app.music.sync.model.ListenEventsSyncFile
import com.Atom2Universe.app.music.sync.model.LyricsSyncFile
import com.Atom2Universe.app.music.sync.model.SyncLyricsEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Client HTTP qui récupère les listen_events d'un pair découvert sur le LAN.
 * Utilise le timestamp du dernier event connu pour ne transférer que le delta.
 */
object PeerSyncClient {

    private const val TAG = "PeerSyncClient"
    private const val TIMEOUT_MS = 8000

    /**
     * Interroge le pair et importe ses events manquants.
     * @return nombre de nouveaux events insérés, ou -1 en cas d'erreur
     */
    suspend fun syncWithPeer(peer: DiscoveredPeer, context: android.content.Context): Int =
        withContext(Dispatchers.IO) {
            try {
                // 1. Vérifier si le pair a des events plus récents que nous
                val info = fetchInfo(peer) ?: return@withContext -1
                val peerLatest = info.optLong("latestEventAt", 0L)
                val peerDeviceId = info.optString("deviceId", "")

                val dao = MusicDatabase.getInstance(context).listenEventDao()
                val ourLatestFromPeer = dao.getLatestTimestampForDevice(peerDeviceId) ?: 0L

                if (peerLatest <= ourLatestFromPeer) {
                    Log.d(TAG, "Peer ${peer.deviceId.take(8)} has nothing new (peer=$peerLatest our=$ourLatestFromPeer)")
                    return@withContext 0
                }

                // 2. Récupérer les events manquants
                val events = fetchEvents(peer, since = ourLatestFromPeer)
                if (events.isEmpty()) return@withContext 0

                // 3. Fusionner (déduplication par UUID)
                val inserted = ListenEventsMerger.merge(context, events)
                if (inserted > 0) {
                    Log.i(TAG, "Synced $inserted events from ${peer.host}:${peer.port}")
                }
                inserted
            } catch (e: Exception) {
                Log.w(TAG, "Sync failed with ${peer.host}:${peer.port}", e)
                -1
            }
        }

    /**
     * Synchronise les paroles depuis un pair.
     * @return nombre de paroles mises à jour, ou -1 en cas d'erreur
     */
    suspend fun syncLyricsWith(peer: DiscoveredPeer, context: android.content.Context): Int =
        withContext(Dispatchers.IO) {
            try {
                val db = MusicDatabase.getInstance(context)
                val ourLatest = db.lyricsDao().getLatestModifiedTimestamp() ?: 0L

                if (peer.latestLyricsTimestamp <= ourLatest) {
                    Log.d(TAG, "Peer ${peer.deviceId.take(8)} has no new lyrics")
                    return@withContext 0
                }

                val entries = fetchLyrics(peer, since = ourLatest)
                if (entries.isEmpty()) return@withContext 0

                val syncFile = LyricsSyncFile(
                    lastModified = entries.maxOf { it.modifiedAt },
                    lyrics = entries
                )
                LyricsMerger.merge(context, syncFile)

                Log.i(TAG, "Synced ${entries.size} lyrics from ${peer.host}:${peer.port}")
                entries.size
            } catch (e: Exception) {
                Log.w(TAG, "Lyrics sync failed with ${peer.host}:${peer.port}", e)
                -1
            }
        }

    private fun fetchLyrics(peer: DiscoveredPeer, since: Long): List<SyncLyricsEntry> {
        return try {
            val url = URL("http://${peer.host}:${peer.port}/lyrics?since=$since")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                requestMethod = "GET"
            }
            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            val arr = JSONArray(body)
            (0 until arr.length()).map { SyncLyricsEntry.fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            Log.w(TAG, "fetchLyrics failed for ${peer.host}:${peer.port}", e)
            emptyList()
        }
    }

    private fun fetchInfo(peer: DiscoveredPeer): JSONObject? {
        return try {
            val url = URL("http://${peer.host}:${peer.port}/info")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                requestMethod = "GET"
            }
            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            JSONObject(body)
        } catch (e: Exception) {
            Log.w(TAG, "fetchInfo failed for ${peer.host}:${peer.port}", e)
            null
        }
    }

    private fun fetchEvents(peer: DiscoveredPeer, since: Long): List<ListenEvent> {
        return try {
            val url = URL("http://${peer.host}:${peer.port}/events?since=$since")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                requestMethod = "GET"
            }
            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            ListenEventsSyncFile.decodeArray(JSONArray(body))
        } catch (e: Exception) {
            Log.w(TAG, "fetchEvents failed for ${peer.host}:${peer.port}", e)
            emptyList()
        }
    }
}
