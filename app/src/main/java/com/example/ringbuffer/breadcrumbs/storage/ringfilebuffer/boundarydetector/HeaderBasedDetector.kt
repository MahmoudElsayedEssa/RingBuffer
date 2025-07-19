package com.example.ringbuffer.breadcrumbs.storage.ringfilebuffer.boundarydetector

import com.example.ringbuffer.breadcrumbs.storage.ringfilebuffer.EntityBoundary
import java.io.RandomAccessFile

class HeaderBasedDetector : IBoundaryDetector {
    companion object {
        private const val HEADER_SIZE = 4 // 4 bytes for JSON size
    }

    override fun findEntityBoundaries(file: RandomAccessFile): List<EntityBoundary> {
        val boundaries = mutableListOf<EntityBoundary>()
        file.seek(0)

        var currentPos = 0L
        while (currentPos < file.length()) {
            try {
                file.seek(currentPos)
                val jsonSize = file.readInt() // Read 4-byte header

                if (jsonSize > 0 && currentPos + HEADER_SIZE + jsonSize + 1 <= file.length()) {
                    val totalSize = HEADER_SIZE + jsonSize + 1 // header + json + \n
                    boundaries.add(
                        EntityBoundary(
                            startOffset = currentPos,
                            endOffset = currentPos + totalSize,
                            size = totalSize
                        )
                    )
                    currentPos += totalSize
                } else {
                    break // Invalid or corrupt entry
                }
            } catch (e: Exception) {
                break // End of valid data
            }
        }

        return boundaries
    }

    override fun calculateEntitySize(jsonLine: String): Int {
        return HEADER_SIZE + jsonLine.toByteArray().size + 1 // header + json + \n
    }

    override fun findNextValidPosition(file: RandomAccessFile, currentPos: Long): Long {
        file.seek(currentPos)

        try {
            val jsonSize = file.readInt()
            return currentPos + HEADER_SIZE + jsonSize + 1
        } catch (e: Exception) {
            return file.length()
        }
    }

    override fun handleWrappedEntities(file: RandomAccessFile): List<EntityBoundary> {
        // Header-based approach handles wrapping more reliably
        val boundaries = mutableListOf<EntityBoundary>()

        // Check for wrapped headers/data
        val fileSize = file.length()
        if (fileSize >= HEADER_SIZE) {
            // Check if we have a partial header at the end
            file.seek(fileSize - HEADER_SIZE)
            try {
                val possibleSize = file.readInt()
                if (possibleSize > 0) {
                    // This might be a wrapped entity
                    boundaries.add(
                        EntityBoundary(
                            startOffset = fileSize - HEADER_SIZE,
                            endOffset = possibleSize.toLong(),
                            size = HEADER_SIZE + possibleSize + 1,
                            isWrapped = true
                        )
                    )
                }
            } catch (e: Exception) {
                // Not a valid header
            }
        }

        return boundaries
    }

    override fun writeEntity(
        file: RandomAccessFile, jsonLine: String, position: Long
    ): EntityBoundary {
        file.seek(position)

        val jsonBytes = jsonLine.toByteArray()

        // Write header (JSON size)
        file.writeInt(jsonBytes.size)

        // Write JSON
        file.write(jsonBytes)

        // Write newline
        file.write('\n'.code)

        val totalSize = HEADER_SIZE + jsonBytes.size + 1

        return EntityBoundary(
            startOffset = position, endOffset = position + totalSize, size = totalSize
        )
    }
}