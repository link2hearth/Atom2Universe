package com.Atom2Universe.app.music.data

import android.content.Context
import android.os.Build
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.UUID

import com.Atom2Universe.app.music.lyrics.data.LyricsEntity
import com.Atom2Universe.app.music.lyrics.data.LyricsDao
import com.Atom2Universe.app.music.lyrics.data.PendingLyricsUpdate
import com.Atom2Universe.app.music.lyrics.data.PendingLyricsUpdateDao
import com.Atom2Universe.app.music.sync.data.SyncPlayCountDelta
import com.Atom2Universe.app.music.sync.data.SyncPlayCountDeltaDao
import com.Atom2Universe.app.music.sync.data.SyncMetadata
import com.Atom2Universe.app.music.sync.data.SyncMetadataDao
import com.Atom2Universe.app.music.equalizer.data.EqPreset
import com.Atom2Universe.app.music.equalizer.data.EqPresetDao
import com.Atom2Universe.app.music.equalizer.data.EqTrackOverride
import com.Atom2Universe.app.music.equalizer.data.EqAlbumOverride
import com.Atom2Universe.app.music.equalizer.data.EqArtistOverride
import com.Atom2Universe.app.music.equalizer.data.EqOverrideDao
import com.Atom2Universe.app.music.equalizer.data.EqSettings
import com.Atom2Universe.app.music.equalizer.data.EqSettingsDao

/**
 * Base de données Room pour le module musique.
 *
 * Tables:
 * - pending_popm_updates: Modifications POPM en attente d'écriture
 * - play_counts: Compteurs d'écoutes (source de vérité)
 * - lyrics_cache: Cache des paroles récupérées
 * - pending_lyrics_updates: Écritures USLT en attente
 * - sync_play_count_deltas: Deltas de lecture pour sync cloud
 * - sync_metadata: Etat de la synchronisation cloud
 * - cached_tracks: Cache des pistes scannées pour chargement instantané
 * - pending_tag_edits: Éditions de tags en attente (quand fichier en lecture)
 */
@Database(
    entities = [
        PendingPopmUpdate::class,
        PlayCountEntry::class,
        LyricsEntity::class,
        PendingLyricsUpdate::class,
        SyncPlayCountDelta::class,
        SyncMetadata::class,
        CachedTrackEntity::class,
        PendingTagEdit::class,
        EqPreset::class,
        EqTrackOverride::class,
        EqAlbumOverride::class,
        EqArtistOverride::class,
        EqSettings::class,
        ListenEvent::class,
        AlbumTrackCheckEntity::class
    ],
    version = 15,
    exportSchema = false
)
abstract class MusicDatabase : RoomDatabase() {

    abstract fun pendingPopmUpdateDao(): PendingPopmUpdateDao
    abstract fun playCountDao(): PlayCountDao
    abstract fun lyricsDao(): LyricsDao
    abstract fun pendingLyricsUpdateDao(): PendingLyricsUpdateDao
    abstract fun syncPlayCountDeltaDao(): SyncPlayCountDeltaDao
    abstract fun syncMetadataDao(): SyncMetadataDao
    abstract fun cachedTrackDao(): CachedTrackDao
    abstract fun pendingTagEditDao(): PendingTagEditDao
    abstract fun eqPresetDao(): EqPresetDao
    abstract fun eqOverrideDao(): EqOverrideDao
    abstract fun eqSettingsDao(): EqSettingsDao
    abstract fun listenEventDao(): ListenEventDao
    abstract fun albumTrackCheckDao(): AlbumTrackCheckDao

