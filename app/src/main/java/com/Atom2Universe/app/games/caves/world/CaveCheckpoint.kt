package com.Atom2Universe.app.games.caves.world

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import java.io.*

/** Terrain, local inventories and actors are committed as one durable checkpoint. */
internal object CaveCheckpoint {
    data class Edit(val blocks: Map<Int,Short>, val meta: Map<Int,Byte>,val revision: Long=0L)
    private fun file(dir: File) = File(dir,"checkpoint.db")
    private fun open(dir: File): SQLiteDatabase {
        dir.mkdirs()
        return SQLiteDatabase.openOrCreateDatabase(file(dir),null).also { db ->
            db.execSQL("PRAGMA synchronous=FULL")
            db.execSQL("CREATE TABLE IF NOT EXISTS state (id INTEGER PRIMARY KEY, json TEXT NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS chunks (key TEXT PRIMARY KEY, blocks BLOB NOT NULL, meta BLOB NOT NULL)")
        }
    }
    @Synchronized fun metadata(dir: File): String? {
        if(!file(dir).exists()) return null
        return open(dir).use { db -> db.rawQuery("SELECT json FROM state WHERE id=1",null).use { c ->
            if(c.moveToFirst()) c.getString(0) else null
        } }
    }
    /** One reader per active world, so streaming does not reopen SQLite for each chunk. */
    class Reader(private val dir: File) : Closeable {
        private var database: SQLiteDatabase?=null
        private var closed=false
        @Synchronized fun chunk(key: String): Edit? {
            if(closed || !file(dir).exists()) return null
            val db=database ?: open(dir).also { database=it }
            return db.rawQuery("SELECT blocks,meta FROM chunks WHERE key=?",arrayOf(key)).use { c ->
                if(!c.moveToFirst()) null else Edit(decode(c.getBlob(0)),decode(c.getBlob(1)).mapValues { it.value.toByte() })
            }
        }
        @Synchronized override fun close() { closed=true;database?.close();database=null }
    }
    @Synchronized fun commit(dir: File,json: String,changes: Map<String,Edit>) {
        open(dir).use { db ->
            db.beginTransaction()
            try {
                for((key,edit) in changes) check(db.insertWithOnConflict("chunks",null,ContentValues().apply {
                    put("key",key); put("blocks",encode(edit.blocks)); put("meta",encode(edit.meta.mapValues { it.value.toShort() }))
                },SQLiteDatabase.CONFLICT_REPLACE) != -1L)
                check(db.insertWithOnConflict("state",null,ContentValues().apply { put("id",1);put("json",json) },
                    SQLiteDatabase.CONFLICT_REPLACE) != -1L)
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
        }
    }
    private fun encode(values: Map<Int,Short>): ByteArray = ByteArrayOutputStream().also { bytes ->
        DataOutputStream(bytes).use { out -> out.writeInt(values.size); values.forEach { (i,v)->out.writeShort(i);out.writeShort(v.toInt()) } }
    }.toByteArray()
    private fun decode(bytes: ByteArray): Map<Int,Short> = DataInputStream(ByteArrayInputStream(bytes)).use { input ->
        val count=input.readInt(); require(count in 0..4096)
        buildMap { repeat(count) { val i=input.readUnsignedShort();require(i<4096);put(i,input.readShort()) } }
    }
}
