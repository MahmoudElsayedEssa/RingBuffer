package com.example.ringbuffer.breadcrumbs

import com.example.ringbuffer.breadcrumbs.Breadcrumb.Companion.toJson
import kotlin.random.Random

/**
 * Breadcrumb generators for testing purposes with different size ranges
 */
object BreadcrumbGenerators {

    private val random = Random.Default


    object BreadcrumbSize {
        const val SMALL_512 = 512
        const val MEDIUM_1k = 1000
        const val LARGE_10k = 10000
    }




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
     * Core breadcrumb generator with configurable parameters
     */
    private fun generateBreadcrumb(
        eventTypes: List<String>,
        threadNames: List<String>,
        minExtras: Int,
        maxExtras: Int,
        minStringLength: Int,
        maxStringLength: Int,
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
            extras = extras,
            threadName = threadName
        )
    }


    /**
     * Generate breadcrumbs of only one size type
     */
    fun generateSingleSizeList(count: Int, size: Int): List<Breadcrumb> {
        return (1..count).map {
            createDummyBreadcrumbWithSize(size)
        }
    }


    /**
     * Creates a dummy breadcrumb with exact size in bytes after JSON serialization
     * Just for testing - content doesn't matter, only the size
     */
    fun createDummyBreadcrumbWithSize(targetSizeBytes: Int): Breadcrumb {
        // Create minimal base structure to measure actual overhead
        val baseBreadcrumb = Breadcrumb.create(
            eventType = "test",
            extras = emptyList(),
            threadName = "t"
        )

        val baseSize = baseBreadcrumb.toJson().toByteArray().size

        require(targetSizeBytes >= baseSize) {
            "Target size must be at least $baseSize bytes (minimum possible size)"
        }

        if (baseSize >= targetSizeBytes) {
            // Already at or above target size
            return baseBreadcrumb
        }

        // Create test breadcrumb with one character to measure exact overhead
        val testBreadcrumb = Breadcrumb.create(
            eventType = "test",
            extras = listOf("X"),
            threadName = "t"
        )

        val testSize = testBreadcrumb.toJson().toByteArray().size
        val actualOverhead = testSize - baseSize - 1 // -1 for the "X" character

        // Calculate exact padding needed
        val paddingNeeded = targetSizeBytes - baseSize - actualOverhead

        if (paddingNeeded <= 0) {
            return baseBreadcrumb
        }

        // Create dummy padding string
        val padding = "X".repeat(paddingNeeded)

        val result = Breadcrumb.create(
            eventType = "test",
            extras = listOf(padding),
            threadName = "t"
        )

        // Verify and fine-tune if needed
        val actualSize = result.toJson().toByteArray().size
        val sizeDiff = targetSizeBytes - actualSize

        if (sizeDiff == 0) {
            return result
        }

        // Fine-tune by adjusting padding length
        val adjustedPadding = if (sizeDiff > 0) {
            padding + "X".repeat(sizeDiff)
        } else {
            padding.dropLast(-sizeDiff)
        }

        return Breadcrumb.create(
            eventType = "test",
            extras = listOf(adjustedPadding),
            threadName = "t"
        )
    }


    val smallBreadcrumb512 = lazy { createDummyBreadcrumbWithSize(BreadcrumbSize.SMALL_512) }
    val mediumBreadcrumb1k = lazy { createDummyBreadcrumbWithSize(BreadcrumbSize.MEDIUM_1k) }
    val largeBreadcrumb10K = lazy { createDummyBreadcrumbWithSize(BreadcrumbSize.LARGE_10k) }



}
