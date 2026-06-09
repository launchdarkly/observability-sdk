package com.example.androidobservability;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.androidobservability.masking.XMLMaskingActivity;
import com.example.androidobservability.masking.XMLUserFormActivity;
import com.example.androidobservability.masking.XMLWebActivity;
import com.example.androidobservability.smoothie.SmoothieListActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.launchdarkly.observability.sdk.LDReplay;

public class MainActivity extends AppCompatActivity {

    private ViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        viewModel = new ViewModelProvider(this).get(ViewModel.class);

        setupToolbarSubtitle();
        setupMaskingButtons();
        setupSessionReplayToggle();
        setupIdentifyButtons();
        setupInstrumentationButtons();
        setupMetricButtons();
        setupCustomerApiButtons();
    }

    private void setupToolbarSubtitle() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        boolean isComposeAvailable;
        try {
            Class.forName("androidx.compose.ui.platform.AbstractComposeView");
            isComposeAvailable = true;
        } catch (ClassNotFoundException e) {
            isComposeAvailable = false;
        }
        if (isComposeAvailable) {
            toolbar.setSubtitle("Compose Detected");
            toolbar.setSubtitleTextColor(Color.parseColor("#FFFFAB40"));
        } else {
            toolbar.setSubtitle("XML Views (Java)");
            toolbar.setSubtitleTextColor(Color.parseColor("#FF4CAF50"));
        }
    }

    private void setupMaskingButtons() {
        bindActivityButton(R.id.btn_user_form_xml, XMLUserFormActivity.class);
        bindActivityButton(R.id.btn_smoothies_xml, SmoothieListActivity.class);
        bindActivityButton(R.id.btn_check_xml, XMLMaskingActivity.class);
        bindActivityButton(R.id.btn_webviews_xml, XMLWebActivity.class);
    }

    private void setupSessionReplayToggle() {
        SwitchMaterial toggle = findViewById(R.id.switch_session_replay);
        toggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                LDReplay.INSTANCE.start();
            } else {
                LDReplay.INSTANCE.stop();
            }
        });
    }

    private void setupIdentifyButtons() {
        findViewById(R.id.btn_identify_user).setOnClickListener(v -> viewModel.identifyUser());
        findViewById(R.id.btn_identify_multi).setOnClickListener(v -> viewModel.identifyMulti());
        findViewById(R.id.btn_identify_anon).setOnClickListener(v -> viewModel.identifyAnonymous());
    }

    private void setupInstrumentationButtons() {
        findViewById(R.id.btn_http_request).setOnClickListener(v -> viewModel.triggerHttpRequests());
        findViewById(R.id.btn_foreground_service).setOnClickListener(v -> viewModel.startForegroundService());
        findViewById(R.id.btn_background_service).setOnClickListener(v -> viewModel.startBackgroundService());
        findViewById(R.id.btn_crash).setOnClickListener(v -> viewModel.triggerCrash());
    }

    private void setupMetricButtons() {
        findViewById(R.id.btn_metric).setOnClickListener(v -> viewModel.triggerMetric());
        findViewById(R.id.btn_histogram).setOnClickListener(v -> viewModel.triggerHistogramMetric());
        findViewById(R.id.btn_count).setOnClickListener(v -> viewModel.triggerCountMetric());
        findViewById(R.id.btn_incremental).setOnClickListener(v -> viewModel.triggerIncrementalMetric());
        findViewById(R.id.btn_up_down_counter).setOnClickListener(v -> viewModel.triggerUpDownCounterMetric());
    }

    private void setupCustomerApiButtons() {
        EditText editLogMessage = findViewById(R.id.edit_log_message);
        EditText editSpanName = findViewById(R.id.edit_span_name);
        EditText editFlagKey = findViewById(R.id.edit_flag_key);

        findViewById(R.id.btn_trigger_error).setOnClickListener(v -> viewModel.triggerError());
        findViewById(R.id.btn_trigger_log).setOnClickListener(v -> viewModel.triggerLog());
        findViewById(R.id.btn_send_custom_log).setOnClickListener(v ->
                viewModel.triggerCustomLog(editLogMessage.getText().toString()));
        findViewById(R.id.btn_nested_spans).setOnClickListener(v -> viewModel.triggerNestedSpans());
        findViewById(R.id.btn_send_custom_span).setOnClickListener(v ->
                viewModel.triggerCustomSpan(editSpanName.getText().toString()));
        findViewById(R.id.btn_evaluate_flag).setOnClickListener(v ->
                viewModel.evaluateBooleanFlag(editFlagKey.getText().toString()));
    }

    private void bindActivityButton(int buttonId, Class<? extends Activity> activityClass) {
        MaterialButton button = findViewById(buttonId);
        button.setOnClickListener(v -> startActivity(new Intent(this, activityClass)));
    }
}
