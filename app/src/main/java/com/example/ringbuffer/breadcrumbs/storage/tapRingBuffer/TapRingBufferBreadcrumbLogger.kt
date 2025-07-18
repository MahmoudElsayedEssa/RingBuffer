package com.example.ringbuffer.breadcrumbs.storage.tapRingBuffer

import android.content.Context
import com.example.ringbuffer.breadcrumbs.Breadcrumb
import com.example.ringbuffer.breadcrumbs.Breadcrumb.Companion.toJson
import com.example.ringbuffer.tap.QueueFile
import java.io.File
import java.io.IOException

class TapRingBufferBreadcrumbLogger(context: Context, private val maxEntries: Int) {
    private val queueFile: QueueFile

    init {
        val file = File(
            context.filesDir,
            "breadcrumbs-${System.currentTimeMillis()}.txt"
        )
        queueFile =
            QueueFile.Builder(
                file
            )
                .build()

    }


    @Throws(IOException::class)
    fun flush(breadcrumbs: List<Breadcrumb>) {
        for (breadcrumb in breadcrumbs) {
            write(breadcrumb.toJson())
        }
    }


    // Flush to disk, ring buffer style
    @Throws(IOException::class)
    fun write(breadCrumb: String) {
        if (queueFile.size() >= maxEntries) queueFile.remove() // overwrite oldest
        queueFile.add(breadCrumb.toByteArray())
    }


    // Optional: Cleanup
    @Throws(IOException::class)
    fun shutdown() = queueFile.close()
}