package com.lin0721.linmusic.feature.localmusic.data.batch

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import com.lin0721.linmusic.feature.localmusic.domain.*
import kotlinx.serialization.encodeToString

/** Independent database: no upstream Room table or migration is changed. One result writes one row. */
internal class BatchLocalMatchStore(context: Context) : SQLiteOpenHelper(context, "local_audio_batch_match.db", null, 1) {
    private val json = BatchLocalMatchRecordCodec.json
    var damagedRowCount: Int = 0
        private set
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE results (uri TEXT PRIMARY KEY NOT NULL, local_json TEXT NOT NULL, result_json TEXT NOT NULL)")
        db.execSQL("CREATE TABLE run_state (id INTEGER PRIMARY KEY, running INTEGER NOT NULL, message TEXT)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun readRows(): List<BatchLocalMatchRow> {
        damagedRowCount = 0
        return readableDatabase.query("results", null, null, null, null, null, "uri").use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val record = BatchLocalMatchRecordCodec.decode(
                        cursor.getString(cursor.getColumnIndexOrThrow("uri")),
                        cursor.getString(cursor.getColumnIndexOrThrow("local_json")),
                        cursor.getString(cursor.getColumnIndexOrThrow("result_json")),
                    )
                    if (record == null) { damagedRowCount++; continue }
                    val local = record.local
                    val result = record.result
                    val track = LocalTrack(local.mediaStoreId, local.songId, local.title, local.artist,
                        local.album, local.durationMs, local.sizeBytes, Uri.parse(local.uri), local.path,
                        local.dateAddedMs, local.source, local.albumArtist, local.year, local.trackNumber)
                    add(BatchLocalMatchRow(track, result.query, result.status, result.matchedTrack,
                        result.candidates, result.reason, result.updatedAtMs, result.usesExistingSource))
                }
            }
        }
    }

    fun replaceLibrary(rows: List<BatchLocalMatchRow>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val active = rows.map { it.local.uri.toString() }.toHashSet()
            db.query("results", arrayOf("uri"), null, null, null, null, null).use { cursor ->
                val stale = buildList { while (cursor.moveToNext()) if (cursor.getString(0) !in active) add(cursor.getString(0)) }
                stale.forEach { db.delete("results", "uri=?", arrayOf(it)) }
            }
            rows.forEach(::writeRow)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun writeRow(row: BatchLocalMatchRow) {
        val values = ContentValues().apply {
            put("uri", row.local.uri.toString())
            put("local_json", json.encodeToString(row.local.persisted()))
            put("result_json", json.encodeToString(BatchLocalMatchRecordCodec.Result(row.query, row.status, row.matchedTrack,
                row.candidates, row.reason, row.updatedAtMs, row.usesExistingSource)))
        }
        check(writableDatabase.insertWithOnConflict("results", null, values, SQLiteDatabase.CONFLICT_REPLACE) >= 0L) {
            "无法保存匹配结果，任务已暂停"
        }
    }

    fun writeRows(rows: List<BatchLocalMatchRow>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            rows.forEach(::writeRow)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun runMessage(): String? = readableDatabase.query("run_state", null, "id=1", null, null, null, null).use {
        if (!it.moveToFirst()) null else if (it.getInt(it.getColumnIndexOrThrow("running")) == 1) {
            "上次匹配因应用退出而暂停，点击继续即可接着处理"
        } else it.getString(it.getColumnIndexOrThrow("message"))
    }
    fun setRunState(running: Boolean, message: String?) {
        check(writableDatabase.insertWithOnConflict("run_state", null, ContentValues().apply {
            put("id", 1); put("running", if (running) 1 else 0); put("message", message)
        }, SQLiteDatabase.CONFLICT_REPLACE) >= 0L) { "无法保存匹配进度" }
    }

    private fun LocalTrack.persisted() = BatchLocalMatchRecordCodec.Local(
        mediaStoreId, songId, title, artist, album, durationMs, sizeBytes, uri.toString(), path,
        dateAddedMs, source, albumArtist, year, trackNumber,
    )
}
