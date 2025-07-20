package com.example.ringbuffer.breadcrumbs.storage.ringfilebuffer

data class EntityBoundary(
    val startOffset: Long,
    val endOffset: Long,
    val size: Int,
    val isWrapped: Boolean = false,
    val wrapPosition: Long? = null  // Where the entity wraps around (if wrapped)
) {
    fun isValid(): Boolean = startOffset >= 0 && endOffset >= 0 && size > 0

    fun getDataPositions(fileSize: Long): Pair<LongRange?, LongRange?> {
        return if (isWrapped && wrapPosition != null) {
            // Wrapped entity: part1 from startOffset to end, part2 from 0 to wrapPosition
            val part1 = startOffset until fileSize
            val part2 = 0L until wrapPosition
            Pair(part1, part2)
        } else {
            // Normal entity: single contiguous range
            Pair(startOffset until endOffset, null)
        }
    }
}

data class RingBufferConfig(
    val maxEntities: Int,
    val boundaryDetectionMode: BoundaryDetectionMode,
    val tempSlotInitialSize: Int = 1024 ,
)

enum class BoundaryDetectionMode {
    INDEX_BASED, SCANNING, HEADER_BASED
}


