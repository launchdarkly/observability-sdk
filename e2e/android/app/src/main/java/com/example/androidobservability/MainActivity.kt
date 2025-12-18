package com.example.androidobservability

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import com.example.androidobservability.masking.ComposeMaskingActivity
import com.example.androidobservability.masking.ComposeUserFormActivity
import com.example.androidobservability.masking.XMLUserFormActivity
import com.example.androidobservability.masking.XMLMaskingActivity
import com.example.androidobservability.smoothie.SmoothieListActivity
import com.example.androidobservability.ui.theme.AndroidObservabilityTheme
import com.example.androidobservability.ui.theme.DangerRed
import com.example.androidobservability.ui.theme.IdentifyBgColor
import com.example.androidobservability.ui.theme.IdentifyTextColor

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
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Masking",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        HorizontalDivider(modifier = Modifier.padding(bottom = 16.dp))

                        MaskingButtons()

                        Text(
                            text = "Observability",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        HorizontalDivider(modifier = Modifier.padding(bottom = 16.dp))

                        IdentifyButtons(viewModel = viewModel)

                        InstrumentationButtons(viewModel = viewModel)

                        MetricButtons(viewModel = viewModel)

                        CustomerApiButtons(viewModel = viewModel)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetricButtons(viewModel: ViewModel) {
    Text(
        text = "Metric",
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        modifier = Modifier.padding(bottom = 8.dp)
    )

    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Button(
            onClick = {
                viewModel.triggerMetric()
            }
        ) {
            Text("Metric")
        }
        Button(
            onClick = {
                viewModel.triggerHistogramMetric()
            }
        ) {
            Text("Histogram")
        }
        Button(
            onClick = {
                viewModel.triggerCountMetric()
            }
        ) {
            Text("Count")
        }
        Button(
            onClick = {
                viewModel.triggerIncrementalMetric()
            }
        ) {
            Text("Incremental")
        }
        Button(
            onClick = {
                viewModel.triggerUpDownCounterMetric()
            }
        ) {
            Text("UpDownCounter")
        }
    }
}

@Composable
private fun InstrumentationButtons(viewModel: ViewModel) {
    Text(
        text = "Instrumentation",
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        modifier = Modifier.padding(bottom = 8.dp, top = 8.dp)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
    ) {
        Button(
            onClick = {
                viewModel.triggerHttpRequests()
            }
        ) {
            Text("Trigger HTTP Request")
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
                viewModel.triggerCrash()
            },
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = DangerRed,
                contentColor = Color.White
            )
        ) {
            Text("Trigger Crash")
        }
    }
}

@Composable
private fun MaskingButtons() {
    val context = LocalContext.current


    // Three-column layout: Name | XML | Compose
    // User Form
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "User Form",
            modifier = Modifier.weight(1f)
        )
        Button(
            onClick = {
                context.startActivity(
                    Intent(
                        context,
                        XMLUserFormActivity::class.java
                    )
                )
            },
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Text("XML")
        }
        Button(
            onClick = {
                context.startActivity(
                    Intent(
                        context,
                        ComposeUserFormActivity::class.java
                    )
                )
            },
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Text("Compose")
        }
    }
    // Smoothies
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Smoothies",
            modifier = Modifier.weight(1f)
        )
        Button(
            onClick = {
                context.startActivity(
                    Intent(
                        context,
                        SmoothieListActivity::class.java
                    )
                )
            },
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Text("XML")
        }
        Button(
            onClick = { /* Compose Smoothies not implemented */ },
            enabled = false,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Text("Compose")
        }
    }
    // Check
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Check",
            modifier = Modifier.weight(1f)
        )
        Button(
            onClick = {
                context.startActivity(
                    Intent(
                        context,
                        XMLMaskingActivity::class.java
                    )
                )
            },
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Text("XML")
        }
        Button(
            onClick = {
                context.startActivity(
                    Intent(
                        context,
                        ComposeMaskingActivity::class.java
                    )
                )
            },
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Text("Compose")
        }
    }
}

@Composable
private fun IdentifyButtons(viewModel: ViewModel) {
    Spacer(modifier = Modifier.height(16.dp))

    Text(
        text = "Identify:",
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        modifier = Modifier.padding(bottom = 8.dp, top = 8.dp)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = { viewModel.identifyUser() },
            colors = ButtonDefaults.buttonColors(
                containerColor = IdentifyBgColor,
                contentColor = IdentifyTextColor
            )
        ) {
            Text("User")
        }
        Button(
            onClick = { viewModel.identifyMulti() },
            colors = ButtonDefaults.buttonColors(
                containerColor = IdentifyBgColor,
                contentColor = IdentifyTextColor
            )
        ) {
            Text("Multi")
        }
        Button(
            onClick = { viewModel.identifyAnonymous() },
            colors = ButtonDefaults.buttonColors(
                containerColor = IdentifyBgColor,
                contentColor = IdentifyTextColor
            )
        ) {
            Text("Anon")
        }
    }
}

@Composable
private fun CustomerApiButtons(viewModel: ViewModel) {
    var customLogText by remember { mutableStateOf("") }
    var customSpanText by remember { mutableStateOf("") }
    var flagKey by remember { mutableStateOf("") }

    Text(
        text = "Customer API",
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        modifier = Modifier.padding(bottom = 8.dp, top = 8.dp)
    )

    Button(
        onClick = {
            viewModel.triggerError()
        },
        colors = ButtonDefaults.buttonColors(
            containerColor = DangerRed,
            contentColor = Color.White
        )
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

    Button(
        onClick = {
            viewModel.triggerNestedSpans()
        }
    ) {
        Text("Trigger Nested Spans")
    }
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
        value = flagKey,
        onValueChange = { flagKey = it },
        label = { Text("Flag key") },
        modifier = Modifier.padding(8.dp)
    )
    Button(
        onClick = {
            viewModel.evaluateBooleanFlag(flagKey)
        },
        modifier = Modifier.padding(8.dp)
    ) {
        Text("Evaluate boolean flag")
    }


}
