package com.example.ringbuffer.breadcrumbs.storage

import android.content.Context
import android.util.Log
import com.example.ringbuffer.breadcrumbs.Breadcrumb
import com.example.ringbuffer.breadcrumbs.BreadcrumbConfig
import com.example.ringbuffer.breadcrumbs.IBreadcrumbFileProvider
import com.example.ringbuffer.breadcrumbs.IBreadcrumbRecorder
import com.example.ringbuffer.breadcrumbs.toJsonStringList
import java.io.File
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class BreadcrumbStorageManager(
    private val context: Context,
    private val fileWriter: IFileWriter
) {
    companion object {
        private const val TAG = "BreadcrumbStorageManager"
        private const val BREADCRUMBS_DIR = "breadcrumbs"
    }

    fun writeBreadcrumbs(breadcrumbs: List<Breadcrumb>): Boolean {
        return try {
            val jsonLines = breadcrumbs.toJsonStringList()
            val boundaries = fileWriter.addEntities(jsonLines)
            boundaries.isNotEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "Error writing breadcrumbs", e)
            false
        }
    }

    fun getFinishedFiles(): List<File> {
        return try {
            val storageDir = File(context.filesDir, BREADCRUMBS_DIR)
            if (storageDir.exists() && storageDir.isDirectory) {
                storageDir.listFiles()?.filter { it.isFile }?.toList() ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting finished files", e)
            emptyList()
        }
    }

    fun deleteFile(filePath: String): Boolean {
        return try {
            val file = File(filePath)
            if (file.exists()) {
                file.delete()
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting file: $filePath", e)
            false
        }
    }

    fun shutdown() {
        try {
            fileWriter.close()
            cleanupDirectory()
        } catch (e: Exception) {
            Log.e(TAG, "Error during shutdown", e)
        }
    }

    private fun cleanupDirectory() {
        try {
            val storageDir = File(context.filesDir, BREADCRUMBS_DIR)
            if (storageDir.exists()) {
                storageDir.listFiles()?.forEach { file ->
                    if (file.isFile && file.lastModified() < System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)) {
                         if (!file.delete()) {
                            Log.w(TAG, "Failed to delete old file: ${file.name}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during directory cleanup", e)
        }
    }





}
