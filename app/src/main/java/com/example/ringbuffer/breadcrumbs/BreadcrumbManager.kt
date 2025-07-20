package com.example.ringbuffer.breadcrumbs

import android.content.Context
import android.util.Log
import com.example.ringbuffer.breadcrumbs.storage.BreadcrumbStorageManager
import com.example.ringbuffer.breadcrumbs.storage.IFileWriter
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Core breadcrumb manager implementing recording and file provider interfaces.
 * Uses Kotlin idioms like coroutines, atomic operations, and concurrent collections.
 */
class BreadcrumbManager(
    private val storageManager: BreadcrumbStorageManager,
    private val config: BreadcrumbConfig
) : IBreadcrumbRecorder, IBreadcrumbFileProvider {
    companion object {
        private const val TAG = "BreadcrumbManager"
        private const val MAX_BUFFER_SIZE = 100
    }

    private val buffer = ArrayDeque<Breadcrumb>(config.maxBreadcrumbsPerLaunch)
    private val flushExecutor: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "BreadcrumbFlushThread").apply { isDaemon = true }
        }
    private val bufferLock = ReentrantLock()

    init {
        // Schedule periodic flushing using config values
        flushExecutor.scheduleWithFixedDelay(
            { forceFlush() },
            config.flushIntervalSeconds.toLong(),
            config.flushIntervalSeconds.toLong(),
            TimeUnit.SECONDS
        )
    }

    override fun addBreadcrumb(
        eventType: String,
        launchId: String,
        vararg extras: String,
        isCritical: Boolean
    ): Boolean {
        if (!config.enabled) {
            return false
        }

        return try {
            val breadcrumb = Breadcrumb(
                eventType = eventType,
                launchId = launchId,
                timestamp = System.currentTimeMillis(),
                extras = extras.toList(),
            )

            bufferLock.withLock {
                buffer.add(breadcrumb)

                // Force flush if critical or buffer is full
                if (isCritical || buffer.size >= MAX_BUFFER_SIZE) {
                    flushExecutor.submit { forceFlush() }
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add breadcrumb", e)
            false
        }
    }

    override fun getFinishedFiles(): List<File> {
        return storageManager.getFinishedFiles()
    }

    override fun cleanupOldFiles(): Boolean {
        return try {
            val files = getFinishedFiles()
            val cutoffTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)

            var allDeleted = true
            files.forEach { file ->
                if (file.lastModified() < cutoffTime) {
                    if (!storageManager.deleteFile(file.absolutePath)) {
                        allDeleted = false
                    }
                }
            }
            allDeleted
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
            false
        }
    }

    fun shutdown() {
        try {
            // Flush remaining breadcrumbs
            forceFlush()

            // Shutdown executor
            flushExecutor.shutdown()
            if (!flushExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                flushExecutor.shutdownNow()
            }

            // Shutdown storage manager
            storageManager.shutdown()
        } catch (e: Exception) {
            Log.e(TAG, "Error during shutdown", e)
        }
    }

    private fun forceFlush(): Boolean {
        return bufferLock.withLock {
            if (buffer.isEmpty()) {
                return true
            }

            val breadcrumbsToFlush = buffer.toList()
            buffer.clear()

                Log.e(TAG, "Failed to flush ${breadcrumbsToFlush.size} breadcrumbs")
                // Re-add failed breadcrumbs to buffer
                buffer.addAll(0, breadcrumbsToFlush)
//            val success = storageManager.writeBreadcrumbs(breadcrumbsToFlush)
//            if (!success) {
//            }
//            success
        }
    }
}
