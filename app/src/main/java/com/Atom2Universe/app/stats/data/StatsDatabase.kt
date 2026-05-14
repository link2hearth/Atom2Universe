package com.Atom2Universe.app.stats.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        UsageSessionEntity::class,
        DailySummaryEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class StatsDatabase : RoomDatabase() {

    abstract fun usageSessionDao(): UsageSessionDao
    abstract fun dailySummaryDao(): DailySummaryDao

    companion object {
        @Volatile
        private var INSTANCE: StatsDatabase? = null
        private const val DATABASE_NAME = "stats_database"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `daily_summaries` (
                        `dateEpochDay` INTEGER NOT NULL,
                        `year` INTEGER NOT NULL,
                        `month` INTEGER NOT NULL,
                        `dayOfMonth` INTEGER NOT NULL,
                        `totalDurationMs` INTEGER NOT NULL,
                        `musicDurationMs` INTEGER NOT NULL,
                        `midiDurationMs` INTEGER NOT NULL,
                        `radioDurationMs` INTEGER NOT NULL,
                        `sessionCount` INTEGER NOT NULL,
                        `musicDetailsJson` TEXT,
                        `midiDetailsJson` TEXT,
                        `radioDetailsJson` TEXT,
                        `lastUpdatedMs` INTEGER NOT NULL,
                        PRIMARY KEY(`dateEpochDay`)
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_summaries_year_month` ON `daily_summaries` (`year`, `month`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_daily_summaries_dateEpochDay` ON `daily_summaries` (`dateEpochDay`)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Supprimer les doublons existants causés par le bug de sync (garder le plus ancien id par groupe)
                db.execSQL("""
                    DELETE FROM usage_sessions WHERE id NOT IN (
                        SELECT MIN(id) FROM usage_sessions
                        GROUP BY moduleType, startTimestamp, endTimestamp
                    )
                """)

                // Ajouter la colonne sourceDeviceId (null = session locale, non-null = importée d'un autre appareil)
                db.execSQL("ALTER TABLE usage_sessions ADD COLUMN sourceDeviceId TEXT")

                // Ajouter la contrainte unique pour éviter les doublons futurs
                db.execSQL("""
                    CREATE UNIQUE INDEX IF NOT EXISTS `index_usage_sessions_module_start_end`
                    ON `usage_sessions` (`moduleType`, `startTimestamp`, `endTimestamp`)
                """)
            }
        }

        fun getInstance(context: Context): StatsDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    StatsDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()

                INSTANCE = instance
                instance
            }
        }

        @androidx.annotation.VisibleForTesting
        fun destroyInstance() {
            INSTANCE = null
        }
    }
}
