package com.Atom2Universe.app.music.sync.algorithm

import android.content.Context
import android.util.Log
import com.Atom2Universe.app.music.MusicPlayCountManager
import com.Atom2Universe.app.music.data.ListenEvent
import com.Atom2Universe.app.music.data.MusicDatabase
import com.Atom2Universe.app.music.data.PlayCountEntry
import com.Atom2Universe.app.music.sync.model.ListenEventsSyncFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Les deux sens de la synchronisation du journal d'écoutes.
 *
 * **Export** ([buildPayload]) : les écoutes de la fenêtre récente sont envoyées
 * une par une, tout ce qui précède est résumé par morceau. La taille du fichier
 * dépend donc de l'activité récente, pas de l'historique total.
 *
 * **Import** ([merge]) : les écoutes détaillées sont dédupliquées par UUID.
 * Le résumé, lui, est *réconcilié* : pour un couple (appareil, morceau) on
 * compare ce qu'on détient au total annoncé, et on ne complète que la différence
 * avec des écoutes synthétiques. C'est ce qui rend l'opération sûre quand la
 * fenêtre glisse — une écoute qui passe du détail au résumé n'est pas recomptée.
 *
 * Tout passe par les DAO : la fusion doit fonctionner dans un worker de fond où
 * MusicPlayCountManager n'a jamais été initialisé. Le cache mémoire de ce dernier
 * n'est rafraîchi que s'il tourne déjà.
 */
object ListenEventsMerger {

    private const val TAG = "ListenEventsMerger"

    /** Fenêtre d'échange détaillé. Au-delà, les écoutes partent en résumé. */
    private const val WINDOW_DAYS = 60L
    private const val DAY_MS = 24L * 60 * 60 * 1000

    // ==================== Export ====================

    /**
     * Construit le contenu à publier pour l'appareil [deviceId].
     */
    suspend fun buildPayload(
        context: Context,
        deviceId: String,
        windowDays: Long = WINDOW_DAYS
    ): ListenEventsSyncFile.Payload = withContext(Dispatchers.IO) {
        val dao = MusicDatabase.getInstance(context).listenEventDao()
        val cutoff = System.currentTimeMillis() - windowDays * DAY_MS

        // Partition exacte : le résumé prend listenedAt <= cutoff, le détail >.
        val events = dao.getLocalEventsSince(deviceId, cutoff)
        val archiveRows = dao.getArchiveSummary(deviceId, cutoff)

        // Dictionnaire des morceaux cités. Les écoutes détaillées passent en
        // dernier : leurs métadonnées sont les plus fraîches.
        val tracks = LinkedHashMap<String, ListenEventsSyncFile.TrackMeta>()
        for (row in archiveRows) {
            tracks[row.trackKey] = ListenEventsSyncFile.TrackMeta(
                key = row.trackKey,
                title = row.title,
                artist = row.artist,
                album = row.album
            )
        }
        for (e in events) {
            tracks[e.trackKey] = ListenEventsSyncFile.TrackMeta(
                key = e.trackKey,
                title = e.title,
                artist = e.artist,
                album = e.album
            )
        }

        ListenEventsSyncFile.Payload(
            deviceId = deviceId,
            windowStart = cutoff,
            tracks = tracks.values.toList(),
            events = events,
            archive = archiveRows.map {
                ListenEventsSyncFile.ArchiveEntry(
                    trackKey = it.trackKey,
                    count = it.eventCount,
                    firstAt = it.firstAt,
                    lastAt = it.lastAt
                )
            }
        )
    }

    /**
     * Journal complet d'un appareil : toutes ses écoutes en détail, sans fenêtre
     * ni résumé. Réservé à la sauvegarde, où l'on veut restituer les dates réelles
     * plutôt que des dates interpolées.
     */
    suspend fun buildFullPayload(
        context: Context,
        deviceId: String
    ): ListenEventsSyncFile.Payload = withContext(Dispatchers.IO) {
        val dao = MusicDatabase.getInstance(context).listenEventDao()
        val events = dao.getAllForDevice(deviceId)

        val tracks = LinkedHashMap<String, ListenEventsSyncFile.TrackMeta>()
        for (e in events) {
            tracks[e.trackKey] = ListenEventsSyncFile.TrackMeta(
                key = e.trackKey,
                title = e.title,
                artist = e.artist,
                album = e.album
            )
        }

        ListenEventsSyncFile.Payload(
            deviceId = deviceId,
            windowStart = 0,
            tracks = tracks.values.toList(),
            events = events,
            archive = emptyList()
        )
    }

    // ==================== Import ====================

    /**
     * Fusionne un tableau d'écoutes brut (transport LAN, sans résumé).
     * @return le nombre d'écoutes réellement ajoutées.
     */
    suspend fun merge(context: Context, incoming: List<ListenEvent>): Int {
        if (incoming.isEmpty()) return 0
        return merge(
            context,
            ListenEventsSyncFile.Payload(
                deviceId = incoming.first().deviceId,
                windowStart = 0,
                tracks = emptyList(),
                events = incoming,
                archive = emptyList()
            )
        )
    }

