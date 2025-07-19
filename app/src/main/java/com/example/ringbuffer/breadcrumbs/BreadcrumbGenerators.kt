package com.example.ringbuffer.breadcrumbs

import kotlin.random.Random

/**
 * Breadcrumb generators for testing purposes with different size ranges and realistic scenarios
 */
object BreadcrumbGenerators {

    private val random = Random.Default

    enum class BreadcrumbSize(val value: Int) {
        SMALL_512(512), 
        MEDIUM_1K(1000), 
        LARGE_10K(10000),
        EXTRA_LARGE_50K(50000)
    }

    // Realistic event types for testing
    private val eventTypes = listOf(
        "app_launch", "user_login", "page_view", "button_click", "api_call",
        "error_occurred", "network_request", "database_query", "file_upload",
        "user_logout", "crash_detected", "memory_warning", "background_task",
        "push_notification", "location_update", "sensor_reading", "timer_expired"
    )

    // Realistic thread names
    private val threadNames = listOf(
        "main", "background", "network", "database", "ui", "worker",
        "timer", "location", "sensor", "upload", "download", "cache"
    )

    // Realistic launch IDs for testing
    private val sampleLaunchIds = listOf(
        "launch_001", "launch_002", "launch_003", "launch_004", "launch_005"
    )

    /**
     * Generates a random alphanumeric string of specified length
     */
    private fun generateRandomString(length: Int): String {
        val charset = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..length)
            .map { charset.random() }
            .joinToString("")
    }

    /**
     * Generate a realistic launch ID
     */
    fun generateLaunchId(): String {
        return "launch_${System.currentTimeMillis()}_${random.nextInt(1000, 9999)}"
    }

    /**
     * Core breadcrumb generator with configurable parameters
     */
    private fun generateBreadcrumb(
        eventTypes: List<String> = BreadcrumbGenerators.eventTypes,
        threadNames: List<String> = BreadcrumbGenerators.threadNames,
        launchId: String = sampleLaunchIds.random(),
        minExtras: Int = 0,
        maxExtras: Int = 5,
        minStringLength: Int = 5,
        maxStringLength: Int = 20,
        threadSuffix: String = ""
    ): Breadcrumb {
        val eventType = eventTypes.random()
        val threadName = threadNames.random() + threadSuffix
        val extrasCount = random.nextInt(minExtras, maxExtras + 1)
        val extras = (0 until extrasCount).map {
            generateRandomString(random.nextInt(minStringLength, maxStringLength + 1))
        }

        return Breadcrumb.create(
            eventType = eventType,
            launchId = launchId,
            extras = extras,
            threadName = threadName
        )
    }

    /**
     * Generate realistic breadcrumbs for a single user session/launch
     */
    fun generateSessionBreadcrumbs(
        count: Int,
        launchId: String = generateLaunchId()
    ): List<Breadcrumb> {
        return (1..count).map { index ->
            when {
                index == 1 -> generateBreadcrumb(
                    eventTypes = listOf("app_launch"),
                    launchId = launchId,
                    minExtras = 1,
                    maxExtras = 3
                )
                index == count -> generateBreadcrumb(
                    eventTypes = listOf("user_logout", "app_close"),
                    launchId = launchId,
                    minExtras = 0,
                    maxExtras = 2
                )
                else -> generateBreadcrumb(launchId = launchId)
            }
        }
    }

    /**
     * Generate breadcrumbs simulating different app usage patterns
     */
    fun generateUsagePatternBreadcrumbs(
        pattern: UsagePattern,
        count: Int,
        launchId: String = generateLaunchId()
    ): List<Breadcrumb> {
        return when (pattern) {
            UsagePattern.HEAVY_USER -> generateHeavyUserBreadcrumbs(count, launchId)
            UsagePattern.CASUAL_USER -> generateCasualUserBreadcrumbs(count, launchId)
            UsagePattern.ERROR_PRONE -> generateErrorProneBreadcrumbs(count, launchId)
            UsagePattern.NETWORK_INTENSIVE -> generateNetworkIntensiveBreadcrumbs(count, launchId)
        }
    }

    /**
     * Generate breadcrumbs of only one size type
     */
    fun generateSingleSizeList(
        count: Int, 
        size: Int,
        launchId: String = generateLaunchId()
    ): List<Breadcrumb> {
        return (1..count).mapIndexed { index, _ ->
            createDummyBreadcrumbWithSize(size, index, launchId)
        }
    }

    /**
     * Generate mixed-size breadcrumbs for stress testing
     */
    fun generateMixedSizeBreadcrumbs(
        count: Int,
        launchId: String = generateLaunchId()
    ): List<Breadcrumb> {
        val sizes = BreadcrumbSize.entries.toTypedArray()
        return (1..count).map { index ->
            val size = sizes.random()
            createDummyBreadcrumbWithSize(size.value, index, launchId)
        }
    }

    /**
     * Creates a dummy breadcrumb with exact size in bytes after JSON serialization
     */
    fun createDummyBreadcrumbWithSize(
        targetSizeBytes: Int, 
        index: Int,
        launchId: String = "test_launch_$index"
    ): Breadcrumb {
        // Create minimal base structure to measure actual overhead
        val baseBreadcrumb = Breadcrumb.create(
            eventType = "test_$index",
            launchId = launchId,
            extras = emptyList(),
            threadName = "test_thread"
        )

        val baseSize = baseBreadcrumb.toJsonString().toByteArray().size

        require(targetSizeBytes >= baseSize) {
            "Target size must be at least $baseSize bytes (minimum possible size)"
        }

        if (baseSize >= targetSizeBytes) {
            return baseBreadcrumb
        }

        // Calculate exact padding needed
        val testBreadcrumb = Breadcrumb.create(
            eventType = "test_$index",
            launchId = launchId,
            extras = listOf("X"),
            threadName = "test_thread"
        )

        val testSize = testBreadcrumb.toJsonString().toByteArray().size
        val overhead = testSize - baseSize - 1 // -1 for the "X" character

        val paddingNeeded = targetSizeBytes - baseSize - overhead

        if (paddingNeeded <= 0) {
            return baseBreadcrumb
        }

        val padding = "X".repeat(paddingNeeded)
        val result = Breadcrumb.create(
            eventType = "test_$index",
            launchId = launchId,
            extras = listOf(padding),
            threadName = "test_thread"
        )

        // Fine-tune if needed
        val actualSize = result.toJsonString().toByteArray().size
        val sizeDiff = targetSizeBytes - actualSize

        return if (sizeDiff == 0) {
            result
        } else {
            val adjustedPadding = if (sizeDiff > 0) {
                padding + "X".repeat(sizeDiff)
            } else {
                padding.dropLast(-sizeDiff)
            }
            
            Breadcrumb.create(
                eventType = "test_$index",
                launchId = launchId,
                extras = listOf(adjustedPadding),
                threadName = "test_thread"
            )
        }
    }

    // Private helper methods for different usage patterns
    private fun generateHeavyUserBreadcrumbs(count: Int, launchId: String): List<Breadcrumb> {
        return (1..count).map {
            generateBreadcrumb(
                eventTypes = listOf("button_click", "page_view", "api_call", "user_action"),
                launchId = launchId,
                minExtras = 2,
                maxExtras = 8,
                minStringLength = 10,
                maxStringLength = 50
            )
        }
    }

    private fun generateCasualUserBreadcrumbs(count: Int, launchId: String): List<Breadcrumb> {
        return (1..count).map {
            generateBreadcrumb(
                eventTypes = listOf("page_view", "app_launch", "user_logout"),
                launchId = launchId,
                minExtras = 0,
                maxExtras = 3,
                minStringLength = 5,
                maxStringLength = 15
            )
        }
    }

    private fun generateErrorProneBreadcrumbs(count: Int, launchId: String): List<Breadcrumb> {
        return (1..count).map {
            if (random.nextFloat() < 0.3f) {
                generateBreadcrumb(
                    eventTypes = listOf("error_occurred", "crash_detected", "network_error", "timeout"),
                    launchId = launchId,
                    minExtras = 3,
                    maxExtras = 10,
                    minStringLength = 20,
                    maxStringLength = 100
                )
            } else {
                generateBreadcrumb(launchId = launchId)
            }
        }
    }

    private fun generateNetworkIntensiveBreadcrumbs(count: Int, launchId: String): List<Breadcrumb> {
        return (1..count).map {
            generateBreadcrumb(
                eventTypes = listOf("network_request", "api_call", "file_upload", "download_started", "sync_data"),
                threadNames = listOf("network", "background", "upload", "download"),
                launchId = launchId,
                minExtras = 1,
                maxExtras = 6,
                minStringLength = 15,
                maxStringLength = 30
            )
        }
    }

    enum class UsagePattern {
        HEAVY_USER,
        CASUAL_USER,
        ERROR_PRONE,
        NETWORK_INTENSIVE
    }
}
