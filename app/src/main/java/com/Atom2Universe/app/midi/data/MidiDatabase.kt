package com.Atom2Universe.app.midi.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Database Room pour le lecteur MIDI
 *
 * Contient:
 * - MidiTrack: Bibliothèque de fichiers MIDI scannés
 * - Playlist: Playlists créées par l'utilisateur
 * - PlaylistTrack: Relation many-to-many entre playlists et tracks
 * - AppSettings: Paramètres de l'app (SoundFont path, etc.)
 * - PracticeSessionResult: Historique des sessions de pratique
 */
@Database(
    entities = [
        MidiTrack::class,
        Playlist::class,
        PlaylistTrack::class,
        AppSettings::class,
        PracticeSessionResult::class
    ],
    version = 3,
    exportSchema = false
)
abstract class MidiDatabase : RoomDatabase() {

    /**
     * DAO pour les operations sur les tracks MIDI
     */
    abstract fun midiTrackDao(): MidiTrackDao

    /**
     * DAO pour les opérations sur les playlists
     */
    abstract fun playlistDao(): PlaylistDao

    /**
     * DAO pour les paramètres de l'application
     */
    abstract fun settingsDao(): SettingsDao

    /**
     * DAO pour l'historique des sessions de pratique
     */
    abstract fun practiceSessionDao(): PracticeSessionDao

    companion object {
        /**
         * Instance singleton de la database
         */
        @Volatile
        private var INSTANCE: MidiDatabase? = null

        /**
         * Nom de la database sur le disque
         */
        private const val DATABASE_NAME = "midi_player_db"

        /**
         * Migration from version 1 to 2: Add practice_sessions table
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS practice_sessions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        timestampMs INTEGER NOT NULL,
                        trackFilePath TEXT NOT NULL,
                        trackTitle TEXT NOT NULL,
                        channelNumber INTEGER NOT NULL,
                        instrumentName TEXT NOT NULL,
                        grade TEXT NOT NULL,
                        score INTEGER NOT NULL,
                        accuracy REAL NOT NULL,
                        perfectNotes INTEGER NOT NULL,
                        goodNotes INTEGER NOT NULL,
                        missedNotes INTEGER NOT NULL,
                        wrongNotes INTEGER NOT NULL,
                        bestStreak INTEGER NOT NULL,
                        totalExpectedNotes INTEGER NOT NULL,
                        maxComboReached INTEGER NOT NULL DEFAULT 1,
                        playbackSpeed REAL NOT NULL DEFAULT 1.0,
                        sessionDurationMs INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }

        /**
         * Migration from version 2 to 3: Add indices on midi_tracks for performance
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Ajouter les indices pour améliorer les performances des requêtes
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_midi_tracks_filePath ON midi_tracks (filePath)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_midi_tracks_artist ON midi_tracks (artist)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_midi_tracks_album ON midi_tracks (album)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_midi_tracks_title ON midi_tracks (title)")
            }
        }

        /**
         * Récupère (ou crée) l'instance singleton de la database
         *
         * @param context Context de l'application
         * @return Instance de MidiDatabase
         */
        fun getInstance(context: Context): MidiDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MidiDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()

                INSTANCE = instance
                instance
            }
        }

        /**
         * Détruit l'instance de la database (utile pour tests)
         * NE PAS UTILISER en production!
         */
        @androidx.annotation.VisibleForTesting
        @Suppress("unused")
        fun destroyInstance() {
            INSTANCE = null
        }

    }
}
