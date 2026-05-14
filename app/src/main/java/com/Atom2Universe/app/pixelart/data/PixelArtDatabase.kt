package com.Atom2Universe.app.pixelart.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Base de données Room pour les modules graphiques
 *
 * Contient:
 * - PixelArtProject: Métadonnées des projets pixel art
 * - PixelArtFrame: Données des frames pixel art (pixels en Base64)
 */
@Database(
    entities = [
        PixelArtProject::class,
        PixelArtFrame::class,
        CanvasProject::class
    ],
    version = 6,
    exportSchema = false
)
abstract class PixelArtDatabase : RoomDatabase() {

    /**
     * DAO pour les opérations sur les projets et frames Pixel Art
     */
    abstract fun pixelArtDao(): PixelArtDao

    /**
     * DAO pour les opérations sur les projets Canvas (Toile Infinie)
     */
    abstract fun canvasDao(): CanvasDao

    companion object {
        /**
         * Instance singleton de la database
         */
        @Volatile
        private var INSTANCE: PixelArtDatabase? = null

        /**
         * Chemin actuel de la database (pour détecter les changements)
         */
        @Volatile
        private var currentDbPath: String? = null

        /**
         * Nom de la database sur le disque
         */
        private const val DATABASE_NAME = "pixelart_db"

        /**
         * Migration v1 -> v2: Ajout de la table canvas_projects
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS canvas_projects (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL DEFAULT 'Canvas',
                        viewportX REAL NOT NULL DEFAULT 0,
                        viewportY REAL NOT NULL DEFAULT 0,
                        viewportZoom REAL NOT NULL DEFAULT 1,
                        objectsJson TEXT NOT NULL DEFAULT '[]',
                        dateCreated INTEGER NOT NULL DEFAULT 0,
                        dateModified INTEGER NOT NULL DEFAULT 0,
                        version INTEGER NOT NULL DEFAULT 1
                    )
                """)
            }
        }

        /**
         * Migration v2 -> v3: Ajout de brushSize à canvas_projects
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    ALTER TABLE canvas_projects ADD COLUMN brushSize REAL NOT NULL DEFAULT 12
                """)
            }
        }

        /**
         * Migration v3 -> v4: Ajout du systeme multi-layer (historique)
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Migration historique - les tables canvas ont été supprimées en v5
            }
        }

        /**
         * Migration v4 -> v5: Suppression du module InfiniteCanvas
         * - Suppression de la table canvas_layers
         * - Suppression de la table canvas_projects
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Supprimer les tables du module InfiniteCanvas
                database.execSQL("DROP TABLE IF EXISTS canvas_layers")
                database.execSQL("DROP TABLE IF EXISTS canvas_projects")
            }
        }

        /**
         * Migration v5 -> v6: Réintégration du module InfiniteCanvas
         * - Recréation de la table canvas_projects
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS canvas_projects (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL DEFAULT 'Canvas',
                        viewportX REAL NOT NULL DEFAULT 0,
                        viewportY REAL NOT NULL DEFAULT 0,
                        viewportZoom REAL NOT NULL DEFAULT 1,
                        objectsJson TEXT NOT NULL DEFAULT '[]',
                        dateCreated INTEGER NOT NULL DEFAULT 0,
                        dateModified INTEGER NOT NULL DEFAULT 0,
                        version INTEGER NOT NULL DEFAULT 1
                    )
                """)
            }
        }

        /**
         * Récupère (ou crée) l'instance singleton de la database
         * Utilise le stockage interne par défaut
         *
         * @param context Context de l'application
         * @return Instance de PixelArtDatabase
         */
        fun getInstance(context: Context): PixelArtDatabase {
            return getInstanceWithPath(context, null)
        }

        /**
         * Récupère (ou crée) l'instance singleton avec un chemin personnalisé
         * Permet de stocker la database sur carte SD ou stockage externe
         *
         * @param context Context de l'application
         * @param customDbPath Chemin complet de la database (null = stockage interne par défaut)
         * @return Instance de PixelArtDatabase
         */
        fun getInstanceWithPath(context: Context, customDbPath: String?): PixelArtDatabase {
            val dbPath = customDbPath ?: DATABASE_NAME

            // Si le chemin a changé, fermer l'ancienne instance
            if (INSTANCE != null && currentDbPath != dbPath) {
                synchronized(this) {
                    INSTANCE?.close()
                    INSTANCE = null
                    currentDbPath = null
                }
            }

            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PixelArtDatabase::class.java,
                    dbPath
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .build()

                currentDbPath = dbPath
                INSTANCE = instance
                instance
            }
        }

        /**
         * Retourne le chemin actuel de la database
         */
        fun getCurrentDbPath(): String? = currentDbPath

        /**
         * Détruit l'instance de la database (utile pour tests)
         */
        @androidx.annotation.VisibleForTesting
        fun destroyInstance() {
            INSTANCE = null
        }

        /**
         * Ferme la database et invalide l'instance singleton.
         * Doit être appelé avant de remplacer les fichiers de la database.
         * Après cet appel, getInstance() créera une nouvelle instance.
         */
        fun closeAndInvalidate() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
                currentDbPath = null
            }
        }
    }
}
