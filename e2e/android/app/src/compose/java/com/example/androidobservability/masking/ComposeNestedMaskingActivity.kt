package com.example.androidobservability.masking

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.androidobservability.ui.theme.AndroidObservabilityTheme
import com.launchdarkly.observability.api.ldMask
import com.launchdarkly.observability.api.ldUnmask

class ComposeNestedMaskingActivity : ComponentActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		setContent {
			AndroidObservabilityTheme {
				Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
					NestedMaskingPropagation(
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

private val Green = Color(0xFF4CAF50)
private val Red = Color(0xFFF44336)
private val ContainerShape = RoundedCornerShape(8.dp)

@Composable
fun NestedMaskingPropagation(modifier: Modifier = Modifier) {
	var baselineText by remember { mutableStateOf("") }
	var unmaskedText by remember { mutableStateOf("") }
	var maskedText by remember { mutableStateOf("") }
	var deepText by remember { mutableStateOf("") }

	Column(
		modifier = modifier.verticalScroll(rememberScrollState()),
		verticalArrangement = Arrangement.spacedBy(24.dp)
	) {
		Text(
			text = "Ancestor Mask Propagation",
			style = MaterialTheme.typography.headlineMedium
		)

		Section(
			title = "1. Baseline (no modifier)",
			note = "Globally masked by maskTextInputs."
		) {
			OutlinedTextField(
				value = baselineText,
				onValueChange = { baselineText = it },
				label = { Text("type here") },
				modifier = Modifier.fillMaxWidth()
			)
		}

		Section(
			title = "2. Ancestor .ldUnmask()",
			note = "Parent Column is unmasked — child TextField should be visible despite maskTextInputs=true."
		) {
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.ldUnmask()
					.background(Green.copy(alpha = 0.15f), ContainerShape)
					.padding(8.dp)
			) {
				OutlinedTextField(
					value = unmaskedText,
					onValueChange = { unmaskedText = it },
					label = { Text("visible inside unmasked ancestor") },
					modifier = Modifier.fillMaxWidth()
				)
			}
		}

		Section(
			title = "3. Ancestor .ldMask()",
			note = "Parent Column is masked — all children get covered, even the plain Text label."
		) {
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.ldMask()
					.background(Red.copy(alpha = 0.15f), ContainerShape)
					.padding(8.dp)
			) {
				Text("plain label that would normally be visible")
				OutlinedTextField(
					value = maskedText,
					onValueChange = { maskedText = it },
					label = { Text("textfield inside masked ancestor") },
					modifier = Modifier.fillMaxWidth()
				)
			}
		}

		Section(
			title = "4. Deep unmask through nesting",
			note = "Two levels of nesting under .ldUnmask() — propagation should still apply."
		) {
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.ldUnmask()
					.background(Green.copy(alpha = 0.15f), ContainerShape)
					.padding(8.dp)
			) {
				Column(
					modifier = Modifier
						.fillMaxWidth()
						.background(Green.copy(alpha = 0.05f), ContainerShape)
						.padding(8.dp)
				) {
					OutlinedTextField(
						value = deepText,
						onValueChange = { deepText = it },
						label = { Text("deeply nested, still unmasked") },
						modifier = Modifier.fillMaxWidth()
					)
				}
			}
		}
	}
}

@Composable
private fun Section(title: String, note: String, content: @Composable ColumnScope.() -> Unit) {
	Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
		Text(text = title, style = MaterialTheme.typography.titleMedium)
		Text(
			text = note,
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.onSurfaceVariant
		)
		content()
	}
}

@Preview(showBackground = true)
@Composable
fun NestedMaskingPropagationPreview() {
	AndroidObservabilityTheme {
		NestedMaskingPropagation(modifier = Modifier.padding(16.dp))
	}
}
