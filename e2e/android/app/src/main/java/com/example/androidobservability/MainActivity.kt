package com.example.androidobservability

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.example.androidobservability.ui.theme.AndroidObservabilityTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val viewModel: ViewModel by viewModels()
        enableEdgeToEdge()
        setContent {
            AndroidObservabilityTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column {
                        Text(
                            text = "Hello Android Observability",
                            modifier = Modifier.padding(innerPadding)
                        )
                        Button(
                            modifier = Modifier.semantics {
                                contentDescription = "buttonGoToSecondActivity"
                            },
                            onClick = {
                                this@MainActivity.startActivity(Intent(this@MainActivity, SecondaryActivity::class.java))
                            }
                        ) {
                            Text("Go to Secondary Activity")
                        }
                        Button(
                            modifier = Modifier.semantics {
                                contentDescription = "buttonTriggerHttp"
                            },
                            onClick = {
                                viewModel.triggerHttpRequests()
                            }
                        ) {
                            Text("Trigger HTTP Request")
                        }
                        Button(
                            modifier = Modifier.semantics {
                                contentDescription = "buttonTriggerMetric"
                            },
                            onClick = {
                                viewModel.triggerMetric()
                            }
                        ) {
                            Text("Trigger Metric")
                        }
                        Button(
                            modifier = Modifier.semantics {
                                contentDescription = "buttonTriggerError"
                            },
                            onClick = {
                                viewModel.triggerError()
                            }
                        ) {
                            Text("Trigger Error")
                        }
                        Button(
                            modifier = Modifier.semantics {
                                contentDescription = "buttonTriggerLog"
                            },
                            onClick = {
                                viewModel.triggerLog()
                            }
                        ) {
                            Text("Trigger Log")
                        }
                        Button(
                            modifier = Modifier.semantics {
                                contentDescription = "buttonTriggerNestedSpans"
                            },
                            onClick = {
                                viewModel.triggerNestedSpans()
                            }
                        ) {
                            Text("Trigger Nested Spans")
                        }
                        Button(
                            modifier = Modifier.semantics {
                                contentDescription = "buttonTriggerCrash"
                            },
                            onClick = {
                                viewModel.triggerCrash()
                            }
                        ) {
                            Text("Trigger Crash")
                        }
                    }
                }
            }
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
    AndroidObservabilityTheme {
        Greeting("Android")
    }
}
