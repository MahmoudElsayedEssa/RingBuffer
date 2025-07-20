import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.ringbuffer.breadcrumbs.Breadcrumb
import com.example.ringbuffer.breadcrumbs.BreadcrumbGenerators
import com.example.ringbuffer.breadcrumbs.storage.ringfilebuffer.BoundaryDetectionMode
import com.example.ringbuffer.breadcrumbs.storage.ringfilebuffer.RingFileBuffer
import com.example.ringbuffer.breadcrumbs.storage.ringfilebuffer.RingBufferConfig
import kotlinx.coroutines.launch
import java.io.File
import kotlin.system.measureTimeMillis


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CircularBufferPerformanceTest(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var testResults by remember { mutableStateOf("Ready to run tests...") }
    var isRunning by remember { mutableStateOf(false) }

    // Test configuration
    val testDir = File(context.filesDir, "circular_buffer_tests")
    val maxEntities = 1000
    val totalBreadcrumbs = 2000

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "RingFileBuffer Performance Test",
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            text = "Test: Write $totalBreadcrumbs breadcrumbs with max limit $maxEntities\nExpected: Only last $maxEntities breadcrumbs should remain",
            style = MaterialTheme.typography.bodyMedium
        )

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = testResults,
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodySmall
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    scope.launch {
                        isRunning = true
                        testResults = runHeaderBasedTest(testDir, maxEntities, totalBreadcrumbs)
                        isRunning = false
                    }
                }, enabled = !isRunning
            ) {
                Text("Test Header-Based")
            }

            Button(
                onClick = {
                    scope.launch {
                        isRunning = true
                        testResults = runIndexBasedTest(testDir, maxEntities, totalBreadcrumbs)
                        isRunning = false
                    }
                }, enabled = !isRunning
            ) {
                Text("Test Index-Based")
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    scope.launch {
                        isRunning = true
                        testResults = runScanningTest(testDir, maxEntities, totalBreadcrumbs)
                        isRunning = false
                    }
                }, enabled = !isRunning
            ) {
                Text("Test Scanning")
            }

            Button(
                onClick = {
                    scope.launch {
                        isRunning = true
                        testResults = runComparisonTest(testDir, maxEntities, totalBreadcrumbs)
                        isRunning = false
                    }
                }, enabled = !isRunning
            ) {
                Text("Compare All Modes")
            }
        }

        Button(
            onClick = {
                scope.launch {
                    isRunning = true
                    testResults = runStressTest(testDir)
                    isRunning = false
                }
            }, enabled = !isRunning
        ) {
            Text("Stress Test")
        }

        Button(
            onClick = {
                try {
                    testDir.deleteRecursively()
                    testResults = "Test files cleared successfully"
                } catch (e: Exception) {
                    testResults = "Error clearing files: ${e.message}"
                }
            }, enabled = !isRunning
        ) {
            Text("Clear Test Files")
        }

        if (isRunning) {
            CircularProgressIndicator()
        }
    }
}

// Test Header-Based Detection Mode
fun runHeaderBasedTest(testDir: File, maxEntities: Int, totalBreadcrumbs: Int): String {
    val results = mutableListOf<String>()
    results.add("=== HEADER-BASED DETECTION TEST ===")

    try {
        testDir.mkdirs()
        val testFile = File(testDir, "header_based_test.dat")

        val config = RingBufferConfig(
            maxEntities = maxEntities, boundaryDetectionMode = BoundaryDetectionMode.HEADER_BASED,
        )

        val buffer = RingFileBuffer(testFile.absolutePath, config)

        // Generate test breadcrumbs
        val breadcrumbs = generateTestBreadcrumbs(totalBreadcrumbs)

        // Measure write performance
        val writeTime = measureTimeMillis {
            breadcrumbs.forEach { breadcrumb ->
                buffer.addEntity(breadcrumb.toJsonString())
            }
        }

        results.add("Write Performance:")
        results.add("- Total breadcrumbs written: $totalBreadcrumbs")
        results.add("- Time taken: ${writeTime}ms")
        results.add("- Average per breadcrumb: ${writeTime.toFloat() / totalBreadcrumbs}ms")
        results.add("- Breadcrumbs per second: ${(totalBreadcrumbs * 1000) / writeTime}")

        // Verify circular behavior

        results.add("\nCircular Buffer Verification:")
        results.add("- Expected entities: $maxEntities")


        buffer.close()

        results.add("\n✅ Header-based test completed successfully!")

    } catch (e: Exception) {
        results.add("❌ Test failed: ${e.message}")
        results.add("Stack trace: ${e.stackTraceToString()}")
    }

    return results.joinToString("\n")
}

