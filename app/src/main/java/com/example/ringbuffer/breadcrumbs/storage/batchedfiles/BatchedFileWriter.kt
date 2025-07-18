package com.example.ringbuffer.breadcrumbs.storage.batchedfiles

import com.example.ringbuffer.breadcrumbs.storage.IFileWriter
import java.io.Closeable
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class BatchedFileWriter(
    private val maxTotalEntries: Int = 1000, private val entriesPerFile: Int = 250
) : IFileWriter, Closeable {

    data class Stats(
        val totalEntries: Int,
        val maxTotalEntries: Int,
        val currentFileIndex: Int,
        val currentFileEntries: Int,
        val activeFiles: Int,
        val totalBytesWritten: Long,
        val totalFilesCreated: Int,
        val totalFilesDeleted: Int,
        val errorCount: Long
    ) {
        val utilizationPercent: Double = (totalEntries.toDouble() / maxTotalEntries) * 100.0
        val averageEntrySize: Double = if (totalEntries > 0) {
            totalBytesWritten.toDouble() / totalEntries
        } else 0.0
    }

    private val maxFiles = maxTotalEntries / entriesPerFile // 4 files
    private val fileInstances = mutableMapOf<String, BatchedFileInstance>()
    private val lock = ReentrantLock()
    private val flushThread: Thread

    @Volatile
    private var closed = false

    init {
        // Background write thread
        flushThread = Thread({
            while (!closed && !Thread.currentThread().isInterrupted) {
                try {
                    Thread.sleep(2000) // Flush every 2 seconds
                    flushAll()
                } catch (_: InterruptedException) {
                    break
                }
            }
        }, "BatchedFileFlush").apply {
            isDaemon = true
            start()
        }
    }

    override fun appendContent(filePath: String, content: String): Boolean {
        if (closed) return false
        return try {
            getOrCreateInstance(filePath).appendContent(content + "\n")
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun flushBuffer(filePath: String) {
        if (closed) return
        lock.withLock {
            fileInstances[filePath]?.flush()
        }
    }

    fun getStats(filePath: String): Stats? {
        return lock.withLock {
            fileInstances[filePath]?.getStats()
        }
    }

    fun getCurrentFiles(filePath: String): List<String> {
        return lock.withLock {
            fileInstances[filePath]?.getCurrentFiles() ?: emptyList()
        }
    }

    private fun getOrCreateInstance(filePath: String): BatchedFileInstance {
        return lock.withLock {
            fileInstances.getOrPut(filePath) {
                BatchedFileInstance(filePath, maxFiles, entriesPerFile)
            }
        }
    }

    private fun flushAll() {
        lock.withLock {
            fileInstances.values.forEach { it.flush() }
        }
    }

    override fun close() {
        if (closed) return
        closed = true

        flushThread.interrupt()
        try {
            flushThread.join(1000)
        } catch (_: InterruptedException) {
        }

        lock.withLock {
            fileInstances.values.forEach { it.close() }
            fileInstances.clear()
        }
    }

    private class BatchedFileInstance(
        private val basePath: String, private val maxFiles: Int, private val entriesPerFile: Int
    ) : Closeable {

        private var currentFileIndex = 0
        private var currentFileEntries = 0
        private var currentWriter: PrintWriter? = null
        private var totalEntries = 0
        private var totalBytesWritten = 0L
        private var totalFilesCreated = 0
        private var totalFilesDeleted = 0
        private var errorCount = 0L

        private val instanceLock = ReentrantLock()
        private val activeFiles = mutableListOf<String>()

        fun appendContent(content: String): Boolean {
            return instanceLock.withLock {
                try {
                    // Check if we need to rotate to a new file
                    if (currentFileEntries >= entriesPerFile || currentWriter == null) {
                        rotateFile()
                    }

                    // Write content
                    currentWriter?.print(content)
                    currentFileEntries++
                    totalEntries++
                    totalBytesWritten += content.toByteArray(Charsets.UTF_8).size.toLong()

                    true
                } catch (e: Exception) {
                    errorCount++
                    false
                }
            }
        }

        private fun rotateFile() {
            // Close current writer
            currentWriter?.close()

            // Create new file
            val newFilePath = "${basePath}_batch_${currentFileIndex}.log"
            val newFile = File(newFilePath)
            newFile.parentFile?.mkdirs()

            currentWriter = PrintWriter(FileWriter(newFile, false))
            currentFileEntries = 0
            totalFilesCreated++

            // Add to active files list
            activeFiles.add(newFilePath)

            // Clean up old files if we exceed maxFiles
            if (activeFiles.size > maxFiles) {
                val oldestFile = activeFiles.removeAt(0)
                val deleted = File(oldestFile).delete()
                if (deleted) {
                    totalFilesDeleted++
                }
            }

            currentFileIndex++
        }

        fun flush() {
            instanceLock.withLock {
                try {
                    currentWriter?.flush()
                } catch (e: Exception) {
                    errorCount++
                }
            }
        }

        fun getStats(): Stats = Stats(
            totalEntries = totalEntries,
            maxTotalEntries = maxFiles * entriesPerFile,
            currentFileIndex = currentFileIndex,
            currentFileEntries = currentFileEntries,
            activeFiles = activeFiles.size,
            totalBytesWritten = totalBytesWritten,
            totalFilesCreated = totalFilesCreated,
            totalFilesDeleted = totalFilesDeleted,
            errorCount = errorCount
        )

        fun getCurrentFiles(): List<String> {
            return instanceLock.withLock {
                activeFiles.toList()
            }
        }

        override fun close() {
            instanceLock.withLock {
                try {
                    currentWriter?.close()
                } catch (e: Exception) {
                    errorCount++
                }
            }
        }
    }
}

// Factory for easy creation
object BatchedFileWriterFactory {

    @JvmStatic
    fun createStandard(): BatchedFileWriter {
        return BatchedFileWriter(maxTotalEntries = 1000, entriesPerFile = 250)
    }

    @JvmStatic
    fun createCustom(maxTotalEntries: Int, entriesPerFile: Int): BatchedFileWriter {
        return BatchedFileWriter(maxTotalEntries, entriesPerFile)
    }

    @JvmStatic
    fun createHighCapacity(): BatchedFileWriter {
        return BatchedFileWriter(maxTotalEntries = 2000, entriesPerFile = 500)
    }
}