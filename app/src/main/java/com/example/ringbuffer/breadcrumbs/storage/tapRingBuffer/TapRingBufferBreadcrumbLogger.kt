package com.example.ringbuffer.breadcrumbs.storage.tapRingBuffer

import android.content.Context
import android.util.Log
import com.example.ringbuffer.breadcrumbs.Breadcrumb
import com.example.ringbuffer.breadcrumbs.Breadcrumb.Companion.toJson
import com.example.ringbuffer.tap.QueueFile
import java.io.File
import java.io.IOException

class TapRingBufferBreadcrumbLogger(context: Context, private val maxEntries: Int) {
    private val queueFile: QueueFile =
        QueueFile.Builder(File(context.filesDir, "breadcrumbs-${System.currentTimeMillis()}.txt"))
            .build()


    @Throws(IOException::class)
    fun flush(breadcrumbs: List<Breadcrumb>) {
        queueFile.clear()
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