package com.Atom2Universe.app.music.sync.peer

import android.content.Context
import android.util.Log
import com.Atom2Universe.app.music.data.MusicDatabase
import com.Atom2Universe.app.music.sync.DeviceIdentity
import com.Atom2Universe.app.music.sync.model.SyncLyricsEntry
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Serveur HTTP minimal embarqué dans l'app.
 * Expose les listen_events locaux aux autres appareils A2U sur le LAN.
 *
 * Endpoints :
 *   GET /info           → {"deviceId":"...", "latestEventAt": 1234567890}
 *   GET /events?since=T → tableau JSON des events depuis le timestamp T
 */
class PeerSyncServer(private val context: Context, val port: Int = PORT) {

    companion object {
        const val PORT = 47123
        private const val TAG = "PeerSyncServer"
        private const val MAX_EVENTS_PER_REQUEST = 5000
    }

    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private val executor = Executors.newCachedThreadPool()

    fun start() {
        if (running.getAndSet(true)) return
        executor.execute { acceptLoop() }
        Log.i(TAG, "Server started on port $port")
    }

    fun stop() {
        running.set(false)
        serverSocket?.close()
        executor.shutdown()
        Log.i(TAG, "Server stopped")
    }

    fun isRunning() = running.get()

    private fun acceptLoop() {
        try {
            serverSocket = ServerSocket(port).also { it.reuseAddress = true }
            while (running.get()) {
                val client = serverSocket?.accept() ?: break
                executor.execute { handleClient(client) }
            }
        } catch (e: Exception) {
            if (running.get()) Log.e(TAG, "Server error", e)
        }
    }

    private fun handleClient(socket: Socket) {
        try {
            socket.soTimeout = 5000
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val writer = PrintWriter(socket.getOutputStream(), true)

            val requestLine = reader.readLine() ?: return
            // Consommer les headers (jusqu'à la ligne vide)
            while (reader.readLine()?.isNotEmpty() == true) { /* skip */ }

            val (method, path) = parseRequestLine(requestLine)
            if (method != "GET") {
                writeResponse(writer, 405, "Method Not Allowed", "{\"error\":\"only GET\"}")
                return
            }

            val (endpoint, params) = parsePath(path)
            val body = when (endpoint) {
                "/info"   -> handleInfo()
                "/events" -> handleEvents(params["since"]?.toLongOrNull() ?: 0L)
                "/lyrics" -> handleLyrics(params["since"]?.toLongOrNull() ?: 0L)
                else -> { writeResponse(writer, 404, "Not Found", "{\"error\":\"unknown endpoint\"}"); return }
            }
            writeResponse(writer, 200, "OK", body)
        } catch (e: Exception) {
            Log.w(TAG, "Client error", e)
        } finally {
            socket.close()
        }
    }

    private fun handleInfo(): String {
        val deviceId = DeviceIdentity.getDeviceId(context)
        val db = MusicDatabase.getInstance(context)
        val (latestEvent, latestLyrics) = runBlocking {
            Pair(
                db.listenEventDao().getLatestTimestamp() ?: 0L,
                db.lyricsDao().getLatestModifiedTimestamp() ?: 0L
            )
        }
        return JSONObject().apply {
            put("deviceId", deviceId)
            put("latestEventAt", latestEvent)
            put("latestLyricsAt", latestLyrics)
        }.toString()
    }

    private fun handleLyrics(since: Long): String {
        val db = MusicDatabase.getInstance(context)
        val entities = runBlocking { db.lyricsDao().getModifiedSince(since) }
        val arr = JSONArray()
        for (e in entities) {
            val parts = e.metadataKey.split("-", limit = 3)
            arr.put(SyncLyricsEntry(
                key = e.metadataKey,
                title = parts.getOrElse(0) { "" },
                artist = parts.getOrElse(1) { "" },
                album = parts.getOrElse(2) { "" },
                lyrics = e.lyrics,
                source = e.source,
                isSynced = e.isSynced,
                modifiedAt = e.lastModified,
                deletedAt = null
            ).toJson())
        }
        return arr.toString()
    }

    private fun handleEvents(since: Long): String {
        val deviceId = DeviceIdentity.getDeviceId(context)
        val events = runBlocking {
            MusicDatabase.getInstance(context)
                .listenEventDao()
                .getLocalEventsSince(deviceId, since)
                .take(MAX_EVENTS_PER_REQUEST)
        }
        val arr = JSONArray()
        for (e in events) {
            arr.put(JSONObject().apply {
                put("uuid", e.uuid)
                put("trackKey", e.trackKey)
                put("deviceId", e.deviceId)
                put("listenedAt", e.listenedAt)
                put("durationListenedMs", e.durationListenedMs)
                put("trackDurationMs", e.trackDurationMs)
                put("title", e.title)
                put("artist", e.artist)
                put("album", e.album)
                put("isMigrated", e.isMigrated)
            })
        }
        return arr.toString()
    }

    private fun writeResponse(writer: PrintWriter, code: Int, status: String, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        writer.print("HTTP/1.1 $code $status\r\n")
        writer.print("Content-Type: application/json; charset=utf-8\r\n")
        writer.print("Content-Length: ${bytes.size}\r\n")
        writer.print("Connection: close\r\n")
        writer.print("\r\n")
        writer.print(body)
        writer.flush()
    }

    private fun parseRequestLine(line: String): Pair<String, String> {
        val parts = line.split(" ")
        return Pair(parts.getOrElse(0) { "GET" }, parts.getOrElse(1) { "/" })
    }

    private fun parsePath(fullPath: String): Pair<String, Map<String, String>> {
        val idx = fullPath.indexOf('?')
        if (idx < 0) return Pair(fullPath, emptyMap())
        val path = fullPath.substring(0, idx)
        val query = fullPath.substring(idx + 1)
        val params = query.split("&").associate { kv ->
            val eq = kv.indexOf('=')
            if (eq < 0) kv to "" else kv.substring(0, eq) to kv.substring(eq + 1)
        }
        return Pair(path, params)
    }
}
