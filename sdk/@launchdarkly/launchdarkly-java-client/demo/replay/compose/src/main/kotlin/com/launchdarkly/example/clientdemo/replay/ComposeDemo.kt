package com.launchdarkly.example.clientdemo.replay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.launchdarkly.observability.api.ldId
import com.launchdarkly.observability.api.ldMask
import kotlin.system.exitProcess

/**
 * A Compose for Desktop window recorded for session replay. With the default privacy profile the
 * text fields are masked; the note is masked by `Modifier.ldMask()`; the rest is shown. The Save
 * button's clicks are reported by its `Modifier.ldId`.
 */
fun main() {
    val replay = ReplayDemo.start("launchdarkly-java-client-demo-replay-compose")
    // By default application() calls System.exit when the window closes, so nothing after it would
    // run. Returning instead lets replay.stop() send what is queued before the process ends.
    application(exitProcessOnExit = false) {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Session replay demo: Compose for Desktop",
            state = rememberWindowState(size = DpSize(640.dp, 600.dp)),
        ) {
            var email by remember { mutableStateOf("jane@example.com") }
            var password by remember { mutableStateOf("hunter2") }
            MaterialTheme {
                Column(
                    Modifier.fillMaxSize().background(Color.White).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Account settings (Compose)", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(email, { email = it }, label = { Text("Email") }, modifier = Modifier.width(320.dp))
                    OutlinedTextField(
                        password, { password = it }, label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(), modifier = Modifier.width(320.dp),
                    )
                    Button(onClick = replay::save, modifier = Modifier.ldId("save")) { Text("Save") }
                    Text(ReplayDemo.NOTE, Modifier.ldMask())
                    Text("Flags", fontWeight = FontWeight.Bold)
                    ReplayDemo.FLAGS.forEach { Text(it) }
                    Text(replay.status(), color = Color.Gray)
                }
            }
        }
    }
    replay.stop()
    exitProcess(0)
}
