package com.example.ringbuffer.breadcrumbs.storage.ringfilebuffer

import com.example.ringbuffer.breadcrumbs.storage.IFileWriter
import com.example.ringbuffer.breadcrumbs.storage.ringfilebuffer.boundarydetector.HeaderBasedDetector
import com.example.ringbuffer.breadcrumbs.storage.ringfilebuffer.boundarydetector.IBoundaryDetector
import com.example.ringbuffer.breadcrumbs.storage.ringfilebuffer.boundarydetector.IndexBasedDetector
import com.example.ringbuffer.breadcrumbs.storage.ringfilebuffer.boundarydetector.ScanningDetector
import java.io.RandomAccessFile

class RingFileBuffer(
    filePath: String, private val config: RingBufferConfig
) : IFileWriter {

    private val boundaryDetector: IBoundaryDetector = createBoundaryDetector()
    private val file: RandomAccessFile = RandomAccessFile(filePath, "rw")

    // Fixed file size for circular buffer
    private val fileSize: Long = calculateFixedFileSize(200L)

    // Circular buffer pointers
    private var headPosition: Long = 0      // Where next entity will be written
    private var tailPosition: Long = 0      // Position of oldest entity
    private var entityCount: Int = 0        // Current number of entities

    // Track entities with their positions (some may be wrapped)
    private val entities = mutableListOf<CircularEntity>()

    init {
        initializeCircularBuffer()
    }


    override fun addEntity(jsonLine: String) {
        addEntityToCircularBuffer(jsonLine)
    }

    override fun addEntities(jsonLines: List<String>) {
        for (jsonLine in jsonLines) {
            addEntityToCircularBuffer(jsonLine)
        }
    }

    override fun close() {
        file.close()
    }

    /**
     * Add entity to circular buffer with proper wrapping
     *
     * Algorithm:
     * 1. Calculate required space
     * 2. Remove tail entities until we have enough space
     * 3. Write entity from head position (may wrap around file)
     * 4. Update head position (circularly)
     */
    private fun addEntityToCircularBuffer(jsonLine: String): EntityBoundary? {
        val requiredSize = boundaryDetector.calculateEntitySize(jsonLine)

        // Step 1: Ensure we have enough space (remove tail entities if needed)
        if (!ensureSpaceAvailable(requiredSize.toLong())) {
            return null // Should not happen with proper file sizing
        }

        // Step 2: Write entity from head position (handling wrapping)
        val circularEntity = writeEntityWithWrapping(jsonLine, requiredSize.toLong())

        // Step 3: Update buffer state
        entities.add(circularEntity)
        entityCount++
        headPosition = (headPosition + requiredSize) % fileSize

        // If this is the first entity, set tail position
        if (entityCount == 1) {
            tailPosition = circularEntity.startPosition
        }

        return circularEntity.toBoundary()
    }

    /**
     * Ensure enough space by removing tail entities
     */
    private fun ensureSpaceAvailable(requiredSize: Long): Boolean {
        while (getAvailableFreeSpace() < requiredSize && entities.isNotEmpty()) {
            removeTailEntity()
        }

        return getAvailableFreeSpace() >= requiredSize
    }

    /**
     * Remove oldest entity (tail) and update tail position
     */
    private fun removeTailEntity() {
        if (entities.isEmpty()) return

        val tailEntity = entities.removeAt(0)
        entityCount--

        // Update tail position to next entity (or head if no entities left)
        if (entities.isNotEmpty()) {
            tailPosition = entities.first().startPosition
        } else {
            // No entities left, tail = head
            tailPosition = headPosition
        }
    }

    /**
     * Write entity with proper wrapping around file boundaries
     */
    private fun writeEntityWithWrapping(jsonLine: String, entitySize: Long): CircularEntity {
        val startPos = headPosition
        val spaceToEndOfFile = fileSize - headPosition

        return if (entitySize <= spaceToEndOfFile) {
            // Entity fits before end of file - no wrapping needed
            val boundary = boundaryDetector.writeEntity(file, jsonLine, headPosition)
            CircularEntity(
                startPosition = startPos, size = entitySize, isWrapped = false, wrapPosition = null
            )
        } else {
            // Entity needs to wrap around
            writeWrappedEntity(jsonLine, entitySize, startPos, spaceToEndOfFile)
        }
    }

    /**
     * Write entity that wraps around file boundaries
     */
    private fun writeWrappedEntity(
        jsonLine: String, entitySize: Long, startPos: Long, spaceToEnd: Long
    ): CircularEntity {

        val jsonBytes = jsonLine.toByteArray()
        val headerSize = if (boundaryDetector is HeaderBasedDetector) 4 else 0
        val newlineSize = 1

        // Calculate how to split the entity
        val sizeAtEnd = spaceToEnd
        val sizeAtBeginning = entitySize - sizeAtEnd

        // Write first part at end of file
        file.seek(startPos)
        if (headerSize > 0) {
            file.writeInt(jsonBytes.size)
        }

        val bytesForEndPart = minOf(jsonBytes.size.toLong(), sizeAtEnd - headerSize - newlineSize)
        file.write(jsonBytes, 0, bytesForEndPart.toInt())

        // Write second part at beginning of file
        file.seek(0)
        if (bytesForEndPart < jsonBytes.size) {
            file.write(jsonBytes, bytesForEndPart.toInt(), jsonBytes.size - bytesForEndPart.toInt())
        }
        file.write('\n'.code) // Newline at the end

        return CircularEntity(
            startPosition = startPos, size = entitySize, isWrapped = true, wrapPosition = sizeAtEnd
        )
    }

    // ========== SPACE CALCULATION ==========

    /**
     * Calculate available free space in circular buffer
     */
    private fun getAvailableFreeSpace(): Long {
        return if (entities.isEmpty()) {
            fileSize
        } else {
            // Free space is between head and tail (considering circular nature)
            if (headPosition >= tailPosition) {
                // Normal case: [tail...entities...head...free space...end][start...free space...tail)
                (fileSize - headPosition) + tailPosition
            } else {
                // Wrapped case: [head...free space...tail)
                tailPosition - headPosition
            }
        }
    }

    /**
     * Calculate total used space by all entities
     */
    private fun getUsedSpace(): Long {
        return entities.sumOf { it.size }
    }

    // ========== INITIALIZATION ==========

    /**
     * Initialize circular buffer with fixed file size
     */
    private fun initializeCircularBuffer() {
        // Set fixed file size
        file.setLength(fileSize)

        headPosition = 0
        tailPosition = 0
        entityCount = 0
    }


    /**
     * Calculate optimal fixed file size
     */
    private fun calculateFixedFileSize(avgEntitySize: Long): Long {
        val bufferMultiplier = 2.0 // 100% extra space for flexibility

        return (config.maxEntities * avgEntitySize * bufferMultiplier).toLong()
    }


    /**
     * Get buffer statistics
     */

    private fun createBoundaryDetector(): IBoundaryDetector {
        return when (config.boundaryDetectionMode) {
            BoundaryDetectionMode.INDEX_BASED -> IndexBasedDetector()
            BoundaryDetectionMode.SCANNING -> ScanningDetector()
            BoundaryDetectionMode.HEADER_BASED -> HeaderBasedDetector()
        }
    }
}

