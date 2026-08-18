package com.Atom2Universe.app.music.sync.model

import com.Atom2Universe.app.music.data.ListenEvent
import org.json.JSONArray
import org.json.JSONObject

/**
 * Format d'échange du journal d'écoutes entre appareils.
 *
 * Chaque appareil publie UN fichier contenant SES écoutes : a2u_events_[deviceId].json,
 * lu par Google Drive (CloudSyncManager) et par le dossier partagé Syncthing.
 *
 * Chaque écoute porte un UUID, donc la fusion est une union dédupliquée :
 * aucun conflit possible, et ré-importer le même fichier n'a aucun effet.
 *
 * ## Format 2 : compact et borné
 *
 * Le format 1 répétait dans chaque écoute le deviceId (36 caractères, alors que
 * le fichier est déjà celui d'un seul appareil) et le titre/artiste/album (déjà
 * contenus dans `trackKey`) : ~280 octets par écoute. Le format 2 sort les
 * métadonnées dans un dictionnaire de morceaux et encode les écoutes en tableaux
 * positionnels : ~90 octets.
 *
 * Surtout, il ne détaille que les écoutes récentes. Tout ce qui précède la
 * fenêtre est envoyé sous forme de résumé par morceau — « 412 écoutes entre
 * telle et telle date » — soit quelques dizaines d'octets par morceau au lieu
 * de 412 écoutes. Le receveur le réhydrate en complétant ce qui lui manque
 * (voir ListenEventsMerger), sans jamais compter deux fois.
 *
 * Le format 1 reste lisible, le temps que tous les appareils soient à jour.
 */
object ListenEventsSyncFile {

    const val FILE_PREFIX = "a2u_events_"
    const val FORMAT_VERSION = 2

    /** Métadonnées d'affichage d'un morceau, mutualisées pour toutes ses écoutes. */
    data class TrackMeta(
        val key: String,
        val title: String,
        val artist: String,
        val album: String
    )

    /** Résumé des écoutes d'un morceau antérieures à la fenêtre détaillée. */
    data class ArchiveEntry(
        val trackKey: String,
        val count: Long,
        val firstAt: Long,
        val lastAt: Long
    )

    /** Contenu complet du fichier d'un appareil. */
    data class Payload(
        val deviceId: String,
        val windowStart: Long,
        val tracks: List<TrackMeta>,
        val events: List<ListenEvent>,
        val archive: List<ArchiveEntry>
    ) {
        /** Nombre total d'écoutes représentées, détaillées + résumées. */
        fun totalListenCount(): Long = events.size + archive.sumOf { it.count }
    }

    /** Nom du fichier publié par l'appareil [deviceId]. */
    fun filenameFor(deviceId: String): String = "$FILE_PREFIX$deviceId.json"

    /** true si [filename] est un fichier d'écoutes d'un AUTRE appareil que [selfDeviceId]. */
    fun isForeignEventsFile(filename: String, selfDeviceId: String): Boolean =
        filename.startsWith(FILE_PREFIX) &&
                filename.endsWith(".json") &&
                !filename.contains(selfDeviceId)

    // ==================== Écriture ====================

    fun encode(payload: Payload): JSONObject {
        val indexOfTrack = HashMap<String, Int>(payload.tracks.size)
        val tracksJson = JSONArray()
        payload.tracks.forEachIndexed { index, meta ->
            indexOfTrack[meta.key] = index
            tracksJson.put(JSONArray().apply {
                put(meta.key)
                put(meta.title)
                put(meta.artist)
                put(meta.album)
            })
        }

        val eventsJson = JSONArray()
        for (e in payload.events) {
            val trackIndex = indexOfTrack[e.trackKey] ?: continue
            eventsJson.put(JSONArray().apply {
                put(e.uuid)
                put(trackIndex)
                put(e.listenedAt)
                put(e.durationListenedMs)
                put(e.trackDurationMs)
                put(if (e.isMigrated) 1 else 0)
            })
        }

        val archiveJson = JSONArray()
        for (a in payload.archive) {
            val trackIndex = indexOfTrack[a.trackKey] ?: continue
            archiveJson.put(JSONArray().apply {
                put(trackIndex)
                put(a.count)
                put(a.firstAt)
                put(a.lastAt)
            })
        }

        return JSONObject().apply {
            put("formatVersion", FORMAT_VERSION)
            put("deviceId", payload.deviceId)
            put("exportedAt", System.currentTimeMillis())
            put("windowStart", payload.windowStart)
            put("tracks", tracksJson)
            put("events", eventsJson)
            put("archive", archiveJson)
        }
    }

