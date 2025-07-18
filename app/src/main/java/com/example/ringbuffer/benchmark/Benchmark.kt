package com.example.ringbuffer.benchmark

import android.annotation.SuppressLint
import com.example.ringbuffer.breadcrumbs.Breadcrumb
import com.example.ringbuffer.breadcrumbs.Breadcrumb.Companion.toJson
import com.example.ringbuffer.breadcrumbs.BreadcrumbGenerators
import com.example.ringbuffer.breadcrumbs.storage.tapRingBuffer.TapRingBufferBreadcrumbLogger
import kotlin.system.measureTimeMillis

class Benchmark {

    val numberOfEvents = listOf<Int>(1, 10, 100, 1000, 10000, 40000, 80000)

    /**
     * Tests a single function with different numbers of events and different event sizes
     * @param action Function that takes a list of breadcrumbs and performs some operation
     */
    fun testThisFun(action: (List<Breadcrumb>) -> Unit) {
        println("=== Starting Comprehensive Benchmark ===")

        for (number in numberOfEvents) {
            println("\n--- Testing with $number events ---")

            // Test with small events
            println("Testing Small Events (20-512 bytes):")
            val smallEvents = BreadcrumbGenerators.generateSingleSizeList(
                number,
                BreadcrumbGenerators.BreadcrumbSize.SMALL_512
            )
            runBenchmark("Small Events", number, smallEvents, action)

            // Test with medium events  
            println("Testing Medium Events (~512 bytes):")
            val mediumEvents = BreadcrumbGenerators.generateSingleSizeList(
                number,
                BreadcrumbGenerators.BreadcrumbSize.MEDIUM_1k
            )
            runBenchmark("Medium Events", number, mediumEvents, action)

            // Test with large events
            println("Testing Large Events (5-10kb):")
            val largeEvents = BreadcrumbGenerators.generateSingleSizeList(
                number,
                BreadcrumbGenerators.BreadcrumbSize.LARGE_10k
            )
            runBenchmark("Large Events", number, largeEvents, action)

//            // Test with mixed events
//            println("Testing Mixed Events:")
//            val mixedEvents = BreadcrumbGenerators.generateBreadcrumbList(number)
//            runBenchmark("Mixed Events", number, mixedEvents, action)
        }

        println("\n=== Benchmark Complete ===")
    }

    /**
     * Tests multiple functions with the same datasets for performance comparison
     * @param functions Map of function name to function implementation
     */
    @SuppressLint("DefaultLocale")
    fun testMultipleFunctions(functions: Map<String, (List<Breadcrumb>) -> Unit>) {
        if (functions.isEmpty()) {
            println("No functions provided for testing")
            return
        }

        println("=== Testing ${functions.size} Functions with Same Data ===")

        for (number in numberOfEvents) {
            println("\n--- Comparing with $number events ---")

            // Generate the same datasets for all functions
            val smallEvents = BreadcrumbGenerators.generateSingleSizeList(
                number,
                BreadcrumbGenerators.BreadcrumbSize.SMALL_512
            )
            val mediumEvents = BreadcrumbGenerators.generateSingleSizeList(
                number,
                BreadcrumbGenerators.BreadcrumbSize.MEDIUM_1k
            )
            val largeEvents = BreadcrumbGenerators.generateSingleSizeList(
                number,
                BreadcrumbGenerators.BreadcrumbSize.LARGE_10k
            )

            val datasets = listOf(
                "Small Events" to smallEvents,
                "Medium Events" to mediumEvents,
                "Large Events" to largeEvents
            )

            // Test each dataset with all functions
            datasets.forEach { (datasetName, events) ->
                println("\n  $datasetName (${events.size} items):")
                val results = mutableMapOf<String, Long>()

                // Run each function with the same data
                functions.forEach { (functionName, function) ->
                    try {
                        val duration = measureTimeMillis {
                            function(events)
                        }
                        results[functionName] = duration
                        println("    $functionName: ${duration}ms")
                    } catch (e: Exception) {
                        println("    $functionName: FAILED - ${e.message}")
                        results[functionName] = Long.MAX_VALUE
                    }
                }

                // Show performance comparison
                if (results.isNotEmpty()) {
                    val sortedResults = results.filter { it.value != Long.MAX_VALUE }.toList()
                        .sortedBy { it.second }
                    if (sortedResults.isNotEmpty()) {
                        val fastest = sortedResults.first()
                        println("    🏆 Winner: ${fastest.first} (${fastest.second}ms)")

                        if (sortedResults.size > 1) {
                            sortedResults.drop(1).forEach { (name, time) ->
                                val speedup = time.toDouble() / fastest.second
                                println(
                                    "    📊 ${fastest.first} is ${
                                        String.format(
                                            "%.2f",
                                            speedup
                                        )
                                    }x faster than $name"
                                )
                            }
                        }
                    }
                }
            }
        }