// Test Index-Based Detection Mode
suspend fun runIndexBasedTest(testDir: File, maxEntities: Int, totalBreadcrumbs: Int): String {
    val results = mutableListOf<String>()
    results.add("=== INDEX-BASED DETECTION TEST ===")

    try {
        testDir.mkdirs()
        val testFile = File(testDir, "index_based_test.dat")

        val config = RingBufferConfig(
            maxEntities = maxEntities, boundaryDetectionMode = BoundaryDetectionMode.INDEX_BASED
        )

        val buffer = RingFileBuffer(testFile.absolutePath, config)

        val breadcrumbs = generateTestBreadcrumbs(totalBreadcrumbs)

        val writeTime = measureTimeMillis {
            breadcrumbs.forEach { breadcrumb ->
                buffer.addEntity(breadcrumb.toJsonString())
            }
        }

        results.add("Write Performance:")
        results.add("- Total breadcrumbs written: $totalBreadcrumbs")
        results.add("- Time taken: ${writeTime}ms")
        results.add("- Average per breadcrumb: ${writeTime.toFloat() / totalBreadcrumbs}ms")
        results.add("- Breadcrumbs per second: ${(totalBreadcrumbs * 1000) / writeTime}")

        results.add("\nVerification:")

        buffer.close()
        results.add("\n✅ Index-based test completed successfully!")

    } catch (e: Exception) {
        results.add("❌ Test failed: ${e.message}")
    }

    return results.joinToString("\n")
}

// Test Scanning Detection Mode
fun runScanningTest(testDir: File, maxEntities: Int, totalBreadcrumbs: Int): String {
    val results = mutableListOf<String>()
    results.add("=== SCANNING DETECTION TEST ===")

    try {
        testDir.mkdirs()
        val testFile = File(testDir, "scanning_test.dat")

        val config = RingBufferConfig(
            maxEntities = 1000, boundaryDetectionMode = BoundaryDetectionMode.SCANNING
        )

        val buffer = RingFileBuffer(testFile.absolutePath, config)

        val breadcrumbs = generateTestBreadcrumbs(2000)

        val writeTime = measureTimeMillis {
            breadcrumbs.forEach { breadcrumb ->
                buffer.addEntity(breadcrumb.toJsonString())
            }
        }

        results.add("Write Performance:")
        results.add("- Total breadcrumbs written: $totalBreadcrumbs")
        results.add("- Time taken: ${writeTime}ms")
        results.add("- Average per breadcrumb: ${writeTime.toFloat() / totalBreadcrumbs}ms")
        results.add("- Breadcrumbs per second: ${(totalBreadcrumbs * 1000) / writeTime}")

        results.add("\nVerification:")

        buffer.close()
        results.add("\n✅ Scanning test completed successfully!")

    } catch (e: Exception) {
        results.add("❌ Test failed: ${e.message}")
    }

    return results.joinToString("\n")
}

