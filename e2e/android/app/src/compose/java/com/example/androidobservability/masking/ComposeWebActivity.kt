package com.example.androidobservability.masking

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.androidobservability.ui.theme.AndroidObservabilityTheme
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.ContentDelegate
import org.mozilla.geckoview.GeckoView

class ComposeWebActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AndroidObservabilityTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val context = LocalContext.current
                    val webView = remember(context) { WebView(context) }
                    val customWebView = remember(context) { CustomWebView(context) }
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = "android.webkit.WebView",
                                fontSize = 16.sp,
                                modifier = Modifier
                                    .background(Color.Yellow)
                                    .align(Alignment.CenterHorizontally)
                            )
                            WebViewItem(
                                url = "https://www.google.com",
                                webView = webView,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(450.dp)
                            )

                            Text(
                                text = "CustomWebView",
                                fontSize = 16.sp,
                                modifier = Modifier
                                    .background(Color.Yellow)
                                    .align(Alignment.CenterHorizontally)
                            )
                            WebViewItem(
                                url = "https://www.google.com",
                                webView = customWebView,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(450.dp)
                            )
                        }

                        LazyGeckoViewItem(
                            label = "org.mozilla.geckoview.GeckoView (device)",
                            url = "https://www.google.com",
                            geckoViewFactory = { GeckoView(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                        )

                        LazyGeckoViewItem(
                            label = "CustomGeckoView (device)",
                            url = "https://www.google.com",
                            geckoViewFactory = { CustomGeckoView(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                        )
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewItem(url: String, webView: WebView, modifier: Modifier = Modifier) {
    val rememberedWebView = remember(webView) {
        webView.apply {
            settings.javaScriptEnabled = true
            webViewClient = WebViewClient()
            loadUrl(url)
        }
    }

    DisposableEffect(rememberedWebView) {
        onDispose {
            rememberedWebView.destroy()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { rememberedWebView },
        update = { view ->
            if (view.url != url) {
                view.loadUrl(url)
            }
        }
    )
}

@Composable
fun LazyGeckoViewItem(
    label: String,
    url: String,
    geckoViewFactory: (android.content.Context) -> GeckoView,
    modifier: Modifier = Modifier
) {
    var loaded by remember { mutableStateOf(false) }

    Text(
        text = if (loaded) label else "Tap to load $label",
        fontSize = 16.sp,
        modifier = Modifier
            .background(Color.Yellow)
            .padding(top = 8.dp)
            .then(if (!loaded) Modifier.clickable { loaded = true } else Modifier)
    )

    if (loaded) {
        val context = LocalContext.current
        val geckoView = remember(context) { geckoViewFactory(context) }
        val runtime = remember { GeckoRuntime.getDefault(context.applicationContext) }
        val session = remember(runtime) {
            GeckoSession().apply {
                setContentDelegate(object : ContentDelegate {})
                open(runtime)
            }
        }

        DisposableEffect(session) {
            onDispose { session.close() }
        }

        AndroidView(
            modifier = modifier,
            factory = { _ -> geckoView.apply { setSession(session) } }
        )

        LaunchedEffect(url) { session.loadUri(url) }
    } else {
        Box(
            modifier = modifier
                .background(Color.LightGray)
                .clickable { loaded = true },
            contentAlignment = Alignment.Center
        ) {
            Text("Tap to load", color = Color.DarkGray)
        }
    }
}
