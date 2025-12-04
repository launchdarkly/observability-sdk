package com.example.androidobservability.masking

import android.os.Bundle
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.androidobservability.ui.theme.AndroidObservabilityTheme
import com.launchdarkly.observability.api.ldMask

class ComposeUserFormActivity : ComponentActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		setContent {
			AndroidObservabilityTheme {
				Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
					UserInfoForm(
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
fun UserInfoForm(modifier: Modifier = Modifier) {
	var password by remember { mutableStateOf("") }
	var streetAddress by remember { mutableStateOf("") }
	var city by remember { mutableStateOf("") }
	var state by remember { mutableStateOf("") }
	var zipCode by remember { mutableStateOf("") }
	var creditCardNumber by remember { mutableStateOf("") }
	var expiryDate by remember { mutableStateOf("") }
	var cvv by remember { mutableStateOf("") }
	var cardholderName by remember { mutableStateOf("") }

	val scrollState = rememberScrollState()
	val addressRotationTransition = rememberInfiniteTransition(label = "addressRotationTransition")
	val addressRotationDegrees by addressRotationTransition.animateFloat(
		initialValue = 0f,
		targetValue = 360f,
		animationSpec = infiniteRepeatable(
			animation = tween(durationMillis = 3000, easing = LinearEasing)
		),
		label = "addressRotationDegrees"
	)

	Column(
		modifier = modifier
			.verticalScroll(scrollState)
			.fillMaxSize(),
		verticalArrangement = Arrangement.spacedBy(16.dp)
	) {
		Text(
			text = "User Information Form",
			style = MaterialTheme.typography.headlineMedium,
			modifier = Modifier.ldMask()
		)

		// Password Section
		Card(
			modifier = Modifier.fillMaxWidth(),
			elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
		) {
			Column(
				modifier = Modifier.padding(16.dp),
				verticalArrangement = Arrangement.spacedBy(8.dp)
			) {
				Text(
					text = "Password",
					style = MaterialTheme.typography.titleMedium
				)
				OutlinedTextField(
					value = password,
					onValueChange = { password = it },
					label = { Text("Password") },
					visualTransformation = PasswordVisualTransformation(),
					keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
					modifier = Modifier.fillMaxWidth()
				)
			}
		}

		// Address Section
		Card(
			modifier = Modifier
				.fillMaxWidth()
				.rotate(addressRotationDegrees),
			elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
		) {
			Column(
				modifier = Modifier.padding(16.dp),
				verticalArrangement = Arrangement.spacedBy(8.dp)
			) {
				Text(
					text = "Address Information",
					style = MaterialTheme.typography.titleMedium
				)
				OutlinedTextField(
					value = streetAddress,
					onValueChange = { streetAddress = it },
					label = { Text("Street Address") },
					modifier = Modifier.fillMaxWidth()
				)
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.spacedBy(8.dp)
				) {
					OutlinedTextField(
						value = city,
						onValueChange = { city = it },
						label = { Text("City") },
						modifier = Modifier.weight(1f)
					)
					OutlinedTextField(
						value = state,
						onValueChange = { state = it },
						label = { Text("State") },
						modifier = Modifier.weight(1f)
					)
				}
				OutlinedTextField(
					value = zipCode,
					onValueChange = { zipCode = it },
					label = { Text("ZIP Code") },
					keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
					modifier = Modifier.fillMaxWidth()
				)
			}
		}

		// Credit Card Section
		Card(
			modifier = Modifier.fillMaxWidth(),
			elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
		) {
			Column(
				modifier = Modifier.padding(16.dp),
				verticalArrangement = Arrangement.spacedBy(8.dp)
			) {
				Text(
					text = "Credit Card Information",
					style = MaterialTheme.typography.titleMedium
				)
				OutlinedTextField(
					value = cardholderName,
					onValueChange = { cardholderName = it },
					label = { Text("Cardholder Name") },
					modifier = Modifier.fillMaxWidth()
				)
				OutlinedTextField(
					value = creditCardNumber,
					onValueChange = { creditCardNumber = it },
					label = { Text("Card Number") },
					keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
					modifier = Modifier.fillMaxWidth()
				)
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.spacedBy(8.dp)
				) {
					OutlinedTextField(
						value = expiryDate,
						onValueChange = { expiryDate = it },
						label = { Text("MM/YY") },
						keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
						modifier = Modifier.weight(1f)
					)
					OutlinedTextField(
						value = cvv,
						onValueChange = { cvv = it },
						label = { Text("CVV") },
						keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
						visualTransformation = PasswordVisualTransformation(),
						modifier = Modifier.weight(1f)
					)
				}
			}
		}

		// Submit Button
		Button(
			onClick = { /* Handle form submission */ },
			modifier = Modifier.fillMaxWidth()
		) {
			Text("Submit Information")
		}
	}
}

@Preview(showBackground = true)
@Composable
fun UserInfoFormPreview() {
	AndroidObservabilityTheme {
		UserInfoForm()
	}
}


