package com.example.ringbuffer.breadcrumbs.storage.Database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteStatement
import android.util.Log
import androidx.core.database.sqlite.transaction

class BreadcrumbRepository(context: Context, private var maxEntities: Int) {
    private val db = BreadcrumbDbHelper(context).writableDatabase
    private var sessionId: Long = -1
    private var oldestBreadcrumbId: Long? = null
    private var entriesCount = 0
    private var insertStatement: SQLiteStatement? = null
    private var deleteRangeStatement: SQLiteStatement? = null

    companion object {
        private const val TAG = "BreadcrumbRepository"
    }

    init {
        createSession()
        insertStatement = db.compileStatement(
            "INSERT INTO breadcrumbs (session_id, timestamp, event) VALUES (?, ?, ?)"
        )
        deleteRangeStatement =
            db.compileStatement("DELETE FROM breadcrumbs WHERE id BETWEEN ? AND ?")
    }

    private fun createSession() {
        val values = ContentValues().apply {
            put("start_time", System.currentTimeMillis())
        }
        sessionId = db.insert("sessions", null, values)
    }

    fun insertMultiple(events: List<String>) {
        if (events.isEmpty()) return


        db.transaction {
            try {
                insertStatement?.let { stmt ->
                    for (event in events) {
                        stmt.bindLong(1, sessionId)
                        stmt.bindLong(2, System.currentTimeMillis())
                        stmt.bindString(3, event)

                        if (oldestBreadcrumbId == null) {
                            oldestBreadcrumbId = stmt.executeInsert()
                        } else {
                            stmt.executeInsert()
                        }
                        stmt.clearBindings()
                    }
                }

                entriesCount += events.size
                trim()
            } catch (sqliteException: SQLiteException) {
                Log.e(TAG, "Error inserting events: ", sqliteException)
            } finally {
            }
        }
    }

//    fun insertMultiple(events: List<String>) {
//
//        synchronized(this) {
//            db.transaction {
//                try {
//                    for (event in events) {
//                        val values = ContentValues().apply {
//                            put("session_id", sessionId)
//                            put("timestamp", System.currentTimeMillis())
//                            put("event", event)
//                        }
//                        if (oldestBreadcrumbId == null) {
//                            oldestBreadcrumbId =
//                                insert("breadcrumbs", null, values)
//
//                        } else {
//
//                            insert("breadcrumbs", null, values)
//                        }
//                        entriesCount++
//                    }
//                    trim()
//                } catch (sqliteException: SQLiteException) {
//                    Log.e(TAG, " Error inserting event: ", sqliteException)
//                } finally {
//                }
//            }
//        }
//    }

    private fun getOldestBreadcrumbId(): Long? {
        oldestBreadcrumbId?.let { return it }

        val query = "SELECT id FROM breadcrumbs WHERE session_id = ? LIMIT 1"
        val params = arrayOf(sessionId.toString())

        db.rawQuery(query, params).use { cursor ->
            return if (cursor.moveToFirst()) {
                cursor.getLong(0).also { oldestBreadcrumbId = it }
            } else null
        }
    }


//    private fun trim() {
//        if (entriesCount <= maxEntities) return
//        val entriesToDelete = entriesCount - maxEntities
//        val idsToDelete = mutableListOf<Long>()
//        val oldestId = getOldestBreadcrumbId() ?: return
//        for (i in 0 until entriesToDelete) {
//            idsToDelete.add(oldestId + i)
//        }
//
//        val chunks = idsToDelete.chunked(999)
//        for (chunk in chunks) {
//            val placeholders = chunk.joinToString(",") { "?" }
//            val whereClause = "id IN ($placeholders)"
//            val whereArgs = chunk.map { it.toString() }.toTypedArray()
//            db.delete("breadcrumbs", whereClause, whereArgs)
//        }
//        entriesCount -= idsToDelete.size
//        oldestBreadcrumbId = null
//    }

    private fun trim() {
        val idsToDelete = getDeleteIds() ?: return
        oldestBreadcrumbId = null
        deleteIdsRanges(idsToDelete)
    }


    private fun getDeleteIds(): List<Long>? {
        if (entriesCount <= maxEntities) return null
        val entriesToDelete = entriesCount - maxEntities
        val idsToDelete = mutableListOf<Long>()
        val oldestId = getOldestBreadcrumbId() ?: return null
        for (i in 0 until entriesToDelete) {
            idsToDelete.add(oldestId + i)
        }
        return idsToDelete
    }

    private fun deleteIds(idsToDelete: List<Long>) {
        if (idsToDelete.isEmpty()) return
        val chunks = idsToDelete.chunked(999)
        for (chunk in chunks) {
            val placeholders = chunk.joinToString(",") { "?" }
            val whereClause = "id IN ($placeholders)"
            val whereArgs = chunk.map { it.toString() }.toTypedArray()
            db.delete("breadcrumbs", whereClause, whereArgs)
        }
        entriesCount -= idsToDelete.size

    }

    private fun deleteIdsRanges(idsToDelete: List<Long>) {
        if (idsToDelete.isEmpty()) return

        // Find continuous ranges:
        val ranges = mutableListOf<Pair<Long, Long>>()  // Start and end inclusive
        var start = idsToDelete[0]
        var prev = start

        for (i in 1 until idsToDelete.size) {
            val current = idsToDelete[i]
            if (current == prev + 1) {
                prev = current
            } else {
                ranges.add(start to prev)
                start = current
                prev = current
            }
        }
        ranges.add(start to prev)

        db.transaction {
            try {
                for ((rangeStart, rangeEnd) in ranges) {
                    deleteRangeStatement?.clearBindings()
                    deleteRangeStatement?.bindLong(1, rangeStart)
                    deleteRangeStatement?.bindLong(2, rangeEnd)
                    deleteRangeStatement?.executeUpdateDelete()
                }
            } finally {
            }
        }

        entriesCount -= idsToDelete.size
    }


    fun clearDatabase() {
        db.delete("breadcrumbs", null, null)
        entriesCount = 0
        oldestBreadcrumbId = null
    }


}
