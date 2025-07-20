package com.example.ringbuffer.breadcrumbs.storage.tapRingBuffer

import android.content.Context
import com.example.ringbuffer.breadcrumbs.Breadcrumb
import com.example.ringbuffer.breadcrumbs.storage.IFileWriter
import com.example.ringbuffer.breadcrumbs.storage.ringfilebuffer.EntityBoundary
import com.example.ringbuffer.tap.QueueFile
import java.io.File
import java.io.IOException


class TapRingBufferBreadcrumbLogger(
    context: Context,
    val file: File,
    private val maxEntries: Int
) : IFileWriter {

    val queueFile: QueueFile
    private var currentOffset = 0L

    init {

        queueFile = QueueFile.Builder(file).build()
    }

    // IFileWriter implementation
    override fun addEntity(jsonLine: String): Boolean {
        return try {
            write(jsonLine + "\n")
            true
        } catch (e: IOException) {
            false
        }

    }

    override fun addEntities(jsonLines: List<String>): List<EntityBoundary> {
        val boundaries = mutableListOf<EntityBoundary>()

        for (jsonLine in jsonLines) {
            val startOffset = currentOffset
            val entitySize = jsonLine.toByteArray().size
            val endOffset = startOffset + entitySize

            if (addEntity(jsonLine)) {
                boundaries.add(EntityBoundary(startOffset, endOffset, entitySize, false))
                currentOffset = endOffset
            }
        }

        return boundaries
    }

    override fun close() {
        try {
            queueFile.close()
        } catch (e: IOException) {
            // Log error but don't throw since close() shouldn't throw
        }
    }

    // Legacy methods for backward compatibility
    @Throws(IOException::class)
    fun flush(breadcrumbs: List<Breadcrumb>) {
        val jsonLines = breadcrumbs.map { it.toJsonString() }
        addEntities(jsonLines)
    }

    // Core ring buffer write functionality
    @Throws(IOException::class)
    private fun write(breadCrumb: String) {
        if (queueFile.size() > maxEntries) {
            queueFile.remove() // overwrite oldest
        }
        queueFile.add(breadCrumb.toByteArray())
    }

    // Legacy method for backward compatibility
    @Throws(IOException::class)
    fun shutdown() = close()
}