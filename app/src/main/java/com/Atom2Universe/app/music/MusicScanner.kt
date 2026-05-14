package com.Atom2Universe.app.music

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.Atom2Universe.app.R
import com.Atom2Universe.app.music.model.MusicTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

object MusicScanner {

    private val ALBUM_ART_URI = Uri.parse("content://media/external/audio/albumart")

    /**
     * Scanne les dossiers configurés (Music par défaut + dossiers personnalisés)
     */
    suspend fun scanConfiguredFolders(context: Context): List<MusicTrack> = withContext(Dispatchers.IO) {
        val allTracks = mutableListOf<MusicTrack>()
        val seenIds = mutableSetOf<Long>()

        // Initialise le gestionnaire de dossiers
        MusicFoldersManager.init(context)

        // Récupère tous les chemins à scanner
        val folderPaths = MusicFoldersManager.getAllFolderPaths(context)

        for (folderPath in folderPaths) {
            val tracks = scanMusicFolder(context, folderPath)
            for (track in tracks) {
                if (!seenIds.contains(track.id)) {
                    seenIds.add(track.id)
                    allTracks.add(track)
                }
            }
        }

        // Trie par titre
        allTracks.sortBy { it.title.lowercase() }
        allTracks
    }

    /**
     * Scanne TOUS les fichiers audio (ancienne méthode, non recommandée)
     */
    suspend fun scanAllMusic(context: Context): List<MusicTrack> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<MusicTrack>()

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

            val unknownTitle = context.getString(R.string.music_unknown_title)
            val unknownArtist = context.getString(R.string.music_unknown_artist)
            val unknownAlbum = context.getString(R.string.music_unknown_album)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val title = (cursor.getString(titleColumn) ?: unknownTitle).fixMetadataEncoding()
                val artist = (cursor.getString(artistColumn) ?: unknownArtist).fixMetadataEncoding()
                val album = (cursor.getString(albumColumn) ?: unknownAlbum).fixMetadataEncoding()
                val albumId = cursor.getLong(albumIdColumn)
                val duration = cursor.getLong(durationColumn)

                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                val albumArtUri = ContentUris.withAppendedId(ALBUM_ART_URI, albumId)

