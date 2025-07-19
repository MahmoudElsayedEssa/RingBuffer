package com.example.ringbuffer.breadcrumbs

import java.io.File
import kotlin.also
import kotlin.apply
import kotlin.collections.all
import kotlin.collections.filter
import kotlin.collections.map
import kotlin.collections.sortedBy
import kotlin.collections.sumOf
import kotlin.collections.toTypedArray

/**
 * Interface for recording breadcrumb events.
 * Uses Kotlin-specific features like default parameters and suspend functions.
 */
interface IBreadcrumbRecorder {
    
    /**
     * Add a breadcrumb with optional criticality flag
     */
    fun addBreadcrumb(
        eventType: String,
        launchId: String,
        vararg extras: String,
        isCritical: Boolean = false
    ): Boolean
    
    /**
     * Add breadcrumb with timestamp (Kotlin extension)
     */
    fun addBreadcrumb(
        eventType: String,
        launchId: String,
        timestamp: Long = System.currentTimeMillis(),
        isCritical: Boolean = false,
        vararg extras: String
    ): Boolean = addBreadcrumb(eventType, launchId, *extras, isCritical = isCritical)
    
    /**
     * Add breadcrumb with structured data
     */
    fun addBreadcrumb(
        eventType: String,
        launchId: String,
        data: Map<String, Any>,
        isCritical: Boolean = false
    ): Boolean {
        val extras = data.map { "${it.key}:${it.value}" }.toTypedArray()
        return addBreadcrumb(eventType, launchId, *extras, isCritical = isCritical)
    }
    
    /**
     * Batch add breadcrumbs for performance
     */
    fun addBreadcrumbs(breadcrumbs: List<Breadcrumb>): Boolean =
        breadcrumbs.all { addBreadcrumb(it.eventType, it.launchId, *it.extras.toTypedArray()) }
}
