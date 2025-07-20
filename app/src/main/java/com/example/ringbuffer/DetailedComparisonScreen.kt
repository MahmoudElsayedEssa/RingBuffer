package com.example.ringbuffer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ringbuffer.breadcrumbs.Breadcrumb
import com.example.ringbuffer.breadcrumbs.BreadcrumbGenerators
import com.example.ringbuffer.breadcrumbs.storage.batchedfiles.BatchesFile
import com.example.ringbuffer.breadcrumbs.storage.ringfilebuffer.BoundaryDetectionMode
import com.example.ringbuffer.breadcrumbs.storage.ringfilebuffer.RingBufferConfig
import com.example.ringbuffer.breadcrumbs.storage.ringfilebuffer.RingFileBuffer
import com.example.ringbuffer.breadcrumbs.storage.tapRingBuffer.TapRingBufferBreadcrumbLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.system.measureTimeMillis


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailedComparisonScreen() {
    val context = LocalContext.current
    var testResults by remember { mutableStateOf<List<DetailedTestResult>>(emptyList()) }
    var isRunning by remember { mutableStateOf(false) }
    var selectedSize by remember { mutableStateOf(BreadcrumbGenerators.BreadcrumbSize.SMALL_512) }
    var breadcrumbCount by remember { mutableStateOf("1000") }
    var useMultipleTestCounts by remember { mutableStateOf(false) }
    var customTestCounts by remember { mutableStateOf("100,500,1000,2000") }
    var maxBreadcrumbLimit by remember { mutableStateOf("5000") }
    val breadcrumbs: List<Breadcrumb> =
        BreadcrumbGenerators.generateSingleSizeList(breadcrumbCount.toInt(), selectedSize.value)


    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Detailed File Writer Analysis",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        // Configuration Card
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Test Configuration",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                // Breadcrumb Size Selection
                Text(
                    text = "Breadcrumb Size",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(BreadcrumbGenerators.BreadcrumbSize.entries) { size ->

                        FilterChip(
                            onClick = { selectedSize = size },
                            label = { Text("${if (size == BreadcrumbGenerators.BreadcrumbSize.MIXED) "Mixed " else size.value}B") },
                            selected = selectedSize == size
                        )
                    }
                }

                // Max Breadcrumb Limit Configuration
                OutlinedTextField(
                    value = maxBreadcrumbLimit,
                    onValueChange = { maxBreadcrumbLimit = it },
                    label = { Text("Max Breadcrumb Capacity") },
                    placeholder = { Text("5000") },
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = {
                        Text("Maximum number of breadcrumbs the writers can hold (ring buffer capacity)")
                    }
                )

                // Test Count Configuration
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = useMultipleTestCounts,
                        onCheckedChange = { useMultipleTestCounts = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Use multiple test counts for comparison",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                if (useMultipleTestCounts) {
                    OutlinedTextField(
                        value = customTestCounts,
                        onValueChange = { customTestCounts = it },
                        label = { Text("Test Counts (comma-separated)") },
                        placeholder = { Text("e.g., 100,500,1000,2000") },
                        modifier = Modifier.fillMaxWidth(),
                        supportingText = {
                            Text("Multiple tests will be run and averaged for each writer")
                        }
                    )
                } else {
                    OutlinedTextField(
                        value = breadcrumbCount,
                        onValueChange = { breadcrumbCount = it },
                        label = { Text("Number of Breadcrumbs") },
                        placeholder = { Text("1000") },
                        modifier = Modifier.fillMaxWidth(),
                        supportingText = {
                            Text("Single test run with specified count")
                        }
                    )
                }

                // Show warning if test count exceeds max limit
                val maxLimit = maxBreadcrumbLimit.toIntOrNull() ?: 5000
                val testCounts = if (useMultipleTestCounts) {
                    customTestCounts.split(",").mapNotNull { it.trim().toIntOrNull() }
                } else {
                    listOf(breadcrumbCount.toIntOrNull() ?: 1000)
                }

                val hasOverflow = testCounts.any { it > maxLimit }
                if (hasOverflow) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "⚠️ Some test counts exceed max capacity - this will test ring buffer overflow behavior",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        val finalTestCounts = if (useMultipleTestCounts) {
                            customTestCounts.split(",")
                                .mapNotNull { it.trim().toIntOrNull() }
                                .takeIf { it.isNotEmpty() } ?: listOf(1000)
                        } else {
                            listOf(breadcrumbCount.toIntOrNull() ?: 1000)
                        }


                        runDetailedTests(
                            context,
                            selectedSize,
                            finalTestCounts,
                            maxLimit,
                            breadcrumbs
                        ) { results ->
                            testResults = results
                            isRunning = false
                        }
                        isRunning = true
                    },
                    enabled = !isRunning,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Testing...")
                    } else {
                        val testCountText = if (useMultipleTestCounts) {
                            val counts =
                                customTestCounts.split(",").mapNotNull { it.trim().toIntOrNull() }
                            "Run Tests (${counts.size} scenarios, max: $maxLimit)"
                        } else {
                            "Run Test (${breadcrumbCount} breadcrumbs, max: $maxLimit)"
                        }
                        Text(testCountText)
                    }
                }
            }
        }

        // Results
        if (testResults.isNotEmpty()) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(testResults) { result ->
                    DetailedTestResultCard(result = result)
                }
            }
        }
    }
}

