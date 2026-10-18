package com.Atom2Universe.app.zoomcanvas.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * La base du canvas infini : un projet, ses couches, et **un élément par ligne** (un trait, une
 * image, une forme ou un texte, rangé dans un `BLOB`). Poser, déplacer ou effacer un élément ne
 * touche donc que sa ligne : l'enregistrement coûte ce qui a changé, pas la taille du dessin.
 *
 * Les images (les pixels) restent des fichiers du dossier du projet : trop gros pour une ligne.
 * La base ne garde que leur clé.
 */
@Entity(tableName = "zc_project", indices = [Index(value = ["uuid"], unique = true)])
class ZcProject(
    @PrimaryKey(autoGenerate = true) val pid: Long = 0,
    /** Le nom du dossier du projet (images, vignette) et l'identifiant que voit l'interface. */
    val uuid: String,
    val name: String,
    val created: Long,
    val modified: Long,
    val ratio: Double,
    /** La caméra : couche de travail, centre dans son repère, zoom. */
    val camDepth: Long,
    val cx: Double,
    val cy: Double,
    val zoom: Double,
    /** Prochain identifiant d'élément (unique dans le projet). */
    val nextId: Long,
    /** [KIND_LAYERS] : le canvas infini à couches ; [KIND_SINGLE] : le « Canvas », une seule couche. */
    @ColumnInfo(defaultValue = "0") val kind: Int = KIND_LAYERS,
    /** La couche de pixels (le « Canvas ») est tracée devant le dessin (1) ou derrière (0). */
    @ColumnInfo(defaultValue = "0") val pixelsAbove: Int = 0,
) {
    companion object {
        const val KIND_LAYERS = 0
        const val KIND_SINGLE = 1
    }
}

/**
 * Une couche : son ancre dans la couche du dessus et son résumé : nombre d'éléments ([count], dont
 * [erasers] coups de gomme) et boîte de ce qu'on y voit. Le résumé permet de tenir la couche pour
 * vide ou non, et de savoir où elle s'étend, sans lire un seul de ses éléments (une couche qu'on ne
 * voit pas n'est pas gardée en mémoire).
 */
@Entity(
    tableName = "zc_layer",
    primaryKeys = ["pid", "depth"],
    foreignKeys = [ForeignKey(entity = ZcProject::class, parentColumns = ["pid"], childColumns = ["pid"], onDelete = ForeignKey.CASCADE)],
)
class ZcLayer(
    val pid: Long,
    val depth: Long,
    val ax: Double,
    val ay: Double,
    val count: Int,
    val erasers: Int,
    /** La boîte de ce qu'on voit ; sans objet quand il n'y a rien à voir. */
    val minX: Double,
    val minY: Double,
    val maxX: Double,
    val maxY: Double,
)

/**
 * Un élément d'une couche. [z] est son rang de dessin (les rangs ont des trous : en insérer un
 * entre deux autres ne renumérote rien). [data] : voir [com.Atom2Universe.app.zoomcanvas.core.ZoomCodec].
 */
@Entity(
    tableName = "zc_item",
    primaryKeys = ["pid", "itemId"],
    // Lire une couche d'un bout à l'autre, par identifiant, sans trier.
    indices = [Index(value = ["pid", "depth", "itemId"])],
    foreignKeys = [ForeignKey(entity = ZcProject::class, parentColumns = ["pid"], childColumns = ["pid"], onDelete = ForeignKey.CASCADE)],
)
class ZcItem(
    val pid: Long,
    val itemId: Long,
    val depth: Long,
    val type: Int,
    val z: Long,
    val data: ByteArray,
)

/**
 * Une tuile de la couche de pixels du « Canvas » : 64 × 64 cases, compressées ([data] : voir
 * [com.Atom2Universe.app.zoomcanvas.core.PixelLayer.encode]). Seules les tuiles qui ont de la couleur existent.
 */
@Entity(
    tableName = "zc_pixel_tile",
    primaryKeys = ["pid", "tx", "ty"],
    foreignKeys = [ForeignKey(entity = ZcProject::class, parentColumns = ["pid"], childColumns = ["pid"], onDelete = ForeignKey.CASCADE)],
)
class ZcPixelTile(
    val pid: Long,
    val tx: Int,
    val ty: Int,
    val data: ByteArray,
)

/** Ce que la galerie affiche d'un projet, lu sans charger un seul élément. */
class ZcSummary(
    val uuid: String,
    val name: String,
    val modified: Long,
    val ratio: Double,
    val kind: Int,
    val layerCount: Int,
    val itemCount: Int,
    /** Octets des éléments du dessin dans la base (les fichiers du projet sont comptés à part). */
    val dataBytes: Long,
)

@Dao
abstract class ZoomDao {

    @Query(
        """
        SELECT p.uuid AS uuid, p.name AS name, p.modified AS modified, p.ratio AS ratio, p.kind AS kind,
               (SELECT COUNT(*) FROM zc_layer l WHERE l.pid = p.pid AND l.count > l.erasers) AS layerCount,
               (SELECT COALESCE(SUM(l.count - l.erasers), 0) FROM zc_layer l WHERE l.pid = p.pid) AS itemCount,
               (SELECT COALESCE(SUM(LENGTH(i.data)), 0) FROM zc_item i WHERE i.pid = p.pid)
                 + (SELECT COALESCE(SUM(LENGTH(t.data)), 0) FROM zc_pixel_tile t WHERE t.pid = p.pid) AS dataBytes
        FROM zc_project p WHERE p.kind = :kind ORDER BY p.modified DESC
        """
    )
    abstract fun summaries(kind: Int): List<ZcSummary>

