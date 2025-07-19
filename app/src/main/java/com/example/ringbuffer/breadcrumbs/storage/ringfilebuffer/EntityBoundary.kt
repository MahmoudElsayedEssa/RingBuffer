package com.example.ringbuffer.breadcrumbs.storage.ringfilebuffer

data class EntityBoundary(
    val startOffset: Long, val endOffset: Long, val size: Int, val isWrapped: Boolean = false
)


data class RingBufferConfig(
    val maxEntities: Int,
    val boundaryDetectionMode: BoundaryDetectionMode,
    val tempSlotInitialSize: Int = 1024,
    val performanceOptimizations: Boolean = true
)

enum class BoundaryDetectionMode {
    INDEX_BASED, SCANNING, HEADER_BASED
}