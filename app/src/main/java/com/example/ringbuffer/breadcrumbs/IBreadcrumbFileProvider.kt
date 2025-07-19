package com.example.ringbuffer.breadcrumbs

import java.io.File


interface IBreadcrumbFileProvider {
    
    /**
     * Get list of finished breadcrumb files
     */
    fun getFinishedFiles(): List<File>
    
    /**
     * Clean up old files and return success status
     */
    fun cleanupOldFiles(): Boolean
    
    /**
     * Get files older than specified time (Kotlin extension)
     */
    fun getFilesOlderThan(timeMs: Long): List<File> = 
        getFinishedFiles().filter { it.lastModified() < timeMs }
    
    /**
     * Get files by pattern (Kotlin extension)
     */
    fun getFilesByPattern(pattern: Regex): List<File> = 
        getFinishedFiles().filter { pattern.matches(it.name) }
    
    /**
     * Get total size of all files (Kotlin extension)
     */
    fun getTotalSize(): Long = getFinishedFiles().sumOf { it.length() }
    
    /**
     * Check if cleanup is needed based on file count or size
     */
    fun needsCleanup(maxFiles: Int = 10, maxSizeBytes: Long = 100 * 1024 * 1024): Boolean {
        val files = getFinishedFiles()
        return files.size > maxFiles || files.sumOf { it.length() } > maxSizeBytes
    }
}
