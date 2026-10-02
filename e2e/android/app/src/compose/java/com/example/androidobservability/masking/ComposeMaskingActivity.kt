package com.example.androidobservability.masking

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.androidobservability.R
import com.example.androidobservability.ui.theme.AndroidObservabilityTheme

class ComposeMaskingActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val launchAsDialog = intent.getBooleanExtra(EXTRA_LAUNCH_AS_DIALOG, false)
        val launchAsConfirmation = intent.getBooleanExtra(EXTRA_LAUNCH_AS_CONFIRMATION, false)
        if (launchAsDialog) {
            if (launchAsConfirmation) {
                setTheme(R.style.Theme_AndroidObservability_ConfirmationDialog)
            } else {
                setTheme(R.style.Theme_AndroidObservability_Dialog)
            }
        }
        enableEdgeToEdge()
        setContent {
            AndroidObservabilityTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        // Show top app bar only for full-screen dialog variant
                        if (launchAsDialog && !launchAsConfirmation) {
                            TopAppBar(
                                title = { Text(text = "Masking") },
                                navigationIcon = {
                                    IconButton(onClick = { finish() }) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_arrow_back),
                                            contentDescription = "Close"
                                        )
                                    }
                                }
                            )
                        }
                    }
                ) { innerPadding ->
                    NativeMaskingBenchScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(16.dp)
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_LAUNCH_AS_DIALOG = "compose_masking_launch_as_dialog"
        const val EXTRA_LAUNCH_AS_CONFIRMATION = "compose_masking_launch_as_confirmation"
    }
}

@Composable
fun NativeMaskingBenchScreen(modifier: Modifier = Modifier) {
    var firstField by remember { mutableStateOf("") }
    var secondField by remember { mutableStateOf("") }
    val context = LocalContext.current
    var showAlert by remember { mutableStateOf(false) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "Windows")
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(onClick = {
                context.startActivity(
                    Intent(context, ComposeMaskingActivity::class.java).apply {
                        putExtra(ComposeMaskingActivity.EXTRA_LAUNCH_AS_DIALOG, true)
                    }
                )
            }) {
                Text("Full Screen")
            }
            Button(onClick = {
                context.startActivity(
                    Intent(context, ComposeMaskingActivity::class.java).apply {
                        putExtra(ComposeMaskingActivity.EXTRA_LAUNCH_AS_DIALOG, true)
                        putExtra(ComposeMaskingActivity.EXTRA_LAUNCH_AS_CONFIRMATION, true)
                    }
                )
            }) {
                Text("Confirmation")
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(onClick = { showAlert = true }) {
                Text("Alert")
            }
            Button(onClick = {
                Toast.makeText(context, "This is an example toast.", Toast.LENGTH_SHORT).show()
            }) {
                Text("Toast")
            }
        }
        // Simple alert dialog content
        if (showAlert) {
            AlertDialog(
                onDismissRequest = { showAlert = false },
                title = { Text("Alert") },
                text = { Text("This is an example alert dialog.") },
                confirmButton = {
                    TextButton(onClick = { showAlert = false }) {
                        Text("OK")
                    }
                }
            )
        }

        Text(text = "maskInputText=true")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(text = "First Field")
                OutlinedTextField(
                    value = firstField,
                    onValueChange = { firstField = it },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(text = "Second Field")
                OutlinedTextField(
                    value = secondField,
                    onValueChange = { secondField = it },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}


