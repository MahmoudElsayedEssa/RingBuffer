package com.example.ringbuffer.breadcrumbs.storage.ringbuffer

import com.example.ringbuffer.breadcrumbs.storage.IFileWriter
import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class RingBufferFileWriter(
    private val maxEntries: Int = 1000,
    private val avgEntrySize: Int = 300
) : IFileWriter, Closeable {

    data class Stats(
        val totalEntries: Int,
        val maxEntries: Int,
        val overwriteCount: Long,
        val totalBytesWritten: Long,
        val errorCount: Long
    )

    private val fileBuffers = mutableMapOf<String, RingBuffer>()
    private val lock = ReentrantLock()
    private val flushThread: Thread

    @Volatile
    private var closed = false

    init {
        // Start background write thread
        flushThread = Thread({
            while (!closed && !Thread.currentThread().isInterrupted) {
                try {
                    Thread.sleep(2000) // Flush every 2 seconds
                    flushAll()
                } catch (_: InterruptedException) {
                    break
                }
            }
        }, "RingBufferFlush").apply {
            isDaemon = true
            start()
        }
    }

    override fun appendContent(filePath: String, content: String): Boolean {
        if (closed) return false
        return try {
            getOrCreateBuffer(filePath).write(content + "\n")
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun flushBuffer(filePath: String) {
        if (closed) return
        lock.withLock {
            fileBuffers[filePath]?.flush()
        }
    }

    fun getStats(filePath: String): Stats? {
        return lock.withLock {
            fileBuffers[filePath]?.getStats()
        }
    }

    private fun getOrCreateBuffer(filePath: String): RingBuffer {
        return lock.withLock {
            fileBuffers.getOrPut(filePath) {
                RingBuffer(File(filePath), maxEntries, avgEntrySize)
            }
        }
    }

    private fun flushAll() {
        lock.withLock {
            fileBuffers.values.forEach { it.flush() }
        }
    }

    override fun close() {
        if (closed) return
        closed = true

        flushThread.interrupt()
        try {
            flushThread.join(1000)
        } catch (_: InterruptedException) {
        }

        lock.withLock {
            fileBuffers.values.forEach { it.close() }
            fileBuffers.clear()
        }
    }

    private class RingBuffer(
        file: File,
        maxEntries: Int,
        avgEntrySize: Int
    ) : Closeable {

        private val bufferSize = maxEntries * avgEntrySize * 2L // 2x buffer for safety
        private val randomAccessFile: RandomAccessFile
        private val fileChannel: FileChannel
        private val mappedBuffer: MappedByteBuffer

        private val writePosition = AtomicLong(0)
        private val entryCount = AtomicInteger(0)
        private val overwriteCount = AtomicLong(0)
        private val totalBytesWritten = AtomicLong(0)
        private val errorCount = AtomicLong(0)
        private val maxEntries = maxEntries

        private val writeLock = ReentrantLock()

        init {
            file.parentFile?.mkdirs()
            randomAccessFile = RandomAccessFile(file, "rw")
            fileChannel = randomAccessFile.channel

            // Pre-allocate and map file
            randomAccessFile.setLength(bufferSize)
            mappedBuffer = fileChannel.map(FileChannel.MapMode.READ_WRITE, 0, bufferSize)
        }

        fun write(content: String): Boolean {
            return writeLock.withLock {
                try {
                    val bytes = content.toByteArray(Charsets.UTF_8)
                    val currentPos = writePosition.getAndAdd(bytes.size.toLong())
                    val bufferPos = (currentPos % bufferSize).toInt()

                    writeToBuffer(bytes, bufferPos)
                    updateCounters(bytes.size)
                    true
                } catch (e: Exception) {
                    errorCount.incrementAndGet()
                    false
                }
            }
        }

        private fun writeToBuffer(data: ByteArray, startPos: Int) {
            synchronized(mappedBuffer) {
                if (startPos + data.size <= mappedBuffer.capacity()) {
                    // No wrapping needed
                    mappedBuffer.position(startPos)
                    mappedBuffer.put(data)
                } else {
                    // Handle buffer wrap
                    val firstChunk = mappedBuffer.capacity() - startPos
                    val secondChunk = data.size - firstChunk

                    // Write first part
                    mappedBuffer.position(startPos)
                    mappedBuffer.put(data, 0, firstChunk)

                    // Write second part at beginning
                    mappedBuffer.position(0)
                    mappedBuffer.put(data, firstChunk, secondChunk)
                }
            }
        }

        private fun updateCounters(bytesWritten: Int) {
            totalBytesWritten.addAndGet(bytesWritten.toLong())

            if (entryCount.get() < maxEntries) {
                entryCount.incrementAndGet()
            } else {
                overwriteCount.incrementAndGet()
            }
        }

        fun flush() {
            writeLock.withLock {
                try {
                    mappedBuffer.force()
                } catch (e: Exception) {
                    errorCount.incrementAndGet()
                }
            }
        }

        fun getStats(): Stats = Stats(
            totalEntries = entryCount.get(),
            maxEntries = maxEntries,
            overwriteCount = overwriteCount.get(),
            totalBytesWritten = totalBytesWritten.get(),
            errorCount = errorCount.get()
        )

        override fun close() {
            writeLock.withLock {
                try {
                    mappedBuffer.force()
                    fileChannel.close()
                    randomAccessFile.close()
                } catch (e: Exception) {
                    errorCount.incrementAndGet()
                }
            }
        }
    }
}

// Simple factory methods
object RingBufferWriter {
    @JvmStatic
    fun create(maxEntries: Int = 1000, avgEntrySize: Int = 300): RingBufferFileWriter {
        return RingBufferFileWriter(maxEntries, avgEntrySize)
    }
}