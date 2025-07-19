package com.example.ringbuffer.breadcrumbs.storage.ringfilebuffer.boundarydetector

import com.example.ringbuffer.breadcrumbs.storage.ringfilebuffer.EntityBoundary
import java.io.RandomAccessFile

interface IBoundaryDetector {
    fun findEntityBoundaries(file: RandomAccessFile): List<EntityBoundary>
    fun calculateEntitySize(jsonLine: String): Int
    fun findNextValidPosition(file: RandomAccessFile, currentPos: Long): Long
    fun handleWrappedEntities(file: RandomAccessFile): List<EntityBoundary>
    fun writeEntity(file: RandomAccessFile, jsonLine: String, position: Long): EntityBoundary
}