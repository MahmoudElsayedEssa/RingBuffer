package com.example.ringbuffer.breadcrumbs.storage.ringfilebuffer.boundarydetector

import com.example.ringbuffer.breadcrumbs.storage.ringfilebuffer.EntityBoundary
import java.io.RandomAccessFile

class IndexBasedDetector : IBoundaryDetector {
    private val entityIndex = mutableListOf<EntityBoundary>()
    
    override fun findEntityBoundaries(file: RandomAccessFile): List<EntityBoundary> {
        if (entityIndex.isEmpty()) {
            rebuildIndex(file)
        }
        return entityIndex.toList()
    }
    
    override fun calculateEntitySize(jsonLine: String): Int {
        return jsonLine.toByteArray().size + 1 // +1 for \n
    }
    
    override fun findNextValidPosition(file: RandomAccessFile, currentPos: Long): Long {
        // Use index to find next position quickly
        val nextBoundary = entityIndex.find { it.startOffset > currentPos }
        return nextBoundary?.startOffset ?: file.length()
    }
    
    override fun handleWrappedEntities(file: RandomAccessFile): List<EntityBoundary> {
        return entityIndex.filter { it.isWrapped }
    }
    
    override fun writeEntity(file: RandomAccessFile, jsonLine: String, position: Long): EntityBoundary {
        file.seek(position)
        val data = "$jsonLine\n".toByteArray()
        file.write(data)
        
        val boundary = EntityBoundary(
            startOffset = position,
            endOffset = position + data.size,
            size = data.size
        )
        
        // Update index
        addToIndex(boundary)
        
        return boundary
    }
    
    fun addToIndex(boundary: EntityBoundary) {
        entityIndex.add(boundary)
        entityIndex.sortBy { it.startOffset }
    }
    
    fun removeFromIndex(boundary: EntityBoundary) {
        entityIndex.remove(boundary)
    }
    
    private fun rebuildIndex(file: RandomAccessFile) {
        entityIndex.clear()
        file.seek(0)
        
        var currentPos = 0L
        while (currentPos < file.length()) {
            val lineStart = currentPos
            val line = readLine(file)
            
            if (line != null) {
                val lineSize = line.toByteArray().size + 1
                entityIndex.add(EntityBoundary(
                    startOffset = lineStart,
                    endOffset = lineStart + lineSize,
                    size = lineSize
                ))
                currentPos = lineStart + lineSize
            } else {
                break
            }
        }
    }
    
    private fun readLine(file: RandomAccessFile): String? {
        val line = StringBuilder()
        
        while (file.filePointer < file.length()) {
            val byte = file.read()
            if (byte == '\n'.code) {
                return line.toString()
            }
            line.append(byte.toChar())
        }
        
        return if (line.isNotEmpty()) line.toString() else null
    }
}