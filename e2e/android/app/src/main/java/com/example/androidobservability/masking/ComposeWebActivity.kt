package com.example.androidobservability.masking

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
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
import androidx.compose.runtime.remember
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
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .verticalScroll(rememberScrollState())
                    ) {
                        val context = LocalContext.current

                        Text(
                            text = "android.webkit.WebView",
                            fontSize = 16.sp,
                            modifier = Modifier
                                .background(Color.Yellow)
                                .align(Alignment.CenterHorizontally)
                        )
                        WebContent(
                            url = "https://www.google.com",
                            webView = WebView(context),
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
                        WebContent(
                            url = "https://www.google.com",
                            webView = CustomWebView(context),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(450.dp)
                        )

                        Text(
                            text = "org.mozilla.geckoview.GeckoView",
                            fontSize = 16.sp,
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .background(Color.Yellow)
                                .padding(top = 8.dp)
                        )
                        GeckoWebContent(
                            url = "https://www.google.com",
                            geckoView = GeckoView(context),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(450.dp)
                        )

                        Text(
                            text = "CustomGeckoView",
                            fontSize = 16.sp,
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .background(Color.Yellow)
                                .padding(top = 8.dp)
                        )
                        GeckoWebContent(
                            url = "https://www.google.com",
                            geckoView = CustomGeckoView(context),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(450.dp)
                        )
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebContent(url: String, webView: WebView, modifier: Modifier = Modifier) {
    val webView = remember {
        webView.apply {
            settings.javaScriptEnabled = true
            webViewClient = WebViewClient()
            loadUrl(url)
        }
    }

    DisposableEffect(webView) {
        onDispose {
            webView.destroy()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { webView },
        update = { view ->
            if (view.url != url) {
                view.loadUrl(url)
            }
        }
    )
}

@Composable
fun GeckoWebContent(url: String, geckoView: GeckoView, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val runtime = remember {
        GeckoRuntime.getDefault(context.applicationContext)
    }
    val session = remember(runtime) {
        GeckoSession().apply {
            setContentDelegate(object : ContentDelegate {})
            open(runtime)
        }
    }

    DisposableEffect(session) {
        onDispose {
            session.close()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { _ ->
            geckoView.apply {
                setSession(session)
            }
        }
    )

    LaunchedEffect(url) {
        session.loadUri(url)
    }
}
