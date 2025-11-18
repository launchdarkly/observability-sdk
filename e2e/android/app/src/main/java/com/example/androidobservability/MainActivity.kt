package com.example.androidobservability

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.androidobservability.ui.theme.AndroidObservabilityTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val viewModel: ViewModel by viewModels()

        enableEdgeToEdge()
        setContent {
            AndroidObservabilityTheme {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding()
                ) { innerPadding ->
                    var customLogText by remember { mutableStateOf("") }
                    var customSpanText by remember { mutableStateOf("") }
                    var customContextKey by remember { mutableStateOf("") }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Hello Telemetry",
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        Button(
                            onClick = {
                                this@MainActivity.startActivity(
                                    Intent(
                                        this@MainActivity,
                                        SecondaryActivity::class.java
                                    )
                                )
                            }
                        ) {
                            Text("Go to Secondary Activity")
                        }
                        Button(
                            onClick = {
                                this@MainActivity.startActivity(
                                    Intent(
                                        this@MainActivity,
                                        com.smoothie.SmoothieListActivity::class.java
                                    )
                                )
                            }
                        ) {
                            Text("Open Fruta (XML)")
                        }
                        Button(
                            onClick = {
                                viewModel.triggerHttpRequests()
                            }
                        ) {
                            Text("Trigger HTTP Request")
                        }
                        Button(
                            onClick = {
                                viewModel.triggerMetric()
                            }
                        ) {
                            Text("Trigger Metric")
                        }
                        Button(
                            onClick = {
                                viewModel.triggerHistogramMetric()
                            }
                        ) {
                            Text("Trigger Histogram Metric")
                        }
                        Button(
                            onClick = {
                                viewModel.triggerCountMetric()
                            }
                        ) {
                            Text("Trigger Count Metric")
                        }
                        Button(
                            onClick = {
                                viewModel.triggerIncrementalMetric()
                            }
                        ) {
                            Text("Trigger Incremental Metric")
                        }
                        Button(
                            onClick = {
                                viewModel.triggerUpDownCounterMetric()
                            }
                        ) {
                            Text("Trigger UpDownCounter Metric")
                        }
                        Button(
                            onClick = {
                                viewModel.triggerError()
                            }
                        ) {
                            Text("Trigger Error")
                        }
                        Button(
                            onClick = {
                                viewModel.triggerLog()
                            }
                        ) {
                            Text("Trigger Log")
                        }
                        Button(
                            onClick = {
                                viewModel.startForegroundService()
                            }
                        ) {
                            Text("Start Foreground Service")
                        }
                        Button(
                            onClick = {
                                viewModel.startBackgroundService()
                            }
                        ) {
                            Text("Start Background Service")
                        }
                        Button(
                            onClick = {
                                viewModel.triggerNestedSpans()
                            }
                        ) {
                            Text("Trigger Nested Spans")
                        }
                        Button(
                            onClick = {
                                viewModel.triggerCrash()
                            }
                        ) {
                            Text("Trigger Crash")
                        }

                        OutlinedTextField(
                            value = customLogText,
                            onValueChange = { customLogText = it },
                            label = { Text("Log Message") },
                            modifier = Modifier.padding(8.dp)
                        )
                        Button(
                            onClick = {
                                viewModel.triggerCustomLog(customLogText)
                            },
                            modifier = Modifier.padding(8.dp)
                        ) {
                            Text("Send custom log")
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = customSpanText,
                            onValueChange = { customSpanText = it },
                            label = { Text("Span Name") },
                            modifier = Modifier.padding(8.dp)
                        )
                        Button(
                            onClick = {
                                viewModel.triggerCustomSpan(customSpanText)
                            },
                            modifier = Modifier.padding(8.dp)
                        ) {
                            Text("Send custom span")
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = customContextKey,
                            onValueChange = { customContextKey = it },
                            label = { Text("LD context key") },
                            modifier = Modifier.padding(8.dp)
                        )
                        Button(
                            onClick = {
                                viewModel.identifyLDContext(customContextKey)
                            },
                            modifier = Modifier.padding(8.dp)
                        ) {
                            Text("Identify LD Context")
                        }
                    }
                }
            }
        }
    }
}
