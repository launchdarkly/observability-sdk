package com.example.androidobservability.masking

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.annotation.IdRes
import com.example.androidobservability.R
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.ContentDelegate
import org.mozilla.geckoview.GeckoView

class XMLWebActivity : ComponentActivity() {

    private val url = "https://www.google.com"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webview)

        setupWebView(R.id.webview)
        setupWebView(R.id.customWebView)
        setupGeckoWebView(R.id.geckoview)
        setupGeckoWebView(R.id.customGeckoView)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView(@IdRes webViewId: Int) {
        val webView = findViewById<WebView>(webViewId)
        webView.settings.javaScriptEnabled = true
        webView.webViewClient = WebViewClient()
        webView.loadUrl(url)
    }

    private fun setupGeckoWebView(@IdRes geckoViewId: Int) {
        val view = findViewById<GeckoView?>(geckoViewId)
        val session = GeckoSession()

        session.setContentDelegate(object : ContentDelegate {})

        GeckoRuntime.getDefault(application).let {
            session.open(it)
        }
        view?.setSession(session)
        session.loadUri(url)
    }
}
