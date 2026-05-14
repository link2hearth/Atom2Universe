package com.Atom2Universe.app.crypto.clicker

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(tableName = "clicker_state")
data class ClickerStateEntity(
    @PrimaryKey val id: Int = 0,
    val atomsSign: Int = 0,
    val atomsLayer: Int = 0,
    val atomsMantissa: Double = 0.0,
    val atomsExponent: Double = 0.0,
    val atomsValue: Double = 0.0,
    val lifetimeSign: Int = 0,
    val lifetimeLayer: Int = 0,
    val lifetimeMantissa: Double = 0.0,
    val lifetimeExponent: Double = 0.0,
    val lifetimeValue: Double = 0.0,
    val perClickSign: Int = 1,
    val perClickLayer: Int = 0,
    val perClickMantissa: Double = 1.0,
    val perClickExponent: Double = 0.0,
    val perClickValue: Double = 0.0,
    val perSecondSign: Int = 0,
    val perSecondLayer: Int = 0,
    val perSecondMantissa: Double = 0.0,
    val perSecondExponent: Double = 0.0,
    val perSecondValue: Double = 0.0,
    val godFingerLevel: Int = 0,
    val starCoreLevel: Int = 0,
    val neutrinosCount: Int = 0,
    val apcToApsLevel: Int = 0,
    val apsToApcLevel: Int = 0
)

@Entity(tableName = "gacha_tickets")
data class GachaTicketStateEntity(
    @PrimaryKey val id: Int = 0,
    val totalTickets: Int = 10,
    val lastTicketAwardMs: Long = 0L
)

@Dao
interface ClickerDao {
    @Query("SELECT * FROM clicker_state WHERE id = 0")
    suspend fun load(): ClickerStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(entity: ClickerStateEntity)

    @Query("DELETE FROM clicker_state")
    suspend fun deleteAll()
}

@Dao
interface GachaTicketDao {
    @Query("SELECT * FROM gacha_tickets WHERE id = 0")
    suspend fun load(): GachaTicketStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(entity: GachaTicketStateEntity)
}

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE clicker_state ADD COLUMN godFingerLevel INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE clicker_state ADD COLUMN starCoreLevel INTEGER NOT NULL DEFAULT 0")
    }
}

private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE TABLE IF NOT EXISTS gacha_tickets (id INTEGER PRIMARY KEY NOT NULL, totalTickets INTEGER NOT NULL DEFAULT 10, lastTicketAwardMs INTEGER NOT NULL DEFAULT 0)")
    }
}

private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE clicker_state ADD COLUMN neutrinosCount INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE clicker_state ADD COLUMN apcToApsLevel INTEGER NOT NULL DEFAULT 0")
    }
}

private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE clicker_state ADD COLUMN apsToApcLevel INTEGER NOT NULL DEFAULT 0")
    }
}

@Database(entities = [ClickerStateEntity::class, GachaTicketStateEntity::class], version = 5, exportSchema = false)
abstract class ClickerDatabase : RoomDatabase() {
    abstract fun dao(): ClickerDao
    abstract fun gachaTicketDao(): GachaTicketDao

    companion object {
        @Volatile private var instance: ClickerDatabase? = null

        fun getInstance(context: Context): ClickerDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ClickerDatabase::class.java,
                    "clicker_game.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build().also { instance = it }
            }
    }
}
