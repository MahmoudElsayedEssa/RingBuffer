package com.example.ringbuffer.breadcrumbs.storage

import com.example.ringbuffer.breadcrumbs.storage.ringfilebuffer.EntityBoundary
import java.io.Closeable

interface IFileWriter : Closeable {
    fun addEntity(jsonLine: String): Boolean
    fun addEntities(jsonLines: List<String>): List<EntityBoundary>
}
