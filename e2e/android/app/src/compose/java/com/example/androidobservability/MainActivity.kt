package com.example.androidobservability

import android.app.Activity
import android.content.Context
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.example.androidobservability.masking.ComposeMaskingActivity
import com.example.androidobservability.masking.ComposeUserFormActivity
import com.example.androidobservability.masking.ComposeWebActivity
import com.example.androidobservability.masking.XMLUserFormActivity
import com.example.androidobservability.masking.XMLMaskingActivity
import com.example.androidobservability.masking.XMLWebActivity
import com.example.androidobservability.smoothie.SmoothieListActivity
import com.example.androidobservability.ui.theme.AndroidObservabilityTheme
import com.example.androidobservability.ui.theme.DangerRed
import com.example.androidobservability.ui.theme.IdentifyBgColor
import com.example.androidobservability.ui.theme.IdentifyTextColor
import com.launchdarkly.observability.api.ldId
import com.launchdarkly.observability.sdk.LDReplay

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val viewModel: MainActivityViewModel by viewModels()

        enableEdgeToEdge()
        setContent {
            AndroidObservabilityTheme {
                @OptIn(ExperimentalMaterial3Api::class)
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding(),
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    "Android Observability",
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            },
                            navigationIcon = {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_launchdarkly_logo),
                                    contentDescription = "LaunchDarkly",
                                    modifier = Modifier.padding(start = 12.dp),
                                    tint = Color.White
                                )
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = Color.Black,
                                titleContentColor = Color.White
                            )
                        )
                    }
                ) { innerPadding ->
                    MainScreen(viewModel, innerPadding)
                }
            }
        }
    }
}

@Composable
private fun MainScreen(viewModel: MainActivityViewModel, innerPadding: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        SessionReplayHeader()
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

        ErrorButtons(viewModel = viewModel)

        LogsButtons(viewModel = viewModel)

        TracesButtons(viewModel = viewModel)
    }
}

@Composable
private fun SessionReplayHeader() {
    var isSessionReplayEnabled by rememberSaveable { mutableStateOf(true) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Session Replay",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
        )
        Switch(
            checked = isSessionReplayEnabled,
            onCheckedChange = { enabled ->
                isSessionReplayEnabled = enabled
                if (enabled) {
                    LDReplay.start()
                } else {
                    LDReplay.stop()
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetricButtons(viewModel: MainActivityViewModel) {
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
            },
            modifier = Modifier.ldId("metric.gauge")
        ) {
            Text("Metric")
        }
        Button(
            onClick = {
                viewModel.triggerHistogramMetric()
            },
            modifier = Modifier.ldId("metric.histogram")
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
        Button(
            onClick = {
                viewModel.trackViaLdClient()
            }
        ) {
            Text("Track (LDClient)")
        }
        Button(
            onClick = {
                viewModel.trackViaLdObserve()
            }
        ) {
            Text("Track (LDObserve)")
        }
        Button(
            onClick = {
                viewModel.trackScreenView()
            }
        ) {
            Text("Track Screen View")
        }
        Button(
            onClick = {
                viewModel.trackNested()
            }
        ) {
            Text("Track (Nested)")
        }
    }
}

@Composable
private fun InstrumentationButtons(viewModel: MainActivityViewModel) {
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
            },
            modifier = Modifier.ldId("instrumentation.http_request")
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
            modifier = Modifier.ldId("instrumentation.crash"),
            colors = ButtonDefaults.buttonColors(
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

    MaskingRow(
        name = "User Form",
        ctx = context,
        activity1 = XMLUserFormActivity::class.java,
        activity2 = ComposeUserFormActivity::class.java
    )

    MaskingRow(
        name = "Smoothies",
        ctx = context,
        activity1 = SmoothieListActivity::class.java,
        activity2 = null
    )

    MaskingRow(
        name = "Dialogs",
        ctx = context,
        activity1 = XMLMaskingActivity::class.java,
        activity2 = ComposeMaskingActivity::class.java
    )

    MaskingRow(
        name = "Webviews",
        ctx = context,
        activity1 = XMLWebActivity::class.java,
        activity2 = ComposeWebActivity::class.java
    )
}

@Composable
private fun MaskingRow(name: String, ctx: Context, activity1: Class<out Activity>??, activity2: Class<out Activity>??) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            modifier = Modifier.weight(1f)
        )
        Button(
            onClick = { goToActivity(ctx, activity1) },
            enabled = activity1 != null,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Text("XML")
        }
        Button(
            onClick = { goToActivity(ctx, activity2) },
            enabled = activity2 != null,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Text("Compose")
        }
    }
}

private fun goToActivity(ctx: Context, activity: Class<out Activity>?){
    activity?.let {
        ctx.startActivity(
            Intent(ctx, it)
        )
    }
}

@Composable
private fun IdentifyButtons(viewModel: MainActivityViewModel) {
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
            modifier = Modifier.ldId("identify.user"),
            colors = ButtonDefaults.buttonColors(
                containerColor = IdentifyBgColor,
                contentColor = IdentifyTextColor
            )
        ) {
            Text("User")
        }
        Button(
            onClick = { viewModel.identifyMulti() },
            modifier = Modifier.ldId("identify.multi"),
            colors = ButtonDefaults.buttonColors(
                containerColor = IdentifyBgColor,
                contentColor = IdentifyTextColor
            )
        ) {
            Text("Multi")
        }
        Button(
            onClick = { viewModel.identifyAnonymous() },
            modifier = Modifier.ldId("identify.anonymous"),
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
private fun ErrorButtons(viewModel: MainActivityViewModel) {
    Text(
        text = "Error",
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        modifier = Modifier.padding(bottom = 8.dp, top = 8.dp)
    )

    Button(
        onClick = {
            viewModel.triggerError()
        },
        modifier = Modifier.ldId("error.trigger"),
        colors = ButtonDefaults.buttonColors(
            containerColor = DangerRed,
            contentColor = Color.White
        )
    ) {
        Text("Trigger Error")
    }

    Button(
        onClick = {
            viewModel.triggerObfuscatedError()
        },
        modifier = Modifier
            .padding(top = 8.dp)
            .ldId("error.obfuscated"),
        colors = ButtonDefaults.buttonColors(
            containerColor = DangerRed,
            contentColor = Color.White
        )
    ) {
        Text("${BuildConfig.VERSION_NAME} Trigger Obfuscated Error")
    }
}

@Composable
private fun LogsButtons(viewModel: MainActivityViewModel) {
    var customLogText by remember { mutableStateOf("Log Message") }

    Text(
        text = "Logs",
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        modifier = Modifier.padding(bottom = 8.dp, top = 8.dp)
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = {
                viewModel.triggerLog()
            }
        ) {
            Text("Trigger Log")
        }
        Button(
            onClick = {
                viewModel.triggerLogWithContext(customLogText)
            }
        ) {
            Text("Log with Context")
        }
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
}

@Composable
private fun TracesButtons(viewModel: MainActivityViewModel) {
    var customSpanText by remember { mutableStateOf("Span Name") }
    var flagKey by remember { mutableStateOf("my-feature") }

    Text(
        text = "Traces",
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        modifier = Modifier.padding(bottom = 8.dp, top = 8.dp)
    )

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
