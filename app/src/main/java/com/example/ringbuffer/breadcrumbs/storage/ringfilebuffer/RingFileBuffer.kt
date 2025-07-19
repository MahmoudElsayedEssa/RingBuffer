package com.example.ringbuffer.breadcrumbs.storage.ringfilebuffer

import com.example.ringbuffer.breadcrumbs.storage.IFileWriter
import com.example.ringbuffer.breadcrumbs.storage.ringfilebuffer.boundarydetector.HeaderBasedDetector
import com.example.ringbuffer.breadcrumbs.storage.ringfilebuffer.boundarydetector.IBoundaryDetector
import com.example.ringbuffer.breadcrumbs.storage.ringfilebuffer.boundarydetector.IndexBasedDetector
import com.example.ringbuffer.breadcrumbs.storage.ringfilebuffer.boundarydetector.ScanningDetector
import java.io.RandomAccessFile

class RingFileBuffer(
    filePath: String,
    private val config: RingBufferConfig
): IFileWriter {
    private val boundaryDetector: IBoundaryDetector = createBoundaryDetector()
    private var currentEntities: Int = 0
    private var currentWriteIndex: Int = 0  // Track where to write next in circular fashion
    private var totalWritesCount: Long = 0  // Track total writes for statistics
    private val entities = mutableListOf<EntityBoundary>()
    private val file: RandomAccessFile = RandomAccessFile(filePath, "rw")

    private fun createBoundaryDetector(): IBoundaryDetector {
        return when (config.boundaryDetectionMode) {
            BoundaryDetectionMode.INDEX_BASED -> IndexBasedDetector()
            BoundaryDetectionMode.SCANNING -> ScanningDetector()
            BoundaryDetectionMode.HEADER_BASED -> HeaderBasedDetector()
        }
    }

    // Single entity operations
    override fun addEntity(jsonLine: String): Boolean {
        return addEntities(listOf(jsonLine)).isNotEmpty()
    }

    // Batch operations - Main method
    override fun addEntities(jsonLines: List<String>): List<EntityBoundary> {
        val addedEntities = mutableListOf<EntityBoundary>()

        for (jsonLine in jsonLines) {
            val writePosition = findWritePosition()

            if (writePosition != -1L) {
                val boundary = boundaryDetector.writeEntity(file, jsonLine, writePosition)

                if (currentEntities < config.maxEntities) {
                    // Still have room, add new entity
                    entities.add(boundary)
                    currentEntities++
                } else {
                    // Buffer is full, overwrite oldest entity
                    entities[currentWriteIndex] = boundary
                }

                addedEntities.add(boundary)
                totalWritesCount++

                // Move write index in circular fashion
                currentWriteIndex = (currentWriteIndex + 1) % config.maxEntities
            }
        }

        return addedEntities
    }

    // Private helper methods
    private fun findWritePosition(): Long {
        return if (currentEntities < config.maxEntities) {
            // Still have room, write at end of file
            file.length()
        } else {
            // Buffer is full, overwrite the oldest entity (circular behavior)
            entities[currentWriteIndex].startOffset
        }
    }

    override fun close() {
        file.close()
    }
}