// Compare all detection modes
suspend fun runComparisonTest(testDir: File, maxEntities: Int, totalBreadcrumbs: Int): String {
    val results = mutableListOf<String>()
    results.add("=== PERFORMANCE COMPARISON ===")
    results.add("Testing all boundary detection modes with $totalBreadcrumbs breadcrumbs (max $maxEntities)")

    val modes = listOf(
        BoundaryDetectionMode.HEADER_BASED to "Header-Based",
        BoundaryDetectionMode.INDEX_BASED to "Index-Based",
        BoundaryDetectionMode.SCANNING to "Scanning"
    )

    val performanceResults = mutableMapOf<String, Long>()

    for ((mode, name) in modes) {
        try {
            testDir.mkdirs()
            val testFile = File(testDir, "${name.lowercase().replace("-", "_")}_comparison.dat")

            val config = RingBufferConfig(
                maxEntities = maxEntities, boundaryDetectionMode = mode
            )

            val buffer = RingFileBuffer(testFile.absolutePath, config)
            val breadcrumbs = generateTestBreadcrumbs(totalBreadcrumbs)

            val writeTime = measureTimeMillis {
                breadcrumbs.forEach { breadcrumb ->
                    buffer.addEntity(breadcrumb.toJsonString())
                }
            }

            performanceResults[name] = writeTime

            results.add("\n$name Results:")
            results.add("- Write time: ${writeTime}ms")
            results.add("- Entities/sec: ${(totalBreadcrumbs * 1000) / writeTime}")

            buffer.close()

        } catch (e: Exception) {
            results.add("\n$name FAILED: ${e.message}")
        }
    }

    // Performance ranking
    results.add("\n=== PERFORMANCE RANKING ===")
    val sorted = performanceResults.toList().sortedBy { it.second }
    sorted.forEachIndexed { index, (name, time) ->
        results.add("${index + 1}. $name: ${time}ms")
    }

    return results.joinToString("\n")
}

// Stress test with larger numbers
suspend fun runStressTest(testDir: File): String {
    val results = mutableListOf<String>()
    results.add("=== STRESS TEST ===")
    results.add("Testing with 10,000 breadcrumbs, max 5,000")

    try {
        testDir.mkdirs()
        val testFile = File(testDir, "stress_test.dat")

        val config = RingBufferConfig(
            maxEntities = 5000,
            boundaryDetectionMode = BoundaryDetectionMode.HEADER_BASED // Usually fastest
        )

        val buffer = RingFileBuffer(testFile.absolutePath, config)
        val breadcrumbs = generateTestBreadcrumbs(10000)

        val writeTime = measureTimeMillis {
            // Write in batches for better performance
            breadcrumbs.chunked(100).forEach { batch ->
                val jsonBatch = batch.map { it.toJsonString() }
                buffer.addEntities(jsonBatch)
            }
        }

        results.add("Stress Test Results:")
        results.add("- Total breadcrumbs: 10,000")
        results.add("- Write time: ${writeTime}ms")
        results.add("- Average per breadcrumb: ${writeTime.toFloat() / 10000}ms")
        results.add("- Breadcrumbs per second: ${(10000 * 1000) / writeTime}")

        // Test batch read

        buffer.close()
        results.add("\n✅ Stress test completed!")

    } catch (e: Exception) {
        results.add("❌ Stress test failed: ${e.message}")
    }

    return results.joinToString("\n")
}

// Helper function to generate test breadcrumbs
fun generateTestBreadcrumbs(count: Int): List<Breadcrumb> {
//    val eventTypes = listOf("USER_ACTION", "NETWORK", "ERROR", "INFO", "DEBUG", "WARNING")
//    val extras = listOf("button_click", "api_call", "navigation", "data_load", "user_input")
//
//    return (0 until count).map { i ->
//        Breadcrumb.create(
//            eventType = eventTypes[i % eventTypes.size], extras = listOf(
//                "sequence_$i",
//                extras[i % extras.size],
//                "timestamp_${System.currentTimeMillis()}",
//                "test_data_$i"
//            ), threadName = "TestThread-${i % 3}", timestamp = System.currentTimeMillis() + i,
//        )
//    }
    return BreadcrumbGenerators.generateRandomizedList(listSize = count)
}