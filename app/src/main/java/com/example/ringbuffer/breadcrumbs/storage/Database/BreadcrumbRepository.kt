package com.example.ringbuffer.breadcrumbs.storage.Database

import android.content.ContentValues
import android.content.Context
import android.util.Log
import androidx.core.database.sqlite.transaction

class BreadcrumbRepository(context: Context, private var maxEntities: Int) {
    private val db = BreadcrumbDbHelper(context).writableDatabase
    private var sessionId: Long = -1
    private var oldestBreadcrumbId: Long? = null
    private var entriesCount = 0

    private val lock = Any()

    companion object {
        private const val TAG = "BreadcrumbRepository"
    }

    init {
        createSession()
        entriesCount = getCurrentBreadcrumbCount()
    }

    private fun createSession() {
        val values = ContentValues().apply {
            put("start_time", System.currentTimeMillis())
        }
        sessionId = db.insert("sessions", null, values)
    }


    fun insertMultiple(events: List<String>) {
        db.transaction {
            // Insert all events first
            for (event in events) {
                val values = ContentValues().apply {
                    put("session_id", sessionId)
                    put("timestamp", System.currentTimeMillis())
                    put("event", event)
                }
                val result = insert("breadcrumbs", null, values)
                if (result == -1L) {
                    Log.e(TAG, "Failed to insert: $event")
                } else {
                    entriesCount++
                }
            }

            // Now enforce limit AFTER insertion to maintain ring buffer behavior
            enforceRingBufferLimit()
        }
    }


    private fun getOldestBreadcrumbId(): Long? {
        if (oldestBreadcrumbId != null) {
            return oldestBreadcrumbId
        }
        val cursor = db.rawQuery(
            "SELECT id FROM breadcrumbs WHERE session_id = ? ORDER BY timestamp ASC LIMIT 1",
            arrayOf(sessionId.toString())
        )

        return try {
            if (cursor.moveToFirst()) {
                oldestBreadcrumbId = cursor.getLong(0)
                oldestBreadcrumbId
            } else {
                null
            }
        } finally {
            cursor.close()
        }
    }

    private fun enforceRingBufferLimit() {
        // Get current count from database to ensure accuracy
        val currentCount = getCurrentBreadcrumbCount()

        if (currentCount <= maxEntities) {
            Log.d(TAG, "No deletion needed. Current: $currentCount, Max: $maxEntities")
            return // No need to delete anything
        }

        val entriesToDelete = currentCount - maxEntities
        Log.d(
            TAG,
            "Ring buffer limit exceeded. Current: $currentCount, Max: $maxEntities, Will delete: $entriesToDelete"
        )

        // Select the oldest entries to delete
        val cursor = db.rawQuery(
            "SELECT id FROM breadcrumbs WHERE session_id = ? ORDER BY timestamp ASC LIMIT ?",
            arrayOf(sessionId.toString(), entriesToDelete.toString())
        )

        try {
            val idsToDelete = mutableListOf<Long>()
            while (cursor.moveToNext()) {
                idsToDelete.add(cursor.getLong(0))
            }

            Log.d(TAG, "Found ${idsToDelete.size} entries to delete: $idsToDelete")

            // Delete the selected entries
            var deletedCount = 0
            for (id in idsToDelete) {
                val deletedRows = db.delete("breadcrumbs", "id = ?", arrayOf(id.toString()))
                if (deletedRows > 0) {
                    entriesCount--
                    deletedCount++
                    Log.d(TAG, "Deleted breadcrumb with ID: $id")
                }
            }

            Log.d(
                TAG,
                "Ring buffer cleanup complete. Deleted $deletedCount entries. New count: ${getCurrentBreadcrumbCount()}"
            )

            // Reset oldest breadcrumb ID cache since we deleted entries
            oldestBreadcrumbId = null

        } finally {
            cursor.close()
        }
    }

    private fun getCurrentBreadcrumbCount(): Int {
        val cursor = db.rawQuery(
            "SELECT COUNT(*) FROM breadcrumbs WHERE session_id = ?",
            arrayOf(sessionId.toString())
        )
        return try {
            if (cursor.moveToFirst()) {
                cursor.getInt(0)
            } else {
                0
            }
        } finally {
            cursor.close()
        }
    }


    fun getBreadcrumbs(): List<String> {
        val result = mutableListOf<String>()
        val cursor = db.query(
            "breadcrumbs", arrayOf("timestamp", "event"),
            "session_id = ?", arrayOf(sessionId.toString()),
            null, null, "timestamp ASC"
        )
        while (cursor.moveToNext()) {
            val ts = cursor.getLong(0)
            val ev = cursor.getString(1)
            result.add("$ts -> $ev")
        }
        cursor.close()
        return result
    }

    fun clearDatabase() {
        db.delete("breadcrumbs", null, null)
        db.delete("sessions", null, null)
    }

    // Debug method to help investigate the issue
    fun debugBreadcrumbs(): String {
        val cursor = db.query(
            "breadcrumbs", arrayOf("id", "timestamp", "event"),
            "session_id = ?", arrayOf(sessionId.toString()),
            null, null, "timestamp ASC"
        )

        val debug = StringBuilder()
        debug.append("Total breadcrumbs: ${cursor.count}\n")
        debug.append("MaxEntities: $maxEntities\n")
        debug.append("SessionId: $sessionId\n")
        debug.append("entriesCount: $entriesCount\n\n")

        var count = 0
        while (cursor.moveToNext()) {
            val id = cursor.getLong(0)
            val ts = cursor.getLong(1)
            val ev = cursor.getString(2)
            debug.append("[$count] ID:$id Event:$ev\n")
            count++
        }
        cursor.close()

        return debug.toString()
    }

    /**
     * Test the ring buffer functionality with a simple example
     */
    fun testRingBuffer(): String {
        val results = StringBuilder()
        results.append("=== RING BUFFER TEST ===\n")
        results.append("MaxEntities: $maxEntities\n\n")

        // Clear for clean test
        clearDatabase()
        createSession()
        entriesCount = 0

        // Test 1: Insert maxEntities + 50% more events
        val testEvents = (1..(maxEntities * 1.5).toInt()).map { "Test event $it" }
        results.append("Inserting ${testEvents.size} events (should keep only newest $maxEntities)\n")

        insertMultiple(testEvents)

        val finalCount = getCurrentBreadcrumbCount()
        results.append("Final count: $finalCount\n")
        results.append("Expected: $maxEntities\n")
        results.append("Ring buffer working: ${if (finalCount == maxEntities) "✅ YES" else "❌ NO"}\n\n")

        // Show first and last few events to verify FIFO behavior
        val breadcrumbs = getBreadcrumbs()
        if (breadcrumbs.isNotEmpty()) {
            results.append("First event (should be recent): ${breadcrumbs.first()}\n")
            results.append("Last event (should be most recent): ${breadcrumbs.last()}\n")
        }

        return results.toString()
    }

}