@Composable
fun DetailedTestResultCard(result: DetailedTestResult) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = result.writerName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            // Test Scenarios and Capacity Info
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = result.testScenarios,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Max Capacity: ${result.maxCapacity}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                result.overflowBehavior?.let { overflow ->
                    Text(
                        text = "Overflow: $overflow",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Performance Metrics
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Performance",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MetricColumn("Avg Write Time", "${result.avgWriteTimeMs}ms")
                    MetricColumn("Min Time", "${result.minWriteTimeMs}ms")
                    MetricColumn("Max Time", "${result.maxWriteTimeMs}ms")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MetricColumn("Throughput", "${result.throughputPerSec}/sec")
                    MetricColumn("Success Rate", "${result.successRate}%")
                    MetricColumn("Error Count", "${result.errorCount}")
                }
            }

            // File System Metrics
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "File System",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MetricColumn("Total Size", "${result.totalFileSizeKB}KB")
                    MetricColumn("File Count", "${result.fileCount}")
                    MetricColumn("Efficiency", "${result.storageEfficiency}%")
                }
            }

            // Test Details
            result.details?.let { details ->
                Text(
                    text = details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            result.error?.let { error ->
                Text(
                    text = "Error: $error",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun MetricColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

data class DetailedTestResult(
    val writerName: String,
    val avgWriteTimeMs: Long,
    val minWriteTimeMs: Long,
    val maxWriteTimeMs: Long,
    val throughputPerSec: Long,
    val successRate: Double,
    val errorCount: Int,
    val totalFileSizeKB: Long,
    val fileCount: Int,
    val storageEfficiency: Double,
    val testScenarios: String, // Updated to show test scenarios
    val maxCapacity: Int, // Added max capacity info
    val overflowBehavior: String? = null, // Added overflow behavior info
    val details: String? = null,
    val error: String? = null
)

private fun runDetailedTests(
    context: android.content.Context,
    size: BreadcrumbGenerators.BreadcrumbSize,
    testCounts: List<Int>, // Now accepts custom test counts
    maxCapacity: Int, // Added max capacity parameter
    breadcrumbs: List<Breadcrumb>,
    onResults: (List<DetailedTestResult>) -> Unit,
) {

    kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch {


        val results = withContext(Dispatchers.IO) {
            listOf(
                runDetailedTest(
                    "TapRingBuffer",
                    testCounts,
                    size.value,
                    maxCapacity
                ) { count, sizeBytes, maxCap ->
                    testTapRingBufferDetailed(context, count, sizeBytes, maxCap, breadcrumbs)
                },
                runDetailedTest(
                    "RingFileBuffer",
                    testCounts,
                    size.value,
                    maxCapacity
                ) { count, sizeBytes, maxCap ->
                    testRingFileBufferDetailed(context, count, sizeBytes, maxCap, breadcrumbs)
                },
                runDetailedTest(
                    "BatchesFile",
                    testCounts,
                    size.value,
                    maxCapacity
                ) { count, sizeBytes, maxCap ->
                    testBatchesFileDetailed(context, count, sizeBytes, maxCap, breadcrumbs)
                }
            )
        }
        onResults(results)
    }
}

private fun runDetailedTest(
    writerName: String,
    testCounts: List<Int>,
    size: Int,
    maxCapacity: Int,
    testFunction: (Int, Int, Int) -> TestMetrics
): DetailedTestResult {
    return try {
        val metrics = testCounts.map { count ->
            // Run each test scenario and clean up between tests
            cleanupTestFiles(writerName)

            testFunction(count, size, maxCapacity)
        }

        val avgWriteTime =
            if (metrics.isNotEmpty()) metrics.map { it.writeTimeMs }.average().toLong() else 0L
        val minWriteTime = if (metrics.isNotEmpty()) metrics.minOf { it.writeTimeMs } else 0L
        val maxWriteTime = if (metrics.isNotEmpty()) metrics.maxOf { it.writeTimeMs } else 0L
        val avgThroughput =
            if (metrics.isNotEmpty()) metrics.map { it.throughputPerSec }.average().toLong() else 0L
        val successRate =
            if (metrics.isNotEmpty()) metrics.map { it.successRate }.average() else 0.0
        val totalErrors = metrics.sumOf { it.errorCount }
        val avgFileSize =
            if (metrics.isNotEmpty()) metrics.map { it.fileSizeKB }.average().toLong() else 0L
        val avgFileCount =
            if (metrics.isNotEmpty()) metrics.map { it.fileCount }.average().toInt() else 0
        val efficiency = calculateStorageEfficiency(metrics)

        val testScenariosText = if (testCounts.size == 1) {
            "${testCounts.first()} breadcrumbs"
        } else {
            "${testCounts.size} scenarios: ${testCounts.joinToString()}"
        }

        val overflowInfo = if (testCounts.any { it > maxCapacity }) {
            when (writerName) {
                "TapRingBuffer" -> "Oldest entries overwritten when capacity exceeded"
                "RingFileBuffer" -> "Circular overwrite of oldest entities"
                "BatchesFile" -> "New batch files created (no overflow limit)"
                else -> null
            }
        } else null

        DetailedTestResult(
            writerName = writerName,
            avgWriteTimeMs = avgWriteTime,
            minWriteTimeMs = minWriteTime,
            maxWriteTimeMs = maxWriteTime,
            throughputPerSec = avgThroughput,
            successRate = successRate,
            errorCount = totalErrors,
            totalFileSizeKB = avgFileSize,
            fileCount = avgFileCount,
            storageEfficiency = efficiency,
            testScenarios = testScenariosText,
            maxCapacity = maxCapacity,
            overflowBehavior = overflowInfo,
            details = "Size: ${size}B, Max: $maxCapacity, Tests: $testScenariosText"
        )
    } catch (e: Exception) {
        DetailedTestResult(
            writerName = writerName,
            avgWriteTimeMs = 0,
            minWriteTimeMs = 0,
            maxWriteTimeMs = 0,
            throughputPerSec = 0,
            successRate = 0.0,
            errorCount = 1,
            totalFileSizeKB = 0,
            fileCount = 0,
            storageEfficiency = 0.0,
            testScenarios = "Failed",
            maxCapacity = maxCapacity,
            error = e.message
        )
    }
}

// Add cleanup function to ensure clean tests
private fun cleanupTestFiles(writerName: String) {
    try {
        // This is a basic cleanup - you might want to make it more sophisticated
        // based on your specific file naming patterns
        when (writerName) {
            "RingFileBuffer" -> {
                // RingFileBuffer creates files with timestamp, so cleanup would be file-pattern based
            }

            "BatchesFile" -> {
                // BatchesFile creates batch directories that could be cleaned up
            }

            "TapRingBuffer" -> {
                // TapRingBuffer files are managed internally
            }
        }
    } catch (e: Exception) {
        // Ignore cleanup errors
    }
}

private fun calculateStorageEfficiency(metrics: List<TestMetrics>): Double {
    val totalDataSize = metrics.sumOf { it.dataSize }
    val totalFileSize = metrics.sumOf { it.fileSizeKB * 1024 }
    return if (totalFileSize > 0) (totalDataSize.toDouble() / totalFileSize) * 100 else 0.0
}

data class TestMetrics(
    val writeTimeMs: Long,
    val throughputPerSec: Long,
    val successRate: Double,
    val errorCount: Int,
    val fileSizeKB: Long,
    val fileCount: Int,
    val dataSize: Long
)

private fun testTapRingBufferDetailed(
    context: android.content.Context,
    count: Int,
    size: Int,
    maxCapacity: Int,
    breadcrumbs: List<Breadcrumb>
): TestMetrics {

    val jsonLines = breadcrumbs.map { it.toJsonString() }
    val dataSize = jsonLines.sumOf { it.toByteArray().size.toLong() }
    val file = File(
        context.filesDir,
        "Tap-breadcrumbs-${System.currentTimeMillis()}.txt"
    )
    val writer =
        TapRingBufferBreadcrumbLogger(context, file, maxCapacity) // Use provided max capacity

    writer.queueFile.clear()
    val writeTime = measureTimeMillis {
        writer.addEntities(jsonLines)
    }

    val throughput = if (writeTime > 0) (count * 1000L) / writeTime else 0L
    val fileSize = if (file.exists()) file.length() / 1024 else 0L

    writer.close()

    return TestMetrics(
        writeTimeMs = writeTime,
        throughputPerSec = throughput,
        successRate = 100.0,
        errorCount = 0,
        fileSizeKB = fileSize, // QueueFile doesn't expose size easily
        fileCount = 1,
        dataSize = dataSize
    )
}

private fun testRingFileBufferDetailed(
    context: android.content.Context,
    count: Int,
    size: Int,
    maxCapacity: Int,
    breadcrumbs: List<Breadcrumb>
): TestMetrics {
    val jsonLines = breadcrumbs.map { it.toJsonString() }
    val dataSize = jsonLines.sumOf { it.toByteArray().size.toLong() }

    val file = File(context.filesDir, "ringbuffer_detailed_${System.currentTimeMillis()}.txt")
    val config = RingBufferConfig(
        maxEntities = maxCapacity, // Use provided max capacity
        boundaryDetectionMode = BoundaryDetectionMode.SCANNING
    )
    val writer = RingFileBuffer(file.absolutePath, config)

    val writeTime = measureTimeMillis {
        writer.addEntities(jsonLines)
    }

    val throughput = if (writeTime > 0) (count * 1000L) / writeTime else 0L
    val fileSize = if (file.exists()) file.length() / 1024 else 0L

    writer.close()

    return TestMetrics(
        writeTimeMs = writeTime,
        throughputPerSec = throughput,
        successRate = 100.0,
        errorCount = 0,
        fileSizeKB = fileSize,
        fileCount = 1,
        dataSize = dataSize
    )
}

private fun testBatchesFileDetailed(
    context: android.content.Context,
    count: Int,
    size: Int,
    maxCapacity: Int,
    breadcrumbs: List<Breadcrumb>
): TestMetrics {
//    val breadcrumbs = BreadcrumbGenerators.generateSingleSizeList(count, size)
    val jsonLines = breadcrumbs.map { it.toJsonString() }
    val dataSize = jsonLines.sumOf { it.toByteArray().size.toLong() }

    // Note: BatchesFile doesn't have a hard capacity limit like ring buffers
    // It manages capacity through file rotation, so maxCapacity is informational
    val writer = BatchesFile(context)

    val writeTime = measureTimeMillis {
        writer.addEntities(jsonLines)
        writer.flushToDisk()
    }

    val throughput = if (writeTime > 0) (count * 1000L) / writeTime else 0L

    // Calculate batch file metrics
    val batchDir = File(context.filesDir, "batches")
    val batchFiles = batchDir.listFiles() ?: emptyArray()
    val totalSize = batchFiles.sumOf { it.length() } / 1024

    writer.close()

    return TestMetrics(
        writeTimeMs = writeTime,
        throughputPerSec = throughput,
        successRate = 100.0,
        errorCount = 0,
        fileSizeKB = totalSize,
        fileCount = batchFiles.size,
        dataSize = dataSize
    )
} 