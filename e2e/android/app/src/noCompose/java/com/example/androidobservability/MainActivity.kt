package com.example.androidobservability

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.EditText
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.example.androidobservability.masking.XMLMaskingActivity
import com.example.androidobservability.masking.XMLUserFormActivity
import com.example.androidobservability.masking.XMLWebActivity
import com.example.androidobservability.smoothie.SmoothieListActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.launchdarkly.observability.sdk.LDReplay

class MainActivity : AppCompatActivity() {

    private val viewModel: ViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupToolbarSubtitle()
        setupMaskingButtons()
        setupSessionReplayToggle()
        setupIdentifyButtons()
        setupInstrumentationButtons()
        setupMetricButtons()
        setupCustomerApiButtons()
    }

    private fun setupToolbarSubtitle() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        val isComposeAvailable = try {
            Class.forName("androidx.compose.ui.platform.AbstractComposeView")
            true
        } catch (_: ClassNotFoundException) {
            false
        }
        if (isComposeAvailable) {
            toolbar.subtitle = "Compose Detected"
            toolbar.setSubtitleTextColor(Color.parseColor("#FFFFAB40"))
        } else {
            toolbar.subtitle = "XML Views"
            toolbar.setSubtitleTextColor(Color.parseColor("#FF4CAF50"))
        }
    }

    private fun setupMaskingButtons() {
        bindActivityButton(R.id.btn_user_form_xml, XMLUserFormActivity::class.java)
        bindActivityButton(R.id.btn_smoothies_xml, SmoothieListActivity::class.java)
        bindActivityButton(R.id.btn_check_xml, XMLMaskingActivity::class.java)
        bindActivityButton(R.id.btn_webviews_xml, XMLWebActivity::class.java)
    }

    private fun setupSessionReplayToggle() {
        findViewById<SwitchMaterial>(R.id.switch_session_replay).setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) LDReplay.start() else LDReplay.stop()
        }
    }

    private fun setupIdentifyButtons() {
        findViewById<MaterialButton>(R.id.btn_identify_user).setOnClickListener { viewModel.identifyUser() }
        findViewById<MaterialButton>(R.id.btn_identify_multi).setOnClickListener { viewModel.identifyMulti() }
        findViewById<MaterialButton>(R.id.btn_identify_anon).setOnClickListener { viewModel.identifyAnonymous() }
    }

    private fun setupInstrumentationButtons() {
        findViewById<MaterialButton>(R.id.btn_http_request).setOnClickListener { viewModel.triggerHttpRequests() }
        findViewById<MaterialButton>(R.id.btn_foreground_service).setOnClickListener { viewModel.startForegroundService() }
        findViewById<MaterialButton>(R.id.btn_background_service).setOnClickListener { viewModel.startBackgroundService() }
        findViewById<MaterialButton>(R.id.btn_crash).setOnClickListener { viewModel.triggerCrash() }
    }

    private fun setupMetricButtons() {
        findViewById<MaterialButton>(R.id.btn_metric).setOnClickListener { viewModel.triggerMetric() }
        findViewById<MaterialButton>(R.id.btn_histogram).setOnClickListener { viewModel.triggerHistogramMetric() }
        findViewById<MaterialButton>(R.id.btn_count).setOnClickListener { viewModel.triggerCountMetric() }
        findViewById<MaterialButton>(R.id.btn_incremental).setOnClickListener { viewModel.triggerIncrementalMetric() }
        findViewById<MaterialButton>(R.id.btn_up_down_counter).setOnClickListener { viewModel.triggerUpDownCounterMetric() }
    }

    private fun setupCustomerApiButtons() {
        val editLogMessage = findViewById<EditText>(R.id.edit_log_message)
        val editSpanName = findViewById<EditText>(R.id.edit_span_name)
        val editFlagKey = findViewById<EditText>(R.id.edit_flag_key)

        findViewById<MaterialButton>(R.id.btn_trigger_error).setOnClickListener { viewModel.triggerError() }
        findViewById<MaterialButton>(R.id.btn_trigger_log).setOnClickListener { viewModel.triggerLog() }
        findViewById<MaterialButton>(R.id.btn_send_custom_log).setOnClickListener {
            viewModel.triggerCustomLog(editLogMessage.text.toString())
        }
        findViewById<MaterialButton>(R.id.btn_nested_spans).setOnClickListener { viewModel.triggerNestedSpans() }
        findViewById<MaterialButton>(R.id.btn_send_custom_span).setOnClickListener {
            viewModel.triggerCustomSpan(editSpanName.text.toString())
        }
        findViewById<MaterialButton>(R.id.btn_evaluate_flag).setOnClickListener {
            viewModel.evaluateBooleanFlag(editFlagKey.text.toString())
        }
    }

    private fun bindActivityButton(buttonId: Int, activityClass: Class<out Activity>) {
        findViewById<MaterialButton>(buttonId).setOnClickListener {
            startActivity(Intent(this, activityClass))
        }
    }
}