    companion object {
        @Volatile
        private var INSTANCE: MusicDatabase? = null
        private const val DATABASE_NAME = "music_database"

        /**
         * Migration de la version 1 vers 2: Ajout de la table play_counts
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Créer la table play_counts
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS play_counts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        metadataKey TEXT NOT NULL,
                        title TEXT NOT NULL,
                        artist TEXT NOT NULL,
                        album TEXT NOT NULL,
                        playCount INTEGER NOT NULL DEFAULT 0,
                        lastPlayed INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL DEFAULT 0,
                        updatedAt INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())

                // Créer l'index unique sur metadataKey
                db.execSQL("""
                    CREATE UNIQUE INDEX IF NOT EXISTS index_play_counts_metadataKey
                    ON play_counts (metadataKey)
                """.trimIndent())
            }
        }

        /**
         * Migration de la version 2 vers 3: Ajout des tables lyrics_cache et pending_lyrics_updates
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Créer la table lyrics_cache
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS lyrics_cache (
                        metadataKey TEXT PRIMARY KEY NOT NULL,
                        trackId INTEGER NOT NULL,
                        lyrics TEXT NOT NULL,
                        source TEXT NOT NULL,
                        language TEXT,
                        isSynced INTEGER NOT NULL DEFAULT 0,
                        fetchedAt INTEGER NOT NULL,
                        lastModified INTEGER NOT NULL,
                        isSyncedToFile INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())

                // Créer la table pending_lyrics_updates
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS pending_lyrics_updates (
                        filePath TEXT PRIMARY KEY NOT NULL,
                        trackId INTEGER NOT NULL,
                        lyrics TEXT NOT NULL,
                        language TEXT NOT NULL DEFAULT 'eng',
                        description TEXT NOT NULL DEFAULT '',
                        createdAt INTEGER NOT NULL,
                        lastAttempt INTEGER NOT NULL DEFAULT 0,
                        attemptCount INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }

        /**
         * Migration de la version 3 vers 4: Ajout des tables pour la sync cloud
         * - sync_play_count_deltas: Deltas de lecture depuis dernière sync
         * - sync_metadata: État de la sync (device ID, timestamps, etc.)
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Créer la table sync_play_count_deltas
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS sync_play_count_deltas (
                        metadataKey TEXT PRIMARY KEY NOT NULL,
                        title TEXT NOT NULL,
                        artist TEXT NOT NULL,
                        album TEXT NOT NULL,
                        delta INTEGER NOT NULL DEFAULT 0,
                        updatedAt INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE UNIQUE INDEX IF NOT EXISTS index_sync_play_count_deltas_metadataKey
                    ON sync_play_count_deltas (metadataKey)
                """.trimIndent())

                // Créer la table sync_metadata
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS sync_metadata (
                        id INTEGER PRIMARY KEY NOT NULL DEFAULT 1,
                        deviceId TEXT NOT NULL,
                        deviceName TEXT NOT NULL,
                        lastSyncTimestamp INTEGER NOT NULL DEFAULT 0,
                        lastUploadedDeltaDate TEXT,
                        syncEnabled INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())

                // Générer et insérer l'ID unique de l'appareil
                val deviceId = UUID.randomUUID().toString()
                val deviceName = Build.MODEL ?: "Android Device"
                db.execSQL("""
                    INSERT INTO sync_metadata (id, deviceId, deviceName, lastSyncTimestamp, syncEnabled)
                    VALUES (1, '$deviceId', '$deviceName', 0, 0)
                """.trimIndent())
            }
        }

        /**
         * Migration de la version 4 vers 5: Ajout de earnedPlayCount à play_counts
         *
         * Ce champ stocke uniquement les écoutes faites SUR CET APPAREIL.
         * Il exclut les imports depuis POPM (WMP, iTunes, etc.) et les syncs cloud.
         * Cela évite le doublement des compteurs quand un MP3 avec POPM est copié.
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    ALTER TABLE play_counts
                    ADD COLUMN earnedPlayCount INTEGER NOT NULL DEFAULT 0
                """.trimIndent())
            }
        }

        /**
         * Migration de la version 5 vers 6: Ajout de baselineDeltasCreated à sync_metadata
         *
         * Ce flag empêche la recréation multiple des baseline deltas à chaque sync,
         * ce qui causait une multiplication des compteurs d'écoutes (bug x5).
         * On initialise à 1 si lastSyncTimestamp > 0, car cela signifie qu'une sync
         * a déjà été effectuée (et donc les baseline deltas ont été créés).
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    ALTER TABLE sync_metadata
                    ADD COLUMN baselineDeltasCreated INTEGER NOT NULL DEFAULT 0
                """.trimIndent())

                // Si une sync a déjà eu lieu, marquer les baseline deltas comme déjà créés
                // pour éviter de les recréer lors de la prochaine sync
                db.execSQL("""
                    UPDATE sync_metadata
                    SET baselineDeltasCreated = 1
                    WHERE lastSyncTimestamp > 0
                """.trimIndent())
            }
        }

        /**
         * Migration de la version 6 vers 7: Ajout de la table cached_tracks
         *
         * Cette table met en cache les pistes scannées pour un chargement instantané
         * de la bibliothèque musicale sans avoir à rescanner MediaStore à chaque lancement.
         */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS cached_tracks (
                        id INTEGER PRIMARY KEY NOT NULL,
                        title TEXT NOT NULL,
                        artist TEXT NOT NULL,
                        album TEXT NOT NULL,
                        duration INTEGER NOT NULL,
                        uri TEXT NOT NULL,
                        albumArtUri TEXT,
                        filePath TEXT,
                        trackNumber INTEGER,
                        year INTEGER,
                        albumArtist TEXT,
                        albumId INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }

        /**
         * Migration de la version 7 vers 8: Ajout de la table pending_tag_edits
         *
         * Cette table stocke les éditions de tags en attente quand un fichier
         * est en cours de lecture. L'édition sera appliquée dès que le fichier
         * n'est plus en lecture ou au redémarrage de l'app.
         */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS pending_tag_edits (
                        filePath TEXT PRIMARY KEY NOT NULL,
                        title TEXT,
                        artist TEXT,
                        albumArtist TEXT,
                        album TEXT,
                        year TEXT,
                        trackNumber TEXT,
                        discNumber TEXT,
                        coverArtPath TEXT,
                        createdAt INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }

        /**
         * Migration de la version 8 vers 9: Ajout des tables pour l'égaliseur
         *
         * Tables:
         * - eq_presets: Presets d'égaliseur (système + utilisateur)
         * - eq_track_overrides: Presets personnalisés par piste
         * - eq_album_overrides: Presets personnalisés par album
         * - eq_artist_overrides: Presets personnalisés par artiste
         */
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Table des presets EQ
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS eq_presets (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        isSystemPreset INTEGER NOT NULL DEFAULT 0,
                        band32Hz INTEGER NOT NULL DEFAULT 0,
                        band64Hz INTEGER NOT NULL DEFAULT 0,
                        band125Hz INTEGER NOT NULL DEFAULT 0,
                        band250Hz INTEGER NOT NULL DEFAULT 0,
                        band500Hz INTEGER NOT NULL DEFAULT 0,
                        band1kHz INTEGER NOT NULL DEFAULT 0,
                        band2kHz INTEGER NOT NULL DEFAULT 0,
                        band4kHz INTEGER NOT NULL DEFAULT 0,
                        band8kHz INTEGER NOT NULL DEFAULT 0,
                        band16kHz INTEGER NOT NULL DEFAULT 0,
                        bassBoostStrength INTEGER NOT NULL DEFAULT 0,
                        virtualizerStrength INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL
                    )
                """.trimIndent())

                // Table des overrides par piste
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS eq_track_overrides (
                        trackId INTEGER PRIMARY KEY NOT NULL,
                        presetId INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        FOREIGN KEY (presetId) REFERENCES eq_presets(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_eq_track_overrides_presetId ON eq_track_overrides(presetId)")

                // Table des overrides par album
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS eq_album_overrides (
                        albumKey TEXT PRIMARY KEY NOT NULL,
                        presetId INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        FOREIGN KEY (presetId) REFERENCES eq_presets(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_eq_album_overrides_presetId ON eq_album_overrides(presetId)")

                // Table des overrides par artiste
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS eq_artist_overrides (
                        artistKey TEXT PRIMARY KEY NOT NULL,
                        presetId INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        FOREIGN KEY (presetId) REFERENCES eq_presets(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_eq_artist_overrides_presetId ON eq_artist_overrides(presetId)")

                // Insérer les presets système par défaut
                val now = System.currentTimeMillis()

                // Flat (neutre)
                db.execSQL("""
                    INSERT INTO eq_presets (name, isSystemPreset, band32Hz, band64Hz, band125Hz, band250Hz, band500Hz, band1kHz, band2kHz, band4kHz, band8kHz, band16kHz, bassBoostStrength, virtualizerStrength, createdAt)
                    VALUES ('Flat', 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, $now)
                """.trimIndent())

                // Rock
                db.execSQL("""
                    INSERT INTO eq_presets (name, isSystemPreset, band32Hz, band64Hz, band125Hz, band250Hz, band500Hz, band1kHz, band2kHz, band4kHz, band8kHz, band16kHz, bassBoostStrength, virtualizerStrength, createdAt)
                    VALUES ('Rock', 1, 400, 300, 200, 0, -100, 0, 200, 400, 500, 400, 0, 0, $now)
                """.trimIndent())

                // Pop
                db.execSQL("""
                    INSERT INTO eq_presets (name, isSystemPreset, band32Hz, band64Hz, band125Hz, band250Hz, band500Hz, band1kHz, band2kHz, band4kHz, band8kHz, band16kHz, bassBoostStrength, virtualizerStrength, createdAt)
                    VALUES ('Pop', 1, 200, 300, 200, 100, 0, 100, 200, 300, 200, 100, 0, 0, $now)
                """.trimIndent())

                // Jazz
                db.execSQL("""
                    INSERT INTO eq_presets (name, isSystemPreset, band32Hz, band64Hz, band125Hz, band250Hz, band500Hz, band1kHz, band2kHz, band4kHz, band8kHz, band16kHz, bassBoostStrength, virtualizerStrength, createdAt)
                    VALUES ('Jazz', 1, 300, 200, 100, 200, 0, 100, 0, 100, 200, 300, 0, 0, $now)
                """.trimIndent())

                // Classical
                db.execSQL("""
                    INSERT INTO eq_presets (name, isSystemPreset, band32Hz, band64Hz, band125Hz, band250Hz, band500Hz, band1kHz, band2kHz, band4kHz, band8kHz, band16kHz, bassBoostStrength, virtualizerStrength, createdAt)
                    VALUES ('Classical', 1, 0, 0, 0, 0, 0, 0, -100, 100, 200, 300, 0, 0, $now)
                """.trimIndent())

                // Electronic
                db.execSQL("""
                    INSERT INTO eq_presets (name, isSystemPreset, band32Hz, band64Hz, band125Hz, band250Hz, band500Hz, band1kHz, band2kHz, band4kHz, band8kHz, band16kHz, bassBoostStrength, virtualizerStrength, createdAt)
                    VALUES ('Electronic', 1, 600, 500, 400, 200, 0, -100, 0, 200, 400, 500, 0, 0, $now)
                """.trimIndent())

                // Bass Boost
                db.execSQL("""
                    INSERT INTO eq_presets (name, isSystemPreset, band32Hz, band64Hz, band125Hz, band250Hz, band500Hz, band1kHz, band2kHz, band4kHz, band8kHz, band16kHz, bassBoostStrength, virtualizerStrength, createdAt)
                    VALUES ('Bass Boost', 1, 800, 600, 400, 200, 0, 0, 0, 0, 0, 0, 500, 0, $now)
                """.trimIndent())

                // Treble Boost
                db.execSQL("""
                    INSERT INTO eq_presets (name, isSystemPreset, band32Hz, band64Hz, band125Hz, band250Hz, band500Hz, band1kHz, band2kHz, band4kHz, band8kHz, band16kHz, bassBoostStrength, virtualizerStrength, createdAt)
                    VALUES ('Treble Boost', 1, 0, 0, 0, 0, 0, 100, 200, 400, 600, 700, 0, 0, $now)
                """.trimIndent())

                // Vocal
                db.execSQL("""
                    INSERT INTO eq_presets (name, isSystemPreset, band32Hz, band64Hz, band125Hz, band250Hz, band500Hz, band1kHz, band2kHz, band4kHz, band8kHz, band16kHz, bassBoostStrength, virtualizerStrength, createdAt)
                    VALUES ('Vocal', 1, -200, -100, 0, 200, 400, 400, 300, 200, 0, -100, 0, 0, $now)
                """.trimIndent())

                // Late Night (réduit les extrêmes pour écoute calme)
                db.execSQL("""
                    INSERT INTO eq_presets (name, isSystemPreset, band32Hz, band64Hz, band125Hz, band250Hz, band500Hz, band1kHz, band2kHz, band4kHz, band8kHz, band16kHz, bassBoostStrength, virtualizerStrength, createdAt)
                    VALUES ('Late Night', 1, -300, -100, 100, 200, 300, 300, 200, 100, -100, -300, 0, 0, $now)
                """.trimIndent())
            }
        }

        /**
         * Migration 9 -> 10: Add eq_settings table for storing global EQ settings
         */
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create eq_settings table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS eq_settings (
                        id INTEGER PRIMARY KEY NOT NULL,
                        isEnabled INTEGER NOT NULL DEFAULT 1,
                        globalPresetId INTEGER NOT NULL DEFAULT 1
                    )
                """.trimIndent())

