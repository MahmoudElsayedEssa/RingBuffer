package com.example.ringbuffer.breadcrumbs.storage.batchedfiles

import android.content.Context
import android.util.Log
import com.example.ringbuffer.breadcrumbs.storage.IFileWriter
import com.example.ringbuffer.breadcrumbs.storage.ringfilebuffer.EntityBoundary
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.util.*
import kotlin.collections.ArrayList

class BatchesFile(
    private val context: Context
) : IFileWriter {

    companion object {
        private const val TAG = "BatchesFile"
        private const val MAX_TOTAL_SIZE = 1000
        private const val BATCH_SIZE = 250
        private const val BUFFER_SIZE = 20
        private const val FLUSH_TIMEOUT_MS = 2000L
    }

    // Configuration
    private val maxBatchFiles = MAX_TOTAL_SIZE / BATCH_SIZE

    // State management
    private val batchFiles: Queue<File> = LinkedList()
    private val buffer: ArrayList<String> = ArrayList()
    private var currentFile: File? = null
    private var currentFileSize = 0
    private var currentFileOffset = 0L
    private var fileCounter = 0

    // Timing and flush management
    private var lastLogTime = System.currentTimeMillis()
    private var flushTimer: Timer? = null
    private var flushTask: TimerTask? = null
    @Volatile
    private var isClosing = false

    init {
        createNewBatchFile()
        startFlushTimer()
    }

    // Main IFileWriter implementation
    override fun addEntity(jsonLine: String): Boolean {
        return addEntities(listOf(jsonLine)).isNotEmpty()
    }

    override fun addEntities(jsonLines: List<String>): List<EntityBoundary> {
        if (isClosing) return emptyList()

        val addedEntities = mutableListOf<EntityBoundary>()

        synchronized(this) {
            for (jsonLine in jsonLines) {
                val boundary = writeEntityToBatch(jsonLine)
                if (boundary != null) {
                    addedEntities.add(boundary)
                }
            }

            // Handle buffer flushing
            handleBufferFlush()
        }

        return addedEntities
    }

    // Private helper methods
    private fun writeEntityToBatch(jsonLine: String): EntityBoundary? {
        return try {
            // Check if we need a new file
            if (currentFileSize >= BATCH_SIZE) {
                createNewBatchFile()
            }

            // Calculate entity boundary
            val startOffset = currentFileOffset
            val entitySize = jsonLine.toByteArray().size + 1 // +1 for newline
            val endOffset = startOffset + entitySize

            // Add to buffer
            buffer.add(jsonLine)
            lastLogTime = System.currentTimeMillis()
            currentFileSize++
            currentFileOffset = endOffset

            EntityBoundary(startOffset, endOffset, entitySize, false)
        } catch (e: Exception) {
            Log.e(TAG, "Error writing entity to batch", e)
            null
        }
    }

    private fun handleBufferFlush() {
        if (buffer.size >= BUFFER_SIZE) {
            flushToDisk()
        } else {
            scheduleFlush()
        }
    }

    private fun createNewBatchFile() {
        fileCounter++
        val batchDir = File(context.filesDir, "batches")

        if (!batchDir.exists() && !batchDir.mkdirs()) {
            throw IOException("Failed to create directory: ${batchDir.absolutePath}")
        }

        currentFile = File(batchDir, "batch_${System.currentTimeMillis()}.txt")
        currentFileSize = 0
        currentFileOffset = 0L

        currentFile?.let { batchFiles.add(it) }

        // Cleanup old files if necessary
        cleanupOldFiles()
    }

    private fun cleanupOldFiles() {
        while (batchFiles.size > maxBatchFiles) {
            val oldestFile = batchFiles.poll()
            if (oldestFile?.exists() == true && !oldestFile.delete()) {
                Log.w(TAG, "Warning: Could not delete old batch file: ${oldestFile.name}")
            }
        }
    }

    private fun startFlushTimer() {
        flushTimer?.cancel()
        flushTimer = Timer("BatchFileFlushTimer", true)
    }

    private fun scheduleFlush() {
        flushTask?.cancel()

        flushTask = object : TimerTask() {
            override fun run() {
                if (!isClosing) {
                    synchronized(this@BatchesFile) {
                        val timeSinceLastLog = System.currentTimeMillis() - lastLogTime
                        if (timeSinceLastLog >= FLUSH_TIMEOUT_MS && buffer.isNotEmpty()) {
                            flushToDisk()
                        }
                    }
                }
            }
        }

        flushTimer?.schedule(flushTask, FLUSH_TIMEOUT_MS)
    }

    fun flushToDisk() {
        if (buffer.isEmpty()) return

        try {
            currentFile?.let { file ->
                BufferedWriter(FileWriter(file, true)).use { writer ->
                    buffer.forEach { log ->
                        writer.write(log)
                        writer.newLine()
                    }
                    writer.flush()
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error flushing to disk", e)
        } finally {
            buffer.clear()
        }
    }

    // Public utility methods
    fun getActiveFileCount(): Int = synchronized(this) { batchFiles.size }

    fun getCurrentFileSize(): Int = synchronized(this) { currentFileSize }

    fun getBufferSize(): Int = synchronized(this) { buffer.size }

    fun getActiveFileNames(): Array<String> = synchronized(this) {
        batchFiles.map { it.name }.toTypedArray()
    }

    override fun close() {
        synchronized(this) {
            isClosing = true
            flushTimer?.cancel()

            if (buffer.isNotEmpty()) {
                flushToDisk()
            }
        }
    }
}