                tracks.add(
                    MusicTrack(
                        id = id,
                        title = title,
                        artist = artist,
                        album = album,
                        duration = duration,
                        uri = contentUri,
                        albumArtUri = albumArtUri
                    )
                )
            }
        }

        tracks
    }

    /**
     * Rescanne un fichier spécifique par son ID MediaStore.
     * Retourne le MusicTrack mis à jour ou null si non trouvé.
     */
    suspend fun scanSingleTrack(context: Context, trackId: Long): MusicTrack? = withContext(Dispatchers.IO) {
        val projection = buildProjection()
        val selection = "${MediaStore.Audio.Media._ID} = ?"
        val selectionArgs = arrayOf(trackId.toString())

        val unknownTitle = context.getString(R.string.music_unknown_title)
        val unknownArtist = context.getString(R.string.music_unknown_artist)
        val unknownAlbum = context.getString(R.string.music_unknown_album)

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return@withContext cursorToTrack(cursor, unknownTitle, unknownArtist, unknownAlbum)
            }
        }
        null
    }

    /**
     * Rescanne un fichier par son chemin (filePath).
     * Utiliser cette méthode après modification des tags ID3, car le MediaStore
     * peut régénérer un nouvel ID pour le fichier modifié.
     * Retourne le MusicTrack mis à jour ou null si non trouvé.
     */
    suspend fun scanSingleTrackByPath(context: Context, filePath: String): MusicTrack? = withContext(Dispatchers.IO) {
        val projection = buildProjection()
        val selection = "${MediaStore.Audio.Media.DATA} = ?"
        val selectionArgs = arrayOf(filePath)

        val unknownTitle = context.getString(R.string.music_unknown_title)
        val unknownArtist = context.getString(R.string.music_unknown_artist)
        val unknownAlbum = context.getString(R.string.music_unknown_album)

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return@withContext cursorToTrack(cursor, unknownTitle, unknownArtist, unknownAlbum)
            }
        }
        null
    }

    /**
     * Rescanne plusieurs fichiers par leurs IDs MediaStore.
     * Retourne la liste des MusicTracks mis à jour.
     */
    suspend fun scanTracks(context: Context, trackIds: List<Long>): List<MusicTrack> = withContext(Dispatchers.IO) {
        if (trackIds.isEmpty()) return@withContext emptyList()

        val tracks = mutableListOf<MusicTrack>()
        val projection = buildProjection()

        val unknownTitle = context.getString(R.string.music_unknown_title)
        val unknownArtist = context.getString(R.string.music_unknown_artist)
        val unknownAlbum = context.getString(R.string.music_unknown_album)

        // Query par batch pour éviter les limites SQL
        val placeholders = trackIds.joinToString(",") { "?" }
        val selection = "${MediaStore.Audio.Media._ID} IN ($placeholders)"
        val selectionArgs = trackIds.map { it.toString() }.toTypedArray()

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                cursorToTrack(cursor, unknownTitle, unknownArtist, unknownAlbum)?.let { tracks.add(it) }
            }
        }
        tracks
    }

    /**
     * Rescanne plusieurs fichiers par leurs chemins (filePath).
     * Utiliser cette méthode après modification des tags ID3, car le MediaStore
     * peut régénérer de nouveaux IDs pour les fichiers modifiés.
     * Retourne une Map de filePath -> MusicTrack mis à jour.
     */
    suspend fun scanTracksByPaths(context: Context, filePaths: List<String>): Map<String, MusicTrack> = withContext(Dispatchers.IO) {
        if (filePaths.isEmpty()) return@withContext emptyMap()

        val tracksByPath = mutableMapOf<String, MusicTrack>()
        val projection = buildProjection()

        val unknownTitle = context.getString(R.string.music_unknown_title)
        val unknownArtist = context.getString(R.string.music_unknown_artist)
        val unknownAlbum = context.getString(R.string.music_unknown_album)

        // Query par batch pour éviter les limites SQL
        val placeholders = filePaths.joinToString(",") { "?" }
        val selection = "${MediaStore.Audio.Media.DATA} IN ($placeholders)"
        val selectionArgs = filePaths.toTypedArray()

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            while (cursor.moveToNext()) {
                val filePath = cursor.getString(dataColumn)
                cursorToTrack(cursor, unknownTitle, unknownArtist, unknownAlbum)?.let { track ->
                    tracksByPath[filePath] = track
                }
            }
        }
        tracksByPath
    }

    private fun buildProjection(): Array<String> {
        val baseProjection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR
        )
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            baseProjection + MediaStore.Audio.Media.ALBUM_ARTIST
        } else {
            baseProjection
        }
    }

    private fun cursorToTrack(
        cursor: android.database.Cursor,
        unknownTitle: String,
        unknownArtist: String,
        unknownAlbum: String
    ): MusicTrack? {
        return try {
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val trackColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val yearColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val albumArtistColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ARTIST)
            } else -1

            val id = cursor.getLong(idColumn)
            val title = (cursor.getString(titleColumn) ?: unknownTitle).fixMetadataEncoding()
            val artist = (cursor.getString(artistColumn) ?: unknownArtist).fixMetadataEncoding()
            val album = (cursor.getString(albumColumn) ?: unknownAlbum).fixMetadataEncoding()
            val albumId = cursor.getLong(albumIdColumn)
            val duration = cursor.getLong(durationColumn)
            val filePath = cursor.getString(dataColumn)

            val albumArtist = if (albumArtistColumn >= 0) {
                cursor.getString(albumArtistColumn)?.takeIf { it.isNotBlank() }?.fixMetadataEncoding()
            } else null

            val trackRaw = cursor.getInt(trackColumn)
            val discNumber = if (trackRaw >= 1000) trackRaw / 1000 else null
            val trackNumber = if (trackRaw > 0) trackRaw % 1000 else null

            val yearRaw = cursor.getInt(yearColumn)
            val year = if (yearRaw > 0) yearRaw else null

            val contentUri = ContentUris.withAppendedId(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                id
            )
            val albumArtUri = ContentUris.withAppendedId(ALBUM_ART_URI, albumId)

            MusicTrack(
                id = id,
                title = title,
                artist = artist,
                album = album,
                duration = duration,
                uri = contentUri,
                albumArtUri = albumArtUri,
                filePath = filePath,
                trackNumber = trackNumber,
                discNumber = discNumber,
                year = year,
                albumArtist = albumArtist
            )
        } catch (e: Exception) {
            null
        }
    }

    @Suppress("DEPRECATION")
    suspend fun scanMusicFolder(context: Context, folderPath: String): List<MusicTrack> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<MusicTrack>()

        // Build projection - ALBUM_ARTIST only available on API 30+
        val baseProjection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,  // Chemin du fichier (deprecated mais nécessaire pour l'édition de tags)
            MediaStore.Audio.Media.TRACK, // Numéro de piste
            MediaStore.Audio.Media.YEAR   // Année de sortie
        )

        val projection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            baseProjection + MediaStore.Audio.Media.ALBUM_ARTIST
        } else {
            baseProjection
        }

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DATA} LIKE ?"
        val selectionArgs = arrayOf("$folderPath%")
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val trackColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val yearColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val albumArtistColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ARTIST)
            } else -1

            val unknownTitle = context.getString(R.string.music_unknown_title)
            val unknownArtist = context.getString(R.string.music_unknown_artist)
            val unknownAlbum = context.getString(R.string.music_unknown_album)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val title = (cursor.getString(titleColumn) ?: unknownTitle).fixMetadataEncoding()
                val artist = (cursor.getString(artistColumn) ?: unknownArtist).fixMetadataEncoding()
                val album = (cursor.getString(albumColumn) ?: unknownAlbum).fixMetadataEncoding()
                val albumId = cursor.getLong(albumIdColumn)
                val duration = cursor.getLong(durationColumn)
                val filePath = cursor.getString(dataColumn)

                // Album Artist (API 30+)
                val albumArtist = if (albumArtistColumn >= 0) {
                    cursor.getString(albumArtistColumn)?.takeIf { it.isNotBlank() }?.fixMetadataEncoding()
                } else null

                // Track number: format peut être "5" ou "5/12" (piste 5 sur 12)
                // Disc number: MediaStore stocke disc*1000 + track (ex: disque 2, piste 5 = 2005)
                val trackRaw = cursor.getInt(trackColumn)
                val discNumber = if (trackRaw >= 1000) trackRaw / 1000 else null
                val trackNumber = if (trackRaw > 0) trackRaw % 1000 else null

                val yearRaw = cursor.getInt(yearColumn)
                val year = if (yearRaw > 0) yearRaw else null

                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                val albumArtUri = ContentUris.withAppendedId(ALBUM_ART_URI, albumId)

                tracks.add(
                    MusicTrack(
                        id = id,
                        title = title,
                        artist = artist,
                        album = album,
                        duration = duration,
                        uri = contentUri,
                        albumArtUri = albumArtUri,
                        filePath = filePath,
                        trackNumber = trackNumber,
                        discNumber = discNumber,
                        year = year,
                        albumArtist = albumArtist
                    )
                )
            }
        }

        tracks
    }

    /**
     * Corrige les problèmes d'encodage courants dans les métadonnées ID3 lues via MediaStore.
     *
     * Trois cas traités :
     * 1. UTF-8 mal décodé comme ISO-8859-1 strict (mojibake "â\u0080\u0099" au lieu de "'")
     *    → Détecté par re-décodage strict UTF-8 après conversion en bytes ISO-8859-1.
     * 2. UTF-8 mal décodé comme Windows-1252 (mojibake "â€™" où € = U+20AC et ™ = U+2122)
     *    → Le mapping Win1252 C1 est inversé (€→U+0080, ™→U+0099) avant re-décodage UTF-8.
     * 3. Caractères Windows-1252 dans la plage C1 (U+0080–U+009F) lus comme ISO-8859-1
     *    (cas typique de l'apostrophe courbe 0x92 qui devient U+0092 au lieu de U+2019)
     *    → Remplacés par leurs équivalents Unicode corrects.
     */
    private fun String.fixMetadataEncoding(): String {
        val s = tryDecodeAsUtf8() ?: this
        return s.fixWin1252C1()
    }

    /**
     * Expose la correction d'encodage pour les métadonnées lues hors MediaStore
     * (ex. MediaMetadataRetriever sur URI externe).
     */
    fun fixEncoding(text: String): String = text.fixMetadataEncoding()

    private fun String.tryDecodeAsUtf8(): String? {
        // Cas 1 : MediaStore a utilisé ISO-8859-1 strict (C1 = U+0080–U+009F)
        // Tous les chars sont dans U+00FF → conversion ISO-8859-1 exacte, pas de perte.
        if (all { it.code <= 0xFF }) {
            return try {
                val bytes = toByteArray(Charsets.ISO_8859_1)
                val decoder = Charsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                val decoded = decoder.decode(ByteBuffer.wrap(bytes)).toString()
                decoded.takeIf { it != this && it.any { c -> c.code > 127 } }
            } catch (_: java.nio.charset.CharacterCodingException) {
                null
            }
        }

        // Cas 2 : MediaStore a utilisé Windows-1252 (C1 = U+20AC, U+2019, U+2122…)
        // On inverse le mapping Win1252 pour retrouver les octets C1 bruts (U+0080–U+009F),
        // puis on tente le re-décodage UTF-8.
        if (any { win1252ReverseMap.containsKey(it) }) {
            val normalized = map { win1252ReverseMap[it] ?: it }.joinToString("")
            if (normalized != this && normalized.all { it.code <= 0xFF }) {
                return try {
                    val bytes = normalized.toByteArray(Charsets.ISO_8859_1)
                    val decoder = Charsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                    val decoded = decoder.decode(ByteBuffer.wrap(bytes)).toString()
                    decoded.takeIf { it != this && it.any { c -> c.code > 127 } }
                } catch (_: java.nio.charset.CharacterCodingException) {
                    null
                }
            }
        }

        return null
    }

    // Mapping Windows-1252 → Unicode pour la plage de contrôle C1 (0x80–0x9F)
    // Ces octets sont des caractères imprimables en Windows-1252 mais des contrôles en ISO-8859-1
    private val win1252C1Map = mapOf(
        '\u0080' to '\u20AC', // €
        '\u0082' to '\u201A', // ‚
        '\u0083' to '\u0192', // ƒ
        '\u0084' to '\u201E', // „
        '\u0085' to '\u2026', // …
        '\u0086' to '\u2020', // †
        '\u0087' to '\u2021', // ‡
        '\u0088' to '\u02C6', // ˆ
        '\u0089' to '\u2030', // ‰
        '\u008A' to '\u0160', // Š
        '\u008B' to '\u2039', // ‹
        '\u008C' to '\u0152', // Œ
        '\u008E' to '\u017D', // Ž
        '\u0091' to '\u2018', // ' (guillemet simple ouvrant)
        '\u0092' to '\u2019', // ' (apostrophe courbe / guillemet simple fermant)
        '\u0093' to '\u201C', // " (guillemet double ouvrant)
        '\u0094' to '\u201D', // " (guillemet double fermant)
        '\u0095' to '\u2022', // • (puce)
        '\u0096' to '\u2013', // – (demi-cadratin)
        '\u0097' to '\u2014', // — (cadratin)
        '\u0098' to '\u02DC', // ˜
        '\u0099' to '\u2122', // ™
        '\u009A' to '\u0161', // š
        '\u009B' to '\u203A', // ›
        '\u009C' to '\u0153', // œ
        '\u009E' to '\u017E', // ž
        '\u009F' to '\u0178', // Ÿ
    )

    // Mapping inverse : Unicode Windows-1252 → octet C1 brut (U+0080–U+009F)
    // Utilisé pour reconvertir les chars Win1252 en bytes C1 avant re-décodage UTF-8
    private val win1252ReverseMap: Map<Char, Char> by lazy {
        win1252C1Map.entries.associate { (k, v) -> v to k }
    }

    private fun String.fixWin1252C1(): String {
        if (none { it.code in 0x80..0x9F }) return this
        return map { win1252C1Map[it] ?: it }.joinToString("")
    }
}