    @Insert
    abstract fun insertProject(p: ZcProject): Long

    @Query("SELECT * FROM zc_project WHERE uuid = :uuid")
    abstract fun project(uuid: String): ZcProject?

    @Query("UPDATE zc_project SET name = :name, modified = :modified WHERE pid = :pid")
    abstract fun rename(pid: Long, name: String, modified: Long)

    @Query("UPDATE zc_project SET camDepth = :camDepth, cx = :cx, cy = :cy, zoom = :zoom, nextId = :nextId WHERE pid = :pid")
    abstract fun saveCamera(pid: Long, camDepth: Long, cx: Double, cy: Double, zoom: Double, nextId: Long)

    @Query("UPDATE zc_project SET modified = :modified WHERE pid = :pid")
    abstract fun touch(pid: Long, modified: Long)

    @Query("DELETE FROM zc_project WHERE pid = :pid")
    abstract fun deleteProject(pid: Long)

    // ---- Couches ----

    @Query("SELECT * FROM zc_layer WHERE pid = :pid ORDER BY depth")
    abstract fun layers(pid: Long): List<ZcLayer>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun putLayers(rows: List<ZcLayer>)

    /** Les couches hors de [lo]..[hi] n'ont plus de raison d'être (vides, hors de la caméra). */
    @Query("DELETE FROM zc_layer WHERE pid = :pid AND (depth < :lo OR depth > :hi)")
    abstract fun pruneLayers(pid: Long, lo: Long, hi: Long)

    // ---- Éléments ----

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun putItems(rows: List<ZcItem>)

    @Query("DELETE FROM zc_item WHERE pid = :pid AND itemId IN (:ids)")
    abstract fun deleteItems(pid: Long, ids: List<Long>)

    /** Tous les éléments d'un type (les images : on veut leurs clés sans lire les traits). */
    @Query("SELECT * FROM zc_item WHERE pid = :pid AND type = :type")
    abstract fun itemsOfType(pid: Long, type: Int): List<ZcItem>

    /** Une page des éléments d'une couche, par identifiant croissant : on lit une grande couche par morceaux. */
    @Query("SELECT * FROM zc_item WHERE pid = :pid AND depth = :depth AND itemId > :after ORDER BY itemId LIMIT :limit")
    abstract fun itemsPage(pid: Long, depth: Long, after: Long, limit: Int): List<ZcItem>

    // ---- Couche de pixels ----

    @Query("SELECT * FROM zc_pixel_tile WHERE pid = :pid")
    abstract fun pixelTiles(pid: Long): List<ZcPixelTile>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun putPixelTiles(rows: List<ZcPixelTile>)

    @Query("DELETE FROM zc_pixel_tile WHERE pid = :pid AND tx = :tx AND ty = :ty")
    abstract fun deletePixelTile(pid: Long, tx: Int, ty: Int)

    @Query("UPDATE zc_project SET pixelsAbove = :above WHERE pid = :pid")
    abstract fun savePixelsAbove(pid: Long, above: Int)

    @Query("INSERT INTO zc_pixel_tile (pid, tx, ty, data) SELECT :to, tx, ty, data FROM zc_pixel_tile WHERE pid = :from")
    abstract fun copyPixelTiles(from: Long, to: Long)

    // ---- Copie d'un projet ----

    @Query("INSERT INTO zc_layer (pid, depth, ax, ay, count, erasers, minX, minY, maxX, maxY) SELECT :to, depth, ax, ay, count, erasers, minX, minY, maxX, maxY FROM zc_layer WHERE pid = :from")
    abstract fun copyLayers(from: Long, to: Long)

    @Query("INSERT INTO zc_item (pid, itemId, depth, type, z, data) SELECT :to, itemId, depth, type, z, data FROM zc_item WHERE pid = :from")
    abstract fun copyItems(from: Long, to: Long)
}

@Database(entities = [ZcProject::class, ZcLayer::class, ZcItem::class, ZcPixelTile::class], version = 3, exportSchema = false)
abstract class ZoomDatabase : RoomDatabase() {
    abstract fun dao(): ZoomDao

    companion object {
        private const val NAME = "zoomcanvas.db"

        /** Version 2 : les projets du « Canvas » (une seule couche) partagent la base du canvas infini. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE zc_project ADD COLUMN kind INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Version 3 : la couche de pixels du « Canvas » (ses tuiles, et son ordre par rapport au dessin). */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE zc_project ADD COLUMN pixelsAbove INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `zc_pixel_tile` (`pid` INTEGER NOT NULL, `tx` INTEGER NOT NULL, `ty` INTEGER NOT NULL, " +
                        "`data` BLOB NOT NULL, PRIMARY KEY(`pid`, `tx`, `ty`), " +
                        "FOREIGN KEY(`pid`) REFERENCES `zc_project`(`pid`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
            }
        }

        internal val MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3)

        @Volatile
        private var instance: ZoomDatabase? = null

        fun get(context: Context): ZoomDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, ZoomDatabase::class.java, NAME)
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .addMigrations(*MIGRATIONS)
                .addCallback(object : Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        // Avec le journal WAL, « NORMAL » ne perd au pire que la toute dernière écriture à une coupure
                        // de courant, sans jamais abîmer la base : bien plus rapide qu'un fsync par trait.
                        db.query("PRAGMA synchronous = NORMAL").close()
                    }
                })
                .build()
                .also { instance = it }
        }
    }
}
