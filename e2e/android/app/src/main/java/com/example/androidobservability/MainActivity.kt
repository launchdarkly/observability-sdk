package com.example.androidobservability

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
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.androidobservability.ui.theme.AndroidObservabilityTheme
import kotlinx.coroutines.launch

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
                            text = "Hello Telemetry",
                            modifier = Modifier.padding(innerPadding)
                        )
                        Button(
                            onClick = {
                                viewModel.triggerMetric()
                            }
                        ) {
                            Text("Trigger Metric")
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