        println("\n=== Multi-Function Benchmark Complete ===")
    }


    /**
     * Runs benchmark for a specific event type and size
     */
    private fun runBenchmark(
        eventType: String,
        numberOfEvents: Int,
        events: List<Breadcrumb>,
        action: (List<Breadcrumb>) -> Unit
    ) {
        try {
            val startTime = System.currentTimeMillis()
            action(events)
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime

            // Calculate size information
            val totalSizeBytes = events.sumOf { it.toJson().toByteArray().size }
            val avgSizeBytes = if (events.isNotEmpty()) totalSizeBytes / events.size else 0

            println("  ✓ $eventType: $numberOfEvents events, ${duration}ms")
            println("    Total size: ${totalSizeBytes / 1024.0} KB, Avg size: ${avgSizeBytes} bytes")

        } catch (e: Exception) {
            println("  ✗ $eventType: Failed - ${e.message}")
        }
    }
}


// Example usage
fun main() {
    val benchmark = Benchmark()

    // Example 1: Test a single function
//    println("Example 1: Testing single function")
//    val tapRingBufferBreadcrumbLogger = TapRingBufferBreadcrumbLogger(context =, maxEntries = 1000)
//    benchmark.testThisFun { tapRingBufferBreadcrumbLogger.flush(it) }

    // Example 2: Test multiple functions with same data
    println("\n\nExample 2: Testing multiple functions with same data")
    val functions: Map<String, (List<Breadcrumb>) -> Unit> = mapOf(
        "forEach Approach" to { breadcrumbs: List<Breadcrumb> ->
            breadcrumbs.forEach { it.toJson() }
        },
        "map Approach" to { breadcrumbs: List<Breadcrumb> ->
            breadcrumbs.map { it.toJson() }
        },
        "for Loop Approach" to { breadcrumbs: List<Breadcrumb> ->
            for (breadcrumb in breadcrumbs) {
                breadcrumb.toJson()
            }
        },
        "Stream Approach" to { breadcrumbs: List<Breadcrumb> ->
            breadcrumbs.stream().forEach { it.toJson() }
        }
    )

    benchmark.testMultipleFunctions(functions)

    // Example 3: Test multiple functions with multiple runs for accuracy
    println("\n\nExample 3: Testing with multiple runs")
    val functionsForRuns: Map<String, (List<Breadcrumb>) -> Unit> = mapOf(
        "JSON Conversion" to { breadcrumbs: List<Breadcrumb> ->
            breadcrumbs.map { it.toJson() }
        },
        "Size Calculation" to { breadcrumbs: List<Breadcrumb> ->
            breadcrumbs.sumOf { it.toJson().length }
        },
        "Filter & Process" to { breadcrumbs: List<Breadcrumb> ->
            breadcrumbs.filter { it.extras.isNotEmpty() }.map { it.toJson() }
        }
    )

//    benchmark.testMultipleFunctionsWithRuns(functionsForRuns, runs = 5)
}