// ========== SUPPORTING CLASSES ==========

/**
 * Represents an entity in circular buffer (may be wrapped)
 */
data class CircularEntity(
    val startPosition: Long,    // Starting position in file
    val size: Long,            // Total size of entity
    val isWrapped: Boolean,    // Whether entity wraps around file end
    val wrapPosition: Long?    // Position where wrapping occurs (if wrapped)
) {
    /**
     * Convert to standard EntityBoundary
     */
    fun toBoundary(): EntityBoundary {
        return EntityBoundary(
            startOffset = startPosition,
            endOffset = if (isWrapped) wrapPosition!! else startPosition + size,
            size = size.toInt(),
            isWrapped = isWrapped
        )
    }
}

/**
 * Statistics for circular buffer
 */
data class CircularBufferStats(
    val entityCount: Int,
    val headPosition: Long,
    val tailPosition: Long,
    val fileSize: Long,
    val usedSpace: Long,
    val freeSpace: Long,
    val wrappedEntities: Int
) {
    val spaceEfficiency: Double = usedSpace.toDouble() / fileSize

    override fun toString(): String {
        return """
            Circular Buffer Statistics:
            - Entities: $entityCount (${wrappedEntities} wrapped)
            - Head Position: $headPosition
            - Tail Position: $tailPosition
            - File Size: $fileSize bytes
            - Used Space: $usedSpace bytes
            - Free Space: $freeSpace bytes
            - Space Efficiency: ${(spaceEfficiency * 100).toInt()}%
        """.trimIndent()
    }
}