                // Insert default settings (enabled, Flat preset)
                db.execSQL("""
                    INSERT INTO eq_settings (id, isEnabled, globalPresetId)
                    VALUES (1, 1, 1)
                """.trimIndent())
            }
        }

        /**
         * Migration 10 -> 11: Add missing indexes on play_counts table
         */
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add index on playCount for ORDER BY queries
                db.execSQL("CREATE INDEX IF NOT EXISTS index_play_counts_playCount ON play_counts(playCount)")
                // Add index on earnedPlayCount for local play count queries
                db.execSQL("CREATE INDEX IF NOT EXISTS index_play_counts_earnedPlayCount ON play_counts(earnedPlayCount)")
            }
        }

        /**
         * Migration 11 -> 12: Add discNumber column to cached_tracks table
         *
         * Le numéro de disque permet de trier correctement les albums multi-disques
         * (ex: disque 1/2 et 2/2) en affichant toutes les pistes du disque 1 d'abord,
         * puis celles du disque 2.
         */
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    ALTER TABLE cached_tracks
                    ADD COLUMN discNumber INTEGER
                """.trimIndent())
            }
        }

        /**
         * Migration 12 -> 13: Ajout du marqueur noLyricsFound dans lyrics_cache
         *
         * Ce flag interne (jamais écrit dans les tags du fichier) indique que les APIs
         * configurées n'ont retourné aucun résultat pour ce titre. Il évite des appels
         * réseau répétés pour de la musique instrumentale ou introuvable.
         * Le marqueur est remplacé automatiquement si l'utilisateur sauvegarde des paroles.
         */
        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    ALTER TABLE lyrics_cache
                    ADD COLUMN noLyricsFound INTEGER NOT NULL DEFAULT 0
                """.trimIndent())
            }
        }

        /**
         * Migration 13 -> 14 : Ajout de la table listen_events (journal immuable d'écoutes).
         * Remplace sync_play_count_deltas comme mécanisme de sync ; les données
         * existantes de earnedPlayCount sont migrées dans MusicPlayCountManager.init().
         */
        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS listen_events (
                        uuid TEXT PRIMARY KEY NOT NULL,
                        trackKey TEXT NOT NULL,
                        deviceId TEXT NOT NULL,
                        listenedAt INTEGER NOT NULL,
                        durationListenedMs INTEGER NOT NULL,
                        trackDurationMs INTEGER NOT NULL,
                        title TEXT NOT NULL,
                        artist TEXT NOT NULL,
                        album TEXT NOT NULL,
                        isMigrated INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_listen_events_trackKey ON listen_events(trackKey)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_listen_events_deviceId ON listen_events(deviceId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_listen_events_listenedAt ON listen_events(listenedAt)")
            }
        }

        /**
         * Migration 14 -> 15 : Ajout de la table album_track_number_checks.
         *
         * Remplace l'ancien StringSet `albums_track_number_checked` des
         * SharedPreferences (réécrit en entier à chaque album et qui grossissait
         * avec la bibliothèque). Les données existantes sont importées une seule
         * fois par AlbumTrackCheckStore au premier accès.
         */
        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS album_track_number_checks (
                        albumKey TEXT PRIMARY KEY NOT NULL
                    )
                """.trimIndent())
            }
        }

        fun getInstance(context: Context): MusicDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MusicDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
