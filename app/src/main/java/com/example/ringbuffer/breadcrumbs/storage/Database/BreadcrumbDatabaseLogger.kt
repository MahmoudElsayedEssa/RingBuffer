package com.example.ringbuffer.breadcrumbs.storage.Database

import android.content.Context
import com.example.ringbuffer.breadcrumbs.storage.IFileWriter

class BreadcrumbDatabaseLogger(context: Context,  maxEntities: Int) : IFileWriter {
    private val repository = BreadcrumbRepository(context, maxEntities)
    private var isClosed = false

    override fun addEntity(jsonLine: String) {
        checkNotClosed()
        repository.insertMultiple(listOf<String>(jsonLine))
    }

    override fun addEntities(jsonLines: List<String>) {
        checkNotClosed()
        repository.insertMultiple(jsonLines)
    }

    fun clearDatabase() {
        repository.clearDatabase()
    }


    override fun close() {
        if (!isClosed) {
            // Note: BreadcrumbRepository doesn't currently expose a close method
            // In a real implementation, you might want to add a close method to BreadcrumbRepository
            // to properly close the database connection
            isClosed = true
        }
    }

    private fun checkNotClosed() {
        if (isClosed) {
            throw IllegalStateException("DatabaseLogger has been closed")
        }
    }
}