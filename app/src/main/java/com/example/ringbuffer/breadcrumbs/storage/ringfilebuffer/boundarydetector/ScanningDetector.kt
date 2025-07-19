package com.example.ringbuffer.breadcrumbs.storage.ringfilebuffer.boundarydetector

import com.example.ringbuffer.breadcrumbs.storage.ringfilebuffer.EntityBoundary
import java.io.RandomAccessFile

class ScanningDetector : IBoundaryDetector {

    override fun findEntityBoundaries(file: RandomAccessFile): List<EntityBoundary> {
        val boundaries = mutableListOf<EntityBoundary>()
        file.seek(0)

        var currentPos = 0L

        while (currentPos < file.length()) {
            val lineStart = currentPos
            val line = readNextLine(file, currentPos)

            if (line != null) {
                val lineSize = line.toByteArray().size + 1 // +1 for \n
                boundaries.add(EntityBoundary(
                    startOffset = lineStart,
                    endOffset = lineStart + lineSize,
                    size = lineSize
                ))
                currentPos = lineStart + lineSize
            } else {
                break
            }
        }

        return boundaries
    }

    override fun calculateEntitySize(jsonLine: String): Int {
        return jsonLine.toByteArray().size + 1 // +1 for \n
    }

    override fun findNextValidPosition(file: RandomAccessFile, currentPos: Long): Long {
        file.seek(currentPos)

        // Scan to find next \n
        while (currentPos < file.length()) {
            val byte = file.read()
            if (byte == '\n'.code) {
                return file.filePointer
            }
        }

        return file.length() // End of file
    }

    override fun handleWrappedEntities(file: RandomAccessFile): List<EntityBoundary> {
        // For ring buffer - handle lines that wrap around
        val fileSize = file.length()
        val boundaries = mutableListOf<EntityBoundary>()

        // Check if there's a partial line at the end that continues at the beginning
        file.seek(fileSize - 1)
        val lastByte = file.read()

        if (lastByte != '\n'.code) {
            // Find where the wrapped line starts from the beginning
            file.seek(0)
            val wrappedEnd = findNextValidPosition(file, 0)

            if (wrappedEnd > 0) {
                boundaries.add(EntityBoundary(
                    startOffset = fileSize,
                    endOffset = wrappedEnd,
                    size = (wrappedEnd + (fileSize - findLineStart(file, fileSize))).toInt(),
                    isWrapped = true
                ))
            }
        }

        return boundaries
    }

    override fun writeEntity(file: RandomAccessFile, jsonLine: String, position: Long): EntityBoundary {
        file.seek(position)
        val data = "$jsonLine\n".toByteArray()
        file.write(data)

        return EntityBoundary(
            startOffset = position,
            endOffset = position + data.size,
            size = data.size
        )
    }

    private fun readNextLine(file: RandomAccessFile, startPos: Long): String? {
        file.seek(startPos)
        val line = StringBuilder()

        while (file.filePointer < file.length()) {
            val byte = file.read()
            if (byte == '\n'.code) {
                return line.toString()
            }
            line.append(byte.toChar())
        }

        return if (line.isNotEmpty()) line.toString() else null
    }

    private fun findLineStart(file: RandomAccessFile, fromPos: Long): Long {
        var pos = fromPos - 1
        file.seek(pos)

        while (pos > 0) {
            val byte = file.read()
            if (byte == '\n'.code) {
                return pos + 1
            }
            pos--
            file.seek(pos)
        }

        return 0L
    }
}