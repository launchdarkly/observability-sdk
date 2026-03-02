package com.example.androidobservability.benchmark

import android.content.res.AssetManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.androidobservability.ui.theme.AndroidObservabilityTheme
import com.launchdarkly.observability.replay.ReplayOptions
import kotlinx.coroutines.launch
import java.io.File

class BenchmarkActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val framesDirectory = File(filesDir, "benchmark/mastodon")
        copyAssetsIfNeeded(assets, "benchmark/mastodon", framesDirectory)

        setContent {
            AndroidObservabilityTheme {
                @OptIn(ExperimentalMaterial3Api::class)
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        TopAppBar(title = { Text("Benchmark") })
                    }
                ) { padding ->
                    BenchmarkScreen(
                        framesDirectory = framesDirectory,
                        modifier = Modifier.padding(padding),
                    )
                }
            }
        }
    }
}

@Composable
private fun BenchmarkScreen(framesDirectory: File, modifier: Modifier = Modifier) {
    val benchmarkRuns = 3
    val executor = remember { BenchmarkExecutor() }
    var results by remember { mutableStateOf<List<BenchmarkResultRow>>(emptyList()) }
    var isRunning by remember { mutableStateOf(false) }
    var showResults by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Button(
            onClick = {
                isRunning = true
                scope.launch {
                    try {
                        val compressionResults = executor.compression(
                            framesDirectory,
                            runs = benchmarkRuns,
                        )
                        val baseline = compressionResults.firstOrNull()?.bytes ?: 1
                        results = compressionResults.map { result ->
                            val pct = result.bytes.toDouble() / baseline * 100
                            BenchmarkResultRow(
                                name = result.compression.displayName,
                                bytes = result.bytes,
                                executionTimeNanos = result.executionTimeNanos,
                                percent = "%.0f%%".format(pct),
                            )
                        }
                        showResults = true
                    } catch (e: Exception) {
                        errorMessage = e.message ?: e.toString()
                    }
                    isRunning = false
                }
            },
            enabled = !isRunning,
        ) {
            if (isRunning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Text("Mastodon iOS 200 sec walk")
            }
        }
    }

    if (showResults) {
        BenchmarkResultsDialog(
            results = results,
            onDismiss = { showResults = false },
        )
    }

    errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text("Benchmark Failed") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { errorMessage = null }) {
                    Text("OK")
                }
            },
        )
    }
}

private data class BenchmarkResultRow(
    val name: String,
    val bytes: Int,
    val executionTimeNanos: Long,
    val percent: String,
) {
    val formattedBytes: String
        get() {
            val kb = bytes / 1024.0
            return if (kb >= 1024) "%.1f MB".format(kb / 1024)
            else "%.1f KB".format(kb)
        }

    val formattedExecutionTime: String
        get() = "%.2fs".format(executionTimeNanos / 1_000_000_000.0)
}

@Composable
private fun BenchmarkResultsDialog(
    results: List<BenchmarkResultRow>,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Results") },
        text = {
            Column {
                results.forEach { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(row.name, modifier = Modifier.weight(1f))
                        Text(
                            row.percent,
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(48.dp),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Light
                            ),
                        )
                        Text(
                            row.formattedExecutionTime,
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(56.dp),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Light
                            ),
                        )
                        Text(
                            row.formattedBytes,
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(72.dp),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Light
                            ),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        },
    )
}

private val ReplayOptions.CompressionMethod.displayName: String
    get() = when (this) {
        is ReplayOptions.CompressionMethod.ScreenImage -> "Screen Image"
        is ReplayOptions.CompressionMethod.OverlayTiles -> "layers: $layers backtracking: $backtracking"
    }

private fun copyAssetsIfNeeded(assets: AssetManager, assetPath: String, destDir: File) {
    if (destDir.exists() && (destDir.list()?.size ?: 0) > 0) return
    destDir.mkdirs()
    for (name in assets.list(assetPath) ?: return) {
        val destFile = File(destDir, name)
        assets.open("$assetPath/$name").use { input ->
            destFile.outputStream().use { output -> input.copyTo(output) }
        }
    }
}
