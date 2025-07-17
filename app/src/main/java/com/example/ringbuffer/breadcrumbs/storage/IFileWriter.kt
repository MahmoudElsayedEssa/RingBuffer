package com.example.ringbuffer.breadcrumbs.storage

interface IFileWriter {
    fun appendContent(filePath: String, content: String): Boolean
    fun flushBuffer(filePath: String)
}
