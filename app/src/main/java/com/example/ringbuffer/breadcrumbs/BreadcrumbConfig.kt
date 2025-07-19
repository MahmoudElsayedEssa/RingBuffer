package com.example.ringbuffer.breadcrumbs

import kotlinx.serialization.Serializable

@Serializable
data class BreadcrumbConfig(
    val enabled: Boolean = true,
    val maxBreadcrumbsPerLaunch: Int = 1000,
    val flushIntervalSeconds: Int = 30,
) {
    
    init {
        require(maxBreadcrumbsPerLaunch > 0) { "maxBreadcrumbsPerLaunch must be positive" }
        require(flushIntervalSeconds > 0) { "flushIntervalSeconds must be positive" }
    }
    

    /**
     * Utility functions using Kotlin extension properties
     */
    val flushIntervalMs: Long get() = flushIntervalSeconds * 1000L
}