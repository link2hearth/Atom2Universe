package com.Atom2Universe.app.crypto.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        MainClickerImageEntity::class,
        MainClickerShuffleEntryEntity::class,
        MainClickerHistoryEntryEntity::class,
        MainClickerStateEntity::class,
        MainClickerFavoriteEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class MainClickerDatabase : RoomDatabase() {

    abstract fun cryptoBackgroundDao(): MainClickerDao

    companion object {
        @Volatile
        private var INSTANCE: MainClickerDatabase? = null
        private const val DATABASE_NAME = "crypto_background_database"

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `crypto_background_favorites` (`uriString` TEXT NOT NULL, `addedAt` INTEGER NOT NULL, PRIMARY KEY(`uriString`))"
                )
            }
        }

        fun getInstance(context: Context): MainClickerDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MainClickerDatabase::class.java,
                    DATABASE_NAME
                ).addMigrations(MIGRATION_1_2).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
