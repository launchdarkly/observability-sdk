package com.example.androidobservability.masking

import android.content.Context
import android.util.AttributeSet
import android.webkit.WebView
import org.mozilla.geckoview.GeckoView

class CustomWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr)

class CustomGeckoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GeckoView(context, attrs)