    /**
     * Fusionne le fichier publié par un autre appareil.
     * @return le nombre d'écoutes réellement ajoutées.
     */
    suspend fun merge(context: Context, payload: ListenEventsSyncFile.Payload): Int =
        withContext(Dispatchers.IO) {
            if (payload.events.isEmpty() && payload.archive.isEmpty()) return@withContext 0

            val db = MusicDatabase.getInstance(context)
            val listenEventDao = db.listenEventDao()
            val playCountDao = db.playCountDao()

            val affectedKeys = HashSet<String>()
            val metadataByKey = HashMap<String, ListenEvent>()
            var insertedCount = 0

            // 1. Écoutes détaillées : déduplication par UUID.
            //    getExistingUuids fait un IN (...) : on découpe pour rester sous
            //    la limite de variables liées de SQLite (999).
            if (payload.events.isNotEmpty()) {
                val known = HashSet<String>()
                payload.events.map { it.uuid }.chunked(500).forEach { batch ->
                    known.addAll(listenEventDao.getExistingUuids(batch))
                }
                val newEvents = payload.events.filter { it.uuid !in known }
                if (newEvents.isNotEmpty()) {
                    listenEventDao.insertAll(newEvents)
                    insertedCount += newEvents.size
                    newEvents.forEach { affectedKeys.add(it.trackKey) }
                }
                payload.events.forEach { metadataByKey.putIfAbsent(it.trackKey, it) }
            }

            // 2. Résumé de l'avant-fenêtre : réconciliation par total.
            if (payload.archive.isNotEmpty()) {
                val detailedPerTrack = payload.events.groupingBy { it.trackKey }.eachCount()
                val metaByKey = payload.tracks.associateBy { it.key }

                for (entry in payload.archive) {
                    val target = entry.count + (detailedPerTrack[entry.trackKey] ?: 0)
                    val held = listenEventDao.countForDeviceAndTrack(payload.deviceId, entry.trackKey)
                    if (held >= target) continue

                    val meta = metaByKey[entry.trackKey]
                    val padding = synthesize(payload.deviceId, entry, meta, from = held, to = target)
                    listenEventDao.insertAll(padding)
                    insertedCount += padding.size
                    affectedKeys.add(entry.trackKey)
                    padding.firstOrNull()?.let { metadataByKey.putIfAbsent(entry.trackKey, it) }
                }
            }

            if (affectedKeys.isEmpty()) return@withContext 0

            // 3. Recalculer playCount = nombre d'écoutes au journal, pour les seuls
            //    morceaux touchés. earnedPlayCount n'est PAS modifié : ce sont des
            //    écoutes distantes, pas des écoutes de cet appareil.
            val now = System.currentTimeMillis()
            for (key in affectedKeys) {
                val count = listenEventDao.getPlayCount(key)
                if (count <= 0) continue

                val current = playCountDao.getByKey(key)
                if (current == null) {
                    // Morceau jamais écouté sur cet appareil : on crée la ligne.
                    val meta = metadataByKey[key] ?: continue
                    playCountDao.insert(
                        PlayCountEntry(
                            metadataKey = key,
                            title = meta.title,
                            artist = meta.artist,
                            album = meta.album,
                            playCount = count,
                            earnedPlayCount = 0,
                            lastPlayed = meta.listenedAt,
                            createdAt = now,
                            updatedAt = now
                        )
                    )
                } else if (count > current.playCount) {
                    // Règle MAX : un compteur ne redescend jamais.
                    playCountDao.updatePlayCountMax(key, count, now)
                }
            }

            // Rafraîchir le cache mémoire si le lecteur tourne déjà.
            MusicPlayCountManager.refreshCache()

            Log.d(TAG, "Merged $insertedCount listen events for ${affectedKeys.size} track(s)")
            insertedCount
        }

    /**
     * Recrée les écoutes manquantes d'un résumé, aux index [from] jusqu'à [to].
     *
     * Les dates sont interpolées sur la plage annoncée et les UUID dérivés de
     * (appareil, morceau, index) : deux fusions concurrentes produisent les mêmes,
     * donc l'insertion reste idempotente. Elles sont marquées isMigrated pour ne
     * pas polluer l'historique d'écoute récent, qui ne montre que le temps réel.
     */
    private fun synthesize(
        deviceId: String,
        entry: ListenEventsSyncFile.ArchiveEntry,
        meta: ListenEventsSyncFile.TrackMeta?,
        from: Long,
        to: Long
    ): List<ListenEvent> {
        val span = (entry.lastAt - entry.firstAt).coerceAtLeast(0)
        val step = if (to > 1) span / to else 0
        return (from until to).map { index ->
            ListenEvent(
                uuid = UUID.nameUUIDFromBytes(
                    "$deviceId|${entry.trackKey}|$index".toByteArray()
                ).toString(),
                trackKey = entry.trackKey,
                deviceId = deviceId,
                listenedAt = (entry.firstAt + index * step).coerceAtMost(entry.lastAt),
                durationListenedMs = -1L,
                trackDurationMs = -1L,
                title = meta?.title ?: "",
                artist = meta?.artist ?: "",
                album = meta?.album ?: "",
                isMigrated = true
            )
        }
    }
}
