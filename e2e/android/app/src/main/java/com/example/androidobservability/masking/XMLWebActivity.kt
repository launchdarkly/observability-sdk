package com.example.androidobservability.masking

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.TextView
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

        setupLazyGeckoView(
            labelId = R.id.geckoLabel,
            containerId = R.id.geckoContainer,
            factory = { GeckoView(this) },
            label = "org.mozilla.geckoview.GeckoView (device)"
        )
        setupLazyGeckoView(
            labelId = R.id.customGeckoLabel,
            containerId = R.id.customGeckoContainer,
            factory = { CustomGeckoView(this) },
            label = "CustomGeckoView (device)"
        )
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView(@IdRes webViewId: Int) {
        val webView = findViewById<WebView>(webViewId)
        webView.settings.javaScriptEnabled = true
        webView.webViewClient = WebViewClient()
        webView.loadUrl(url)
    }

    private fun setupLazyGeckoView(
        @IdRes labelId: Int,
        @IdRes containerId: Int,
        factory: () -> GeckoView,
        label: String
    ) {
        val labelView = findViewById<TextView>(labelId)
        val container = findViewById<FrameLayout>(containerId)

        labelView.setOnClickListener { loadGeckoView(container, factory(), label, labelView) }
        container.setOnClickListener { loadGeckoView(container, factory(), label, labelView) }
    }

    private fun loadGeckoView(
        container: FrameLayout,
        geckoView: GeckoView,
        label: String,
        labelView: TextView
    ) {
        container.setOnClickListener(null)
        labelView.setOnClickListener(null)
        labelView.text = label

        val session = GeckoSession()
        session.setContentDelegate(object : ContentDelegate {})
        GeckoRuntime.getDefault(application).let { session.open(it) }
        geckoView.setSession(session)
        session.loadUri(url)

        container.addView(
            geckoView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
    }
}
