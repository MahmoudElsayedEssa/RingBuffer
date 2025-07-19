package com.example.ringbuffer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.ringbuffer.benchmark.Benchmark
import com.example.ringbuffer.breadcrumbs.Breadcrumb
import com.example.ringbuffer.breadcrumbs.storage.tapRingBuffer.TapRingBufferBreadcrumbLogger
import com.example.ringbuffer.ui.theme.RingBufferTheme
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RingBufferTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        Executors.newSingleThreadExecutor().execute {
            val benchmark = Benchmark()

            // Example 1: Test a single function
            println("Example 1: Testing single function")
            val tapRingBufferBreadcrumbLogger =
                TapRingBufferBreadcrumbLogger(context = this, maxEntries = 1000)
            benchmark.testThisFun({ tapRingBufferBreadcrumbLogger.queueFile.clear() }) {
                tapRingBufferBreadcrumbLogger.flush(
                    it
                )
            }

//            // Example 2: Test multiple functions with same data
//            println("\n\nExample 2: Testing multiple functions with same data")
//            val functions: Map<String, (List<Breadcrumb>) -> Unit> = mapOf(
//                "tapRingBufferBreadcrumbLogger" to { breadcrumbs: List<Breadcrumb> ->
//                    tapRingBufferBreadcrumbLogger.flush(breadcrumbs)
//                }
//            )
//            benchmark.testMultipleFunctions(functions)
        }
    }

}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    RingBufferTheme {
        Greeting("Android")
    }
}