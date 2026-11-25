package com.example.androidobservability.masking

import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import com.example.androidobservability.R
import com.launchdarkly.observability.api.ldMask
import com.launchdarkly.observability.api.ldUnmask

class XMLNestedMaskingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nested_masking)

        findViewById<View>(R.id.section_unmasked_ancestor).ldUnmask()
        findViewById<View>(R.id.section_masked_ancestor).ldMask()
        findViewById<View>(R.id.section_deep_unmasked_ancestor).ldUnmask()
    }
}
