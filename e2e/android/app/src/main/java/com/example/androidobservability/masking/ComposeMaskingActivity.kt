package com.example.androidobservability.masking

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
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.androidobservability.ui.theme.AndroidObservabilityTheme

class ComposeMaskingActivity : ComponentActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		setContent {
			AndroidObservabilityTheme {
				Scaffold(
					modifier = Modifier.fillMaxSize()
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
}

@Composable
fun NativeMaskingBenchScreen(modifier: Modifier = Modifier) {
	var firstField by remember { mutableStateOf("") }
	var secondField by remember { mutableStateOf("") }

	Column(
		modifier = modifier,
		verticalArrangement = Arrangement.spacedBy(16.dp)
	) {
		Text(text = "Windows")
		Row(
			horizontalArrangement = Arrangement.spacedBy(12.dp)
		) {
			Button(onClick = { /* no-op */ }) {
				Text("Dialog")
			}
			Button(onClick = { /* no-op */ }) {
				Text("Toast")
			}
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