    // ==================== Lecture ====================

    /**
     * Désérialise un fichier complet, format 1 ou 2.
     * @return le contenu, ou null si la version de format est inconnue.
     */
    fun decode(json: JSONObject): Payload? = when (json.optInt("formatVersion", 0)) {
        2 -> decodeV2(json)
        1 -> decodeV1(json)
        else -> null
    }

    private fun decodeV2(json: JSONObject): Payload {
        val deviceId = json.optString("deviceId", "")

        val tracksJson = json.optJSONArray("tracks") ?: JSONArray()
        val tracks = ArrayList<TrackMeta>(tracksJson.length())
        for (i in 0 until tracksJson.length()) {
            val t = tracksJson.getJSONArray(i)
            tracks.add(
                TrackMeta(
                    key = t.getString(0),
                    title = t.optString(1, ""),
                    artist = t.optString(2, ""),
                    album = t.optString(3, "")
                )
            )
        }

        val eventsJson = json.optJSONArray("events") ?: JSONArray()
        val events = ArrayList<ListenEvent>(eventsJson.length())
        for (i in 0 until eventsJson.length()) {
            val e = eventsJson.getJSONArray(i)
            val meta = tracks.getOrNull(e.getInt(1)) ?: continue
            events.add(
                ListenEvent(
                    uuid = e.getString(0),
                    trackKey = meta.key,
                    deviceId = deviceId,
                    listenedAt = e.getLong(2),
                    durationListenedMs = e.getLong(3),
                    trackDurationMs = e.getLong(4),
                    title = meta.title,
                    artist = meta.artist,
                    album = meta.album,
                    isMigrated = e.optInt(5, 0) == 1
                )
            )
        }

        val archiveJson = json.optJSONArray("archive") ?: JSONArray()
        val archive = ArrayList<ArchiveEntry>(archiveJson.length())
        for (i in 0 until archiveJson.length()) {
            val a = archiveJson.getJSONArray(i)
            val meta = tracks.getOrNull(a.getInt(0)) ?: continue
            archive.add(
                ArchiveEntry(
                    trackKey = meta.key,
                    count = a.getLong(1),
                    firstAt = a.getLong(2),
                    lastAt = a.getLong(3)
                )
            )
        }

        return Payload(
            deviceId = deviceId,
            windowStart = json.optLong("windowStart", 0),
            tracks = tracks,
            events = events,
            archive = archive
        )
    }

    /** Format 1 : écoutes détaillées uniquement, métadonnées répétées. */
    private fun decodeV1(json: JSONObject): Payload {
        val events = decodeArray(json.optJSONArray("events") ?: JSONArray())
        val tracks = events
            .associateBy { it.trackKey }
            .map { (key, e) -> TrackMeta(key, e.title, e.artist, e.album) }
        return Payload(
            deviceId = json.optString("deviceId", ""),
            windowStart = 0,
            tracks = tracks,
            events = events,
            archive = emptyList()
        )
    }

    // ==================== Tableau nu (sync LAN) ====================

    /**
     * Le transport LAN échange de petits deltas en direct : pas de dictionnaire
     * ni de résumé, juste un tableau d'écoutes complètes.
     */
    fun encodeArray(events: List<ListenEvent>): JSONArray {
        val array = JSONArray()
        for (e in events) {
            array.put(JSONObject().apply {
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
        return array
    }

    fun decodeArray(array: JSONArray): List<ListenEvent> {
        val result = ArrayList<ListenEvent>(array.length())
        for (i in 0 until array.length()) {
            val e = array.getJSONObject(i)
            result.add(
                ListenEvent(
                    uuid = e.getString("uuid"),
                    trackKey = e.getString("trackKey"),
                    deviceId = e.getString("deviceId"),
                    listenedAt = e.getLong("listenedAt"),
                    durationListenedMs = e.getLong("durationListenedMs"),
                    trackDurationMs = e.getLong("trackDurationMs"),
                    title = e.getString("title"),
                    artist = e.getString("artist"),
                    album = e.getString("album"),
                    isMigrated = e.optBoolean("isMigrated", false)
                )
            )
        }
        return result
    }
}
