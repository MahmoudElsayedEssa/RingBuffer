package com.example.benchmark


import android.util.Log
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.write
import kotlin.time.measureTimedValue


/**
 * Simple configuration for ring buffer
 */
data class RingBufferConfig(
    val maxEntities: Int = 1000,
) {
    init {
        require(maxEntities > 0) { "Max entities must be positive" }
    }
}


/**
 * Simple file manager for plain text ring buffer
 * No header, no metadata - just JSON entities line by line
 */
class FileManager(filePath: String) {
    val file = File(filePath)
    private val writeCount = AtomicLong(0L)

    /**
     * Add entity with ring buffer overflow handling
     * Keeps only the newest maxEntities, removes oldest
     */
    fun addEntity(jsonLine: String, maxEntities: Int): Boolean {
        return try {
            val existingLines = try {
                file.readLines()
            } catch (e: Exception) {
                emptyList()
            }.toMutableList()

            // Add new entity
            existingLines.add(jsonLine)

            // Ring buffer: keep only newest maxEntities
            val linesToKeep = if (existingLines.size > maxEntities) {
                existingLines.takeLast(maxEntities)
            } else {
                existingLines
            }

            // Write plain text file
            file.writeText(linesToKeep.joinToString("\n") + "\n")
            writeCount.incrementAndGet()
            true
        } catch (e: Exception) {
            false
        }
    }


    fun addEntities(jsonLines: List<String>, maxEntities: Int): Boolean {
        return try {
            val existingLines = try {
                ArrayDeque(file.readLines())
            } catch (e: Exception) {
                ArrayDeque()
            }

            for (line in jsonLines) {
                existingLines.addLast(line)
                if (existingLines.size > maxEntities) {
                    existingLines.removeFirst()
                }
            }

            file.writeText(existingLines.joinToString("\n") + "\n")
            writeCount.incrementAndGet()
            true
        } catch (e: Exception) {
            false
        }
    }


    /**
     * Read all entities from file
     */
    fun readAllEntities(): List<String> {
        return try {
            if (!file.exists()) emptyList()
            else file.readLines().filter { it.isNotBlank() }
        } catch (e: Exception) {
            emptyList()
        }
    }
}


/**
 * Simple ring buffer that creates plain text files
 * No header, no binary data - just JSON entities line by line
 */
class RingBuffer(
    filePath: String, private val config: RingBufferConfig = RingBufferConfig()
) {
    private val fileManager = FileManager(filePath)
    private val lock = ReentrantReadWriteLock()
    private val writeSequence = AtomicLong(0L)

    @Volatile
    private var isClosed = false

    /**
     * Add single JSON entity
     */
    fun addEntity(jsonLine: String): CompletableFuture<Boolean> {
        if (isClosed || jsonLine.isBlank()) {
            return CompletableFuture.completedFuture(false)
        }

        return CompletableFuture.supplyAsync {
            lock.write {
                try {

                    // Add entity with ring buffer logic
                    val success = fileManager.addEntity(jsonLine, config.maxEntities)
                    if (success) {
                        writeSequence.incrementAndGet()
                    }

                    success
                } catch (e: Exception) {
                    false
                }
            }
        }
    }

    /**
     * Add multiple entities with ring buffer overflow
     */
    fun addEntities(jsonLines: List<String>): CompletableFuture<BulkResult> {
        if (isClosed || jsonLines.isEmpty()) {
            return CompletableFuture.completedFuture(BulkResult(0, jsonLines.size, false))
        }

        return CompletableFuture.supplyAsync {
            lock.write {
                val x = measureTimedValue {
                    var successCount = 0

                    try {
                        // Efficient ring buffer structure
                        val buffer = try {
                            ArrayDeque(fileManager.readAllEntities())
                        } catch (e: Exception) {
                            ArrayDeque()
                        }

                        // Add new entities and maintain ring size
                        for (jsonLine in jsonLines) {

                            buffer.addLast(jsonLine)
                            successCount++

                            if (buffer.size > config.maxEntities) {
                                buffer.removeFirst()
                            }
                        }

                        // Write back to file
                        fileManager.file.writeText(buffer.joinToString("\n") + "\n")
                        writeSequence.addAndGet(successCount.toLong())
                    } catch (e: Exception) {
                        // Partial failure
                    }

                    BulkResult(successCount, jsonLines.size, true)
                }

                Log.d("MOE", "addEntities:time:${x.duration}")
                x.value
            }
        }
    }

    /**
     * Close buffer
     */
    fun close(): CompletableFuture<Void> {
        if (isClosed) {
            return CompletableFuture.completedFuture(null)
        }

        return CompletableFuture.runAsync {
            isClosed = true
            // Nothing special to do on close for plain text file
        }
    }
}


/**
 * Bulk operation result
 */
data class BulkResult(
    val successCount: Int, val totalCount: Int, val completed: Boolean
)